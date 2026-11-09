package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.setup.SkyPortClusterSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Does a squad cross between sky port towers by the walkways, and only by them (`voids.md`)? The tower cluster
 * from the sky port look: three roofs over a void, A joined to B by a two-cell catwalk, to C by a catwalk that
 * turns a corner over the drop, and B to C by a six-cell skybridge. A rifle squad on A is sent, unopposed, to a
 * point on another roof, three ways:
 *
 * <ul>
 *   <li>{@code a-to-b}: the catwalk is the short way, and the squad should take it and no other walkway;</li>
 *   <li>{@code a-to-c}: the dogleg is the short way;</li>
 *   <li>{@code catwalk-cut}: the same order as {@code a-to-b} with the catwalk gone, so the only way is round by
 *       the dogleg, across C and over the skybridge.</li>
 * </ul>
 *
 * <p>Every loop asserts that no one ever stands over the drop, that the squad reaches the roof it was sent to,
 * and which walkways it crossed. The other side is one unarmed defender on a lone roof in the far corner, out of
 * sight, so the battle runs; the mission's end is off.
 */
public final class WalkwayCrossingScene implements BehaviorScene {

    static final int MAX_TICKS = 9000, FRAME_EVERY = 45;
    /** How near the order's cell every member must come to have arrived. */
    static final float ARRIVED_CELLS = 8f;
    static final int[] TO_B = {140, 104}, TO_C = {128, 38};

    @Override public String id() { return "walkway-crossing"; }

    @Override public String label() { return "Squads cross between sky port towers by the walkways, never over the drop"; }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        reports.add(loop("a-to-b", true, TO_B, List.of("catwalk"), List.of("dogleg", "skybridge"), frames));
        reports.add(loop("a-to-c", true, TO_C, List.of("dogleg"), List.of("catwalk", "skybridge"), frames));
        reports.add(loop("catwalk-cut", false, TO_B, List.of("dogleg", "skybridge"), List.of(), frames));
        return reports;
    }

    private SceneReport loop(String loop, boolean catwalk, int[] goal, List<String> crossed, List<String> avoided,
                             FrameSink frames) {
        SkyPortClusterSetup.Ground ground = SkyPortClusterSetup.lay(catwalk);
        SceneBuilder builder = SceneBuilder.over(ground.grid(), ground.topology()).missionCompletion(false)
                .seed(20261109L);
        builder.squad("rifles").faction(Faction.MARINE).size(6).primary("weapon.field-rifle")
                .inFile(36, 84, 1, 0)
                .assigned(id -> ObjectiveAssignment.attackMove(id, goal[0], goal[1]))
                .done();
        int lx = SkyPortClusterSetup.OUTPOST[0] + 3, ly = SkyPortClusterSetup.OUTPOST[1] + 3;
        builder.squad("lookout").faction(Faction.DEFENDER).size(1).unarmed().stationary().at(lx, ly)
                .assigned(id -> ObjectiveAssignment.defendArea(id, lx, ly))
                .done();
        SceneWorld scene = builder.build();
        try (BattleSimulation sim = scene.sim()) {
            CellTopology topology = sim.getTopology();
            long[] members = scene.members("rifles");
            Map<String, boolean[]> stoodOn = new LinkedHashMap<>();
            for (String walkway : List.of("catwalk", "dogleg", "skybridge")) {
                stoodOn.put(walkway, new boolean[members.length]);
            }
            int overDrop = 0, arrivedAt = -1, tick = 0;
            String firstOverDrop = "";
            for (; tick < MAX_TICKS && arrivedAt < 0; tick++) {
                sim.advance(BattleSimulation.TICK_DT);
                boolean all = true;
                for (int m = 0; m < members.length; m++) {
                    float x = sim.world().x(members[m]), y = sim.world().y(members[m]);
                    int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
                    if (topology.getGroundKind(cx, cy) == GroundKind.VOID && overDrop++ == 0) {
                        firstOverDrop = "member " + m + " at (" + cx + ", " + cy + ") on tick " + tick;
                    }
                    if (SkyPortClusterSetup.inside(cx, cy, SkyPortClusterSetup.CATWALK)) stoodOn.get("catwalk")[m] = true;
                    if (SkyPortClusterSetup.inside(cx, cy, SkyPortClusterSetup.SKYBRIDGE)) stoodOn.get("skybridge")[m] = true;
                    for (int[] leg : SkyPortClusterSetup.DOGLEG) {
                        if (SkyPortClusterSetup.inside(cx, cy, leg)) stoodOn.get("dogleg")[m] = true;
                    }
                    all &= Math.hypot(x - goal[0] - .5f, y - goal[1] - .5f) <= ARRIVED_CELLS;
                }
                if (all) arrivedAt = tick;
                if (tick % FRAME_EVERY == 0) frames.frame(loop, sim, tick, loop + " / " + tick);
            }
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("never-over-the-drop", overDrop == 0, overDrop == 0
                    ? "no member stood on a void cell"
                    : overDrop + " member-ticks over the drop, first " + firstOverDrop));
            verdicts.add(Verdict.of("arrived", arrivedAt >= 0, arrivedAt >= 0
                    ? "every member within " + ARRIVED_CELLS + " cells of the order by tick " + arrivedAt
                    : "not every member arrived in " + MAX_TICKS + " ticks"));
            for (String walkway : crossed) {
                int count = count(stoodOn.get(walkway));
                verdicts.add(Verdict.of("crossed-" + walkway, count == members.length,
                        count + " of " + members.length + " members stood on the " + walkway));
            }
            for (String walkway : avoided) {
                int count = count(stoodOn.get(walkway));
                verdicts.add(Verdict.of("kept-off-" + walkway, count == 0,
                        count + " of " + members.length + " members stood on the " + walkway + ", not the short way"));
            }
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("arrivedTick", arrivedAt);
            for (Map.Entry<String, boolean[]> e : stoodOn.entrySet()) metrics.put(e.getKey(), count(e.getValue()));
            return new SceneReport(id(), loop, tick, verdicts, metrics);
        }
    }

    private static int count(boolean[] flags) {
        int n = 0;
        for (boolean f : flags) if (f) n++;
        return n;
    }
}
