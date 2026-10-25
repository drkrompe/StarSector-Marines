package com.dillon.starsectormarines.testsupport;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.cordon.CordonPlan;
import com.dillon.starsectormarines.battle.cordon.CordonPosture;
import com.dillon.starsectormarines.battle.cordon.CordonVariant;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.CordonBattleFixture;
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
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The one way a headless harness stands a battle up, shared by {@code battleProbe} and the snapshot suites that play
 * a fixture, so a probe reading reproduces in a clip. They used to differ: the probe installed every disk catalog and
 * the mod's strings, a suite only map generation and special equipment, and the same fixture played a different
 * battle (a heart lane that broke at tick 8341 under the probe broke at 19213 in the suite). The deterministic
 * scheduler switches are the other half, and {@code createSnapshots} and {@code battleProbe} are both given them.
 */
public final class HeadlessBattles {

    private HeadlessBattles() {
    }

    /**
     * Installs what the game would have loaded (every disk catalog, {@link TileRegistryTestInstaller}) and the mod's
     * strings; the battle must be played before the returned strings are closed.
     */
    public static HeadlessStrings install(Path projectRoot) throws Exception {
        TileRegistryTestInstaller.install(projectRoot);
        return new HeadlessStrings(projectRoot.resolve("mod"));
    }

    /** A fixture JSON's battle, at tick zero, through its production factory. */
    public static BattleSimulation fixture(JSONObject fixture) throws Exception {
        return BattleFixtureJson.fromJson(fixture).build();
    }

    /**
     * A battle source: {@code fixture:<path>}, {@code thunder-run[:seed=N,...]}, {@code relief[:seed=N,surface=S]} or
     * {@code cordon[:variant=line|road,density=rural|town|urban,biome=VERDANT,seed=N]}.
     */
    public static BattleSimulation build(String source) throws Exception {
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
            case "fixture" -> fixture(new JSONObject(Files.readString(Path.of(rest))));
            case "cordon" -> cordon(options(rest));
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

    /**
     * A Cordon at one pooled seed, built as {@code CordonCommandEvidenceTest} builds each seed of a pool: the checked-in
     * fixture of the variant standing in for everything but the seed, biome and density.
     */
    private static BattleSimulation cordon(JSONObject options) throws Exception {
        CordonVariant variant = CordonVariant.valueOf(options.optString("variant", "line").toUpperCase(Locale.ROOT));
        String resource = "/battle-fixtures/cordon-" + variant.name().toLowerCase(Locale.ROOT) + "-v1.json";
        String json;
        try (InputStream stream = HeadlessBattles.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("missing Cordon fixture " + resource);
            json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        CordonBattleFixture template = (CordonBattleFixture) BattleFixtureJson.fromJson(new JSONObject(json));
        CordonPlan.Density density = CordonPlan.Density.valueOf(options.optString("density", "rural").toUpperCase(Locale.ROOT));
        SurfacePalette biome = SurfacePalette.valueOf(options.optString("biome", "VERDANT").toUpperCase(Locale.ROOT));
        long seed = options.optLong("seed", template.seed());
        CordonPosture posture = CordonPosture.valueOf(options.optString("posture", "hold").toUpperCase(Locale.ROOT));
        return new CordonBattleFixture(seed, variant, biome, density, template.manifest(), posture).build();
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
