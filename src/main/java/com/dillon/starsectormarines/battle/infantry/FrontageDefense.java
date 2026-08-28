package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.DefenseFrontage;
import com.dillon.starsectormarines.battle.decision.DefenseFrontage.Aperture;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.world.GarrisonArea;
import com.dillon.starsectormarines.battle.decision.goap.world.ZoneQueries;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stand to on the frontage of a held place while an assault is still
 * approaching it. Consumes {@link DefenseFrontage} and issues a single
 * perpetual {@link ApertureHold}.
 *
 * <p>This is the missing posture in the garrison stack. {@link GarrisonPatrol}
 * walks interior rooms; {@link GarrisonAmbush} needs a hostile already in the
 * squad's own belief picture and reasons over zone portals, which excludes the
 * outdoor zone a perimeter squad actually stands in. Between the two there was
 * nothing, so the approach — the readable part of an assault, and the part a
 * player watches build — was defended by a patrol facing the wrong way.
 *
 * <h2>What makes it stand to</h2>
 * Believed hostile pressure on the ground its own apertures cover, read from
 * the squad's faction influence field. That field is aggregated from believed
 * contacts and propagated through navigable topology, so a forward patrol's
 * contact is what mans the wall facing it and a garrison that has personally
 * seen nothing still reacts. It is belief and not truth: the garrison can be
 * wrong, a feint can pull it, and an unobserved approach arrives against a
 * quiet patrol — all of which is the intended texture rather than a gap.
 *
 * <h2>Who gets it</h2>
 * Whoever holds the place, not whoever is defending. A marine squad holding a
 * captured compound resolves through its {@code HOLD_NODE} assignment and mans
 * the same wall against the counter-attack; a defender garrison resolves
 * through its assigned post. The compound's primary node mans the perimeter and
 * its other garrisons man their own building shells, so a layered defense is
 * what running one goal at two scopes produces rather than a second behavior.
 * A standalone post is the degenerate case where the two scopes coincide, which
 * is what makes this work for an isolated structure with no compound at all.
 *
 * <p>{@link Priority#MISSION} at relevance {@value #RELEVANCE} — under
 * {@link GarrisonAmbush}, so a live chokepoint contact still preempts, and over
 * {@link GarrisonCompound}. {@link GuardPost} yields explicitly.
 *
 * <p>Goes inactive once the place is breached: an enemy inside the held zones
 * means the fight has moved indoors, and re-clearing rooms is
 * {@link GarrisonPatrol}'s CONTEST state and {@link GarrisonAmbush}'s doorways.
 * Standing at a window while enemies are behind you is the failure mode this
 * gate exists to prevent.
 */
public final class FrontageDefense implements Goal {

    public static final FrontageDefense INSTANCE = new FrontageDefense();

    /** Below {@link GarrisonAmbush} (1.0) so live chokepoint contact preempts; above {@link GarrisonCompound} (0.95). */
    public static final float RELEVANCE = 0.98f;

    /**
     * Believed hostile pressure on an aperture's approach at which the garrison
     * mans the wall. Influence values attenuate at 0.85 per topology step and
     * are floored at 0.05, so a threshold in this range means "believed
     * hostiles near enough that the field still carries weight here" rather
     * than a force-size measurement — it does not need to be rescaled when a
     * mission authors a larger assault.
     */
    public static final float STAND_TO_THRESHOLD = 0.15f;

    /** Fraction of the squad held back inside rather than bound to an aperture, so one threatened facing cannot strip the rest of the perimeter. */
    public static final float RESERVE_FRACTION = 0.3f;

    /** Squads smaller than this commit everyone to the frontage — a reserve of one out of three is a squad that defends nothing. */
    public static final int MIN_SQUAD_FOR_RESERVE = 4;

    /** Radius around the node anchor searched for reserve stances. Wide enough to find walkable ground near an anchor that is itself a structure cell. */
    public static final int RESERVE_RADIUS = 6;

    private FrontageDefense() {}

    @Override public String name() { return "FrontageDefense"; }

    @Override
    public Priority priority() {
        return Priority.MISSION;
    }

    @Override
    public float relevance(WorldState state, Squad squad, BattleView sim) {
        if (state.get(Predicate.MORALE_BROKEN)) return 0f;
        return plan(squad, sim) != null ? RELEVANCE : 0f;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleView sim) {
        return WorldState.EMPTY;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleView sim) {
        Held held = plan(squad, sim);
        return held != null ? new SquadPlan(List.of(new SquadPlan.Step(held.hold))) : null;
    }

    /** The resolved posture — kept together so {@link #relevance} and {@link #customPlan} answer from one derivation rather than two that could disagree. */
    private record Held(ApertureHold hold) {}

    /**
     * Resolve this squad's frontage posture, or null when it should not stand
     * to. Recomputed per replan rather than cached: derivation reads live grid
     * state, which is what lets a breach register as an entrance, and a static
     * cache shared across squads would be a hazard the moment replanning stops
     * being serial.
     */
    private Held plan(Squad squad, BattleView sim) {
        TacticalNode node = heldNode(squad, sim);
        if (node == null) return null;

        boolean compoundScope = GarrisonCompound.defenderAreaPatrol(squad, sim)
                || marineHeldNode(squad) != null;
        int boxL = compoundScope ? node.compoundLeft() : node.left;
        int boxT = compoundScope ? node.compoundTop() : node.top;
        int boxR = compoundScope ? node.compoundRight() : node.right;
        int boxB = compoundScope ? node.compoundBottom() : node.bottom;

        List<Integer> heldZones = GarrisonArea.garrisonZones(
                boxL - DefenseFrontage.COMPOUND_MARGIN, boxT - DefenseFrontage.COMPOUND_MARGIN,
                boxR + DefenseFrontage.COMPOUND_MARGIN, boxB + DefenseFrontage.COMPOUND_MARGIN, sim);
        if (heldZones.isEmpty()) return null;
        if (breached(heldZones, squad, sim)) return null;

        List<Aperture> frontage = DefenseFrontage.derive(
                boxL, boxT, boxR, boxB, DefenseFrontage.COMPOUND_MARGIN, sim);
        if (frontage.isEmpty()) return null;

        List<Aperture> threatened = byThreat(frontage, squad.faction, sim);
        if (threatened.isEmpty()) return null;

        int alive = Math.max(1, squad.aliveMembers);
        int reserve = alive >= MIN_SQUAD_FOR_RESERVE
                ? Math.max(1, Math.round(alive * RESERVE_FRACTION)) : 0;
        int onPost = Math.max(1, alive - reserve);

        List<ApertureHold.Post> posts = new ArrayList<>(alive);
        Set<Long> taken = new HashSet<>();
        for (Aperture aperture : threatened) {
            if (posts.size() >= onPost) break;
            if (!taken.add(key(aperture.stanceX(), aperture.stanceY()))) continue;
            posts.add(new ApertureHold.Post(aperture.stanceX(), aperture.stanceY(),
                    aperture.outsideX(), aperture.outsideY(), aperture.kind()));
        }
        if (posts.isEmpty()) return null;

        for (int[] cell : reserveCells(node, heldZones, alive - posts.size(), taken, sim)) {
            posts.add(ApertureHold.Post.reserve(cell[0], cell[1]));
        }
        return new Held(new ApertureHold(posts));
    }

    /**
     * Apertures whose approach carries believed hostile pressure, most
     * threatened first. Entrances outrank equally-threatened windows because
     * an entrance is the one an assault can actually come through; cell order
     * breaks the remaining ties so the post list is stable across replans and
     * members are not shuffled between adjacent windows every cycle.
     */
    private static List<Aperture> byThreat(List<Aperture> frontage, Faction faction, BattleView sim) {
        List<Aperture> out = new ArrayList<>(frontage.size());
        List<Float> threats = new ArrayList<>(frontage.size());
        for (Aperture aperture : frontage) {
            float threat = DefenseFrontage.threatAt(aperture, faction, sim);
            if (threat < STAND_TO_THRESHOLD) continue;
            out.add(aperture);
            threats.add(threat);
        }
        List<Integer> order = new ArrayList<>(out.size());
        for (int i = 0; i < out.size(); i++) order.add(i);
        order.sort(Comparator
                .<Integer, Float>comparing(i -> -threats.get(i))
                .thenComparing(i -> out.get(i).kind() == DefenseFrontage.Kind.ENTRANCE ? 0 : 1)
                .thenComparing(i -> out.get(i).y())
                .thenComparing(i -> out.get(i).x()));
        List<Aperture> sorted = new ArrayList<>(out.size());
        for (int i : order) sorted.add(out.get(i));
        return sorted;
    }

    /**
     * Interior stances for members held off the wall, nearest the node anchor
     * and preferring cover. The anchor is frequently the structure cell itself
     * and not walkable ([[battle_tactical_node_anchor_contract]]), so this
     * searches outward from it rather than standing on it.
     */
    private static List<int[]> reserveCells(TacticalNode node, List<Integer> heldZones,
                                            int count, Set<Long> taken, BattleView sim) {
        if (count <= 0) return List.of();
        NavigationGrid grid = sim.getGrid();
        ZoneGraph graph = sim.getZoneGraph();
        Set<Integer> held = new HashSet<>(heldZones);
        List<int[]> pool = new ArrayList<>();
        for (int y = node.anchorY - RESERVE_RADIUS; y <= node.anchorY + RESERVE_RADIUS; y++) {
            for (int x = node.anchorX - RESERVE_RADIUS; x <= node.anchorX + RESERVE_RADIUS; x++) {
                if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) continue;
                if (!held.contains(graph.zoneIdAt(x, y))) continue;
                if (taken.contains(key(x, y))) continue;
                int dist = Math.abs(x - node.anchorX) + Math.abs(y - node.anchorY);
                pool.add(new int[]{x, y, dist});
            }
        }
        pool.sort(Comparator
                .comparingInt((int[] c) -> -grid.getCoverAt(c[0], c[1]))
                .thenComparingInt(c -> c[2])
                .thenComparingInt(c -> c[1])
                .thenComparingInt(c -> c[0]));
        List<int[]> out = new ArrayList<>(Math.min(count, pool.size()));
        for (int i = 0; i < pool.size() && out.size() < count; i++) {
            int[] cell = pool.get(i);
            if (!taken.add(key(cell[0], cell[1]))) continue;
            out.add(new int[]{cell[0], cell[1]});
        }
        return out;
    }

    /** True once any enemy stands inside the held zones — the fight is indoors and belongs to the room-clearing behaviors. */
    private static boolean breached(List<Integer> heldZones, Squad squad, BattleView sim) {
        Faction enemy = squad.faction == Faction.MARINE ? Faction.DEFENDER : Faction.MARINE;
        for (int zoneId : heldZones) {
            if (!ZoneQueries.zoneClear(zoneId, enemy, sim)) return true;
        }
        return false;
    }

    /**
     * The node this squad holds, either side of the battle: a marine
     * {@code HOLD_NODE} assignment's target, or a defender garrison's assigned
     * post. Null for a squad that holds nothing, which is the cheap early-out
     * every non-garrison squad takes on every replan.
     */
    private static TacticalNode heldNode(Squad squad, BattleView sim) {
        TacticalNode marine = marineHeldNode(squad);
        if (marine != null) return marine;
        return squad.holdsFireUntilKillZone ? squad.assignedNode : null;
    }

    /** Target node of a {@code HOLD_NODE} assignment, or null when the squad has another assignment. */
    private static TacticalNode marineHeldNode(Squad squad) {
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        return assignment != null && assignment.kind() == AssignmentKind.HOLD_NODE
                ? assignment.targetNode() : null;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }
}
