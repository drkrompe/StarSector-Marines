package com.dillon.starsectormarines.ops;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shell front door onto the authoring tools.
 *
 * <p>Asserts the contract a shell actually depends on — exit status, which
 * stream a thing lands on, and the three ways arguments arrive — rather than
 * re-testing the tools themselves, which have their own tests. The tools here
 * are the real discovered catalog, because a CLI that passes against a fake
 * catalog has not shown that the catalog is reachable from this entry point.
 */
class AuthoringToolCliTest {

    private static final int TOOL_FAILED = 1;
    private static final int USAGE = 2;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @TempDir
    Path projectRoot;

    private int run(String... command) {
        return run(new ByteArrayInputStream(new byte[0]), command);
    }

    private int run(InputStream in, String... command) {
        String[] argv = new String[command.length + 2];
        argv[0] = projectRoot.toString();
        argv[1] = projectRoot.resolve("starsector-core").toString();
        System.arraycopy(command, 0, argv, 2, command.length);
        return AuthoringToolCli.run(argv, in,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private String stdout() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String stderr() {
        return err.toString(StandardCharsets.UTF_8);
    }

    @Test
    void listsTheSameToolsTheServerServes() {
        assertEquals(0, run("--list"));
        String listing = stdout();
        assertTrue(listing.contains("tileset_list"), listing);
        assertTrue(listing.contains("tileset_export"), listing);
        assertTrue(listing.contains("snapshot_create"), listing);
    }

    @Test
    void describingAToolPrintsItsSchema() {
        assertEquals(0, run("--describe", "tileset_measure"));
        String described = stdout();
        assertTrue(described.contains("gridCols"), described);
        assertTrue(described.contains("\"required\""), described);
    }

    @Test
    void callingAToolPrintsItsTextAndSucceeds() {
        assertEquals(0, run("tileset_list"));
        // An empty project is a legitimate answer, not a failure.
        assertTrue(stdout().contains("No sheets under"), stdout());
    }

    @Test
    void aToolThatReportsFailureExitsNonZeroWithoutAStackTrace() {
        int status = run("tileset_measure", "{\"sheet\":\"not-a-sheet\"}");
        assertEquals(TOOL_FAILED, status);
        assertTrue(stdout().contains("no such sheet"), stdout());
        // A tool answering "no" is not an exception; a trace here would train a
        // caller to ignore traces that do mean something.
        assertFalse(stderr().contains("at com.dillon.starsectormarines"), stderr());
    }

    @Test
    void anUnknownToolIsAUsageMistakeAndNamesTheAlternatives() {
        assertEquals(USAGE, run("tileset_frobnicate"));
        assertTrue(stderr().contains("no tool named"), stderr());
        assertTrue(stderr().contains("tileset_list"), stderr());
    }

    @Test
    void jsonModeReturnsTheStructuredResultTheTextSummarizes() throws Exception {
        assertEquals(0, run("tileset_list", "--json"));
        JSONObject envelope = new JSONObject(stdout());
        assertEquals("tileset_list", envelope.getString("tool"));
        assertFalse(envelope.getBoolean("error"));
        assertTrue(envelope.getJSONObject("structured").has("sheets"));
    }

    @Test
    void argumentsCanArriveFromStdin() {
        InputStream in = new ByteArrayInputStream(
                "{\"sheet\":\"not-a-sheet\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals(TOOL_FAILED, run(in, "tileset_measure", "-"));
        assertTrue(stdout().contains("not-a-sheet"), stdout());
    }

    @Test
    void argumentsCanArriveFromAFile() throws Exception {
        // The reason this form exists: a document large enough to be worth
        // writing is one no shell should be asked to quote.
        Path arguments = projectRoot.resolve("call.json");
        Files.writeString(arguments, "{\"sheet\":\"not-a-sheet\"}");
        assertEquals(TOOL_FAILED, run("tileset_measure", "@" + arguments));
        assertTrue(stdout().contains("not-a-sheet"), stdout());
    }

    @Test
    void malformedArgumentsAreAUsageMistakeRatherThanACall() {
        assertEquals(USAGE, run("tileset_list", "{not json}"));
        assertTrue(stderr().contains("not a JSON object"), stderr());
    }

    @Test
    void aShellThatSplitTheJsonIsToldSoRatherThanBlamedForAnExtraArgument() {
        // What arrives when a shell breaks one object at its spaces. Rejoining
        // it would parse, and accepting that is how a collapsed space inside a
        // string becomes a value nobody typed - so it is refused, with the
        // cause named.
        assertEquals(USAGE, run("tileset_measure", "{\"sheet\":", "\"a\"}"));
        String reported = stderr();
        assertTrue(reported.contains("split one JSON object"), reported);
        assertTrue(reported.contains("splatting operator"), reported);
    }

    @Test
    void genuinelySeveralArgumentsGetThePlainComplaint() {
        assertEquals(USAGE, run("tileset_list", "{}", "{}"));
        String reported = stderr();
        assertTrue(reported.contains("expected one JSON object but got 2"), reported);
        assertFalse(reported.contains("split one JSON object"), reported);
    }
}
