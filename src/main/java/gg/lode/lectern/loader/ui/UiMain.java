package gg.lode.lectern.loader.ui;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public final class UiMain {

    public static void main(String[] args) throws Exception {
        SwingUi ui = new SwingUi();
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintWriter out = new PrintWriter(new java.io.OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);

        String line;
        while ((line = in.readLine()) != null) {
            String[] parts = line.split("\t", -1);
            switch (parts[0]) {
                case "first-run" -> out.println(ui.askFirstRun(parts[1]));
                case "update" -> out.println(ui.askUpdate(parts[1], parts[2]).name());
                case "start" -> ui.downloadStarted(parts[1], parts[2], Long.parseLong(parts[3]));
                case "progress" -> ui.downloadProgress(Long.parseLong(parts[1]));
                case "finish" -> ui.downloadFinished();
                case "bye" -> {
                    ui.close();
                    System.exit(0);
                }
                default -> {
                }
            }
        }
        ui.close();
        System.exit(0);
    }
}
