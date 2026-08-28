package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.tools.mcp.McpTool;
import com.dillon.starsectormarines.tools.mcp.McpToolCatalog;
import com.dillon.starsectormarines.tools.mcp.McpToolContext;
import com.dillon.starsectormarines.tools.mcp.McpToolResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Calls one authoring tool and exits, for a caller holding a shell rather than
 * an MCP client.
 *
 * <p>Beside {@link AuthoringMcpCli}, over the same catalog and the same
 * {@link McpToolContext}, because it is the same tools reached a different way.
 * The stdio server is the right shape for a client that spawns and owns a
 * process for a whole session; it is the wrong shape for anything else, because
 * a stdio server has to be registered before the session that wants it starts.
 * An agent that decides mid-task that it needs to look at a tileset cannot go
 * back and register a server, and telling it to run a long-lived process and
 * talk to a socket only moves the same problem behind a port number.
 *
 * <p>So: one process, one call, one answer, no lifecycle. The cost is a JVM
 * start per call, which is the correct trade for an authoring step measured in
 * seconds of thought per invocation.
 *
 * <p><b>This is not a second implementation of the tools.</b> It resolves the
 * same {@link McpToolCatalog} the server does and calls {@link McpTool#call}
 * directly; a tool added for one caller is available to the other with no
 * further work. What differs is only the presentation: a result's text goes to
 * stdout for a human or a model to read, and a tool that reported failure sets
 * a non-zero exit status so a shell can tell.
 */
public final class AuthoringToolCli {

    private AuthoringToolCli() {}

    /** A tool answered, and its answer was negative. Distinct from a usage mistake. */
    private static final int TOOL_FAILED = 1;
    /** The command line was wrong. Nothing was called. */
    private static final int USAGE = 2;

    public static void main(String[] args) {
        // System.out encodes in the console's code page, which on Windows is
        // still cp1252 — every em dash in a tool description arrives as '?',
        // and a JSON payload piped to another program arrives corrupted rather
        // than merely ugly. The stdio server wraps its streams for the same
        // reason; this is the same fix at the other entry point.
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(System.err, true, StandardCharsets.UTF_8);
        int status = run(args, System.in, out, err);
        out.flush();
        err.flush();
        System.exit(status);
    }

    /**
     * @param argv {@code <projectRoot> <starsectorCore> <command...>}; the two
     *             roots are supplied by the generated launcher rather than typed
     */
    static int run(String[] argv, InputStream in, PrintStream out, PrintStream err) {
        if (argv.length < 3) {
            err.println(usage());
            return USAGE;
        }
        McpToolContext context =
                new McpToolContext(Path.of(argv[0]), Path.of(argv[1]));
        McpToolCatalog catalog = McpToolCatalog.discover();

        String command = argv[2];
        try {
            switch (command) {
                case "--help", "-h", "help":
                    out.println(usage());
                    out.println();
                    out.println(listing(catalog));
                    return 0;
                case "--list", "list":
                    out.println(listing(catalog));
                    return 0;
                case "--describe", "describe":
                    if (argv.length < 4) {
                        err.println("--describe needs a tool name");
                        return USAGE;
                    }
                    return describe(catalog, argv[3], out, err);
                default:
                    return call(catalog, context, argv, in, out, err);
            }
        } catch (Exception failure) {
            // A tool that threw has still answered the question; the caller
            // needs the answer far more than it needs a Java stack trace, and
            // the trace is on stderr for the case where it does not.
            err.println(command + " failed: " + failure.getClass().getSimpleName()
                    + ": " + failure.getMessage());
            failure.printStackTrace(err);
            return TOOL_FAILED;
        }
    }

    private static int call(McpToolCatalog catalog, McpToolContext context, String[] argv,
                            InputStream in, PrintStream out, PrintStream err) throws Exception {
        String name = argv[2];
        McpTool tool = catalog.find(name);
        if (tool == null) {
            err.println("no tool named '" + name + "'. Known tools:");
            err.println(listing(catalog));
            return USAGE;
        }

        boolean asJson = false;
        String argument = null;
        for (int index = 3; index < argv.length; index++) {
            String token = argv[index];
            if (token.equals("--json")) {
                asJson = true;
            } else if (argument == null) {
                argument = token;
            } else {
                err.println("unexpected argument '" + token + "'; a tool takes one JSON object");
                return USAGE;
            }
        }

        JSONObject arguments;
        try {
            arguments = parse(argument, in);
        } catch (JSONException malformed) {
            err.println("arguments are not a JSON object: " + malformed.getMessage());
            return USAGE;
        }

        McpToolResult result = tool.call(arguments, context);
        if (asJson) {
            JSONObject envelope = new JSONObject();
            envelope.put("tool", name);
            envelope.put("text", result.text());
            envelope.put("error", result.error());
            if (result.structured() != null) envelope.put("structured", result.structured());
            out.println(envelope.toString(2));
        } else {
            out.println(result.text());
        }
        return result.error() ? TOOL_FAILED : 0;
    }

    /**
     * Read a tool's arguments from the command line, a file, or stdin.
     *
     * <p>The file and stdin forms are not conveniences. {@code tileset_write_document}
     * takes a whole authoring document, and a document large enough to be worth
     * writing is one no shell should be asked to quote — PowerShell in
     * particular will happily mangle it into something that parses and means
     * something else.
     */
    private static JSONObject parse(String argument, InputStream in)
            throws IOException, JSONException {
        if (argument == null || argument.isBlank()) return new JSONObject();
        if (argument.equals("-")) {
            return new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        if (argument.startsWith("@")) {
            return new JSONObject(Files.readString(Path.of(argument.substring(1))));
        }
        return new JSONObject(argument);
    }

    private static int describe(McpToolCatalog catalog, String name,
                                PrintStream out, PrintStream err) throws JSONException {
        McpTool tool = catalog.find(name);
        if (tool == null) {
            err.println("no tool named '" + name + "'. Known tools:");
            err.println(listing(catalog));
            return USAGE;
        }
        out.println(tool.name());
        out.println();
        out.println(tool.description());
        out.println();
        out.println(tool.inputSchema().toString(2));
        return 0;
    }

    /** Every tool with the first sentence of its description, which is what a chooser reads. */
    private static String listing(McpToolCatalog catalog) {
        StringBuilder text = new StringBuilder();
        for (McpTool tool : catalog.tools()) {
            text.append(String.format("  %-24s %s%n", tool.name(), firstSentence(tool.description())));
        }
        return text.toString().stripTrailing();
    }

    private static String firstSentence(String description) {
        int stop = description.indexOf(". ");
        return stop < 0 ? description : description.substring(0, stop + 1);
    }

    private static String usage() {
        return """
                Call one authoring tool and exit.

                  authoring <tool> [arguments] [--json]
                  authoring --list
                  authoring --describe <tool>

                Arguments are one JSON object. Pass it inline, as @file, or as - to
                read stdin. Omit it for a tool that takes none.

                  authoring tileset_list
                  authoring tileset_measure '{"sheet":"urban-tileset","gridCols":10,"gridRows":10}'
                  authoring tileset_write_document @document.json

                --json prints the structured result instead of the text summary.
                Exit status is 1 when the tool reports a failure, 2 on a usage mistake.""";
    }
}
