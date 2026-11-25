// javac -d /tmp tools/FrameFreeMushafPages.java
// java -cp /tmp FrameFreeMushafPages app/src/main/assets/mushaf
// Keeps the original pixels and dimensions; removes only the ornamental
// surah-title bands and, on page 2, the full-page decoration.
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public class FrameFreeMushafPages {
    private static final int[][] PAGE_TWO_TEXT = {
        {145, 198, 300, 233}, // printed basmala
        {105, 231, 335, 271}, {82, 268, 360, 307},
        {78, 305, 365, 343}, {82, 341, 360, 381},
        {105, 378, 338, 417}, {145, 413, 295, 451}
    };

    public static void main(String[] args) throws Exception {
        File directory = new File(args[0]);
        int pages = 0, bands = 0;
        for (int page = 2; page <= 604; page++) {
            BufferedImage source = ImageIO.read(new File(directory, page + ".png"));
            BufferedImage output;
            if (page == 2) {
                output = pageTwo(source);
            } else {
                List<int[]> ornaments = findOrnaments(source);
                if (ornaments.isEmpty()) continue;
                bands += ornaments.size();
                output = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < source.getHeight(); y++) {
                    boolean ornament = false;
                    for (int[] band : ornaments) {
                        if (y >= band[0] && y <= band[1]) { ornament = true; break; }
                    }
                    if (!ornament) for (int x = 0; x < source.getWidth(); x++) {
                        output.setRGB(x, y, source.getRGB(x, y));
                    }
                }
            }
            if (!ImageIO.write(output, "png", new File(directory, page + "-no-frame.png"))) {
                throw new IllegalStateException("PNG encoder missing");
            }
            pages++;
        }
        if (pages != 97 || bands != 112) {
            throw new IllegalStateException("Expected 97 further framed pages and 112 title bands; got "
                + pages + " pages and " + bands + " bands");
        }
        System.out.println("Created " + pages + " transparent pages; removed " + bands + " title bands.");
    }

    // A title has two nearly full-width dark border strokes 57-59 rows apart.
    // The intervening decoration and title are removed together. White edge
    // pixels in the adjacent row belong to the same full-width banner.
    private static List<int[]> findOrnaments(BufferedImage image) {
        List<Integer> denseRows = new ArrayList<>();
        int width = image.getWidth(), height = image.getHeight();
        for (int y = 0; y < height; y++) {
            int dark = 0;
            for (int x = 0; x < width; x++) {
                int pixel = image.getRGB(x, y);
                int luminance = (77 * ((pixel >>> 16) & 255)
                    + 150 * ((pixel >>> 8) & 255) + 29 * (pixel & 255)) >>> 8;
                if ((pixel >>> 24) != 0 && luminance < 200) dark++;
            }
            if (dark * 4 > width * 3) denseRows.add(y);
        }
        List<int[]> groups = new ArrayList<>();
        for (int row : denseRows) {
            if (!groups.isEmpty() && row - groups.get(groups.size() - 1)[1] <= 62) {
                groups.get(groups.size() - 1)[1] = row;
            } else groups.add(new int[] {row, row});
        }
        for (int[] group : groups) {
            if (group[1] - group[0] < 57 || group[1] - group[0] > 59) {
                throw new IllegalStateException("Unexpected dense rows: " + group[0] + "-" + group[1]);
            }
            if (group[0] > 0 && mostlyOpaque(image, group[0] - 1)) group[0]--;
            if (group[1] + 1 < height && mostlyOpaque(image, group[1] + 1)) group[1]++;
        }
        return groups;
    }

    private static boolean mostlyOpaque(BufferedImage image, int y) {
        int opaque = 0;
        for (int x = 0; x < image.getWidth(); x++) {
            if ((image.getRGB(x, y) >>> 24) != 0) opaque++;
        }
        return opaque * 4 > image.getWidth() * 3;
    }

    private static BufferedImage pageTwo(BufferedImage source) {
        int width = source.getWidth(), height = source.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        boolean[] allowed = new boolean[width * height];
        boolean[] ink = new boolean[width * height];
        for (int[] box : PAGE_TWO_TEXT) {
            for (int y = box[1]; y < box[3]; y++) for (int x = box[0]; x < box[2]; x++) {
                int pixel = source.getRGB(x, y), index = y * width + x;
                int luminance = (77 * ((pixel >>> 16) & 255)
                    + 150 * ((pixel >>> 8) & 255) + 29 * (pixel & 255)) >>> 8;
                allowed[index] = true;
                if ((pixel >>> 24) != 0 && luminance < 205) ink[index] = true;
            }
        }
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int index = y * width + x;
            if (!allowed[index]) continue;
            boolean nearInk = false;
            for (int dy = -1; dy <= 1 && !nearInk; dy++) for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < width && ny < height && ink[ny * width + nx]) {
                    nearInk = true; break;
                }
            }
            if (nearInk) result.setRGB(x, y, source.getRGB(x, y));
        }
        return result;
    }
}
