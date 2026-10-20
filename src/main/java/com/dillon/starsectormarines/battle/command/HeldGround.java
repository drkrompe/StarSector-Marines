package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.influence.CommanderContact;
import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A side holding ground it has taken (`garrisonables.md`, Decided direction): the fighting-position runs of the forts
 * it holds are manned in answer to its own believed counter-attack pressure, through the same assignment tier the
 * defender's reactive manning uses ({@link GarrisonDirector}) and under the same laws: belief only, hysteresis with a
 * dwell, pickets before massing, the useful-strength floor, an arrival bound, fall back to the fort's works on a breach,
 * release when the pressure passes, candidates found through the registry's block index and a bounded number sent per
 * pulse.
 *
 * <p><b>A counter-attack is a mover.</b> Only a believed hostile that is not an emplacement wakes a place, and a place
 * is priced only on the blocks a believed mover is within reach of: an enemy nest left standing beside a taken belt
 * would otherwise keep it pressed for the rest of the battle. A holder driven off a run the enemy is believed inside
 * falls back like one still standing on it.
 *
 * <p>Nothing here knows which side it serves. The caller says which squads may hold and how much of its force it may
 * spend: an attacker keeps its momentum by offering only its lane pool and by holding at most {@link #POOL_SHARE} of
 * it ({@link #cap}). Pure planning over frozen inputs; the caller turns the answers into orders. One instance per
 * commander, so per simulation.
 */
final class HeldGround {

    /** Largest share of the offered pool (holders included) that holds ground at once. UNTUNED. */
    static final float POOL_SHARE = 0.25f;
    /** A pool smaller than this holds nothing: every squad of a small force is the attack. UNTUNED. */
    static final int MIN_POOL_SQUADS = 3;

    /** A squad of the caller's pool as this planner needs it. {@code order} is what it carries now. */
    record Body(int squadId, int anchorX, int anchorY, float centroidX, float centroidY, int alive, int original,
                boolean localContact, ObjectiveAssignment order) { }

    /** What becomes of a squad holding, or sent to hold, a place this pulse. */
    enum Verdict { STAND, FALL_BACK, RELEASE }

    /**
     * One holder's answer. {@code order} is the run order to keep for {@link Verdict#STAND}, the works post for
     * {@link Verdict#FALL_BACK}, and {@code null} for a release.
     */
    record Decision(int squadId, Verdict verdict, ObjectiveAssignment order) { }

    /** Whether a squad standing on one cell can walk to another. */
    @FunctionalInterface
    interface Reach {
        boolean to(int fromX, int fromY, int x, int y);
    }

    /** A squad newly sent to hold a run, with its order. */
    record Sent(int squadId, int garrisonable, ObjectiveAssignment order) { }

    private final GarrisonDirector director = new GarrisonDirector();
    private int[] contactScratch = new int[64];
    /** Per influence block, the generation in which a believed mover was last within reach of it. */
    private int[] nearMover = new int[0];
    private int generation;

    /** The most squads that may hold at once out of a pool of {@code pool} offered squads, holders included. */
    static int cap(int pool) {
        if (pool < MIN_POOL_SQUADS) return 0;
        return Math.max(1, (int) Math.floor(pool * POOL_SHARE));
    }

    /**
     * The forts {@code perspective} holds: disclosed to it, with at least one aspect and none held by a hostile side. On
     * an attacker that is a fort it has taken, the same test that leaves a company nothing of its fort to take.
     */
    static Set<Integer> heldForts(Faction perspective, List<DefenceStructureFact> structures) {
        Set<Integer> held = new TreeSet<>();
        Set<Integer> contested = new HashSet<>();
        for (DefenceStructureFact structure : structures) {
            if (!structure.ofFort()) continue;
            if (perspective.friendlyTo(structure.holder())) held.add(structure.fortId());
            else contested.add(structure.fortId());
        }
        held.removeAll(contested);
        return held;
    }

    /** Whether the influence picture is laid on the registry's own block lattice, which every pressure read assumes. */
    static boolean usable(GarrisonableRegistry places, CommanderInfluenceSnapshot influence) {
        return places != null && places.count() > 0
                && influence != null && influence.blockSize() == places.blockSize()
                && influence.width() == places.blockWidth() && influence.height() == places.blockHeight();
    }

    /**
     * The place this squad holds or has just been sent to, as the director reckons it, or {@code null} for a squad that
     * holds nothing. A run renumbered or shortened by shelling is found again by its centre cell; a run that is gone
     * frees the squad.
     */
    GarrisonDirector.Garrison holderOf(Body body, GarrisonableRegistry places) {
        ObjectiveAssignment order = body.order();
        int cellX;
        int cellY;
        if (order != null && order.isGarrison()) {
            cellX = order.targetCellX();
            cellY = order.targetCellY();
            director.adopted(body.squadId());
        } else {
            int[] cell = director.pendingCell(body.squadId());
            if (cell == null) return null;
            cellX = cell[0];
            cellY = cell[1];
        }
        int place = places.idAt(cellX, cellY);
        if (place < 0) {
            director.forget(body.squadId());
            return null;
        }
        int reach = places.radius(place);
        boolean arrived = Math.abs(body.centroidX() - places.centreX(place)) <= reach
                && Math.abs(body.centroidY() - places.centreY(place)) <= reach;
        return new GarrisonDirector.Garrison(body.squadId(), place, !body.localContact(), arrived, body.alive(),
                (int) body.centroidX(), (int) body.centroidY(), body.original());
    }

    /**
     * Keeps, withdraws or gives back every holder. With {@code standDown} (the side has a last objective to put
     * everything into) every holder is given back at once and nothing is priced.
     *
     * @param held    the holders {@link #holderOf} found this pulse
     * @param bodies  by squad id, at least every holder
     */
    List<Decision> standOrRelease(GarrisonableRegistry places, CommanderInfluenceSnapshot influence,
                                  List<GarrisonDirector.Garrison> held, Map<Integer, Body> bodies, Faction perspective,
                                  List<DefenceStructureFact> structures, CommandTopology topology, boolean standDown) {
        List<Decision> out = new ArrayList<>();
        if (standDown) {
            for (GarrisonDirector.Garrison garrison : held) {
                director.forget(garrison.squadId());
                out.add(new Decision(garrison.squadId(), Verdict.RELEASE, null));
            }
            return out;
        }
        int count = believedMovers(influence, places);
        int bw = places.blockWidth();
        return standOrRelease(places, (bx, by) -> nearMover[by * bw + bx] == generation ? influence.hostileAt(bx, by) : 0f,
                contactScratch, count, held, bodies, perspective, structures, topology);
    }

    /** As the influence form, over a pressure field and packed believed contact cells; the seam unit tests use. */
    List<Decision> standOrRelease(GarrisonableRegistry places, GarrisonDirector.Pressure field, int[] contactXY,
                                  int contactCount, List<GarrisonDirector.Garrison> held, Map<Integer, Body> bodies,
                                  Faction perspective, List<DefenceStructureFact> structures, CommandTopology topology) {
        List<Decision> out = new ArrayList<>();
        Set<Integer> releasing = new HashSet<>(director.evaluate(places, field, contactXY, contactCount, held));
        Set<Integer> fallingBack = new HashSet<>(director.fallingBack());
        for (GarrisonDirector.Garrison garrison : held) {
            int id = garrison.squadId();
            Body body = bodies.get(id);
            if (fallingBack.contains(id)) {
                director.forget(id);
                ObjectiveAssignment post = body == null ? null
                        : fallBackPost(body, garrison.garrisonable(), places, perspective, structures, topology);
                out.add(post != null ? new Decision(id, Verdict.FALL_BACK, post) : new Decision(id, Verdict.RELEASE, null));
                continue;
            }
            if (!garrison.arrived() && director.lockedOut(places.centreX(garrison.garrisonable()),
                    places.centreY(garrison.garrisonable()))) {
                // Driven off a run the enemy is now believed inside: a breach, whether or not the squad still stands on it.
                director.forget(id);
                ObjectiveAssignment post = body == null ? null
                        : fallBackPost(body, garrison.garrisonable(), places, perspective, structures, topology);
                out.add(post != null ? new Decision(id, Verdict.FALL_BACK, post) : new Decision(id, Verdict.RELEASE, null));
                continue;
            }
            if (releasing.contains(id)) {
                director.forget(id);
                out.add(new Decision(id, Verdict.RELEASE, null));
                continue;
            }
            out.add(new Decision(id, Verdict.STAND, runOrder(id, garrison.garrisonable(), places)));
        }
        return out;
    }

    /**
     * Sends squads of {@code free} to the pressed runs of the forts in {@code heldForts}, at most {@code budget} this
     * pulse. Call after {@link #standOrRelease} in the same pulse; with a stand-down there is nothing to send to.
     *
     * @param free free squads in a stable order, none holding or sent
     */
    List<Sent> man(GarrisonableRegistry places, List<Body> free, int budget, Set<Integer> heldForts, Reach reach) {
        if (budget <= 0 || heldForts.isEmpty() || free.isEmpty()) {
            // The director's pulse is still closed out, so its per-pulse state does not leak into the next one.
            director.assign(List.of(), (s, g) -> false, null, 0);
            return List.of();
        }
        Map<Integer, Body> byId = new TreeMap<>();
        List<GarrisonDirector.Free> offered = new ArrayList<>(free.size());
        for (Body body : free) {
            byId.put(body.squadId(), body);
            offered.add(new GarrisonDirector.Free(body.squadId(), body.anchorX(), body.anchorY(), false,
                    body.alive(), body.original()));
        }
        List<GarrisonDirector.Man> men = director.assign(offered, (squadId, place) -> {
            if (!heldForts.contains(places.fortId(place))) return false;
            Body body = byId.get(squadId);
            int x = places.centreX(place);
            int y = places.centreY(place);
            return reach.to(body.anchorX(), body.anchorY(), x, y);
        }, null, budget);
        List<Sent> out = new ArrayList<>(men.size());
        for (GarrisonDirector.Man man : men) {
            out.add(new Sent(man.squadId(), man.garrisonable(), runOrder(man.squadId(), man.garrisonable(), places)));
        }
        return out;
    }

    /** The places the last pulse found pressed to the manning level; for tests and evidence. */
    int[] pressedPlaces() {
        return director.hotPlaces();
    }

    private static ObjectiveAssignment runOrder(int squadId, int place, GarrisonableRegistry places) {
        return ObjectiveAssignment.defendRun(squadId, places.centreX(place), places.centreY(place),
                places.radius(place), place);
    }

    /**
     * Packs the believed movers heavy enough to wake a place into the scratch array and marks the blocks within the
     * registry's reach of each; returns how many. A believed emplacement is not a counter-attack: an enemy nest left
     * standing beside a taken belt presses it for as long as it stands, and reading it as pressure would hold the belt
     * for the rest of the battle against an enemy that never comes. So only a mover wakes a place, and a place is priced
     * only on blocks a mover is near.
     */
    private int believedMovers(CommanderInfluenceSnapshot influence, GarrisonableRegistry places) {
        int bw = places.blockWidth();
        int bh = places.blockHeight();
        int size = places.blockSize();
        if (nearMover.length != bw * bh) {
            nearMover = new int[bw * bh];
            generation = 0;
        }
        generation++;
        int reach = GarrisonableRegistry.REACH_BLOCKS;
        int count = 0;
        for (CommanderContact contact : influence.contacts()) {
            if (contact.structure()) continue;
            if (contact.confidence() * contact.strength() < GarrisonDirector.MIN_CONTACT_WEIGHT) continue;
            int cbx = Math.min(bw - 1, Math.max(0, contact.cellX() / size));
            int cby = Math.min(bh - 1, Math.max(0, contact.cellY() / size));
            for (int by = Math.max(0, cby - reach); by <= Math.min(bh - 1, cby + reach); by++) {
                for (int bx = Math.max(0, cbx - reach); bx <= Math.min(bw - 1, cbx + reach); bx++) {
                    nearMover[by * bw + bx] = generation;
                }
            }
            if (2 * count + 2 > contactScratch.length) {
                contactScratch = Arrays.copyOf(contactScratch, contactScratch.length * 2);
            }
            contactScratch[2 * count] = contact.cellX();
            contactScratch[2 * count + 1] = contact.cellY();
            count++;
        }
        return count;
    }

    /**
     * Where a holder whose run is lost withdraws to: the doorstep of the nearest work of the same fort its side holds,
     * or {@code null} (released instead) when the run belongs to no fort or no such work stands.
     */
    private static ObjectiveAssignment fallBackPost(Body body, int place, GarrisonableRegistry places, Faction perspective,
                                                    List<DefenceStructureFact> structures, CommandTopology topology) {
        int fort = places.fortId(place);
        if (fort < 0) return null;
        DefenceStructureFact works = null;
        long best = Long.MAX_VALUE;
        for (DefenceStructureFact structure : structures) {
            if (structure.fortId() != fort || !perspective.friendlyTo(structure.holder())) continue;
            long dx = structure.cellX() - places.centreX(place);
            long dy = structure.cellY() - places.centreY(place);
            long gap = dx * dx + dy * dy;
            if (gap < best) {
                best = gap;
                works = structure;
            }
        }
        if (works == null) return null;
        return DefenceStructureTactics.doorstepPost(body.squadId(), works, body.anchorX(), body.anchorY(), topology);
    }
}
