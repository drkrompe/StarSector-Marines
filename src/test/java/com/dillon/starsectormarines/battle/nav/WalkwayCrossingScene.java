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
 * Does a squad cross between the sky port station's decks by the walkways, and only by them (`voids.md`)? The
 * station from the sky port look: the landing deck on the west edge, joined by a wide and a narrow viaduct to the
 * raised concourse, five platforms running east from it, each reached by a stair bridge down from the concourse,
 * and footbridges across the track canyons between the platforms. A rifle squad is sent, unopposed, three ways:
 *
 * <ul>
 *   <li>{@code up-to-the-concourse}: from the landing deck to the concourse's north end, so the wide viaduct is
 *       the short way, and the squad should take it and not the narrow one;</li>
 *   <li>{@code across-the-tracks}: from the second platform to the first, the footbridge between them the short
 *       way, so it should never go up to the concourse;</li>
 *   <li>{@code footbridge-cut}: the same order with that footbridge gone, so the only way is back up a stair bridge,
 *       across the concourse and down another.</li>
 * </ul>
 *
 * <p>The concourse stands a tier above the rest, reached by the stairs at the ends of the viaducts and the stair
 * bridges. Every loop asserts that no one ever stands over the drop, that no one stands on the concourse without
 * having climbed a ramp, that the squad reaches the deck it was sent to, and which walkways it crossed. The other
 * side is one unarmed defender on a lone roof in the far corner, out of sight, so the battle runs; the mission's
 * end is off.
 */
public final class WalkwayCrossingScene implements BehaviorScene {

    static final int MAX_TICKS = 12000, FRAME_EVERY = 45;
    /** How near the order's cell every member must come to have arrived. */
    static final float ARRIVED_CELLS = 8f;
    static final int[] FROM_LANDING = {14, 150}, TO_CONCOURSE = {100, 160};
    static final int[] FROM_PLATFORM = {186, 89}, TO_PLATFORM = {210, 60};

    @Override public String id() { return "walkway-crossing"; }

    @Override public String label() { return "Squads cross between sky port towers by the walkways, never over the drop"; }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        reports.add(loop("up-to-the-concourse", true, FROM_LANDING, TO_CONCOURSE, List.of("wide-viaduct"),
                List.of("narrow-viaduct"), frames));
        reports.add(loop("across-the-tracks", true, FROM_PLATFORM, TO_PLATFORM, List.of("footbridge"),
                List.of("stair-bridge"), frames));
        reports.add(loop("footbridge-cut", false, FROM_PLATFORM, TO_PLATFORM, List.of("stair-bridge"), List.of(), frames));
        return reports;
    }

    private SceneReport loop(String loop, boolean footbridge, int[] from, int[] goal, List<String> crossed,
                             List<String> avoided, FrameSink frames) {
        SkyPortClusterSetup.Ground ground = SkyPortClusterSetup.lay(footbridge);
        SceneBuilder builder = SceneBuilder.over(ground.grid(), ground.topology()).missionCompletion(false)
                .seed(20261109L);
        builder.squad("rifles").faction(Faction.MARINE).size(6).primary("weapon.field-rifle")
                .inFile(from[0], from[1], 1, 0)
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
            Map<String, List<int[]>> walkways = new LinkedHashMap<>();
            walkways.put("wide-viaduct", List.of(SkyPortClusterSetup.VIADUCT_N));
            walkways.put("narrow-viaduct", List.of(SkyPortClusterSetup.VIADUCT_S));
            walkways.put("stair-bridge", List.of(SkyPortClusterSetup.STAIRWAYS));
            walkways.put("footbridge", List.of(SkyPortClusterSetup.FOOTBRIDGES[0]));
            for (String walkway : walkways.keySet()) {
                stoodOn.put(walkway, new boolean[members.length]);
            }
            ElevationMap elevation = sim.getGrid().elevation();
            boolean[] onStairs = new boolean[members.length];
            int offStairs = 0, climbed = 0;
            String firstOffStairs = "";
            int overDrop = 0, arrivedAt = -1, tick = 0;
            String firstOverDrop = "";
            for (; tick < MAX_TICKS && arrivedAt < 0; tick++) {
                sim.advance(BattleSimulation.TICK_DT);
                boolean all = true;
                for (int m = 0; m < members.length; m++) {
                    float x = sim.world().x(members[m]), y = sim.world().y(members[m]);
                    int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
                    int at = cy * topology.getWidth() + cx;
                    if (elevation.isRampAt(at)) onStairs[m] = true;
                    if (elevation.eyeTier(cx, cy) > 0 && !elevation.isRampAt(at) && !onStairs[m]
                            && offStairs++ == 0) {
                        firstOffStairs = "member " + m + " at (" + cx + ", " + cy + ") on tick " + tick;
                    }
                    if (topology.getGroundKind(cx, cy) == GroundKind.VOID && overDrop++ == 0) {
                        firstOverDrop = "member " + m + " at (" + cx + ", " + cy + ") on tick " + tick;
                    }
                    for (Map.Entry<String, List<int[]>> way : walkways.entrySet()) {
                        for (int[] r : way.getValue()) {
                            if (SkyPortClusterSetup.inside(cx, cy, r)) stoodOn.get(way.getKey())[m] = true;
                        }
                    }
                    all &= Math.hypot(x - goal[0] - .5f, y - goal[1] - .5f) <= ARRIVED_CELLS;
                }
                if (all) arrivedAt = tick;
                if (tick % FRAME_EVERY == 0) frames.frame(loop, sim, tick, loop + " / " + tick);
            }
            for (long member : members) {
                int mx = (int) Math.floor(sim.world().x(member)), my = (int) Math.floor(sim.world().y(member));
                if (elevation.eyeTier(mx, my) > 0) climbed++;
            }
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("up-by-the-stairs", offStairs == 0, offStairs == 0
                    ? "no member stood on the raised concourse without having climbed a ramp"
                    : offStairs + " member-ticks on the raised concourse never having climbed, first " + firstOffStairs));
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
            metrics.put("onConcourseAtEnd", climbed);
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
