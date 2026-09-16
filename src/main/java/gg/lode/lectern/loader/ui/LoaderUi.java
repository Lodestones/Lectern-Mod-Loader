package gg.lode.lectern.loader.ui;

public interface LoaderUi extends AutoCloseable {

    boolean askFirstRun(String displayName);

    Answer askUpdate(String displayName, String version);

    void downloadStarted(String displayName, String version, long totalBytes);

    void downloadProgress(long bytes);

    void downloadFinished();

    void problem(String displayName, String message);

    @Override
    void close();

    enum Answer {
        DOWNLOAD,
        DOWNLOAD_AND_ALWAYS,
        SKIP
    }

    LoaderUi SILENT = new LoaderUi() {
        @Override
        public boolean askFirstRun(String displayName) {
            return true;
        }

        @Override
        public Answer askUpdate(String displayName, String version) {
            return Answer.SKIP;
        }

        @Override
        public void downloadStarted(String displayName, String version, long totalBytes) {
        }

        @Override
        public void downloadProgress(long bytes) {
        }

        @Override
        public void downloadFinished() {
        }

        @Override
        public void problem(String displayName, String message) {
        }

        @Override
        public void close() {
        }
    };
}
