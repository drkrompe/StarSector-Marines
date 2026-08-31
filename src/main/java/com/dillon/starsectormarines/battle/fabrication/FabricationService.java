package com.dillon.starsectormarines.battle.fabrication;

import com.dillon.starsectormarines.battle.ambient.JobBoard;
import com.dillon.starsectormarines.battle.ambient.RoomSite;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.Gantry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What every vehicle bay on this map is building, and how far along it is.
 *
 * <p>A {@link Gantry} is a berth generation cut and left clear; this is the work
 * going on among them. The two are deliberately separate, the same way an
 * authored landing pad is separate from the shuttle standing on it: the map says
 * where machines are worked and this says what the working produces, so a bay is
 * still a bay on a map that builds nothing.
 *
 * <p><b>A bay is a working motor pool, so its berths are occupied.</b> That is
 * what makes the room a posting at all: a technician's trade is servicing
 * whatever is parked in a bay, an empty bay publishes no servicing, and a shed
 * with nothing in its berths is a shed the manning pass declines to staff — a
 * garage with nobody in it, which is a worse answer than a garage with work in
 * it. So the garrison's own machines are taken to be standing in them, in and
 * out for maintenance, and the crew has something to weld from the first tick.
 *
 * <p><b>One machine on the stocks per bay, not one per berth.</b> A shed's crew
 * is a handful of people and a rank of berths is six, so work credited to the
 * berth somebody happens to be standing at spreads across all of them and
 * nothing is ever finished — six machines at a sixth each, for the whole battle.
 * What a motor pool actually has is one job in hand and a queue behind it, so
 * the bay's work is the bay's, and where the finished machine comes out is
 * wherever there is room.
 *
 * <p>Follows the {@code *Service} convention: state owner, advanced by
 * {@link FabricationSystem}.
 */
public final class FabricationService {

    /**
     * The heaviest chassis a field shed lays down, as structure.
     *
     * <p>A bay on a garrison map is a rank of five-by-seven berths and a
     * fabrication shop at one end. That builds and repairs the machines a
     * garrison actually runs — a scout, a light brawler — and it does not lay a
     * keel for an assault chassis, which is a yard's work and arrives by ship.
     * Stated as what the shed can carry rather than as a list of chassis names,
     * so a chassis added later is admitted or refused by what it weighs instead
     * of by whether somebody remembered to add it here.
     */
    public static final float FIELD_SHED_STRUCTURE_LIMIT = 400f;

    /**
     * Hand-seconds of work one machine takes from keel to roll-out.
     *
     * <p>Counted in hands rather than in seconds, which is the whole point of
     * the thing: a bay nobody works builds nothing however long the battle
     * lasts, and a bay with four technicians in it builds twice as fast as one
     * with two. A wall-clock figure would make the shed a timer that a marine
     * assault could not slow down.
     */
    public static final float HAND_SECONDS_PER_MACHINE = 240f;

    /** One bay, and the machine it has in hand. */
    public static final class Works {

        /** The room, which is what its work is published under. */
        public final int siteId;
        /** The berths under this roof, in map order. */
        public final List<Integer> berths;
        /** What is being made. */
        public MechVariant chassis;
        /** Hand-seconds worked so far. */
        public float worked;
        /** How many machines this bay has put out. */
        public int completed;

        Works(int siteId, List<Integer> berths, MechVariant chassis) {
            this.siteId = siteId;
            this.berths = List.copyOf(berths);
            this.chassis = chassis;
        }

        /** How far along, in [0, 1]. */
        public float progress() {
            return Math.min(1f, worked / HAND_SECONDS_PER_MACHINE);
        }
    }

    private final List<Gantry> berths;
    private final Map<Integer, Works> bays = new LinkedHashMap<>();
    /** Standing cell of every berth-bound servicing point, to the bay it belongs to. */
    private final Map<Long, Integer> serviceCells = new HashMap<>();
    private final List<MechVariant> buildable;
    private Faction lastBuilder = Faction.DEFENDER;

    /**
     * Open the works in every vehicle bay on this map.
     *
     * @param berths the map's authored machine berths, in map order
     * @param authored the map's authored work points, for finding the cells a
     *     berth is serviced from
     * @param sites the map's rooms, for deciding which room each berth is in
     */
    public FabricationService(List<Gantry> berths, List<FixtureTask> authored,
                              List<RoomSite> sites) {
        this.berths = List.copyOf(berths);
        this.buildable = fieldBuildable();

        Map<Integer, List<Integer>> berthsBySite = new LinkedHashMap<>();
        for (int berth = 0; berth < this.berths.size(); berth++) {
            Gantry gantry = this.berths.get(berth);
            RoomSite site = RoomSite.covering(sites, gantry.centerX, gantry.centerY);
            if (site == null) continue;
            berthsBySite.computeIfAbsent(site.id(), key -> new ArrayList<>()).add(berth);
        }
        for (Map.Entry<Integer, List<Integer>> bay : berthsBySite.entrySet()) {
            bays.put(bay.getKey(), new Works(bay.getKey(), bay.getValue(), first()));
        }

        // A servicing point names the berth it works; what this needs is the
        // bay, because the work is the bay's. Resolved once here rather than
        // per tick, since neither the fill nor the rooms move.
        Map<Integer, Integer> bayOfBerth = new HashMap<>();
        for (Works works : bays.values()) {
            for (int berth : works.berths) bayOfBerth.put(berth, works.siteId);
        }
        for (FixtureTask task : authored) {
            if (task.affordance() != Affordance.SERVICE) continue;
            Integer siteId = bayOfBerth.get(task.berth());
            if (siteId == null) continue;
            serviceCells.put(key(task.cellX(), task.cellY()), siteId);
        }
    }

    /** Every chassis light enough for a field shed, in declaration order. */
    private static List<MechVariant> fieldBuildable() {
        List<MechVariant> light = new ArrayList<>();
        for (MechVariant variant : MechVariant.values()) {
            if (variant.maxStructure <= FIELD_SHED_STRUCTURE_LIMIT) light.add(variant);
        }
        return List.copyOf(light);
    }

    private MechVariant first() {
        return buildable.isEmpty() ? null : buildable.get(0);
    }

    /**
     * The bay serviced from this standing cell, or -1 where none is.
     *
     * <p>The cell rather than the room, because standing in the bay is not
     * working in it: the same technician on the same rotation is at the stores
     * one minute and the terminal the next, and neither of those is welding.
     */
    public int bayServicedFrom(int cellX, int cellY) {
        Integer siteId = serviceCells.get(key(cellX, cellY));
        return siteId == null ? -1 : siteId;
    }

    /** The claim group welding in this bay is published under. */
    public static String weldingGroup(int siteId) {
        return JobBoard.group(siteId, Affordance.SERVICE);
    }

    /** Whether any bay on this map is working. */
    public boolean isEmpty() {
        return bays.isEmpty();
    }

    /** Every working bay, in map order. */
    public List<Works> bays() {
        return List.copyOf(bays.values());
    }

    /** The works in one bay, or null where that room has none. */
    public Works bay(int siteId) {
        return bays.get(siteId);
    }

    /**
     * Which berths hold something, as the job board reads occupancy.
     *
     * <p>Every berth of a working bay, because a motor pool has the garrison's
     * machines in it. Built fresh each call rather than held: it is a view of
     * this state rather than a second copy, and a stale array telling the board
     * a bay is working after its works have gone is the one way that goes wrong.
     */
    public boolean[] berthed() {
        boolean[] held = new boolean[berths.size()];
        for (Works works : bays.values()) {
            for (int berth : works.berths) held[berth] = true;
        }
        return held;
    }

    /** The berth at this index. */
    public Gantry gantry(int berth) {
        return berths.get(berth);
    }

    /** Credit hand-seconds of work to one bay. */
    public void work(int siteId, float handSeconds) {
        Works works = bays.get(siteId);
        if (works != null) works.worked += handSeconds;
    }

    /**
     * Clear a finished bay and lay the next machine down.
     *
     * <p>The next one is a different chassis from the one just finished. A shed
     * that rebuilt what it had just built would turn out a column of one model
     * over a long battle, which no motor pool has ever looked like.
     */
    public void rollOut(int siteId) {
        Works works = bays.get(siteId);
        if (works == null) return;
        works.worked = 0f;
        works.completed++;
        works.chassis = next(works.chassis);
    }

    private MechVariant next(MechVariant finished) {
        if (buildable.isEmpty()) return null;
        int index = buildable.indexOf(finished);
        return buildable.get(Math.floorMod(index + 1, buildable.size()));
    }

    /**
     * Whose the last machine off the stocks was.
     *
     * <p>A bay belongs to whoever is working it rather than to whoever holds the
     * ground: the hands at the benches are the production, and a shed whose
     * technicians are dead or driven off builds nothing for anybody. So the side
     * is read off the workers as they work, and this is what that reading left
     * behind — which matters only for the case where the last hand died on the
     * tick the machine finished.
     */
    public Faction lastBuilder() {
        return lastBuilder;
    }

    public void setLastBuilder(Faction faction) {
        if (faction != null) lastBuilder = faction;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }
}
