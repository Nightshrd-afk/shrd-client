import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Counts exact matches of Shrd Client's opaque theme colours in a screenshot.
 * <p>
 * The client draws its accents and body text with fully opaque colours, so finding them in a
 * captured frame is direct evidence that the overlays or the ClickGUI actually rendered —
 * not merely that the game started.
 * <p>
 * Usage: {@code java tools/UiCheck.java <screenshot.png> [required,comma,separated] [minPixels]}
 * <p>
 * Colour keys: accent, accentAlt, text, textDim, textDisabled.
 */
public final class UiCheck {

	private static final Map<String, Integer> COLORS = new LinkedHashMap<>();

	static {
		// Opaque theme colours from dev/shrd/client/gui/Theme.java (purple on black).
		COLORS.put("accent", 0xFFA855F7);
		COLORS.put("accentAlt", 0xFFC084FC);
		COLORS.put("text", 0xFFEFE7FB);
		COLORS.put("textDim", 0xFF9E93B8);
		COLORS.put("textAccent", 0xFFCDA9FF);
		COLORS.put("textDisabled", 0xFF6B6280);
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			System.err.println("usage: UiCheck <screenshot.png> [required,comma,separated] [minPixels]");
			System.exit(2);
		}

		String requiredList = args.length > 1 ? args[1] : "accent,text";
		int minPixels = args.length > 2 ? Integer.parseInt(args[2]) : 30;
		Set<String> required = new HashSet<>(Arrays.asList(requiredList.split(",")));

		BufferedImage image = ImageIO.read(new File(args[0]));

		if (image == null) {
			System.err.println("could not read " + args[0]);
			System.exit(2);
		}

		Map<String, Integer> counts = new LinkedHashMap<>();

		for (String key : COLORS.keySet()) {
			counts.put(key, 0);
		}

		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				long argb = image.getRGB(x, y) & 0xFFFFFFFFL;

				for (Map.Entry<String, Integer> entry : COLORS.entrySet()) {
					if (argb == (entry.getValue() & 0xFFFFFFFFL)) {
						counts.merge(entry.getKey(), 1, Integer::sum);
					}
				}
			}
		}

		System.out.println("screenshot: " + args[0] + "  " + image.getWidth() + "x" + image.getHeight());

		boolean allRequired = true;

		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			boolean isRequired = required.contains(entry.getKey());
			boolean present = entry.getValue() >= minPixels;

			if (isRequired && !present) {
				allRequired = false;
			}

			System.out.printf("  %-13s %-9s %-8s %d px%n",
				entry.getKey(), isRequired ? "REQUIRED" : "optional", present ? "FOUND" : "MISSING", entry.getValue());
		}

		System.out.println(allRequired
			? "RESULT: PASS — required shrd UI colours are on screen"
			: "RESULT: FAIL — a required shrd UI colour is missing");
		System.exit(allRequired ? 0 : 1);
	}
}
