package com.dillon.starsectormarines.tools.mcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * Runs an {@link McpServer} over newline-delimited JSON on a pair of streams.
 *
 * <p>The whole transport, because that is all the stdio transport is: one JSON
 * object per line in, one per line out, UTF-8, no framing header.
 *
 * <p><b>Nothing but protocol may reach the output stream.</b> A stray
 * {@code println} anywhere in the tool classpath corrupts the session in a way
 * that reads as a client bug, which is why the launcher execs this class
 * directly rather than routing it through Gradle. Diagnostics go to stderr,
 * which the client shows and the protocol ignores.
 */
public final class McpStdioServer {

    private final McpServer server;

    public McpStdioServer(McpServer server) {
        if (server == null) throw new IllegalArgumentException("a server is required");
        this.server = server;
    }

    /** Serve until the input stream ends, which is how the client says stop. */
    public void serve(InputStream in, OutputStream out) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        String line;
        while ((line = reader.readLine()) != null) {
            String response = server.handleLine(line);
            if (response == null) continue;
            // A response the client never sees is a hung session, and there is
            // no natural flush point in a request/response loop, so flush every
            // line rather than trusting the buffer to fill.
            writer.write(response);
            writer.write('\n');
            writer.flush();
        }
    }
}
