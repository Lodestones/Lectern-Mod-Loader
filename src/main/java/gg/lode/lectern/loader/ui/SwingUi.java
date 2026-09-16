package gg.lode.lectern.loader.ui;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ImageIcon;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.io.InputStream;
import java.net.URI;
import java.time.Year;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class SwingUi implements LoaderUi {

    private static final Color BACKGROUND = new Color(0x16, 0x16, 0x1A);
    private static final Color FOREGROUND = new Color(0xEC, 0xEC, 0xF1);
    private static final Color MUTED = new Color(0x9A, 0x9A, 0xA8);
    private static final Color ACCENT = new Color(0xB4, 0x8C, 0xFF);
    private static final String LECTERN_PAGE = "https://lode.gg/mod/lectern";

    private JFrame progress;
    private JProgressBar bar;
    private JLabel status;
    private long total;

    public SwingUi() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception keepTheDefault) {
            // Any look and feel will do.
        }
    }

    @Override
    public boolean askFirstRun(String displayName) {
        AtomicReference<Boolean> answer = new AtomicReference<>(Boolean.TRUE);
        onSwingThread(() -> {
            JCheckBox opted = checkBox("Keep " + displayName + " up to date automatically", true);

            JPanel content = column(
                    title("Welcome to " + displayName + "!"),
                    row(text(displayName + " checks "), link("lode.gg", LECTERN_PAGE),
                            text(" for a newer version each time the game")),
                    text("starts, and installs it before you reach the title screen."),
                    Box.createVerticalStrut(10),
                    text("Nothing about you is sent. The check asks our server which version is"),
                    text("newest and downloads the file if yours is older."),
                    Box.createVerticalStrut(12),
                    opted,
                    Box.createVerticalStrut(4),
                    small("If unchecked, you will be asked to update each time instead. You can change"),
                    small("this in .minecraft/lectern/loader.properties at any time if you want to opt"),
                    small("back in."));

            JButton ok = button("Continue");
            JDialog dialog = dialog(displayName, content, ok);
            ok.addActionListener(event -> {
                answer.set(opted.isSelected());
                dialog.dispose();
            });
            dialog.setVisible(true);
        });
        return answer.get();
    }

    @Override
    public Answer askUpdate(String displayName, String version) {
        AtomicReference<Answer> answer = new AtomicReference<>(Answer.SKIP);
        onSwingThread(() -> {
            JCheckBox always = checkBox("Install updates automatically from now on", false);

            JPanel content = column(
                    title(displayName + " " + version + " is available"),
                    text("You are set to be asked before updating."),
                    Box.createVerticalStrut(12),
                    always);

            JButton skip = button("Skip");
            JButton download = button("Download");
            JDialog dialog = dialog(displayName, content, skip, download);
            skip.addActionListener(event -> {
                answer.set(Answer.SKIP);
                dialog.dispose();
            });
            download.addActionListener(event -> {
                answer.set(always.isSelected() ? Answer.DOWNLOAD_AND_ALWAYS : Answer.DOWNLOAD);
                dialog.dispose();
            });
            dialog.setVisible(true);
        });
        return answer.get();
    }

    @Override
    public void downloadStarted(String displayName, String version, long totalBytes) {
        this.total = totalBytes;
        onSwingThread(() -> {
            progress = new JFrame(displayName);
            progress.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            progress.setUndecorated(false);
            progress.setResizable(false);

            status = text("Downloading " + displayName + " " + version);
            bar = new JProgressBar(0, 1000);
            bar.setPreferredSize(new Dimension(360, 8));
            bar.setBorderPainted(false);
            bar.setBackground(new Color(0x2A, 0x2A, 0x33));
            bar.setForeground(ACCENT);
            bar.setIndeterminate(totalBytes <= 0);

            JPanel content = column(status, Box.createVerticalStrut(10), bar);
            progress.setContentPane(frame(content));
            progress.pack();
            progress.setLocationRelativeTo(null);
            progress.setVisible(true);
        });
    }

    @Override
    public void downloadProgress(long bytes) {
        if (bar == null || total <= 0) return;
        int permille = (int) Math.min(1000, bytes * 1000 / total);
        SwingUtilities.invokeLater(() -> {
            bar.setValue(permille);
            status.setText(String.format("Downloading  %.1f of %.1f MB",
                    bytes / 1_048_576f, total / 1_048_576f));
        });
    }

    @Override
    public void downloadFinished() {
        onSwingThread(() -> {
            if (progress != null) {
                progress.dispose();
                progress = null;
            }
        });
    }

    @Override
    public void close() {
        downloadFinished();
    }

    private JDialog dialog(String heading, JPanel content, JButton... buttons) {
        JDialog dialog = new JDialog((JFrame) null, heading, true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        row.setOpaque(false);
        for (JButton button : buttons) row.add(button);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(copyright(), BorderLayout.WEST);
        footer.add(row, BorderLayout.EAST);

        JPanel panel = frame(content);
        panel.add(footer, BorderLayout.SOUTH);
        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        return dialog;
    }

    private JPanel frame(JPanel content) {
        JPanel panel = new JPanel(new BorderLayout(0, 14));
        panel.setBackground(BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        panel.add(header(), BorderLayout.NORTH);
        panel.add(content, BorderLayout.CENTER);
        panel.add(copyright(), BorderLayout.SOUTH);
        return panel;
    }

    private JLabel copyright() {
        int year = Year.now().getValue();
        String span = year > 2026 ? "2026-" + year : "2026";
        JLabel label = small("© Lodestone Services LLC " + span);
        label.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        return label;
    }

    private JPanel row(java.awt.Component... parts) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        for (java.awt.Component part : parts) row.add(part);
        return row;
    }

    private JLabel link(String value, String url) {
        JLabel label = text(value);
        label.setForeground(ACCENT);
        Map<TextAttribute, Object> attributes = new HashMap<>(label.getFont().getAttributes());
        attributes.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
        label.setFont(label.getFont().deriveFont(attributes));
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                try {
                    Desktop.getDesktop().browse(URI.create(url));
                } catch (Exception noBrowser) {
                    // Nothing to do about it, and not worth interrupting the dialog for.
                }
            }
        });
        return label;
    }

    private JPanel header() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

        JLabel mark = new JLabel();
        Image logo = logo();
        if (logo != null) mark.setIcon(new ImageIcon(logo));
        row.add(mark);

        JLabel name = new JLabel("Lodestone");
        name.setForeground(FOREGROUND);
        name.setFont(name.getFont().deriveFont(Font.BOLD, 13f));
        row.add(name);
        return row;
    }

    private Image logo() {
        try (InputStream in = SwingUi.class.getResourceAsStream("/lodestone.png")) {
            if (in == null) return null;
            return ImageIO.read(in).getScaledInstance(22, 22, Image.SCALE_SMOOTH);
        } catch (Exception noLogo) {
            return null;
        }
    }

    private JPanel column(java.awt.Component... parts) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        for (java.awt.Component part : parts) {
            if (part instanceof javax.swing.JComponent component) {
                component.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            }
            panel.add(part);
        }
        return panel;
    }

    private JLabel title(String value) {
        JLabel label = new JLabel(value);
        label.setForeground(FOREGROUND);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 15f));
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        return label;
    }

    private JLabel text(String value) {
        JLabel label = new JLabel(value);
        label.setForeground(FOREGROUND);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 12f));
        return label;
    }

    private JLabel small(String value) {
        JLabel label = text(value);
        label.setForeground(MUTED);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 11f));
        return label;
    }

    private JCheckBox checkBox(String value, boolean selected) {
        JCheckBox box = new JCheckBox(value, selected);
        box.setOpaque(false);
        box.setForeground(FOREGROUND);
        box.setFont(box.getFont().deriveFont(Font.PLAIN, 12f));
        return box;
    }

    private JButton button(String value) {
        JButton button = new JButton(value);
        button.setFocusPainted(false);
        return button;
    }

    private void onSwingThread(Runnable work) {
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                work.run();
            } else {
                SwingUtilities.invokeAndWait(work);
            }
        } catch (Exception noUi) {
            // A window that will not open must not stop the game from starting.
        }
    }
}
