"""Rebuild the numbered pages from the source WebP without resampling Arabic ink.

The source checkout is Miftah-Fentaw/Quran-Images, commit cca352724b9f29e326e077ce142172ddd9006368.
Regular pages only gain transparent space between intact printed lines. Printed
numerals are retained while isolated ornamental rings are exchanged for red
circles. Source title glyphs are retained and framed with the Glossa floral mask.
The accompanying JSON records hit areas in the new image coordinates.
"""

import json
import sys
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw


RED = (239, 77, 77, 255)
SOURCE_SIZE = (1080, 1440)
CROP = (40, 1040)
GAP = 33
ROOT = Path(__file__).resolve().parent.parent
MOTIF = Image.open(ROOT / "design/surah-title-floral-mask.png").convert("RGBA")


def alpha(source):
    return np.asarray(source.getchannel("A"))


def ring_templates(source):
    samples = [(4, 150, 115, 85, 65), (14, 70, 216, 72, 54),
               (270, 451, 212, 80, 60)]
    result = []
    for page, x, y, width, height in samples:
        a = alpha(Image.open(source / f"{page:03}.webp").convert("RGBA"))
        _, labels, stats, _ = cv2.connectedComponentsWithStats((a > 16).astype("uint8"), 8)
        matches = [i for i in range(1, len(stats)) if tuple(stats[i, :4]) == (x, y, width, height)]
        assert len(matches) == 1, (page, matches)
        result.append((a[y:y+height, x:x+width], (labels[y:y+height, x:x+width] == matches[0])))
    return result


def markers(image, templates, page, expected):
    a = alpha(image)
    if page <= 2:
        _, labels, stats, _ = cv2.connectedComponentsWithStats((a > 16).astype("uint8"), 8)
        rings = [(x+w//2, y+h//2, labels == i) for i, (x,y,w,h,area) in enumerate(stats)
                 if i and 63 <= w <= 66 and h == 49 and 700 <= area <= 800]
        assert len(rings) == (7 if page == 1 else 5), (page, len(rings))
        return sorted(rings, key=lambda m: (m[1], -m[0]))
    if page == 3:
        _, labels, stats, _ = cv2.connectedComponentsWithStats((a > 16).astype("uint8"), 8)
        rings = [(x+w//2,y+h//2,labels == i) for i,(x,y,w,h,area) in enumerate(stats)
                 if i and 90<=w<=92 and 66<=h<=68 and 1250<=area<=1400]
        assert len(rings) == 11, (page,len(rings))
        return sorted(rings,key=lambda m:(m[1]//75,-m[0]))

    # Correlation locates the source's printed ornament, including those whose
    # outer edge touches a letter and therefore is not a separate component.
    hits = []
    for style, (template, ring) in enumerate(templates):
        scores = cv2.matchTemplate(a, template, cv2.TM_CCOEFF_NORMED)
        peaks = (scores == cv2.dilate(scores, np.ones((27, 27), np.uint8))) & (scores > .45)
        for y, x in zip(*np.where(peaks)):
            cx, cy = x + template.shape[1]//2, y + template.shape[0]//2
            if all(abs(cx - old[0]) > 30 or abs(cy - old[1]) > 30 for old in hits):
                mask = np.zeros(a.shape, bool)
                mask[y:y+ring.shape[0], x:x+ring.shape[1]] = ring
                hits.append((cx, cy, mask))
        if len(hits)==expected:
            break
    return sorted(hits, key=lambda m: (round((m[1]-52)/93), -m[0]))


def erase_and_redraw(image, positions, page):
    a = alpha(image).copy()
    for cx,cy,mask in positions:
        # The numeral is a distinct source ink component. Remove only the
        # matched outer ornament and its one-pixel antialias fringe.
        a[cv2.dilate(mask.astype("uint8"),np.ones((3,3),np.uint8)).astype(bool)] = 0
    # The WebP RGB channels carry a gray haze even where alpha already encodes
    # the antialias edge. Page 3's crisp pilot used black ink with that alpha.
    result = Image.new("RGBA", image.size, (0, 0, 0, 0))
    result.putalpha(Image.fromarray(a,"L"))
    draw = ImageDraw.Draw(result)
    for cx,cy,_ in positions:
        radius = 22 if page <= 2 else 25
        draw.ellipse((cx-radius,cy-radius,cx+radius,cy+radius),outline=RED,width=2)
    return result


def line_cuts(image, page):
    a = alpha(image)
    gaps=[]
    if page == 3:
        cuts = [96,189,288,384,480,576,672,768,864,960,1056,1152,1249,1344]
        gaps=[True]*14
    else:
        cuts = []
        for n in range(1,15):
            target=93*n+5
            possible=[y for y in range(target-28,target+29) if not np.any(a[y]>16)]
            safe=bool(possible)
            if not safe:
                counts=[(int(np.count_nonzero(a[y]>16)),y) for y in range(target-28,target+29)]
                least,y=min(counts,key=lambda item:(item[0],abs(item[1]-target)))
                if least>12:raise ValueError(f"Page {page} line {n}: cut intersects {least} ink pixels")
                possible=[y]
            cuts.append(min(possible,key=lambda y:abs(y-target)))
            gaps.append(safe)
    if any(np.any(a[y]) for y in cuts):
        a=a.copy()
        for y,safe in zip(cuts,gaps):
            if safe:a[y,a[y]<=16]=0
        image.putalpha(Image.fromarray(a,"L"))
    return cuts,gaps


def slot_widths(image):
    a=alpha(image)
    widths=[]
    for slot in range(15):
        band=a[93*slot+15:min(1440,93*slot+75)]>16
        xs=np.where(band)[1]
        widths.append((int(xs.max()-xs.min()),int((xs.max()+xs.min())//2)) if len(xs) else (0,0))
    return widths


def title_slots(source, page, image):
    """Find source title rows, including titles moved across page boundaries.

    Every source title is a narrow centered row followed by the distinctive
    centered Bismillah row. At-Tawbah has no Bismillah, by design.
    """
    widths=slot_widths(image)
    next_first=slot_widths(Image.open(source/f'{page+1:03}.webp').convert('RGBA'))[0] if page<604 else (0,0)
    slots=[]
    for slot,(width,center) in enumerate(widths,1):
        following=widths[slot] if slot<15 else next_first
        if (100<width<500 and 400<center<680 and
            (520<following[0]<700 and 480<following[1]<640 or page==187 and slot==1)):
            slots.append(slot)
    return slots


def begins_with_bismillah(source,page,image):
    if page<=3:return False
    first=slot_widths(image)[0]
    if not (520<first[0]<700 and 480<first[1]<640):return False
    previous=Image.open(source/f'{page-1:03}.webp').convert('RGBA')
    title=slot_widths(previous)[-1]
    return 100<title[0]<500 and 400<title[1]<680


def banner(image, top, bottom):
    draw=ImageDraw.Draw(image)
    x1,x2=5,image.width-6
    y1,y2=top+6,bottom-6
    draw.rectangle((x1,y1,x2,y2),outline=RED,width=2)
    for crop, right in ((MOTIF.crop((0,0,82,32)),False),
                        (MOTIF.crop((272,0,357,32)),True)):
        width=round(crop.width*(y2-y1)/crop.height)
        motif=crop.resize((width,y2-y1),Image.Resampling.LANCZOS)
        tint=Image.new("RGBA",motif.size,RED)
        tint.putalpha(motif.getchannel("A"))
        image.alpha_composite(tint,(x2-width if right else x1,y1))


def spaced_image(image,cuts,gaps):
    x1,x2=CROP
    a=alpha(image)
    if np.any(a[:,:x1]) or np.any(a[:,x2:]):
        # Some printed markers reach beyond the standard crop. Give those
        # pages only the extra pixels they need, instead of shrinking the
        # entire page to the full 1080-pixel source width.
        left,_,right,_=image.getbbox()
        width=min(1080,max(1000,right-left+8))
        x1=max(0,min(1080-width,(left+right-width)//2))
        x2=x1+width
    out=Image.new("RGBA",(x2-x1,1440+14*GAP))
    previous=0;offset=0
    for i,end in enumerate((*cuts,1440)):
        out.paste(image.crop((x1,previous,x2,end)),(0,previous+offset))
        if i<14 and gaps[i]:offset+=GAP
        previous=end
    return out,x1


def opening(image,page):
    # The source opening artwork is already frame-free. Place its unaltered
    # printed lines and title at the reference's sparse portrait proportions.
    out=Image.new("RGBA",(1000,1902))
    title=image.crop((180,48,900,115)).resize((936,87),Image.Resampling.LANCZOS)
    text=image.crop((190,132,890,680)).resize((875,685),Image.Resampling.LANCZOS)
    out.alpha_composite(title,(32,414))
    out.alpha_composite(text,(62,700))
    banner(out,422,507)
    return out


def opening_regions(page,positions,metadata):
    refs=[]
    for item in metadata:
        ref=(item[1],item[2])
        if ref not in refs:refs.append(ref)
    if len(refs)!=len(positions):raise ValueError((page,len(refs),len(positions)))
    areas=[];cursor=0
    for row in range(0 if page==1 else 1,7):
        top=700+row*95
        bottom=top+95
        row_end=132+(row+1)*76
        row_start=132+row*76
        row_markers=sorted((p for p in positions if row_start<=p[1]<row_end),
                           key=lambda p:-p[0])
        right=937
        for cx,cy,_ in row_markers:
            split=round(62+(cx-190)*1.25)
            areas.append([*refs[cursor],split,top,right,bottom])
            right=split;cursor+=1
        if cursor<len(refs):areas.append([*refs[cursor],62,top,right,bottom])
    if cursor!=len(refs):raise ValueError((page,cursor,len(refs)))
    return areas


def regions(page, markers_on_page, cuts, gaps, image_width, xcrop, metadata, slots, leading_basmalah):
    # The source's marker order is the definitive end-of-ayah order. This
    # avoids reusing horizontal bounds measured on the older small artwork.
    refs=[]
    for b in metadata:
        r=(b[1],b[2])
        if r not in refs:refs.append(r)
    if len(refs)!=len(markers_on_page):
        raise ValueError(f"Page {page}: {len(markers_on_page)} source markers but {len(refs)} verses")
    first_line=min(b[0] for b in metadata)
    if slots and slots[0]==1 and first_line==1:
        first_line=2 if page==187 else 3
    if leading_basmalah:first_line=max(first_line,2)
    cursor=0; areas=[]
    for line in range(first_line,16):
        if line in slots or (page!=187 and line-1 in slots):continue
        offset=sum(gaps[:line-1])*GAP
        top=0 if line==1 else cuts[line-2]+offset
        bottom=1440+offset if line==15 else cuts[line-1]+offset
        in_line=sorted((m for m in markers_on_page if (sum(c < m[1] for c in cuts)+1)==line),reverse=True)
        right=image_width
        for cx,cy,_ in in_line:
            if cursor>=len(refs):raise ValueError(f"Page {page}: marker exceeds verse list")
            split=cx-xcrop
            if right>split:areas.append([*refs[cursor],int(max(0,split)),int(top),int(right),int(bottom)])
            right=split
            cursor+=1
        if cursor<len(refs) and right>0:
            areas.append([*refs[cursor],0,int(top),int(right),int(bottom)])
    if cursor!=len(refs):raise ValueError(f"Page {page}: mapped {cursor}/{len(refs)} markers")
    return areas


def build(source, old, target, selected=None):
    templates=ring_templates(source)
    meta=json.loads((old/'ayah_bounds.json').read_text())
    mapping_path=target/'highres_ayah_bounds.json'
    mapping=json.loads(mapping_path.read_text()) if mapping_path.exists() else {}
    pages=selected or range(1,605)
    marker_total=0
    banner_total=0
    for page in pages:
        image=Image.open(source/f'{page:03}.webp').convert('RGBA')
        if image.size!=SOURCE_SIZE:raise ValueError((page,image.size))
        expected=len({(b[1],b[2]) for b in meta[str(page)]})
        positions=markers(image,templates,page,expected)
        source_title_slots=title_slots(source,page,image) if page>2 else []
        leading_basmalah=begins_with_bismillah(source,page,image) if page>2 else False
        marker_total+=len(positions)
        banner_total+=len(source_title_slots) if page>2 else 1
        image=erase_and_redraw(image,positions,page)
        if page<=2:
            out=opening(image,page)
            mapping[str(page)]={'size':[out.width,out.height],
                'areas':opening_regions(page,positions,meta[str(page)])}
        else:
            cuts,gaps=line_cuts(image,page)
            out,xcrop=spaced_image(image,cuts,gaps)
            for slot in source_title_slots:
                offset=sum(gaps[:slot-1])*GAP
                top=0 if slot==1 else cuts[slot-2]+offset
                bottom=1440+offset if slot==15 else cuts[slot-1]+offset
                banner(out,top,bottom)
            mapping[str(page)]={'size':[out.width,out.height],
                'areas':regions(page,positions,cuts,gaps,out.width,xcrop,meta[str(page)],source_title_slots,leading_basmalah)}
        out.save(target/f'{page}.png',optimize=True)
        if page%10==0:mapping_path.write_text(json.dumps(mapping,separators=(',',':')))
        if page%50==0:print(f"processed {page}",flush=True)
    mapping_path.write_text(json.dumps(mapping,separators=(',',':')))
    if len(mapping)==604:
        assert sum(len({(b[1],b[2]) for b in meta[str(p)]}) for p in range(1,605))==6236
        if selected is None:
            assert marker_total==6236 and banner_total==114,(marker_total,banner_total)
        print(f"Complete: 604 pages and 6,236 mapped ayahs; this run drew {marker_total} circles and {banner_total} banners.")


if __name__=='__main__':
    output=Path(sys.argv[3]);output.mkdir(parents=True,exist_ok=True)
    selected=(range(int(sys.argv[5]),605) if len(sys.argv)>5 and sys.argv[4]=='--from'
              else [int(p) for p in sys.argv[4:]] if len(sys.argv)>4 else None)
    build(Path(sys.argv[1]),Path(sys.argv[2]),output,selected)
