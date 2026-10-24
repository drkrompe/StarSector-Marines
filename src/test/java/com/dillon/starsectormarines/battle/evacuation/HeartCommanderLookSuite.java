package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
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
            + "\"biome\":\"VERDANT\",\"density\":\"TOWN\",\"shuttles\":["
            + "{\"type\":\"AEROSHUTTLE\",\"cycles\":1,\"seatsPerSortie\":6},"
            + "{\"type\":\"AEROSHUTTLE\",\"cycles\":1,\"seatsPerSortie\":6},"
            + "{\"type\":\"AEROSHUTTLE\",\"cycles\":1,\"seatsPerSortie\":6},"
            + "{\"type\":\"AEROSHUTTLE\",\"cycles\":1,\"seatsPerSortie\":6}]}";
    /** The latest tick a call is waited for. */
    static final int CALL_BY = 16000;
    /** Most machines one call sends: a pair, or a lance. */
    static final int TOOLS = 3;

    @Override public String id() { return "heart-commander-look"; }

    @Override public String label() { return "Heart allied commander: a call for machines and a gunship call in one Broad Front battle"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        out.addAll(LookReview.record(context.modRoot(), shot("commander-armour", "armour_called", 900, 60f)));
        out.addAll(LookReview.record(context.modRoot(), shot("commander-gunship", "gunship_called", 1300, 70f)));
        return out;
    }

    /**
     * The battle played unrecorded to the first beat containing {@code call}, then {@code ticks} recorded with the
     * camera {@code cellsAcross} wide on the allied machines or aircraft that came on with the call.
     */
    static LookReview.Shot shot(String name, String call, int ticks, float cellsAcross) {
        String[] keys = new String[TOOLS];
        for (int i = 0; i < TOOLS; i++) keys[i] = "tool" + i;
        return new LookReview.Shot(name, LookGround.verdant(), 40, 30, placement -> {
            BattleSimulation sim;
            try {
                sim = BattleFixtureJson.fromJson(new JSONObject(FIXTURE)).build();
            } catch (Exception e) {
                throw new IllegalStateException("heart fixture", e);
            }
            Set<Long> before = allied(sim);
            int seen = 0;
            boolean called = false;
            while (!called && sim.getSimTickIndex() < CALL_BY) {
                before = allied(sim);
                sim.advance(BattleSimulation.TICK_DT);
                List<StoryBeatLog.Event> beats = sim.getStoryBeats();
                for (; seen < beats.size(); seen++) if (beats.get(seen).id().contains(call)) called = true;
            }
            int n = 0;
            for (long id : allied(sim)) if (!before.contains(id) && n < TOOLS) placement.subject(keys[n++], id);
            System.out.printf("    [look/%s] %s at tick %d, %d sent%n", name, call, sim.getSimTickIndex(), n);
            return sim;
        }, placement -> List.of(LookReview.View.follow("play", placement, cellsAcross, keys)), ticks, 6, 4);
    }

    /** Every allied machine and aircraft on the field: hulls, mechs and the base's aircraft. */
    private static Set<Long> allied(BattleSimulation sim) {
        Set<Long> ids = new HashSet<>();
        for (long id : sim.getConvoyVehicleIds()) if (sim.convoy().faction(id) == Faction.ALLY) ids.add(id);
        for (long id : sim.getAirEntityIds()) if (sim.identity().faction(id) == Faction.ALLY) ids.add(id);
        for (int i = 0, count = sim.liveUnitCount(); i < count; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) == Faction.ALLY && sim.identity().type(unit) == UnitType.HEAVY_MECH) ids.add(unit);
        }
        return ids;
    }
}
