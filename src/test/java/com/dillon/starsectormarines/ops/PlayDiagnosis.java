package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.probe.BattleProbe;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.tools.mcp.McpSchema;
import com.dillon.starsectormarines.tools.mcp.McpServer;
import com.dillon.starsectormarines.tools.mcp.McpTool;
import com.dillon.starsectormarines.tools.mcp.McpToolCatalog;
import com.dillon.starsectormarines.tools.mcp.McpToolContext;
import com.dillon.starsectormarines.tools.mcp.McpToolResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@code playBattle}'s live diagnosis server: MCP over HTTP on the loopback address, so a Claude session can ask a
 * battle questions while someone plays it. What it asks is {@link BattleProbe}'s, the same commands {@code battleProbe}
 * answers; {@link McpServer} speaks the protocol and this adds the socket and the wait for a safe moment.
 *
 * <p><b>Reads happen on the host's frame.</b> The HTTP thread only queues a read and waits; {@link #frame} runs it on
 * the thread that runs every tick, between {@code advance} and {@code render}. A tick is never in progress there: the
 * parallel unit update joins inside {@code advance}, and the workers that outlive it (influence, clearance and defend
 * routes) build from frozen captures and hand over at a later tick, so nothing a read touches is moving. An answer is
 * also turned to text there, so no live object reaches another thread.
 *
 * <p><b>The frame stays responsive.</b> One HTTP thread means one call waits at a time, so a frame answers at most one
 * call, however hard a client asks, and a frame with nothing asked costs one queue poll. Each call's commands run
 * within that frame and so see one tick. The slowest call so far is in {@code play_status}.
 *
 * <p><b>Nothing here steps the battle:</b> {@link BattleProbe#reads} refuses every stepping command, and the only write
 * is {@code battle_hitlog}, which switches an observer on. Host and Origin are checked, so a web page in a browser on
 * this machine cannot reach the port by way of DNS rebinding.
 */
final class PlayDiagnosis implements AutoCloseable {

    /** What the host can say of itself, asked only from {@link #frame}. */
    interface View {
        BattleSimulation battle();

        String screen();

        String world();

        String controls();
    }

    private interface Read {
        McpToolResult at(View view) throws Exception;
    }

    private interface Call {
        McpToolResult run(JSONObject arguments) throws Exception;
    }

    private record Pending(Read read, CompletableFuture<McpToolResult> answer) { }

    static final int DEFAULT_PORT = 47811;
    private static final long ANSWER_WAIT_SECONDS = 5L;
    private static final int MAX_BODY_BYTES = 1 << 20;
    private static final int MAX_COMMANDS = 32;
    private static final String INSTRUCTIONS = "A live Starsector Marines battle being played by hand in the playBattle "
            + "window. Everything reads between ticks and nothing steps it, so the tick moves on between calls unless the "
            + "player has paused; commands sent together in one battle_query see the same tick. Start with play_status, "
            + "then battle_query 'squads' to find a squad and 'why <id>' to learn what it is doing and why.";

    private final Queue<Pending> pending = new ConcurrentLinkedQueue<>();
    private final HttpServer http;
    private final ExecutorService thread;
    private final McpServer protocol;
    private final long opened = System.nanoTime();
    // The rest are touched only from frame().
    private BattleProbe probe;
    private long frames;
    private long calls;
    private float slowestCallMs;

    private PlayDiagnosis(HttpServer http, ExecutorService thread, Path project, Path core) {
        this.http = http;
        this.thread = thread;
        this.protocol = new McpServer(new McpToolCatalog(List.of(status(), query(), hitlog())),
                new McpToolContext(project, core), "starsector-marines-play", "0.0.1", INSTRUCTIONS);
    }

    /** Opens the server on the given port, or the default for a blank one; 0 picks any free port. */
    static PlayDiagnosis open(String port, Path project, Path core) throws IOException {
        int number = port.isBlank() ? DEFAULT_PORT : Integer.parseInt(port);
        InetAddress loopback = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        HttpServer http;
        try {
            http = HttpServer.create(new InetSocketAddress(loopback, number), 0);
        } catch (BindException taken) {
            throw new IOException("-PplayServer: port " + number + " is taken; is another playBattle open? "
                    + "-PplayServer=<port> picks another", taken);
        }
        // One thread on purpose: see the class comment.
        ExecutorService thread = Executors.newSingleThreadExecutor(work -> {
            Thread daemon = new Thread(work, "play-diagnosis");
            daemon.setDaemon(true);
            return daemon;
        });
        PlayDiagnosis server = new PlayDiagnosis(http, thread, project, core);
        http.setExecutor(thread);
        http.createContext("/mcp", server::serve);
        http.start();
        System.out.println("[playBattle] diagnosis server at " + server.url());
        return server;
    }

    String url() {
        return "http://127.0.0.1:" + http.getAddress().getPort() + "/mcp";
    }

    /** Answers what has been asked. The host calls this once a frame, between ticks, on the thread that runs them. */
    void frame(View view) {
        frames++;
        for (Pending call; (call = pending.poll()) != null; ) {
            // Given up on while the window was busy.
            if (call.answer().isDone()) continue;
            long started = System.nanoTime();
            try {
                call.answer().complete(call.read().at(view));
            } catch (Throwable failure) {
                // A question that breaks must not end the playtest.
                call.answer().complete(McpToolResult.failure(failure.toString()));
            }
            calls++;
            slowestCallMs = Math.max(slowestCallMs, (System.nanoTime() - started) / 1e6f);
        }
    }

    @Override
    public void close() {
        http.stop(0);
        thread.shutdownNow();
    }

    /** A browser's page can reach loopback too: only a caller that names this machine is answered. */
    static boolean allowedCaller(String host, String origin) {
        if (host == null || !isLoopback("http://" + host)) return false;
        return origin == null || isLoopback(origin);
    }

    private static boolean isLoopback(String address) {
        try {
            String name = URI.create(address).getHost();
            return "127.0.0.1".equals(name) || "localhost".equals(name);
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    private void serve(HttpExchange exchange) throws IOException {
        try {
            var headers = exchange.getRequestHeaders();
            if (!allowedCaller(headers.getFirst("Host"), headers.getFirst("Origin"))) {
                exchange.sendResponseHeaders(403, -1);
            } else if (!exchange.getRequestMethod().equals("POST")) {
                // No server-sent stream: this server never speaks first.
                exchange.getResponseHeaders().set("Allow", "POST");
                exchange.sendResponseHeaders(405, -1);
            } else {
                byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES);
                String reply = protocol.handleLine(new String(body, StandardCharsets.UTF_8));
                if (reply == null) {
                    exchange.sendResponseHeaders(202, -1);
                } else {
                    byte[] out = reply.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, out.length);
                    exchange.getResponseBody().write(out);
                }
            }
        } finally {
            exchange.close();
        }
    }

    private McpToolResult atFrame(Read read) throws Exception {
        Pending call = new Pending(read, new CompletableFuture<>());
        pending.add(call);
        try {
            return call.answer().get(ANSWER_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException busy) {
            call.answer().cancel(true);
            return McpToolResult.failure("the window reached no frame in " + ANSWER_WAIT_SECONDS
                    + " s: it is still opening, or building a battle. Ask again.");
        }
    }

    private BattleProbe probe(BattleSimulation battle) {
        if (probe == null || probe.sim() != battle) probe = new BattleProbe(battle);
        return probe;
    }

    /** The commands in one call, on either separator a model is likely to reach for. */
    static List<String> commands(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("[;\\r\\n]+")) if (!line.isBlank()) lines.add(line.strip());
        return lines;
    }

    private static McpToolResult noBattle(View view) {
        return McpToolResult.failure("no battle is being played; the window is on " + view.screen());
    }

    private McpTool status() {
        return tool("play_status", "Where the player is now: the current screen, the world docked at, what can be pressed, "
                        + "and the battle's tick, seconds and winner if one is being played. Also frames shown, how long the server has been open "
                        + "and the slowest call so far, to see what asking has cost the window.",
                McpSchema.object().build(), arguments -> atFrame(view -> {
                    BattleSimulation battle = view.battle();
                    return McpToolResult.of(new JSONObject().put("screen", view.screen()).put("world", view.world())
                            .put("battle", battle == null ? JSONObject.NULL : probe(battle).run("tick"))
                            .put("controls", view.controls()).put("frames", frames)
                            .put("serverSeconds", (System.nanoTime() - opened) / 1_000_000_000L)
                            .put("calls", calls).put("slowestCallMs", Math.round(slowestCallMs * 100f) / 100d).toString());
                }));
    }

    private McpTool query() {
        return tool("battle_query", """
                        Ask the running battle one or more questions. Commands sent together are answered at one tick. Reads only; \
                        a command that would step the battle is refused. Most used:
                        why SQUAD: what a squad is doing and why (owner, directive and reason, commander phase, goal, plan steps, members).
                        squads [label=TEXT]: every squad alive with assignment, reason, goal, plan, route status and centroid.
                        units [label=TEXT] [squad=N]: bodies with position, health, squad, goal, order, reflex, target. A label is FACTION:TYPE; filter a big battle.
                        entity ID: everything readable about one body.
                        command: mission command, each side's strategy, phase and directives, and every objective.
                        presence: what keeps the company in play. beats [since=T] [text=TEXT]: story beats. places: compounds and capture.
                        near ID R, near-cell X Y R, sight ID R, find [label=TEXT], path ID [count=N], members SQUAD, speed ID, cell X Y, grid X0 Y0 X1 Y1, \
                        ground X0 Y0 X1 Y1, windows X0 Y0 X1 Y1, route X0 Y0 X1 Y1, drive ID X Y, escort, lanes, bores, creep, pads, cordon, airbase, corridor FROM TO, tick.
                        hits [target=ID] [attacker=ID] [since=T] [limit=N] and hits-by attacker|target: need battle_hitlog on first.""",
                McpSchema.object().requiredString("commands", "Commands separated by newlines or ';', at most "
                        + MAX_COMMANDS + ".").build(), arguments -> {
                    List<String> lines = commands(arguments.optString("commands", ""));
                    if (lines.isEmpty()) return McpToolResult.failure("no command given");
                    if (lines.size() > MAX_COMMANDS) return McpToolResult.failure("at most " + MAX_COMMANDS + " commands in one call");
                    // Refused here, before anything waits on the window.
                    for (String line : lines) {
                        if (!BattleProbe.reads(line)) {
                            return McpToolResult.failure("refused: '" + line + "' is not a read; the battle is being played, "
                                    + "so nothing here steps it. Reads: " + new JSONArray(BattleProbe.readCommands()));
                        }
                    }
                    return atFrame(view -> {
                        BattleSimulation battle = view.battle();
                        if (battle == null) return noBattle(view);
                        JSONArray answers = new JSONArray();
                        for (String line : lines) {
                            answers.put(new JSONObject().put("command", line).put("answer", probe(battle).run(line)));
                        }
                        return McpToolResult.of(new JSONObject().put("tick", battle.getSimTickIndex()).put("answers", answers).toString());
                    });
                });
    }

    private McpTool hitlog() {
        return tool("battle_hitlog", "The one write: start or stop recording every resolved hit, so battle_query's hits and "
                        + "hits-by can answer who hurt whom from here on. An observer: it draws no random number and changes nothing "
                        + "in the battle.",
                McpSchema.object().requiredString("action", "on, off, clear or status").build(), arguments -> {
                    String action = arguments.optString("action", "");
                    if (!List.of("on", "off", "clear", "status").contains(action)) {
                        return McpToolResult.failure("action is on, off, clear or status");
                    }
                    return atFrame(view -> {
                        BattleSimulation battle = view.battle();
                        return battle == null ? noBattle(view) : McpToolResult.of(probe(battle).run("hitlog " + action).toString());
                    });
                });
    }

    private static McpTool tool(String name, String description, JSONObject schema, Call call) {
        return new McpTool() {
            @Override public String name() { return name; }
            @Override public String description() { return description; }
            @Override public JSONObject inputSchema() { return schema; }
            @Override public McpToolResult call(JSONObject arguments, McpToolContext context) throws Exception {
                return call.run(arguments);
            }
        };
    }
}
