package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.DefenseFrontage;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.decision.goap.world.GarrisonArea;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.Compound;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.MilitaryBaseFiller;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A single generated military compound in open ground, a defender garrison
 * inside it, and a marine assault walking in from one edge — small enough to
 * run headless in seconds and specific enough to watch one behavior.
 *
 * <p>The compound is stamped by the production {@link MilitaryBaseFiller}, so
 * its perimeter wall, gates, sub-buildings, and firing apertures are the ones
 * a real map gets rather than geometry authored to suit the test. That is the
 * point of the scene: {@link FrontageDefense} unit tests already pin the
 * derivation against a hand-drawn envelope, and what they cannot show is
 * whether the same rules find anything on a generated compound and whether a
 * garrison actually walks to the apertures while an assault closes.
 */
final class FrontageScene {

    static final int WIDTH = 112;
    static final int HEIGHT = 112;

    /**
     * Four compound member leaves around a central parade cross, with roughly
     * thirty-five cells of open ground outside every wall. The approach length
     * is load-bearing rather than cosmetic: a garrison spawns beside its
     * command building and has to walk to the perimeter, so a scene where the
     * assault crosses the last fifteen cells in under two hundred ticks
     * measures nothing except that the walk did not finish.
     */
    private static final BlockLeaf COMMAND = new BlockLeaf(40, 40, 53, 53, false);
    private static final BlockLeaf BARRACKS = new BlockLeaf(58, 40, 71, 53, false);
    private static final BlockLeaf ARMORY = new BlockLeaf(40, 58, 53, 71, false);
    private static final BlockLeaf VEHICLE_BAY = new BlockLeaf(58, 58, 71, 71, false);

    /** Road reservation lines through the inter-leaf gaps, which is what makes the filler read four buildings as one base. */
    private static final int PARADE_X = 56;
    private static final int PARADE_Y = 56;

    /** Outer extent of the member leaves — the reservation must not run past this or the wall ring is left open. */
    private static final int COMPOUND_LEFT = 40;
    private static final int COMPOUND_RIGHT = 71;

    private static final int GARRISON_SPAWN_RADIUS = 5;

    private FrontageScene() {}

    /** Where the marine assault enters from. The compound's own geometry is identical in every case; only the approach differs. */
    enum Approach {
        SOUTH(WIDTH / 2, HEIGHT - 4),
        NORTH(WIDTH / 2, 3),
        EAST(WIDTH - 4, HEIGHT / 2),
        WEST(3, HEIGHT / 2);

        final int cellX;
        final int cellY;

        Approach(int cellX, int cellY) {
            this.cellX = cellX;
            this.cellY = cellY;
        }
    }

    record Scene(BattleSimulation sim, TacticalNode primary, Squad garrison, Squad assault,
                 List<TacticalNode> nodes, Approach approach) {}

    /**
     * One observation of the garrison, taken between ticks.
     *
     * <p>{@code postsFacingThreat} counts aperture posts whose watched ground
     * lies on the same side of the compound centre as the live marine
     * centroid. Deliberately measured against where the assault actually is
     * rather than the compass direction it entered from: once marines walk
     * around a compound looking for a gate, "the southern approach" stops
     * describing the threat, and facing the south wall would then be the wrong
     * behavior to assert.
     */
    record Sample(int tick, String goal, int aperturePosts, int reservePosts,
                  int postsFacingThreat, int membersOnPost, float believedPressure,
                  boolean enemyInside, float marineX, float marineY, int liveMarines) {

        int posts() { return aperturePosts + reservePosts; }
    }

    static Scene build(long seed, int garrisonSize, int assaultSize, Approach approach) {
        GenContext ctx = stampCompound(seed);
        BattleSimulation sim = new BattleSimulation(ctx.grid, ctx.topology, seed);
        List<TacticalNode> nodes = List.copyOf(ctx.tactical);
        sim.setTacticalMap(new TacticalMap(nodes));
        for (Doodad doodad : ctx.doodads) sim.addDoodad(doodad);

        TacticalNode primary = primaryNode(nodes);
        Squad garrison = spawnGarrison(sim, primary, garrisonSize);
        Squad assault = spawnAssault(sim, primary, assaultSize, approach);
        return new Scene(sim, primary, garrison, assault, nodes, approach);
    }

    /** Run the scene, sampling the garrison every {@code samplePeriod} ticks. */
    static List<Sample> play(Scene scene, int ticks, int samplePeriod) {
        List<Sample> samples = new ArrayList<>();
        for (int tick = 0; tick <= ticks; tick++) {
            if (tick % samplePeriod == 0) samples.add(sample(scene, tick));
            scene.sim().advance(BattleSimulation.TICK_DT);
        }
        return samples;
    }

    static Sample sample(Scene scene, int tick) {
        Squad garrison = scene.garrison();
        BattleSimulation sim = scene.sim();
        String goal = garrison.currentGoal != null ? garrison.currentGoal.name() : "none";

        float[] marines = marineCentroid(sim);
        int aperture = 0;
        int reserve = 0;
        int facingThreat = 0;
        int onPost = 0;
        ApertureHold hold = activeHold(garrison);
        if (hold != null) {
            for (ApertureHold.Post post : hold.posts()) {
                if (post.isReserve()) {
                    reserve++;
                } else {
                    aperture++;
                    if (facesThreat(scene, post, marines)) facingThreat++;
                }
            }
            onPost = membersOnPost(sim, garrison, hold);
        }

        float pressure = sim.getCommanderInfluence(Faction.DEFENDER).maxHostile();
        return new Sample(tick, goal, aperture, reserve, facingThreat, onPost,
                pressure, enemyInside(scene), marines[0], marines[1], (int) marines[2]);
    }

    /** The {@link ApertureHold} the garrison is executing right now, or null when it is doing something else. */
    static ApertureHold activeHold(Squad squad) {
        SquadPlan plan = squad.currentPlan;
        if (plan == null || plan.isComplete()) return null;
        SquadPlan.Step step = plan.currentStep();
        return step != null && step.action instanceof ApertureHold hold ? hold : null;
    }

    /**
     * Whether a post watches ground on the same side of the compound as the
     * live marine centroid. Judged on the dominant axis of that offset so a
     * post is scored against the wall it is actually on instead of against a
     * diagonal tie.
     */
    private static boolean facesThreat(Scene scene, ApertureHold.Post post, float[] marines) {
        if (marines[2] <= 0f) return false;
        TacticalNode node = scene.primary();
        float centreX = (node.compoundLeft() + node.compoundRight()) / 2f;
        float centreY = (node.compoundTop() + node.compoundBottom()) / 2f;
        float offsetX = marines[0] - centreX;
        float offsetY = marines[1] - centreY;
        if (Math.abs(offsetX) >= Math.abs(offsetY)) {
            return Math.signum(post.watchX() - centreX) == Math.signum(offsetX);
        }
        return Math.signum(post.watchY() - centreY) == Math.signum(offsetY);
    }

    /** Live marine centroid as {@code [x, y, count]}; a zero count means no marines remain. */
    private static float[] marineCentroid(BattleSimulation sim) {
        float sumX = 0f;
        float sumY = 0f;
        int count = 0;
        for (int i = 0; i < sim.getRoster().liveCount(); i++) {
            long unit = sim.getRoster().get(i);
            if (sim.identity().faction(unit) != Faction.MARINE) continue;
            if (!sim.identity().type(unit).combatant) continue;
            sumX += sim.world().x(unit);
            sumY += sim.world().y(unit);
            count++;
        }
        return count == 0 ? new float[]{0f, 0f, 0f}
                : new float[]{sumX / count, sumY / count, count};
    }

    private static int membersOnPost(BattleSimulation sim, Squad squad, ApertureHold hold) {
        SquadPlan plan = squad.currentPlan;
        SquadPlan.Step step = plan != null && !plan.isComplete() ? plan.currentStep() : null;
        if (step == null) return 0;
        int count = 0;
        for (int i = 0; i < hold.posts().size(); i++) {
            ApertureHold.Post post = hold.posts().get(i);
            for (long member : step.assignments.getOrDefault(ApertureHold.slotName(i), List.of())) {
                if (sim.getRoster().isLive(member)
                        && sim.movement().atCell(member, post.standX(), post.standY())) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Whether a marine stands in the compound's held zones — the same question
     * the breach gate asks. Deliberately not a bounding-box test: a box calls a
     * marine on unheld ground just outside a wall "inside" and would make the
     * goal look like it had failed to release when it had not.
     */
    private static boolean enemyInside(Scene scene) {
        BattleSimulation sim = scene.sim();
        List<Integer> held = GarrisonArea.garrisonZones(scene.primary(),
                DefenseFrontage.COMPOUND_MARGIN, sim);
        for (int i = 0; i < sim.getRoster().liveCount(); i++) {
            long unit = sim.getRoster().get(i);
            if (sim.identity().faction(unit) != Faction.MARINE) continue;
            if (!sim.identity().type(unit).combatant) continue;
            if (held.contains(sim.getZoneGraph().zoneIdAt(
                    sim.world().cellX(unit), sim.world().cellY(unit)))) {
                return true;
            }
        }
        return false;
    }

    /** Open ground with one production-stamped military compound in the middle. */
    private static GenContext stampCompound(long seed) {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        boolean[][] road = new boolean[WIDTH][HEIGHT];
        boolean[][] reservation = new boolean[WIDTH][HEIGHT];
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.STREET);
                road[x][y] = true;
            }
        }
        // Reserve the inter-leaf road centrelines only where they run BETWEEN
        // the member leaves. A reservation that continues past the compound is
        // not a detail: paintWallRing deliberately skips reserved cells, so a
        // full-length centreline punches a permanent hole in the perimeter on
        // every side, the parade ground joins the street as one zone, and the
        // compound's frontage becomes its building shells instead of its wall.
        for (int i = COMPOUND_LEFT + 1; i <= COMPOUND_RIGHT - 1; i++) {
            reservation[PARADE_X][i] = true;
            reservation[i][PARADE_Y] = true;
        }

        GenContext ctx = new GenContext(grid, topology, new Random(seed), WIDTH, HEIGHT, seed);
        ctx.put(BspKeys.ROAD_CELLS, road);
        ctx.put(BspKeys.ROAD_RESERVATION, reservation);
        new MilitaryBaseFiller().fill(compound(), ctx);
        return ctx;
    }

    private static Compound compound() {
        List<BlockLeaf> members = new ArrayList<>(
                List.of(COMMAND, BARRACKS, ARMORY, VEHICLE_BAY));
        Map<BlockLeaf, Compound.Role> roles = new IdentityHashMap<>();
        roles.put(COMMAND, Compound.Role.COMMAND);
        roles.put(BARRACKS, Compound.Role.BARRACKS);
        roles.put(ARMORY, Compound.Role.ARMORY);
        roles.put(VEHICLE_BAY, Compound.Role.VEHICLE_BAY);
        return new Compound(BlockKind.MILITARY_BASE, COMMAND, members, roles, null);
    }

    /** Highest-priority emitted node, anchor-ordered for ties — the same primary rule the garrison behaviors use. */
    private static TacticalNode primaryNode(List<TacticalNode> nodes) {
        TacticalNode best = null;
        for (TacticalNode node : nodes) {
            if (best == null
                    || node.priorityScore > best.priorityScore
                    || (node.priorityScore == best.priorityScore && node.anchorX < best.anchorX)) {
                best = node;
            }
        }
        if (best == null) throw new IllegalStateException("compound emitted no tactical nodes");
        return best;
    }

    private static Squad spawnGarrison(BattleSimulation sim, TacticalNode node, int size) {
        List<int[]> cells = BattleSetup.pickCellsNear(sim.getGrid(), sim.getZoneGraph(),
                node.anchorX, node.anchorY, GARRISON_SPAWN_RADIUS, size);
        if (cells.isEmpty()) throw new IllegalStateException("no garrison spawn cells near anchor");
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MARINE_RED);
        Squad squad = sim.getSquad(squadId);
        squad.assignedNode = node;
        squad.holdsFireUntilKillZone = true;
        int index = 0;
        for (int[] cell : cells) {
            EntitySpec spec = new EntitySpec("garrison-" + index++, Faction.DEFENDER,
                    UnitType.MARINE_RED, cell[0], cell[1]);
            spec.role(UnitRole.GARRISON).home(cell[0], cell[1]).squad(squadId);
            sim.spawn(spec);
        }
        squad.originalSize = cells.size();
        return squad;
    }

    private static Squad spawnAssault(BattleSimulation sim, TacticalNode node, int size,
                                      Approach approach) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        List<int[]> cells = BattleSetup.pickCellsNear(sim.getGrid(), sim.getZoneGraph(),
                approach.cellX, approach.cellY, 4, size);
        if (cells.isEmpty()) throw new IllegalStateException("no assault spawn cells at " + approach);
        int index = 0;
        for (int[] cell : cells) {
            EntitySpec spec = new EntitySpec("assault-" + index++, Faction.MARINE,
                    UnitType.MARINE, cell[0], cell[1]);
            spec.squad(squadId);
            sim.spawn(spec);
        }
        squad.originalSize = cells.size();
        squad.assignedObjective = ObjectiveAssignment.secureCompound(squadId,
                sim.getZoneGraph().zoneIdAt(node.anchorX, node.anchorY), node);
        return squad;
    }
}
