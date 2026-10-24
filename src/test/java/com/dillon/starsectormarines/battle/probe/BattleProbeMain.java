package com.dillon.starsectormarines.battle.probe;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchOverlay;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.power.CommandPowerResources;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import com.dillon.starsectormarines.ops.ThunderRunEvidenceBattle;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code gradlew battleProbe -Pbattle=<source> [-Pscript=<file>]}: stands one battle up and answers
 * {@link BattleProbe} commands against it, from a script file or, without one, from standard input.
 *
 * <p>Sources:
 * <ul>
 *   <li>{@code thunder-run[:key=value,...]} — the Thunder Run the evidence harness plays
 *       ({@code seed}, default 9301; {@code companySize}; {@code retry=true});</li>
 *   <li>{@code relief[:seed=N,surface=VERDANT]} — the Relief ground start, the campaign's first mission: the
 *       starter company already ashore beside the hauler, as {@code ReliefCommandEvidenceTest} stands it up;</li>
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
                JSONObject fixture = options(rest);
                if (!fixture.has("seed")) fixture.put("seed", 9301L);
                yield ThunderRunEvidenceBattle.launched(fixture);
            }
            case "relief" -> {
                JSONObject options = options(rest);
                yield relief(options.optLong("seed", 1L), SurfacePalette.valueOf(options.optString("surface", "VERDANT")));
            }
            case "fixture" -> BattleFixtureJson.fromJson(new JSONObject(Files.readString(Path.of(rest)))).build();
            default -> throw new IllegalArgumentException("unknown battle source: " + kind);
        };
    }

    private static JSONObject options(String rest) throws Exception {
        JSONObject options = new JSONObject();
        for (String pair : rest.split(",")) {
            if (pair.isBlank()) continue;
            String[] kv = pair.split("=", 2);
            options.put(kv[0].trim(), JSONObject.stringToValue(kv.length > 1 ? kv[1].trim() : "true"));
        }
        return options;
    }

    /** The Relief ground start: twelve named starter recruits ashore at tick zero, the employer's shuttle flying. */
    private static BattleSimulation relief(long seed, SurfacePalette surface) {
        TargetProfile profile = new TargetProfile(0, 0, 0, 0, "", Set.of(), surface, SettlementLink.ROAD);
        int companySize = 12;
        BattleSimulation sim = BattleSetup.createOpeningOperation(seed,
                List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1)), 1, OpeningOperationKind.RELIEF, profile,
                false, true);
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(companySize);
        var selected = roster.squads().stream().filter(s -> !s.reserve()).map(s -> s.id()).collect(Collectors.toSet());
        var deployment = CampaignMarineDeployment.freezeSelection(roster, selected, companySize);
        BattleLaunchOverlay.capture(1, deployment, FlybyRoster.EMPTY, FlybyRoster.EMPTY, List.of(), 0, null)
                .applyTo(sim, CommandPowerResources.FREE);
        return sim;
    }
}
