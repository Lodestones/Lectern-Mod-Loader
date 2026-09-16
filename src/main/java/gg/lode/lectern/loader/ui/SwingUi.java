package gg.lode.lectern.loader.ui;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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
import java.awt.Taskbar;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public final class SwingUi implements LoaderUi {

    private static final Color BACKGROUND = new Color(0x16, 0x16, 0x1A);
    private static final Color FOREGROUND = new Color(0xEC, 0xEC, 0xF1);
    private static final Color MUTED = new Color(0x9A, 0x9A, 0xA8);
    private static final Color ACCENT = new Color(0xB4, 0x8C, 0xFF);
    private static final String LECTERN_PAGE = "https://lode.gg/mod/lectern";
    private static final String PRIVACY_POLICY = "https://lode.gg/legal/lectern-privacy";
    private static final int[] ICON_SIZES = {16, 20, 24, 32, 48, 64, 128};

    private static final String LODESTONE_MARK = "/lodestone.png";
    private static final String LECTERN_MARK = "/lectern.png";

    private static final Map<String, BufferedImage> SOURCES = new HashMap<>();
    private static List<Image> icons;

    private JFrame progress;
    private JProgressBar bar;
    private JLabel status;
    private long total;

    public static void applyAppIcon() {
        Image mark = scaled(LECTERN_MARK, 256);
        if (mark == null) return;
        try {
            Taskbar taskbar = Taskbar.getTaskbar();
            if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) taskbar.setIconImage(mark);
        } catch (Throwable noTaskbar) {
            // No dock or taskbar to stamp; the window icons still apply.
        }
    }

    public SwingUi() {
        applyAppIcon();
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception keepTheDefault) {
            // Any look and feel will do.
        }
    }

    @Override
    public Consent askFirstRun(String displayName) {
        AtomicReference<Consent> answer = new AtomicReference<>(Consent.DISMISSED);
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
                small("back in."),
                Box.createVerticalStrut(10),
                row(small("By installing " + displayName + " for the first time, you agree to our "),
                        smallLink("Privacy Policy", PRIVACY_POLICY),
                        small(".")));

        JButton ok = button("Continue");
        prompt(displayName, content, frame -> ok.addActionListener(event -> {
            answer.set(opted.isSelected() ? Consent.AUTOMATIC : Consent.MANUAL);
            frame.dispose();
        }), ok);
        return answer.get();
    }

    @Override
    public Answer askUpdate(String displayName, String version) {
        AtomicReference<Answer> answer = new AtomicReference<>(Answer.SKIP);
        JCheckBox always = checkBox("Install updates automatically from now on", false);

        JPanel content = column(
                title(displayName + " " + version + " is available"),
                text("You are set to be asked before updating."),
                Box.createVerticalStrut(12),
                always);

        JButton skip = button("Skip");
        JButton download = button("Download");
        prompt(displayName, content, frame -> {
            skip.addActionListener(event -> {
                answer.set(Answer.SKIP);
                frame.dispose();
            });
            download.addActionListener(event -> {
                answer.set(always.isSelected() ? Answer.DOWNLOAD_AND_ALWAYS : Answer.DOWNLOAD);
                frame.dispose();
            });
        }, skip, download);
        return answer.get();
    }

    @Override
    public void downloadStarted(String displayName, String version, long totalBytes) {
        this.total = totalBytes;
        onSwingThread(() -> {
            progress = new JFrame(displayName);
            progress.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            progress.setResizable(false);
            progress.setIconImages(icons());

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
            progress.toFront();
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
    public void problem(String displayName, String message) {
        JPanel content = column(title(displayName + " could not start"), text(message));
        JButton ok = button("Continue without " + displayName);
        prompt(displayName, content, frame -> ok.addActionListener(event -> frame.dispose()), ok);
    }

    @Override
    public void close() {
        downloadFinished();
    }

    private interface Wiring {
        void accept(JFrame frame);
    }

    private void prompt(String heading, JPanel content, Wiring wiring, JButton... buttons) {
        CountDownLatch answered = new CountDownLatch(1);
        AtomicReference<JFrame> shown = new AtomicReference<>();
        onSwingThread(() -> {
            JFrame frame = new JFrame(heading);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setResizable(false);
            frame.setIconImages(icons());
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent event) {
                    answered.countDown();
                }
            });

            JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            row.setOpaque(false);
            for (JButton button : buttons) row.add(button);

            JPanel footer = new JPanel(new BorderLayout());
            footer.setOpaque(false);
            footer.add(copyright(), BorderLayout.WEST);
            footer.add(row, BorderLayout.EAST);

            JPanel panel = frame(content);
            panel.add(footer, BorderLayout.SOUTH);
            frame.setContentPane(panel);

            wiring.accept(frame);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setAlwaysOnTop(true);
            frame.setVisible(true);
            frame.toFront();
            frame.requestFocus();
            if (buttons.length > 0) buttons[buttons.length - 1].requestFocusInWindow();
            shown.set(frame);
        });

        JFrame opened = shown.get();
        if (opened == null || !opened.isDisplayable()) return;
        if (SwingUtilities.isEventDispatchThread()) return;
        try {
            answered.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
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

    private JPanel copyright() {
        int year = Year.now().getValue();
        String span = year > 2026 ? "2026-" + year : "2026";

        JPanel panel = row(small("© Lodestone Services LLC " + span));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        return panel;
    }

    private JPanel row(java.awt.Component... parts) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        for (java.awt.Component part : parts) row.add(part);
        return row;
    }

    private JLabel smallLink(String value, String url) {
        JLabel label = link(value, url);
        label.setFont(small("").getFont());
        Map<TextAttribute, Object> attributes = new HashMap<>(label.getFont().getAttributes());
        attributes.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
        label.setFont(label.getFont().deriveFont(attributes));
        return label;
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
        Image logo = scaled(LODESTONE_MARK, 22);
        if (logo != null) mark.setIcon(new ImageIcon(logo));
        row.add(mark);

        JLabel name = new JLabel("Lodestone");
        name.setForeground(FOREGROUND);
        name.setFont(name.getFont().deriveFont(Font.BOLD, 13f));
        row.add(name);
        return row;
    }

    private static synchronized List<Image> icons() {
        if (icons != null) return icons;
        List<Image> sizes = new ArrayList<>();
        for (int size : ICON_SIZES) {
            Image mark = scaled(LECTERN_MARK, size);
            if (mark != null) sizes.add(mark);
        }
        icons = sizes;
        return icons;
    }

    private static synchronized Image scaled(String resource, int size) {
        BufferedImage mark = SOURCES.get(resource);
        if (mark == null && !SOURCES.containsKey(resource)) {
            try (InputStream in = SwingUi.class.getResourceAsStream(resource)) {
                mark = in == null ? null : ImageIO.read(in);
            } catch (Exception noMark) {
                mark = null;
            }
            SOURCES.put(resource, mark);
        }
        return mark == null ? null : mark.getScaledInstance(size, size, Image.SCALE_SMOOTH);
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
