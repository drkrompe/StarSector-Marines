package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.objective.optional.ObjectiveBoard;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.staffed.Loading;
import com.dillon.starsectormarines.battle.staffed.LoadingObjective;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Locale;

/**
 * How many maps carry the freight yard set piece ({@code set-piece-blocks.md}), at which size, and
 * whether the Colony Breach loading stands its truck in a yard's bay (it does only where the bay has a
 * drivable way off the map). Colonies as the rescue plays them, re-rolls included, and stock city maps.
 *
 * <p>Evidence rather than a unit test: every row generates a whole map, and the colony rows stand a
 * rescue up on it. It asserts nothing; the readings go to {@code build/reports/freight-yard/summary.md}.
 */
@Tag("generated-space-evidence")
class FreightYardCoverageEvidenceTest {

    private static final int COLONIES = Integer.getInteger("freight.yard.evidence.colonies", 24);
    private static final int CITIES = Integer.getInteger("freight.yard.evidence.cities", 12);
    private static final int ATTEMPTS = 8;
    /** The same arithmetic {@code createCivilianRescue} steps its re-rolls by. */
    private static final long ATTEMPT_STEP = 0x9E3779B97F4A7C15L;

    @Test
    void freightYardsAcrossSeeds() throws Exception {
        SceneRegistries.installArmoury(Path.of("").toAbsolutePath());
        BspCityGenerator generator = new BspCityGenerator();
        StringBuilder md = new StringBuilder("# Freight yard coverage\n\n## Colonies\n\n")
                .append("| seed | bays | loading |\n|---:|---:|---|\n");
        int colonyYards = 0, large = 0, atBay = 0;
        for (long seed = 1; seed <= COLONIES; seed++) {
            MapResult map = null;
            for (int attempt = 0; attempt < ATTEMPTS && map == null; attempt++) {
                long battleSeed = seed + attempt * ATTEMPT_STEP;
                MapResult candidate = generator.generate(MapScale.COLONY.width, MapScale.COLONY.height,
                        battleSeed, null, TargetProfile.NEUTRAL, BattleSetup.colonyPlanFor(MapScale.COLONY, battleSeed));
                if (MissionMapRequirements.missingFromColony(candidate).isEmpty()) map = candidate;
            }
            int bays = map == null ? 0 : map.loadingBays.size();
            if (bays > 0) colonyYards++;
            if (bays > 1) large++;
            String loading;
            try (BattleSimulation sim = BattleSetup.createCivilianRescue(seed, Collections.emptyList(), false,
                    RiskLevel.HIGH)) {
                ObjectiveBoard board = sim.getOptionalObjectives();
                Loading l = board != null && board.runtime(LoadingObjective.KEY) instanceof LoadingObjective o
                        ? o.loading() : null;
                loading = l == null ? "none" : l.atBay() ? "in a bay" : "by search";
                if (l != null && l.atBay()) atBay++;
            }
            md.append(String.format(Locale.ROOT, "| %d | %d | %s |%n", seed, bays, loading));
            System.out.printf(Locale.ROOT, "[freight-yard] colony %d: %d bays, loading %s%n", seed, bays, loading);
        }
        md.append(String.format(Locale.ROOT, "%nA yard on %d of %d colonies (%d with more than one bay); the truck stood in a bay"
                + " on %d.%n%n## Cities%n%n| seed | bays |%n|---:|---:|%n", colonyYards, COLONIES, large, atBay));
        int cityYards = 0;
        for (long seed = 1; seed <= CITIES; seed++) {
            MapResult map = generator.generate(MapScale.LARGE.width, MapScale.LARGE.height, seed);
            if (!map.loadingBays.isEmpty()) cityYards++;
            md.append(String.format(Locale.ROOT, "| %d | %d |%n", seed, map.loadingBays.size()));
            System.out.printf(Locale.ROOT, "[freight-yard] city %d: %d bays%n", seed, map.loadingBays.size());
        }
        md.append(String.format(Locale.ROOT, "%nA yard on %d of %d cities.%n", cityYards, CITIES));
        Path out = Path.of("build", "reports", "freight-yard", "summary.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, md.toString(), StandardCharsets.UTF_8);
        System.out.print(md);
    }
}
