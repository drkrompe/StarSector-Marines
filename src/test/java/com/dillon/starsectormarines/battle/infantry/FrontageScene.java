package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.DefenseFrontage;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    /**
     * Compound footprints worth testing against, because a real map does not
     * generate the tidy one. BSP leaves differ in size, a compound may have two
     * or three members rather than four, and dropping a member leaves a
     * concave notch that the filler absorbs into the footprint — none of which
     * the square case exercises.
     */
    enum Shape {
        /** Four equal leaves around a parade cross. The symmetric baseline. */
        SQUARE,
        /** Three leaves; the missing quadrant leaves a concave notch in the footprint. */
        ELL,
        /** Two leaves side by side — a compound much wider than it is deep. */
        RIBBON,
        /** Four leaves of markedly different sizes, as unequal BSP splits produce. */
        LOPSIDED
    }

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

    /** Cells between adjacent assault squads' spawn seeds along the approach edge. */
    private static final int ASSAULT_SQUAD_SPACING = 14;

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

        /**
         * Which edge of a rendered frame this approach enters from. World +y
         * draws upward, so the world name and the picture disagree: anything a
         * person reads — a recording's caption, a report label — should use
         * this, and anything about world geometry should use the name.
         */
        String renderedEdge() {
            return switch (this) {
                case SOUTH -> "top";
                case NORTH -> "bottom";
                case EAST -> "right";
                case WEST -> "left";
            };
        }
    }

    /**
     * @param garrisons defender squads, one per emitted tactical node, in node order
     * @param assaults  marine squads, spread along the approach edge
     */
    record Scene(BattleSimulation sim, TacticalNode primary, List<Squad> garrisons,
                 List<Squad> assaults, List<TacticalNode> nodes, List<Approach> approaches,
                 Map<Integer, Approach> approachBySquad) {

        /** The first assaulted edge — the label a single-axis scene is named for. */
        Approach approach() {
            return approaches.get(0);
        }

        /**
         * The garrison whose frontage is the compound perimeter, falling back to
         * the highest-priority node's squad when none of them holds it. Asked of
         * the live sim rather than assumed from spawn order, since which squad
         * holds the whole compound depends on the room count its footprint
         * currently resolves to.
         */
        Squad perimeterGarrison() {
            for (Squad squad : garrisons) {
                if (FrontageDefense.holdsWholeCompound(squad, sim)) return squad;
            }
            return garrisons.get(0);
        }
    }

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
                  boolean enemyInside, boolean frontageRelevant, String perimeterLayer,
                  float marineX, float marineY, int liveMarines,
                  List<SquadSample> garrisons, List<AxisCoverage> axes, Crowding crowding) {

        /** Aperture posts manned across every garrison at this instant. */
        int mannedApertures() {
            return garrisons.stream().mapToInt(SquadSample::aperturePosts).sum();
        }

        /** Posts covering the least-covered edge that still has live attackers, or -1 when none do. */
        int coverageOfWeakestLiveAxis() {
            return weakestAxis(false);
        }

        /** The same, restricted to edges the defender actually believes are threatened. */
        int coverageOfWeakestBelievedAxis() {
            return weakestAxis(true);
        }

        private int weakestAxis(boolean believedOnly) {
            int worst = -1;
            for (AxisCoverage axis : axes) {
                if (axis.liveMarines() == 0) continue;
                if (believedOnly && !axis.believed()) continue;
                worst = worst < 0 ? axis.postsCovering()
                        : Math.min(worst, axis.postsCovering());
            }
            return worst;
        }

        int posts() { return aperturePosts + reservePosts; }
    }

    /**
     * One garrison squad's state at a sampled instant. {@code layer} is the
     * envelope it is currently holding — {@code COMPOUND} for the perimeter,
     * {@code STRUCTURE} for a building shell, {@code none} when it is not
     * standing to. Two squads may share a layer; overlapping posts within one
     * are still a defect.
     */
    /**
     * One assaulted edge at a sampled instant: how much of the assault that
     * came in there is still alive, and how many aperture posts cover that side
     * of the compound.
     *
     * <p>Measured per edge rather than against the whole marine centroid,
     * because two assaults on opposite sides average out to the middle of the
     * compound and a single centroid would call a defense facing neither of
     * them well aimed.
     */
    record AxisCoverage(String approach, String edge, int liveMarines, int postsCovering,
                        float believedPressure) {

        /**
         * Whether the defender has reason to man this side. Coverage of an
         * approach nobody has observed is not something belief-driven stand-to
         * promises, and should not be: a garrison that picketed an unobserved
         * side would be reading the map rather than its own reports.
         */
        boolean believed() {
            return believedPressure >= FrontageDefense.STAND_TO_THRESHOLD;
        }
    }

    record SquadSample(int squadId, String layer, String goal, int aperturePosts,
                       int reservePosts, int membersOnPost, boolean frontageRelevant,
                       int aliveMembers) {}

    /**
     * How tightly the battle is packed at a sampled instant.
     *
     * <p>Two different questions, deliberately kept apart. {@code postCollisions}
     * counts stance cells that more than one squad has assigned somebody to —
     * an allocation defect, since two squads sending members to one cell leaves
     * one of them permanently unable to reach its post. {@code maxUnitsInCell}
     * and {@code crowdedPairs} measure where bodies physically ended up, which
     * separation and occupancy already govern; a squad correctly ordered to a
     * doorway is expected to bunch there.
     *
     * @param postCells      distinct stance cells assigned across every garrison squad
     * @param postCollisions stance cells assigned by more than one squad
     * @param liveUnits      live combatants of either side
     * @param occupiedCells  distinct cells those units stand on
     * @param maxUnitsInCell most units sharing any one cell
     * @param crowdedPairs   pairs of same-faction units within {@link #CROWDING_RADIUS} of each other
     * @param minGarrisonGap smallest distance between any two garrison squad centroids, or
     *                       {@code -1} when fewer than two garrisons still have members
     */
    record Crowding(int postCells, int postCollisions, int liveUnits, int occupiedCells,
                    int maxUnitsInCell, int crowdedPairs, float minGarrisonGap) {

        /** Units per distinct occupied cell; 1.0 means nobody is sharing ground. */
        double packing() {
            return occupiedCells == 0 ? 0d : (double) liveUnits / occupiedCells;
        }
    }

    /**
     * Separation below which two same-faction units count as bunched, in cells.
     * Matches {@code SeparationSystem.QUERY_RADIUS} — the distance at which the
     * sim itself starts treating a pair as neighbours worth pushing apart.
     *
     * <p>Note that bunching is not by itself a defect: infantry compresses to
     * {@code SeparationSystem.INFANTRY_FORMATION_MIN_DISTANCE} (0.75 cells) in a
     * tight passage on purpose, so several bodies legitimately share one cell
     * at a doorway. These counts are for watching a trend, not for a threshold.
     */
    static final float CROWDING_RADIUS = 1.5f;

    /** One garrison on the compound's primary node and one assault squad — the original two-squad scene. */
    static Scene build(long seed, int garrisonSize, int assaultSize, Approach approach) {
        return build(seed, 1, garrisonSize, 1, assaultSize, approach);
    }

    /**
     * @param garrisonSquads defender squads to raise, one per emitted tactical node in
     *                       priority order; more than the compound has nodes is an error
     *                       rather than two squads silently sharing a post
     * @param assaultSquads  marine squads, spread across the approach edge so the
     *                       scene measures an assault on a frontage rather than one
     *                       column walking into one gate
     */
    /** Assault squads on each of {@code approaches}, converging on one compound. */
    static Scene build(long seed, int garrisonSquads, int garrisonSize,
                       int squadsPerApproach, int assaultSize, List<Approach> approaches) {
        return buildScene(seed, garrisonSquads, garrisonSize, squadsPerApproach,
                assaultSize, approaches);
    }

    static Scene build(long seed, int garrisonSquads, int garrisonSize,
                       int assaultSquads, int assaultSize, Approach approach) {
        return buildScene(seed, garrisonSquads, garrisonSize, assaultSquads,
                assaultSize, List.of(approach));
    }

    private static Scene buildScene(long seed, int garrisonSquads, int garrisonSize,
                                    int squadsPerApproach, int assaultSize,
                                    List<Approach> approaches) {
        return buildScene(seed, garrisonSquads, garrisonSize, squadsPerApproach,
                assaultSize, approaches, Shape.SQUARE);
    }

    /** Build on a named compound footprint rather than the symmetric default. */
    static Scene build(long seed, int garrisonSquads, int garrisonSize, int squadsPerApproach,
                       int assaultSize, List<Approach> approaches, Shape shape) {
        return buildScene(seed, garrisonSquads, garrisonSize, squadsPerApproach,
                assaultSize, approaches, shape);
    }

    private static Scene buildScene(long seed, int garrisonSquads, int garrisonSize,
                                    int squadsPerApproach, int assaultSize,
                                    List<Approach> approaches, Shape shape) {
        GenContext ctx = stampCompound(seed, shape);
        BattleSimulation sim = serialSimulation(ctx, seed);
        List<TacticalNode> nodes = List.copyOf(ctx.tactical);
        sim.setTacticalMap(new TacticalMap(nodes));
        for (Doodad doodad : ctx.doodads) sim.addDoodad(doodad);

        List<TacticalNode> garrisoned = nodesByPriority(nodes);
        if (garrisonSquads < 1) {
            throw new IllegalArgumentException("a scene needs at least one garrison");
        }
        // A smaller footprint emits fewer nodes; garrison what there is rather
        // than refusing to build the shape or doubling squads onto one node.
        garrisonSquads = Math.min(garrisonSquads, garrisoned.size());
        List<Squad> garrisons = new ArrayList<>(garrisonSquads);
        for (int i = 0; i < garrisonSquads; i++) {
            garrisons.add(spawnGarrison(sim, garrisoned.get(i), garrisonSize, i));
        }

        if (approaches.isEmpty()) throw new IllegalArgumentException("no approach given");
        TacticalNode primary = garrisoned.get(0);
        List<Squad> assaults = new ArrayList<>(squadsPerApproach * approaches.size());
        Map<Integer, Approach> approachBySquad = new LinkedHashMap<>();
        for (Approach approach : approaches) {
            for (int i = 0; i < squadsPerApproach; i++) {
                Squad squad = spawnAssault(sim, primary, assaultSize, approach, i,
                        squadsPerApproach);
                assaults.add(squad);
                approachBySquad.put(squad.id, approach);
            }
        }
        return new Scene(sim, primary, List.copyOf(garrisons), List.copyOf(assaults),
                nodes, List.copyOf(approaches), Map.copyOf(approachBySquad));
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
        BattleSimulation sim = scene.sim();
        float[] marines = marineCentroid(sim);

        List<SquadSample> rows = new ArrayList<>(scene.garrisons().size());
        for (Squad squad : scene.garrisons()) rows.add(squadSample(scene, squad, sim));

        // The headline numbers describe the squad holding the perimeter: it is
        // the one whose posts face the approach, and the one a recording's
        // caption is about. The rest are reported per squad.
        Squad perimeter = scene.perimeterGarrison();
        ApertureHold hold = activeHold(perimeter);
        int aperture = 0;
        int reserve = 0;
        int facingThreat = 0;
        if (hold != null) {
            for (ApertureHold.Post post : hold.posts()) {
                if (post.isReserve()) {
                    reserve++;
                } else {
                    aperture++;
                    if (facesThreat(scene, post, marines)) facingThreat++;
                }
            }
        }
        String goal = perimeter.currentGoal != null ? perimeter.currentGoal.name() : "none";
        int onPost = hold == null ? 0 : membersOnPost(sim, perimeter, hold);
        float pressure = sim.getCommanderInfluence(Faction.DEFENDER).maxHostile();
        boolean frontageRelevant = FrontageDefense.INSTANCE.relevance(
                WorldState.EMPTY, perimeter, sim) > 0f;

        return new Sample(tick, goal, aperture, reserve, facingThreat, onPost,
                pressure, enemyInside(scene), frontageRelevant, layerName(perimeter, sim),
                marines[0], marines[1], (int) marines[2],
                List.copyOf(rows), axisCoverage(scene, sim), crowding(scene, sim));
    }

    private static SquadSample squadSample(Scene scene, Squad squad, BattleSimulation sim) {
        ApertureHold hold = activeHold(squad);
        int aperture = 0;
        int reserve = 0;
        if (hold != null) {
            for (ApertureHold.Post post : hold.posts()) {
                if (post.isReserve()) reserve++; else aperture++;
            }
        }
        return new SquadSample(squad.id, layerName(squad, sim),
                squad.currentGoal != null ? squad.currentGoal.name() : "none",
                aperture, reserve, hold == null ? 0 : membersOnPost(sim, squad, hold),
                FrontageDefense.INSTANCE.relevance(WorldState.EMPTY, squad, sim) > 0f,
                squad.aliveMembers);
    }

    /**
     * Per-edge coverage across every garrison currently standing to. Counts
     * posts from all garrisons, not just the perimeter holders, because an
     * inner building shell facing a breached side is covering that side too.
     */
    private static List<AxisCoverage> axisCoverage(Scene scene, BattleSimulation sim) {
        List<DefenseFrontage.Aperture> frontage =
                DefenseFrontage.forCompound(scene.primary(), sim);
        List<AxisCoverage> out = new ArrayList<>(scene.approaches().size());
        for (Approach approach : scene.approaches()) {
            int live = 0;
            for (Squad squad : scene.assaults()) {
                if (scene.approachBySquad().get(squad.id) != approach) continue;
                live += liveMembers(sim, squad);
            }
            int covering = 0;
            for (Squad garrison : scene.garrisons()) {
                ApertureHold hold = activeHold(garrison);
                if (hold == null) continue;
                for (ApertureHold.Post post : hold.posts()) {
                    if (post.isReserve()) continue;
                    if (coversEdge(post, approach)) covering++;
                }
            }
            float pressure = 0f;
            for (DefenseFrontage.Aperture aperture : frontage) {
                if (!facesEdge(aperture.facing(), approach)) continue;
                pressure = Math.max(pressure,
                        DefenseFrontage.threatAt(aperture, Faction.DEFENDER, sim));
            }
            out.add(new AxisCoverage(approach.name(), approach.renderedEdge(), live,
                    covering, pressure));
        }
        return List.copyOf(out);
    }

    /** Whether an aperture's outward facing is {@code approach}'s edge. */
    private static boolean facesEdge(DefenseFrontage.Facing facing, Approach approach) {
        return switch (approach) {
            case SOUTH -> facing == DefenseFrontage.Facing.SOUTH;
            case NORTH -> facing == DefenseFrontage.Facing.NORTH;
            case EAST -> facing == DefenseFrontage.Facing.EAST;
            case WEST -> facing == DefenseFrontage.Facing.WEST;
        };
    }

    /**
     * Whether a post faces {@code approach}. Read from the post's own outward
     * direction — stance to watched cell — rather than from where it sits
     * relative to the compound centre. Position is not enough: a post on the
     * south wall standing east of centre is east of centre, and a
     * position-based test counts it as covering an eastern assault it has its
     * shoulder to. Aperture axes are orthogonal, so a post faces exactly one
     * edge.
     */
    private static boolean coversEdge(ApertureHold.Post post, Approach approach) {
        int dx = post.watchX() - post.standX();
        int dy = post.watchY() - post.standY();
        return switch (approach) {
            case SOUTH -> dy > 0;
            case NORTH -> dy < 0;
            case EAST -> dx > 0;
            case WEST -> dx < 0;
        };
    }

    private static int liveMembers(BattleSimulation sim, Squad squad) {
        int count = 0;
        for (int i = 0; i < sim.getRoster().liveCount(); i++) {
            long unit = sim.getRoster().get(i);
            if (sim.squad().hasSquad(unit) && sim.squad().squadId(unit) == squad.id) count++;
        }
        return count;
    }

    /** The envelope a squad is holding at this instant, or {@code none} when it is not standing to. */
    private static String layerName(Squad squad, BattleSimulation sim) {
        FrontageDefense.Layer layer = FrontageDefense.activeLayer(squad, sim);
        return layer == null ? "none" : layer.name();
    }

    /** Post overlap between squads, and how tightly live bodies are packed. */
    private static Crowding crowding(Scene scene, BattleSimulation sim) {
        Map<Long, Integer> squadsPerPostCell = new LinkedHashMap<>();
        for (Squad squad : scene.garrisons()) {
            ApertureHold hold = activeHold(squad);
            if (hold == null) continue;
            Set<Long> own = new HashSet<>();
            for (ApertureHold.Post post : hold.posts()) own.add(key(post.standX(), post.standY()));
            for (long cell : own) squadsPerPostCell.merge(cell, 1, Integer::sum);
        }
        int collisions = 0;
        for (int count : squadsPerPostCell.values()) if (count > 1) collisions++;

        Map<Long, Integer> unitsPerCell = new LinkedHashMap<>();
        List<float[]> live = new ArrayList<>();
        for (int i = 0; i < sim.getRoster().liveCount(); i++) {
            long unit = sim.getRoster().get(i);
            if (!sim.identity().type(unit).combatant) continue;
            Faction faction = sim.identity().faction(unit);
            if (faction != Faction.MARINE && faction != Faction.DEFENDER) continue;
            unitsPerCell.merge(key(sim.world().cellX(unit), sim.world().cellY(unit)), 1, Integer::sum);
            live.add(new float[]{sim.world().x(unit), sim.world().y(unit), faction.ordinal()});
        }
        int maxPerCell = 0;
        for (int count : unitsPerCell.values()) maxPerCell = Math.max(maxPerCell, count);

        int crowded = 0;
        for (int a = 0; a < live.size(); a++) {
            for (int b = a + 1; b < live.size(); b++) {
                float[] first = live.get(a);
                float[] second = live.get(b);
                if (first[2] != second[2]) continue;
                float dx = first[0] - second[0];
                float dy = first[1] - second[1];
                if (dx * dx + dy * dy <= CROWDING_RADIUS * CROWDING_RADIUS) crowded++;
            }
        }
        return new Crowding(squadsPerPostCell.size(), collisions, live.size(),
                unitsPerCell.size(), maxPerCell, crowded, minGarrisonGap(scene, sim));
    }

    /**
     * Smallest distance between two garrison squad centroids. This is the
     * question "are they all in the same place" asked at the level it means
     * something: individual bodies compress together by design, but two squads
     * holding two different layers of a compound should not be standing on top
     * of one another.
     */
    private static float minGarrisonGap(Scene scene, BattleSimulation sim) {
        List<float[]> centroids = new ArrayList<>();
        for (Squad squad : scene.garrisons()) {
            float sumX = 0f;
            float sumY = 0f;
            int count = 0;
            for (int i = 0; i < sim.getRoster().liveCount(); i++) {
                long unit = sim.getRoster().get(i);
                if (!sim.squad().hasSquad(unit) || sim.squad().squadId(unit) != squad.id) continue;
                sumX += sim.world().x(unit);
                sumY += sim.world().y(unit);
                count++;
            }
            if (count > 0) centroids.add(new float[]{sumX / count, sumY / count});
        }
        if (centroids.size() < 2) return -1f;
        float best = Float.MAX_VALUE;
        for (int a = 0; a < centroids.size(); a++) {
            for (int b = a + 1; b < centroids.size(); b++) {
                float dx = centroids.get(a)[0] - centroids.get(b)[0];
                float dy = centroids.get(a)[1] - centroids.get(b)[1];
                best = Math.min(best, (float) Math.sqrt(dx * dx + dy * dy));
            }
        }
        return best;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
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
     * Whether a marine stands in the garrison's held zones — asked of
     * {@link FrontageDefense} itself rather than recomputed here. Scope is the
     * reason: a garrison holding one structure inside a compound holds only
     * that building, so a compound-scope answer reports a breach the gate does
     * not see and the scene would accuse it of failing to release.
     */
    private static boolean enemyInside(Scene scene) {
        BattleSimulation sim = scene.sim();
        List<Integer> held = FrontageDefense.heldZones(scene.perimeterGarrison(), sim);
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

    /**
     * Build the simulation with per-unit dispatch pinned serial.
     *
     * <p>{@code UnitUpdateSystem} goes parallel at
     * {@code DEFAULT_MINIMUM_PARALLEL_UNITS} (48) live units, and a scene with
     * several squads a side is comfortably past that. Parallel dispatch makes a
     * run irreproducible, which quietly costs this harness the property it
     * exists for: the same seed has to produce the same battle or a difference
     * between two runs means nothing. The commander-evidence task pins the same
     * property for the same reason; doing it here as well keeps a scene
     * reproducible whichever task runs it.
     *
     * <p>The threshold is read once, when the system is constructed, so the
     * previous value is restored immediately afterwards rather than left set
     * for the rest of the JVM.
     */
    private static BattleSimulation serialSimulation(GenContext ctx, long seed) {
        String property = UnitUpdateSystem.MINIMUM_PARALLEL_UNITS_PROPERTY;
        String previous = System.getProperty(property);
        System.setProperty(property, Integer.toString(Integer.MAX_VALUE));
        try {
            return new BattleSimulation(ctx.grid, ctx.topology, seed);
        } finally {
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }

    /** Open ground with one production-stamped military compound in the middle. */
    private static GenContext stampCompound(long seed, Shape shape) {
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
        // the member leaves, which means clipping them to this shape's own
        // footprint rather than a constant. A reservation that continues past
        // the compound is not a detail: paintWallRing deliberately skips
        // reserved cells, so an over-long centreline punches a permanent hole
        // in the perimeter, the parade ground joins the street as one zone, and
        // the compound's frontage becomes its building shells instead of its
        // wall. A shape shallower than the square baseline would hit exactly
        // that with a fixed span.
        reserveParadeLines(compound(shape), reservation);

        GenContext ctx = new GenContext(grid, topology, new Random(seed), WIDTH, HEIGHT, seed);
        ctx.put(BspKeys.ROAD_CELLS, road);
        ctx.put(BspKeys.ROAD_RESERVATION, reservation);
        new MilitaryBaseFiller().fill(compound(shape), ctx);
        return ctx;
    }

    /**
     * Reserve the parade centrelines only where they genuinely run between two
     * member leaves.
     *
     * <p>Clipping to the footprint bounding box is not enough on a shape with a
     * notch. {@code paintWallRing} deliberately skips reserved cells so a road
     * can cross a compound, so a centreline continued across the missing
     * quadrant of an L opens the wall there, the courtyard joins the street as
     * one zone, and every perimeter window loses the held interior it needs to
     * be frontage — an L-shaped compound then reads as having no firing line at
     * all, when what it actually has is a road driven through its wall.
     */
    private static void reserveParadeLines(Compound compound, boolean[][] reservation) {
        int[] vertical = interiorSpan(compound, true);
        for (int y = vertical[0]; y <= vertical[1]; y++) reservation[PARADE_X][y] = true;
        int[] horizontal = interiorSpan(compound, false);
        for (int x = horizontal[0]; x <= horizontal[1]; x++) reservation[x][PARADE_Y] = true;
    }

    /**
     * First and last position along a centreline that genuinely runs between
     * two leaves, as an inclusive span.
     *
     * <p>Taken as a span rather than cell by cell because the reservation does
     * double duty: it opens the wall, and it is also what makes the filler
     * bridge the inter-leaf roads into the compound. A line reserved only where
     * leaves flank it is broken at the point the two centrelines cross, the
     * central plaza never gets bridged in, and the compound ends up with no
     * courtyard at all. Filling between the ends keeps the interior continuous
     * while still stopping the line before it reaches open ground.
     */
    private static int[] interiorSpan(Compound compound, boolean vertical) {
        int first = Integer.MAX_VALUE;
        int last = Integer.MIN_VALUE;
        int extent = vertical ? HEIGHT : WIDTH;
        for (int i = 0; i < extent; i++) {
            int x = vertical ? PARADE_X : i;
            int y = vertical ? i : PARADE_Y;
            if (!between(compound, x, y, vertical)) continue;
            first = Math.min(first, i);
            last = Math.max(last, i);
        }
        // Inset by one at each end: the reservation is a road centreline
        // through the gap, not a cut across the leaf edges that bound it.
        return first > last ? new int[]{1, 0} : new int[]{first + 1, last - 1};
    }

    /**
     * Whether {@code (x, y)} lies in a gap with a member leaf on both sides —
     * across the line for a vertical centreline, above and below for a
     * horizontal one.
     */
    private static boolean between(Compound compound, int x, int y, boolean vertical) {
        boolean before = false;
        boolean after = false;
        for (BlockLeaf leaf : compound.members) {
            if (vertical) {
                if (y < leaf.top || y > leaf.bottom) continue;
                if (leaf.right < x) before = true;
                if (leaf.left > x) after = true;
            } else {
                if (x < leaf.left || x > leaf.right) continue;
                if (leaf.bottom < y) before = true;
                if (leaf.top > y) after = true;
            }
        }
        return before && after;
    }

    /** Union bbox of a compound's member leaves as {@code [left, top, right, bottom]}. */
    private static int[] footprint(Compound compound) {
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (BlockLeaf leaf : compound.members) {
            left = Math.min(left, leaf.left);
            top = Math.min(top, leaf.top);
            right = Math.max(right, leaf.right);
            bottom = Math.max(bottom, leaf.bottom);
        }
        return new int[]{left, top, right, bottom};
    }

    private static Compound compound(Shape shape) {
        List<BlockLeaf> members = new ArrayList<>();
        Map<BlockLeaf, Compound.Role> roles = new IdentityHashMap<>();
        BlockLeaf command;
        switch (shape) {
            case SQUARE -> {
                command = COMMAND;
                members.addAll(List.of(COMMAND, BARRACKS, ARMORY, VEHICLE_BAY));
                roles.put(COMMAND, Compound.Role.COMMAND);
                roles.put(BARRACKS, Compound.Role.BARRACKS);
                roles.put(ARMORY, Compound.Role.ARMORY);
                roles.put(VEHICLE_BAY, Compound.Role.VEHICLE_BAY);
            }
            case ELL -> {
                command = COMMAND;
                members.addAll(List.of(COMMAND, BARRACKS, ARMORY));
                roles.put(COMMAND, Compound.Role.COMMAND);
                roles.put(BARRACKS, Compound.Role.BARRACKS);
                roles.put(ARMORY, Compound.Role.ARMORY);
            }
            case RIBBON -> {
                command = COMMAND;
                members.addAll(List.of(COMMAND, BARRACKS));
                roles.put(COMMAND, Compound.Role.COMMAND);
                roles.put(BARRACKS, Compound.Role.BARRACKS);
            }
            case LOPSIDED -> {
                // Sizes vary widely; the gaps between them stay on the
                // parade lines so the wall ring is only opened where a road
                // actually runs.
                BlockLeaf big = new BlockLeaf(38, 38, 55, 55, false);
                BlockLeaf narrow = new BlockLeaf(58, 40, 71, 49, false);
                BlockLeaf squat = new BlockLeaf(40, 58, 49, 71, false);
                BlockLeaf small = new BlockLeaf(58, 58, 65, 65, false);
                command = big;
                members.addAll(List.of(big, narrow, squat, small));
                roles.put(big, Compound.Role.COMMAND);
                roles.put(narrow, Compound.Role.BARRACKS);
                roles.put(squat, Compound.Role.ARMORY);
                roles.put(small, Compound.Role.VEHICLE_BAY);
            }
            default -> throw new IllegalArgumentException("unhandled shape " + shape);
        }
        return new Compound(BlockKind.MILITARY_BASE, command, members, roles, null);
    }

    /** Emitted nodes, highest priority first and anchor-ordered for ties — the same primary rule the garrison behaviors use. */
    private static List<TacticalNode> nodesByPriority(List<TacticalNode> nodes) {
        if (nodes.isEmpty()) throw new IllegalStateException("compound emitted no tactical nodes");
        List<TacticalNode> ordered = new ArrayList<>(nodes);
        ordered.sort(Comparator
                .comparingInt((TacticalNode n) -> -n.priorityScore)
                .thenComparingInt(n -> n.anchorX)
                .thenComparingInt(n -> n.anchorY));
        return ordered;
    }

    private static Squad spawnGarrison(BattleSimulation sim, TacticalNode node, int size, int ordinal) {
        List<int[]> cells = BattleSetup.pickCellsNear(sim.getGrid(), sim.getZoneGraph(),
                node.anchorX, node.anchorY, GARRISON_SPAWN_RADIUS, size);
        if (cells.isEmpty()) {
            throw new IllegalStateException("no garrison spawn cells near " + node.kind + " anchor");
        }
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MARINE_RED);
        Squad squad = sim.getSquad(squadId);
        squad.assignedNode = node;
        squad.holdsFireUntilKillZone = true;
        int index = 0;
        for (int[] cell : cells) {
            EntitySpec spec = new EntitySpec("garrison-" + ordinal + "-" + index++,
                    Faction.DEFENDER, UnitType.MARINE_RED, cell[0], cell[1]);
            spec.role(UnitRole.GARRISON).home(cell[0], cell[1]).squad(squadId);
            sim.spawn(spec);
        }
        squad.originalSize = cells.size();
        return squad;
    }

    /**
     * One assault squad, spawned at its own point along the approach edge.
     * Squads are spread across the edge rather than stacked on one seed cell,
     * because an assault that starts as a single column tells you nothing about
     * whether a defense spreads to meet it.
     */
    private static Squad spawnAssault(BattleSimulation sim, TacticalNode node, int size,
                                      Approach approach, int ordinal, int total) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        int[] seed = assaultSeed(approach, ordinal, total);
        List<int[]> cells = BattleSetup.pickCellsNear(sim.getGrid(), sim.getZoneGraph(),
                seed[0], seed[1], 4, size);
        if (cells.isEmpty()) {
            throw new IllegalStateException("no assault spawn cells at " + approach + " slot " + ordinal);
        }
        int index = 0;
        for (int[] cell : cells) {
            EntitySpec spec = new EntitySpec("assault-" + ordinal + "-" + index++,
                    Faction.MARINE, UnitType.MARINE, cell[0], cell[1]);
            spec.squad(squadId);
            sim.spawn(spec);
        }
        squad.originalSize = cells.size();
        squad.assignedObjective = ObjectiveAssignment.secureCompound(squadId,
                sim.getZoneGraph().zoneIdAt(node.anchorX, node.anchorY), node);
        return squad;
    }

    /** Evenly spaced seed cells along the approach edge, centred on its midpoint. */
    private static int[] assaultSeed(Approach approach, int ordinal, int total) {
        int spread = ASSAULT_SQUAD_SPACING * (total - 1);
        int offset = -spread / 2 + ASSAULT_SQUAD_SPACING * ordinal;
        boolean alongX = approach == Approach.SOUTH || approach == Approach.NORTH;
        int x = clampToMap(approach.cellX + (alongX ? offset : 0), WIDTH);
        int y = clampToMap(approach.cellY + (alongX ? 0 : offset), HEIGHT);
        return new int[]{x, y};
    }

    private static int clampToMap(int value, int extent) {
        return Math.max(2, Math.min(extent - 3, value));
    }
}
