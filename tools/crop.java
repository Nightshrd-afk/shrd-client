import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Crops and upscales a region of a screenshot so small HUD text can be read back.
 * Usage: java tools/crop.java <in.png> <out.png> <x> <y> <width> <height> <scale>
 */
public class crop {

	public static void main(String[] args) throws Exception {
		BufferedImage source = ImageIO.read(new File(args[0]));
		int x = Integer.parseInt(args[2]);
		int y = Integer.parseInt(args[3]);
		int width = Math.min(Integer.parseInt(args[4]), source.getWidth() - x);
		int height = Math.min(Integer.parseInt(args[5]), source.getHeight() - y);
		int scale = Integer.parseInt(args[6]);

		BufferedImage out = new BufferedImage(width * scale, height * scale, BufferedImage.TYPE_INT_ARGB);

		for (int py = 0; py < height * scale; py++) {
			for (int px = 0; px < width * scale; px++) {
				out.setRGB(px, py, source.getRGB(x + px / scale, y + py / scale));
			}
		}

		ImageIO.write(out, "png", new File(args[1]));
		System.out.println("wrote " + args[1] + " " + out.getWidth() + "x" + out.getHeight());
	}
}
