package com.dillon.starsectormarines.tools.authoring;

import javax.swing.SwingUtilities;
import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Catches what the workbench would otherwise lose.
 *
 * <p>Swing does not route event-thread failures through the default uncaught
 * handler: {@code EventDispatchThread} catches them itself and prints to stderr.
 * Launched through Gradle that stream is buffered into a build log nobody reads,
 * so the visible result of a bug is a window that stops working, or stops
 * existing, with no reason recorded anywhere. An {@link Error} was worse still —
 * the frame's own startup guard caught only {@link Exception}.
 *
 * <p>So every dispatch is wrapped, every thread gets a handler, and every
 * failure is written to a file and shown in a dialog that can be copied. The
 * point is not to keep running afterwards — a half-broken editor is its own
 * hazard — but to make the next report say what happened.
 */
public final class AuthoringCrashReporter {

    private AuthoringCrashReporter() {}

    /** Where failures accumulate, under the project's build directory. */
    public static final String LOG_PATH = "build/layer-authoring-errors.log";

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static volatile Path logFile;
    private static volatile boolean reporting;

    /**
     * Route event-thread and background-thread failures to a log and a dialog.
     *
     * @param projectRoot repository root; the log is written beneath it
     */
    public static void install(Path projectRoot) {
        logFile = projectRoot.toAbsolutePath().normalize().resolve(LOG_PATH);
        Thread.setDefaultUncaughtExceptionHandler(
                (thread, failure) -> report("thread " + thread.getName(), failure));
        Toolkit.getDefaultToolkit().getSystemEventQueue().push(new EventQueue() {
            @Override
            protected void dispatchEvent(AWTEvent event) {
                try {
                    super.dispatchEvent(event);
                } catch (Throwable failure) {
                    report("event thread", failure);
                }
            }
        });
    }

    /**
     * Record a failure and show it.
     *
     * <p>Re-entrant reports are dropped: a failure raised while drawing the
     * report dialog would otherwise recurse until the stack runs out, replacing
     * a legible bug with an illegible one.
     */
    public static void report(String where, Throwable failure) {
        if (reporting) return;
        reporting = true;
        try {
            String detail = detail(where, failure);
            System.err.println(detail);
            write(detail);
            String shown = "The workbench hit an error in the " + where + "."
                    + (logFile == null ? "" : "\n\nAlso written to " + logFile)
                    + "\n\n" + detail;
            if (SwingUtilities.isEventDispatchThread()) {
                AuthoringMessages.error(null, "Workbench error", shown);
            } else {
                SwingUtilities.invokeLater(
                        () -> AuthoringMessages.error(null, "Workbench error", shown));
            }
        } catch (Throwable ignored) {
            // Reporting must never be the thing that takes the process down.
        } finally {
            reporting = false;
        }
    }

    private static String detail(String where, Throwable failure) {
        StringWriter out = new StringWriter();
        out.append(ZonedDateTime.now().format(STAMP)).append("  ").append(where).append('\n');
        failure.printStackTrace(new PrintWriter(out));
        return out.toString();
    }

    private static void write(String detail) {
        Path path = logFile;
        if (path == null) return;
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, detail + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception unwritable) {
            System.err.println("Could not write " + path + ": " + unwritable);
        }
    }
}
