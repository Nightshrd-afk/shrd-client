package dev.shrd.launcher.ui.pages;

import dev.shrd.launcher.Log;
import dev.shrd.launcher.ShrdPaths;
import dev.shrd.launcher.ui.App;
import dev.shrd.launcher.ui.Icons;
import dev.shrd.launcher.ui.Page;
import dev.shrd.launcher.ui.Theme;
import dev.shrd.launcher.ui.Ui;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;

/** Everything the launcher and the game printed, with the game's own log file one press away. */
public final class ConsolePage extends Page {

	private final Ui.Area area = new Ui.Area(20, 80);
	private final JScrollPane scroll;
	private final Ui.Switch autoScroll = new Ui.Switch(true, value -> {
	});

	public ConsolePage(App app) {
		super(app, "Console", "Launcher steps and the game's own output, live");

		actions().add(Ui.dim("Auto-scroll"));
		actions().add(Ui.hgap(8));
		actions().add(autoScroll);
		actions().add(Ui.hgap(14));
		actions().add(Ui.secondary("Copy").size(90, 34).onClick(button -> {
			java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents(new java.awt.datatransfer.StringSelection(area.getText()), null);
			app.status("Copied " + area.getText().length() + " characters of log");
		}));
		actions().add(Ui.hgap(8));
		actions().add(Ui.secondary("Clear").size(90, 34).onClick(button -> {
			Log.clear();
			area.setText("");
		}));
		actions().add(Ui.hgap(8));
		actions().add(Ui.secondary("Open logs").size(120, 34).withGlyph(Icons::folder).onClick(button -> app.openFolder(ShrdPaths.logs())));

		Ui.Card card = new Ui.Card("Output");
		scroll = Ui.scroll(area);
		card.body().add(scroll);
		body(card);

		Log.addListener(line -> SwingUtilities.invokeLater(() -> append(line)));
	}

	private void append(Log.Line line) {
		// A JTextArea's document is a StyledDocument; the interface is what carries attributes.
		StyledDocument document = (StyledDocument) area.getDocument();
		SimpleAttributeSet attributes = new SimpleAttributeSet();
		StyleConstants.setForeground(attributes, colorFor(line.level()));

		try {
			document.insertString(document.getLength(), line.time() + "  " + line.text() + System.lineSeparator(), attributes);
		} catch (BadLocationException ignored) {
			// Appending at the end cannot realistically fail; ignoring keeps the UI alive if it does.
		}

		if (autoScroll.isOn()) {
			area.setCaretPosition(document.getLength());
		}
	}

	private Color colorFor(Log.Level level) {
		return switch (level) {
			case TITLE -> Theme.TEXT_ACCENT;
			case WARN -> Theme.WARN;
			case ERROR -> Theme.BAD;
			case GAME -> Theme.TEXT;
			default -> Theme.TEXT_DIM;
		};
	}

	@Override
	public void reload() {
		setSubtitle("Launcher steps and the " + app.config.active().name + " game log, live");
	}
}
