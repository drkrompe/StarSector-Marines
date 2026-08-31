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
 * What every vehicle bay on this map has on the stocks.
 *
 * <p>A {@link Gantry} is a berth generation cut and left clear; this is the
 * machine standing in one and the work going on around it. The two are
 * deliberately separate, the same way an authored landing pad is separate from
 * the shuttle standing on it: the map says where machines are worked, this says
 * what is being worked there, so a bay is still a bay on a map that builds
 * nothing.
 *
 * <p><b>The machine on the stocks has a body, and its structure is how built it
 * is.</b> Not a proxy for progress — the progress itself. Welding raises it, a
 * marine lowers it, and the machine is finished when it is whole. That one
 * decision is what makes a production line something an attacker can do
 * anything about: a number going up inside a building can only be stopped by
 * killing everybody who is adding to it, while a half-built chassis standing in
 * a gantry can be shot. It also makes the fragility right for free — a keel is
 * trivially easy to destroy and a nearly-finished machine is nearly a mech, and
 * nothing had to say so.
 *
 * <p><b>One machine on the stocks per bay, not one per berth.</b> A shed's crew
 * is a handful of people and a rank of berths is six, so work spread across all
 * of them finishes nothing — six machines at a sixth each, for the whole battle.
 * What a motor pool has is one job in hand and a rank of empty gantries behind
 * it, so the bay's berths beyond the one being worked stand empty and publish no
 * servicing. What is offered is servicing for the machine that is actually
 * there.
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
     * Structure one technician puts on a machine in one second at the gantry.
     *
     * <p>Counted in hands rather than in seconds, which is the whole point of
     * the thing: a bay nobody works builds nothing however long the battle
     * lasts, and a bay with four technicians in it builds twice as fast as one
     * with two. A wall-clock figure would make the shed a timer that a marine
     * assault could not slow down.
     *
     * <p>Per point of structure rather than per machine, so a lighter chassis is
     * genuinely quicker to build — which is the only reason a shed would choose
     * one, and it falls out of the chassis rather than out of a table.
     */
    public static final float STRUCTURE_PER_HAND_SECOND = 1.5f;

    /**
     * What a laid keel already amounts to, as a share of the finished machine.
     *
     * <p>Not nothing. A frame that spawned at a single point of structure would
     * die to one stray round on the tick it was laid, and a bay under
     * intermittent fire would spend the battle laying keels that never survived
     * long enough to be seen. Small enough that an attacker who reaches the shed
     * early destroys the work cheaply, which is the point.
     */
    public static final float KEEL_FRACTION = 0.08f;

    /** One bay, and the machine it has in hand. */
    public static final class Works {

        /** The room, which is what its work is published under. */
        public final int siteId;
        /** The berths under this roof, in map order. */
        public final List<Integer> berths;
        /** The one worked, where the machine on the stocks stands. */
        public final int stocks;
        /** What is being made. */
        public MechVariant chassis;
        /** The body standing on the stocks, or 0 while there is none. */
        public long frameId;
        /** How many machines this bay has put out. */
        public int completed;

        Works(int siteId, List<Integer> berths, int stocks, MechVariant chassis) {
            this.siteId = siteId;
            this.berths = List.copyOf(berths);
            this.stocks = stocks;
            this.chassis = chassis;
        }

        /** Whether anything is standing on the stocks right now. */
        public boolean hasFrame() {
            return frameId != 0L;
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
            List<Integer> under = bay.getValue();
            bays.put(bay.getKey(),
                    new Works(bay.getKey(), under, under.get(0), first()));
        }

        // A servicing point names the berth it works; what this needs is the
        // bay, because the machine on the stocks is the bay's. Resolved once
        // here rather than per tick, since neither the fill nor the rooms move.
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
     * <p>Only the one being worked. A bay publishes servicing for the machine
     * that is standing in it, and offering it for five empty gantries as well
     * would put four technicians in five places welding nothing — the exact
     * failure the berth-bound link exists to prevent, arrived at from the
     * generous end.
     */
    public boolean[] berthed() {
        boolean[] held = new boolean[berths.size()];
        for (Works works : bays.values()) held[works.stocks] = true;
        return held;
    }

    /** The berth at this index. */
    public Gantry gantry(int berth) {
        return berths.get(berth);
    }

    /** Record the body now standing on a bay's stocks. */
    public void lay(int siteId, long frameId) {
        Works works = bays.get(siteId);
        if (works != null) works.frameId = frameId;
    }

    /** Forget a body that is no longer standing there, however it went. */
    public void clearStocks(int siteId) {
        Works works = bays.get(siteId);
        if (works != null) works.frameId = 0L;
    }

    /**
     * Count a finished machine and choose what goes on the stocks next.
     *
     * <p>The next one is a different chassis from the one just finished. A shed
     * that rebuilt what it had just built would turn out a column of one model
     * over a long battle, which no motor pool has ever looked like.
     */
    public void rollOut(int siteId) {
        Works works = bays.get(siteId);
        if (works == null) return;
        works.frameId = 0L;
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
