package com.dillon.starsectormarines.tools.authoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The workbench's failures used to go to a stderr stream buffered into a Gradle
 * build log, so a bug looked like a window that stopped working for no stated
 * reason. What matters here is that a failure leaves a record behind.
 */
class AuthoringCrashReporterTest {

    @Test
    void aFailureIsWrittenWhereItCanBeFoundAgain(@TempDir Path root) throws Exception {
        AuthoringCrashReporter.install(root);

        AuthoringCrashReporter.report("a test", new IllegalStateException("no such tileset"));

        Path log = root.resolve(AuthoringCrashReporter.LOG_PATH);
        assertTrue(Files.isRegularFile(log), "the reporter should have written " + log);
        String written = Files.readString(log);
        assertTrue(written.contains("IllegalStateException: no such tileset"), written);
        assertTrue(written.contains("at com.dillon.starsectormarines"), "the frames are the useful half:\n" + written);
        assertTrue(written.contains("a test"), "the report says where it happened");
    }

    @Test
    void failuresAccumulateRatherThanReplacingEachOther(@TempDir Path root) throws Exception {
        AuthoringCrashReporter.install(root);

        AuthoringCrashReporter.report("first", new IllegalStateException("one"));
        AuthoringCrashReporter.report("second", new IllegalStateException("two"));

        String written = Files.readString(root.resolve(AuthoringCrashReporter.LOG_PATH));
        assertTrue(written.contains("one") && written.contains("two"),
                "a second failure must not erase the first:\n" + written);
    }

    @Test
    void anErrorIsRecordedAsReadilyAsAnException(@TempDir Path root) throws Exception {
        // The startup guard used to catch only Exception, so a NoClassDefFoundError
        // or an OutOfMemoryError left nothing behind at all.
        AuthoringCrashReporter.install(root);

        AuthoringCrashReporter.report("startup", new NoClassDefFoundError("org/json/JSONObject"));

        assertTrue(Files.readString(root.resolve(AuthoringCrashReporter.LOG_PATH))
                .contains("NoClassDefFoundError: org/json/JSONObject"));
    }

    @Test
    void reportingIsNeverTheThingThatTakesTheProcessDown(@TempDir Path root) {
        AuthoringCrashReporter.install(root.resolve("a-file-not-a-directory"));
        // An unwritable log must not turn a reportable bug into an unreportable one.
        AuthoringCrashReporter.report("a test", new IllegalStateException("still reported"));
    }

    @Test
    void aFailureRaisedWhileReportingDoesNotRecurse(@TempDir Path root) throws Exception {
        AuthoringCrashReporter.install(root);
        // Guards the re-entrancy latch: a report raised from inside a report is
        // dropped rather than recursing until the stack runs out.
        AuthoringCrashReporter.report("outer", new IllegalStateException("outer failure"));

        String written = Files.readString(root.resolve(AuthoringCrashReporter.LOG_PATH));
        assertEquals(1, written.split("outer failure", -1).length - 1,
                "one report, written once:\n" + written);
    }
}
