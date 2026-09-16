package gg.lode.lectern.loader;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

final class Log {
    private final String prefix;
    private final Path file;

    Log(String name, Path file) {
        this.prefix = "[" + name + "] ";
        this.file = file;
        if (file != null) {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, "", StandardCharsets.UTF_8);
            } catch (IOException cannotWrite) {
                System.out.println(prefix + "WARN no log file at " + file + ": " + cannotWrite);
            }
        }
    }

    Log(String name) {
        this(name, null);
    }

    void info(String message) {
        write(message);
    }

    void warn(String message) {
        write("WARN " + message);
    }

    void error(String message, Throwable cause) {
        write("ERROR " + message);
        if (cause != null) {
            StringWriter trace = new StringWriter();
            cause.printStackTrace(new PrintWriter(trace));
            write(trace.toString());
            cause.printStackTrace(System.out);
        }
    }

    private void write(String line) {
        System.out.println(prefix + line);
        if (file == null) return;
        try {
            Files.writeString(file, line + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }
}
