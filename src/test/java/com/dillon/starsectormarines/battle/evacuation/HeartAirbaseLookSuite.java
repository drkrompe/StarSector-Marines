package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.airsupport.WingAirbase;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Broad Front's allied airbase as the owner would see it (`heart-nouns.md`, Allied airbase): one Broad Front battle
 * (verdant, seed 3) stood up and played unrecorded to {@code -Dsnapshot.heartAirbase.from} (default 60), then recorded
 * on a fixed camera over the base: the aircraft on its hardstands, the ground crew, and the standing patrol's fighters
 * taxiing out and rolling off the strip ({@code airbase}). Opt-in.
 */
public final class HeartAirbaseLookSuite implements SnapshotSuite {

    static final String FIXTURE = "{\"schemaVersion\":2,\"kind\":\"BLOOM_HEART\",\"seed\":3,\"form\":\"BROAD_FRONT\","
            + "\"biome\":\"VERDANT\",\"shuttles\":["
            + "{\"type\":\"AEROSHUTTLE\",\"cycles\":1,\"seatsPerSortie\":6},"
            + "{\"type\":\"AEROSHUTTLE\",\"cycles\":1,\"seatsPerSortie\":6},"
            + "{\"type\":\"AEROSHUTTLE\",\"cycles\":1,\"seatsPerSortie\":6},"
            + "{\"type\":\"AEROSHUTTLE\",\"cycles\":1,\"seatsPerSortie\":6}]}";

    @Override public String id() { return "heart-airbase-look"; }

    @Override public String label() { return "Heart allied airbase: parked aircraft, crew and sorties off the strip (Broad Front verdant 3)"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        int from = Integer.getInteger("snapshot.heartAirbase.from", 60);
        int ticks = Integer.getInteger("snapshot.heartAirbase.ticks", 900);
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            return new ArrayList<>(LookReview.record(context.modRoot(), shot("airbase", from, ticks)));
        }
    }

    /** The fixture played unrecorded to {@code fromTick}, then {@code ticks} recorded on a camera over the airbase. */
    static LookReview.Shot shot(String name, int fromTick, int ticks) {
        float[] camera = {0f, 0f, 64f};
        return new LookReview.Shot(name, LookGround.verdant(), 40, 30, placement -> {
            BattleSimulation sim;
            try {
                sim = HeadlessBattles.fixture(new JSONObject(FIXTURE));
            } catch (Exception e) {
                throw new IllegalStateException("heart fixture", e);
            }
            WingAirbase base = sim.airPool() == null ? null : sim.airPool().base(Faction.ALLY);
            if (base == null) throw new IllegalStateException("Broad Front verdant 3 laid no airbase");
            int[] b = base.bounds();
            camera[0] = (b[0] + b[2] + 1) / 2f;
            camera[1] = (b[1] + b[3] + 1) / 2f;
            while (sim.getSimTickIndex() < fromTick) sim.advance(BattleSimulation.TICK_DT);
            System.out.printf("    [look/%s] airbase %d,%d to %d,%d at tick %d%n", name, b[0], b[1], b[2], b[3],
                    sim.getSimTickIndex());
            return sim;
        }, placement -> List.of(LookReview.View.close("play", camera[0], camera[1], camera[2])), ticks, 6, 4);
    }
}
