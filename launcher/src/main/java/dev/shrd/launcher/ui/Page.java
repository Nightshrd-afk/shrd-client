package dev.shrd.launcher.ui;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;

/** Common scaffolding for a content page: title, subtitle, optional header actions, body. */
public abstract class Page extends JPanel {

	protected final App app;
	private final JLabel subtitleLabel;
	private final JPanel actions;
	private final JPanel body;
	private final JPanel header;

	protected Page(App app, String title, String subtitle) {
		this.app = app;
		setOpaque(false);
		setLayout(new BorderLayout(0, 18));
		setBorder(new EmptyBorder(20, 24, 12, 24));

		header = Ui.row();
		JPanel titles = Ui.column();
		titles.add(Ui.pageTitle(title));
		subtitleLabel = Ui.dim(subtitle);
		titles.add(Ui.gap(4));
		titles.add(subtitleLabel);
		header.add(titles);
		header.add(javax.swing.Box.createHorizontalGlue());

		actions = Ui.row();
		header.add(actions);
		add(header, BorderLayout.NORTH);

		body = Ui.row(new BorderLayout(18, 0));
		body.setOpaque(false);
		add(body, BorderLayout.CENTER);
	}

	protected void setSubtitle(String text) {
		subtitleLabel.setText(text);
	}

	protected JPanel actions() {
		return actions;
	}

	/**
	 * Drops the title block. Home does this: its own brand tabs say where you are, and a heading
	 * above them would only push the banner down.
	 */
	protected void hideHeader() {
		header.setVisible(false);
		setBorder(new EmptyBorder(10, 24, 12, 24));
	}

	protected JPanel body() {
		return body;
	}

	protected void body(JComponent component) {
		body.add(component, BorderLayout.CENTER);
	}

	/** Called whenever the page becomes visible or the config changes. */
	public void reload() {
	}

	@Override
	public Dimension getPreferredSize() {
		Dimension size = super.getPreferredSize();

		if (size.width < 200) {
			size.width = 200;
		}

		return size;
	}
}
