package gg.lode.lectern.loader.ui;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

public final class ForkedUi implements LoaderUi {

    private final Process process;
    private final BufferedWriter out;
    private final BufferedReader in;

    private ForkedUi(Process process) {
        this.process = process;
        this.out = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        this.in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
    }

    public static LoaderUi spawn() {
        try {
            Path self = Paths.get(ForkedUi.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path java = Paths.get(System.getProperty("java.home"), "bin",
                    System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java");

            ProcessBuilder builder = new ProcessBuilder(
                    java.toString(), "-Djava.awt.headless=false",
                    "-Xdock:name=Lectern",
                    "-Dapple.awt.application.name=Lectern",
                    "-cp", self.toString(), UiMain.class.getName());
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            return new ForkedUi(builder.start());
        } catch (Throwable noFork) {
            return LoaderUi.SILENT;
        }
    }

    @Override
    public boolean askFirstRun(String displayName) {
        String answer = ask("first-run\t" + displayName);
        return !"false".equals(answer);
    }

    @Override
    public Answer askUpdate(String displayName, String version) {
        String answer = ask("update\t" + displayName + "\t" + version);
        try {
            return answer == null ? Answer.SKIP : Answer.valueOf(answer);
        } catch (IllegalArgumentException unknown) {
            return Answer.SKIP;
        }
    }

    @Override
    public void downloadStarted(String displayName, String version, long totalBytes) {
        tell("start\t" + displayName + "\t" + version + "\t" + totalBytes);
    }

    @Override
    public void downloadProgress(long bytes) {
        tell("progress\t" + bytes);
    }

    @Override
    public void downloadFinished() {
        tell("finish");
    }

    @Override
    public void problem(String displayName, String message) {
        ask("problem\t" + displayName + "\t" + message);
    }

    @Override
    public void close() {
        tell("bye");
        try {
            if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private void tell(String line) {
        try {
            out.write(line);
            out.newLine();
            out.flush();
        } catch (Exception gone) {
            // The child died; the launch carries on without a window.
        }
    }

    private String ask(String line) {
        try {
            tell(line);
            return in.readLine();
        } catch (Exception gone) {
            return null;
        }
    }
}
