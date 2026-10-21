package com.dillon.starsectormarines.battle.probe;

import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.ThunderRunEvidenceBattle;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code gradlew battleProbe -Pbattle=<source> [-Pscript=<file>]}: stands one battle up and answers
 * {@link BattleProbe} commands against it, from a script file or, without one, from standard input.
 *
 * <p>Sources:
 * <ul>
 *   <li>{@code thunder-run[:key=value,...]} — the Thunder Run the evidence harness plays
 *       ({@code seed}, default 9301; {@code companySize}; {@code retry=true});</li>
 *   <li>{@code fixture:<path>} — any battle fixture JSON, rebuilt at tick zero through its production factory.</li>
 * </ul>
 * Each command is echoed as {@code > command} and answered with one line of JSON, so a transcript reads in order and
 * greps cleanly. {@code -Pout=<file>} writes the transcript there instead of standard output.
 */
public final class BattleProbeMain {

    private BattleProbeMain() {
    }

    public static void main(String[] args) throws Exception {
        String source = System.getProperty("probe.battle", "");
        if (source.isBlank()) throw new IllegalArgumentException("-Pbattle=thunder-run[:seed=N,...] or fixture:<path>");
        String script = System.getProperty("probe.script", "");
        String out = System.getProperty("probe.out", "");
        PrintStream print = out.isBlank() ? System.out
                : new PrintStream(Files.newOutputStream(Path.of(out)), true, StandardCharsets.UTF_8);
        long started = System.nanoTime();
        // What the JUnit extensions install before every evidence test: disk catalogs and the mod's strings.
        TileRegistryTestInstaller.install(Path.of(""));
        try (var strings = new HeadlessStrings(Path.of("mod")); BattleSimulation sim = build(source)) {
            print.println("# " + source + " built in " + (System.nanoTime() - started) / 1_000_000 + " ms");
            BattleProbe probe = new BattleProbe(sim);
            BufferedReader reader = script.isBlank()
                    ? new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))
                    : Files.newBufferedReader(Path.of(script), StandardCharsets.UTF_8);
            try (reader) {
                for (String line; (line = reader.readLine()) != null; ) {
                    String command = line.strip();
                    if (command.isEmpty() || command.startsWith("#")) continue;
                    if (command.equals("quit") || command.equals("exit")) break;
                    long t0 = System.nanoTime();
                    JSONObject answer = probe.run(command);
                    print.println("> " + command + "   (" + (System.nanoTime() - t0) / 1_000_000 + " ms)");
                    print.println(answer);
                    print.flush();
                }
            }
        } finally {
            if (print != System.out) print.close();
        }
        // Simulation-owned executors are closed with the battle; nothing else should hold the JVM up.
        System.exit(0);
    }

    static BattleSimulation build(String source) throws Exception {
        int colon = source.indexOf(':');
        String kind = colon < 0 ? source : source.substring(0, colon);
        String rest = colon < 0 ? "" : source.substring(colon + 1);
        return switch (kind) {
            case "thunder-run" -> {
                JSONObject fixture = new JSONObject().put("seed", 9301L);
                for (String pair : rest.split(",")) {
                    if (pair.isBlank()) continue;
                    String[] kv = pair.split("=", 2);
                    fixture.put(kv[0].trim(), JSONObject.stringToValue(kv.length > 1 ? kv[1].trim() : "true"));
                }
                yield ThunderRunEvidenceBattle.launched(fixture);
            }
            case "fixture" -> BattleFixtureJson.fromJson(new JSONObject(Files.readString(Path.of(rest)))).build();
            default -> throw new IllegalArgumentException("unknown battle source: " + kind);
        };
    }
}
