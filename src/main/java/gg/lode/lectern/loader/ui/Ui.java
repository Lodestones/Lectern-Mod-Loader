package gg.lode.lectern.loader.ui;

public final class Ui {

    private Ui() {
    }

    public static LoaderUi open(boolean wanted) {
        if (!wanted || Boolean.getBoolean("java.awt.headless")) return LoaderUi.SILENT;
        try {
            return isMac() ? ForkedUi.spawn() : new SwingUi();
        } catch (Throwable noUi) {
            return LoaderUi.SILENT;
        }
    }

    public static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }
}
