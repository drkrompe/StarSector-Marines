package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The heart's allied commander at work, as the owner would see it (`heart-allied-commander.md`): one played Broad Front
 * town battle, its first call for machines and its gunship call, each recorded through the look preset from the moment
 * the commander makes the call, the camera on what the base sent. {@code commander-armour} and
 * {@code commander-gunship} are the same battle, so the two read as one fight. Each stands the battle up and plays it to
 * the call unrecorded, which costs minutes; opt-in.
 */
public final class HeartCommanderLookSuite implements SnapshotSuite {

    /** The battle: the canonical Broad Front town seed the commander's evidence plays. */
    static final String FIXTURE = "{\"schemaVersion\":2,\"kind\":\"BLOOM_HEART\",\"seed\":2,\"form\":\"BROAD_FRONT\","
            + "\"biome\":\"VERDANT\",\"density\":\"TOWN\",\"declaredMechDrops\":{\"MARINE\":{\"lances\":1,\"lance\":[\"BULWARK\",\"HOUND\",\"HOUND\",\"WARDEN\"],\"debugCallTick\":300}},\"shuttles\":["
            + "{\"type\":\"AEROSHUTTLE\",\"cycles\":1,\"seatsPerSortie\":6},"
            + "{\"type\":\"AEROSHUTTLE\",\"cycles\":1,\"seatsPerSortie\":6},"
            + "{\"type\":\"AEROSHUTTLE\",\"cycles\":1,\"seatsPerSortie\":6},"
            + "{\"type\":\"AEROSHUTTLE\",\"cycles\":1,\"seatsPerSortie\":6}]}";
    /** The latest tick a call is waited for. */
    static final int CALL_BY = 16000;
    /** The latest tick a re-aimed wave is waited for: lanes break late. */
    static final int REAIM_BY = 26000;
    /** Most machines one call sends: a pair, or a lance. */
    static final int TOOLS = 3;

    @Override public String id() { return "heart-commander-look"; }

    @Override public String label() { return "Heart allied commander: a call for machines and a gunship call in one Broad Front battle"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
        List<SnapshotArtifact> out = new ArrayList<>();
        String only = System.getProperty("snapshot.heartCommander.shots", "");
        if (only.isEmpty() || only.contains("armour"))
            out.addAll(LookReview.record(context.modRoot(), shot("commander-armour", "armour_called", 900, 60f, false, 6)));
        if (only.isEmpty() || only.contains("gunship"))
            out.addAll(LookReview.record(context.modRoot(), shot("commander-gunship", "gunship_called", 1300, 70f, false, 6)));
        // F2: the wave the commander re-aims at a broken lane, from its stepping off to the gap.
        if (only.isEmpty() || only.contains("reaim"))
            out.addAll(LookReview.record(context.modRoot(), shot("commander-reaim", "wave_sent", 2700, 90f, true, 12)));
        return out;
        }
    }

    /**
     * The battle played unrecorded to the first beat containing {@code call}, then {@code ticks} recorded with the
     * camera {@code cellsAcross} wide on the allied machines or aircraft that came on with the call.
     */
    static LookReview.Shot shot(String name, String call, int ticks, float cellsAcross, boolean infantry, int everyTicks) {
        String[] keys = new String[TOOLS];
        for (int i = 0; i < TOOLS; i++) keys[i] = "tool" + i;
        return new LookReview.Shot(name, LookGround.verdant(), 40, 30, placement -> {
            BattleSimulation sim;
            try {
                sim = HeadlessBattles.fixture(new JSONObject(FIXTURE));
            } catch (Exception e) {
                throw new IllegalStateException("heart fixture", e);
            }
            Set<Long> before = allied(sim, infantry);
            int seen = 0;
            String called = null;
            int by = infantry ? REAIM_BY : CALL_BY;
            while (called == null && sim.getSimTickIndex() < by) {
                before = allied(sim, infantry);
                sim.advance(BattleSimulation.TICK_DT);
                List<StoryBeatLog.Event> beats = sim.getStoryBeats();
                for (; seen < beats.size(); seen++) {
                    String id = beats.get(seen).id();
                    if (id.contains("lane_broken") || id.contains("wave_")) System.out.printf("    [look/%s] %s at %d%n", name, id, beats.get(seen).tick());
                    if (id.contains(call)) called = id;
                }
            }
            // A re-aimed wave steps off with every other lane's: its teams are the new ones at the sending lane's foot.
            int[] foot = infantry && called != null ? sendingFoot(sim, called) : null;
            int n = 0;
            for (long id : allied(sim, infantry)) {
                if (before.contains(id) || n >= TOOLS) continue;
                if (foot != null && Math.hypot(sim.world().x(id) - foot[0], sim.world().y(id) - foot[1]) > FOOT_CELLS) continue;
                placement.subject(keys[n++], id);
            }
            System.out.printf("    [look/%s] %s at tick %d, %d sent%n", name, call, sim.getSimTickIndex(), n);
            return sim;
        }, placement -> List.of(LookReview.View.follow("play", placement, cellsAcross, keys)), ticks, everyTicks, 4);
    }

    /** Cells from a lane's foot within which a wave that has just stepped off stands. */
    static final float FOOT_CELLS = 20f;

    /** The foot of the lane a {@code wave_sent_<gap>_from_<lane>_<n>} beat names. */
    private static int[] sendingFoot(BattleSimulation sim, String beat) {
        String[] parts = beat.substring(beat.indexOf("_from_") + "_from_".length()).split("_");
        return sim.getHeart().lanes().entry(Integer.parseInt(parts[0]));
    }

    /** Every allied machine and aircraft on the field (hulls, mechs and the base's aircraft), or with {@code infantry} its militia. */
    private static Set<Long> allied(BattleSimulation sim, boolean infantry) {
        Set<Long> ids = new HashSet<>();
        if (!infantry) {
            for (long id : sim.getConvoyVehicleIds()) if (sim.convoy().faction(id) == Faction.ALLY) ids.add(id);
            for (long id : sim.getAirEntityIds()) if (sim.identity().faction(id) == Faction.ALLY) ids.add(id);
        }
        for (int i = 0, count = sim.liveUnitCount(); i < count; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.ALLY) continue;
            boolean mech = sim.identity().type(unit) == UnitType.HEAVY_MECH;
            if (infantry ? !mech && !sim.identity().type(unit).isTurret() : mech) ids.add(unit);
        }
        return ids;
    }
}
