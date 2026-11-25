// Regenerate the optional frame-free page 1 from the attributed original:
// javac -d /tmp tools/FrameFreePage1.java
// java -cp /tmp FrameFreePage1 app/src/main/assets/mushaf/1.png app/src/main/assets/mushaf/1-no-frame.png
// Only source pixels within the printed title and verse bands are copied.
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class FrameFreePage1 {
    // Conservative text bands calibrated against the source page and ayah boxes.
    private static final int[][] VERSE_SEGMENTS = {
        {140, 200, 302, 230}, {114, 232, 341, 268},
        {84, 268, 363, 305}, {82, 303, 359, 340},
        {82, 337, 359, 379}, {112, 377, 321, 414},
        {152, 414, 285, 448}
    };

    public static void main(String[] args) throws Exception {
        BufferedImage original = ImageIO.read(new File(args[0]));
        BufferedImage cleaned = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int width = original.getWidth();
        int height = original.getHeight();
        boolean[] allowed = new boolean[width * height];
        boolean[] ink = new boolean[width * height];
        for (int y = 0; y < cleaned.getHeight(); y++) {
            for (int x = 0; x < cleaned.getWidth(); x++) cleaned.setRGB(x, y, 0xffffffff);
        }
        for (int[] box : VERSE_SEGMENTS) {
            for (int y = Math.max(0, box[1]); y < Math.min(height, box[3]); y++) {
                for (int x = Math.max(0, box[0]); x < Math.min(width, box[2]); x++) {
                    int argb = original.getRGB(x, y);
                    int red = (argb >>> 16) & 255;
                    int green = (argb >>> 8) & 255;
                    int blue = argb & 255;
                    int luminance = (77 * red + 150 * green + 29 * blue) >>> 8;
                    allowed[y * width + x] = true;
                    if ((argb >>> 24) > 0 && luminance < 205) ink[y * width + x] = true;
                }
            }
        }
        // Keep the original title glyph pixels, omitting the solid arch fill.
        for (int y = 49; y < 73; y++) {
            for (int x = 179; x < 271; x++) {
                int argb = original.getRGB(x, y);
                int red = (argb >>> 16) & 255;
                int green = (argb >>> 8) & 255;
                int blue = argb & 255;
                int luminance = (77 * red + 150 * green + 29 * blue) >>> 8;
                allowed[y * width + x] = true;
                if ((argb >>> 24) > 0 && luminance < 205) ink[y * width + x] = true;
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (!allowed[index]) continue;
                boolean nearInk = ink[index];
                for (int dy = -1; !nearInk && dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx >= 0 && nx < width && ny >= 0 && ny < height && ink[ny * width + nx]) {
                            nearInk = true;
                            break;
                        }
                    }
                }
                int argb = original.getRGB(x, y);
                if (nearInk && (argb >>> 24) > 0) cleaned.setRGB(x, y, argb);
            }
        }
        ImageIO.write(cleaned, "png", new File(args[1]));
    }
}
