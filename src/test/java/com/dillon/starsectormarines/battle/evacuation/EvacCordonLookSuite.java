package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The evac cordon's works for the owner ({@code colony-breach-nouns.md}, Evac cordon): the colony played as shipped
 * and unrecorded to {@code -Dsnapshot.evacCordon.tick} (default 1200, forty seconds, the line landed and in its
 * trench), then four seconds recorded through the look preset on the pickup, the whole cordon ({@code cordon}) and
 * the field round it ({@code wide}). Each seed plays twice, with the works ({@code works}) and on the control
 * {@code -Dbattle.colonyBreach.cordonWorks=false} ({@code open}). {@code -Dsnapshot.evacCordon.seeds} (default
 * {@code 38/arid,16/rock}, the pooled maps) picks the seeds, each {@code seed} or {@code seed/biome}. Opt-in.
 */
public final class EvacCordonLookSuite implements SnapshotSuite {

    private static final String FIXTURE = "/battle-fixtures/colony-breach-v1.json";
    private static final String SWITCH = "battle.colonyBreach.cordonWorks";
    /** The cordon across the frame: the wall ring is 39 cells across. */
    private static final float CORDON_CELLS = 52f;
    private static final float WIDE_CELLS = 120f;

    @Override public String id() { return "evac-cordon-look"; }

    @Override public String label() { return "Colony Breach's evac cordon: the pickup's works and its line in the trench"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        int tick = Integer.getInteger("snapshot.evacCordon.tick", 1200);
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            List<SnapshotArtifact> out = new ArrayList<>();
            for (String token : System.getProperty("snapshot.evacCordon.seeds", "38/arid,16/rock").split(",")) {
                String[] seedAndBiome = token.trim().split("/");
                long seed = Long.parseLong(seedAndBiome[0]);
                String biome = seedAndBiome.length > 1 ? seedAndBiome[1].toUpperCase(Locale.ROOT) : null;
                for (boolean works : new boolean[]{true, false}) {
                    String before = System.getProperty(SWITCH);
                    System.setProperty(SWITCH, Boolean.toString(works));
                    try {
                        out.addAll(LookReview.record(context.modRoot(), shot(seed, biome, tick, works)));
                    } finally {
                        if (before == null) System.clearProperty(SWITCH);
                        else System.setProperty(SWITCH, before);
                    }
                }
            }
            return out;
        }
    }

    private static LookReview.Shot shot(long seed, String biome, int fromTick, boolean works) {
        float[] lift = new float[2];
        String name = String.format(Locale.ROOT, "cordon-s%d-%s-t%d", seed, works ? "works" : "open", fromTick);
        return new LookReview.Shot(name, LookGround.verdant(), 40, 30, placement -> {
            BattleSimulation sim;
            try {
                sim = HeadlessBattles.fixture(colony(seed, biome));
            } catch (Exception e) {
                throw new IllegalStateException("colony fixture", e);
            }
            float[] at = lift(sim);
            lift[0] = at[0];
            lift[1] = at[1];
            while (sim.getSimTickIndex() < fromTick && !sim.isComplete()) sim.advance(BattleSimulation.TICK_DT);
            CivilianEvacuationTracker tracker = sim.getCivilianEvacuationTracker();
            System.out.printf("    [look/%s] lift at %.0f,%.0f, %d guards alive, %d of %d lifted, %d lost, tick %d%n",
                    name, lift[0], lift[1], sim.liveRescuePickupGuards(), tracker.evacuatedCount(),
                    tracker.registeredCount(), tracker.lostCount(), sim.getSimTickIndex());
            return sim;
        }, placement -> List.of(
                new LookReview.View("cordon", sim -> new float[]{lift[0], lift[1], CORDON_CELLS}),
                new LookReview.View("wide", sim -> new float[]{lift[0], lift[1], WIDE_CELLS})), 120, 6, 1);
    }

    /** Where the survivors' pickup comes down: the civilian craft's landing cell. */
    private static float[] lift(BattleSimulation sim) {
        for (long id : sim.getAirEntityIds()) {
            ShuttleMission mission = sim.world().mission(id);
            if (sim.world().airFaction(id) == Faction.CIVILIAN && mission != null && mission.awaitingEvacuees) {
                return new float[]{mission.lzX, mission.lzY};
            }
        }
        throw new IllegalStateException("no pickup craft");
    }

    /** The checked-in colony fixture on {@code seed}, on the world {@code biome} names (null keeps the fixture's). */
    private static JSONObject colony(long seed, String biome) throws Exception {
        try (InputStream stream = EvacCordonLookSuite.class.getResourceAsStream(FIXTURE)) {
            if (stream == null) throw new IllegalStateException("Missing fixture " + FIXTURE);
            JSONObject fixture = new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).put("seed", seed);
            if (biome != null) fixture.getJSONObject("targetProfile").put("surface", biome);
            return fixture;
        }
    }
}
