package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.BuildingStanding;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Marine Raid contract: work one or more authored sites, then reach the authored egress
 * ({@code raid-mission.md}).
 *
 * <p>A raid for valuables has one site, the target, and plays as it always has: seize it, then return the
 * recovered package to the egress. The package is abstract rather than bound to one carrier; after seizure, any
 * surviving Marine combatant can complete the extraction. This prevents carrier-death bookkeeping from becoming a
 * hidden second objective while retaining the required strike-and-egress shape.
 *
 * <p>A raid to disrupt an industry has several sites, the machines of its works ({@link #works}). Each is wrecked
 * by the same law that seizes a target: a settled Marine combatant holding its pocket uncontested for the service
 * time, progress lost on interruption; or by bringing down the hall it stands in ({@link #withHalls}). Machines can
 * be worked in any order and at once. The way out opens with the
 * first machine wrecked ({@link Phase#EGRESS}): leaving then is the player's call, and machines left standing can
 * still be wrecked on the way. {@link #share} is the fraction wrecked, which decides how long the industry stays
 * disrupted.
 */
public final class RaidObjective implements Objective {

    public static final float DEFAULT_SERVICE_DURATION = 6f;
    /** Seconds a charge takes to set on one machine of the works. UNTUNED. */
    public static final float WRECK_DURATION = 8f;
    public static final int TARGET_RADIUS = 2;
    public static final int EGRESS_RADIUS = 3;

    public enum Phase { APPROACH, SERVICE, EGRESS, COMPLETE }

    /**
     * When the target may be worked, in sim ticks: from {@code opensTick} until {@code closesTick}, as a freighter
     * stands at its berth only so long. Before it opens the target cannot be served; once it closes with the target
     * not seized, the raid has failed.
     */
    public record ServiceWindow(int opensTick, int closesTick) {
        public ServiceWindow {
            if (opensTick < 0 || closesTick <= opensTick) {
                throw new IllegalArgumentException("a service window opens before it closes");
            }
        }

        /** Whether the target may be served on {@code tick}. */
        public boolean open(int tick) { return tick >= opensTick && tick < closesTick; }

        /** Whether the window has closed on {@code tick} without the target seized. */
        public boolean missed(int tick, boolean secured) { return !secured && tick >= closesTick; }
    }

    /** Identity-free target alarm available to the installation defender. */
    public record TargetAlarm(boolean active, int raisedTick) {
        public static TargetAlarm quiet() { return new TargetAlarm(false, -1); }
    }

    /** One place the raid works: the target, or a machine of the works. The zone is frozen at setup. */
    public record Site(int cellX, int cellY, int zoneId) {
        public Site {
            if (zoneId < 0) throw new IllegalArgumentException("valid Raid site zone required");
        }
    }

    private final String targetId;
    private final String targetName;
    private final List<Site> sites;
    private final boolean works;
    private final int egressCellX;
    private final int egressCellY;
    private final float serviceDuration;

    private final float[] progress;
    private final boolean[] done;
    private int doneCount;
    private boolean marineServicing;
    private boolean complete;
    private boolean failed;
    private int alarmRaisedTick = -1;
    private ServiceWindow window;
    private List<List<int[]>> halls;
    private int lastTick;

    public RaidObjective(String targetId, String targetName,
                         int targetCellX, int targetCellY, int targetZoneId,
                         int egressCellX, int egressCellY) {
        this(targetId, targetName, targetCellX, targetCellY, targetZoneId,
                egressCellX, egressCellY, DEFAULT_SERVICE_DURATION);
    }

    RaidObjective(String targetId, String targetName,
                  int targetCellX, int targetCellY, int targetZoneId,
                  int egressCellX, int egressCellY, float serviceDuration) {
        this(targetId, targetName, List.of(new Site(targetCellX, targetCellY, targetZoneId)), false,
                egressCellX, egressCellY, serviceDuration);
    }

    private RaidObjective(String targetId, String targetName, List<Site> sites, boolean works,
                          int egressCellX, int egressCellY, float serviceDuration) {
        if (targetId == null || targetId.isBlank()
                || targetName == null || targetName.isBlank()) {
            throw new IllegalArgumentException("stable Raid target identity required");
        }
        if (sites == null || sites.isEmpty() || serviceDuration <= 0f) {
            throw new IllegalArgumentException("valid Raid target zone required");
        }
        this.targetId = targetId;
        this.targetName = targetName;
        this.sites = List.copyOf(sites);
        this.works = works;
        this.egressCellX = egressCellX;
        this.egressCellY = egressCellY;
        this.serviceDuration = serviceDuration;
        this.progress = new float[this.sites.size()];
        this.done = new boolean[this.sites.size()];
    }

    /** A raid to disrupt an industry: its works' machines, each wrecked by a charge ({@link #WRECK_DURATION}). */
    public static RaidObjective works(String targetId, String targetName, List<Site> machines,
                                      int egressCellX, int egressCellY) {
        return works(targetId, targetName, machines, egressCellX, egressCellY, WRECK_DURATION);
    }

    static RaidObjective works(String targetId, String targetName, List<Site> machines,
                               int egressCellX, int egressCellY, float chargeDuration) {
        return new RaidObjective(targetId, targetName, machines, true, egressCellX, egressCellY, chargeDuration);
    }

    @Override public Faction owningFaction() { return Faction.MARINE; }

    /**
     * The halls of the works' machines ({@code raid-demolish-the-works.md}): {@code outlines.get(i)} is the outline
     * ({@link BuildingStanding#outline}) of the building machine {@code i} stands in, or empty for a machine in the
     * open. A machine whose hall is brought down ({@link BuildingStanding#demolished}) is wrecked with it, whatever
     * brought it down; a charge still wrecks it too. Two machines in one hall share its outline and fall together.
     */
    public RaidObjective withHalls(List<List<int[]>> outlines) {
        if (outlines.size() != sites.size()) throw new IllegalArgumentException("one hall outline per site");
        List<List<int[]>> copy = new ArrayList<>(outlines.size());
        for (List<int[]> outline : outlines) copy.add(List.copyOf(outline));
        this.halls = List.copyOf(copy);
        return this;
    }

    /** Only within {@code window} may the target be served; a window missed fails the raid. */
    public RaidObjective withServiceWindow(ServiceWindow window) {
        this.window = window;
        return this;
    }

    @Override
    public void tick(BattleView sim) {
        if (complete || failed) return;
        lastTick = sim.getSimTickIndex();
        if (!targetSecured() && window != null && !window.open(lastTick)) {
            marineServicing = false;
            Arrays.fill(progress, 0f);
            failed = window.missed(lastTick, false);
            return;
        }
        marineServicing = false;
        for (int i = 0; i < sites.size(); i++) {
            if (done[i]) continue;
            if (hallDown(sim, i)) {
                if (alarmRaisedTick < 0) alarmRaisedTick = sim.getSimTickIndex();
                progress[i] = serviceDuration;
                done[i] = true;
                doneCount++;
                continue;
            }
            Site site = sites.get(i);
            int zone = zoneAt(sim, site);
            boolean marinePresent = combatantPresent(sim, Faction.MARINE,
                    site.cellX(), site.cellY(), TARGET_RADIUS, zone, true);
            boolean defenderPresent = combatantPresent(sim, Faction.DEFENDER,
                    site.cellX(), site.cellY(), TARGET_RADIUS, zone, false);
            if (!marinePresent || defenderPresent) {
                progress[i] = 0f;
                continue;
            }
            marineServicing = true;
            if (alarmRaisedTick < 0) alarmRaisedTick = sim.getSimTickIndex();
            progress[i] += BattleSimulation.TICK_DT;
            if (progress[i] >= serviceDuration) {
                progress[i] = serviceDuration;
                done[i] = true;
                doneCount++;
            }
        }
        // The way out opens with the first site done; reaching it then ends the raid.
        if (doneCount > 0 && combatantPresent(sim, Faction.MARINE, egressCellX, egressCellY, EGRESS_RADIUS, -1,
                true)) {
            complete = true;
            marineServicing = false;
        }
    }

    private boolean hallDown(BattleView sim, int site) {
        if (halls == null || halls.get(site).isEmpty()) return false;
        CellTopology topology = sim.getTopology();
        return BuildingStanding.demolished(BuildingStanding.standingShare(halls.get(site), topology::isWall));
    }

    /**
     * How far machine {@code site}'s hall is toward brought down, 0 whole to 1 down
     * ({@link BuildingStanding#progress}); 0 for a machine in the open.
     */
    public float hallProgress(BattleView sim, int site) {
        if (halls == null || halls.get(site).isEmpty()) return 0f;
        CellTopology topology = sim.getTopology();
        return BuildingStanding.progress(BuildingStanding.standingShare(halls.get(site), topology::isWall));
    }

    /** Whether machine {@code site} stands in a hall that can be brought down. */
    public boolean hasHall(int site) { return halls != null && !halls.get(site).isEmpty(); }

    private static int zoneAt(BattleView sim, Site site) {
        int zone = sim.getZoneGraph().zoneIdAt(site.cellX(), site.cellY());
        return zone >= 0 ? zone : site.zoneId();
    }

    private static boolean combatantPresent(BattleView sim, Faction faction,
                                             int centerX, int centerY, int radius,
                                             int requiredZone,
                                             boolean mustBeSettled) {
        int radiusSquared = radius * radius;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != faction
                    || !sim.identity().type(unit).combatant) continue;
            int dx = sim.world().cellX(unit) - centerX;
            int dy = sim.world().cellY(unit) - centerY;
            if (dx * dx + dy * dy > radiusSquared) continue;
            if (requiredZone >= 0 && sim.getZoneGraph().zoneIdAt(
                    sim.world().cellX(unit), sim.world().cellY(unit))
                    != requiredZone) continue;
            if (!mustBeSettled || sim.movement().settled(unit)) return true;
        }
        return false;
    }

    /**
     * The site the raid is about now: the standing one furthest along, else the first standing, else the last.
     * A one-site raid's is always its target.
     */
    private int current() {
        int best = -1;
        for (int i = 0; i < sites.size(); i++) {
            if (done[i]) continue;
            if (best < 0 || progress[i] > progress[best]) best = i;
        }
        return best >= 0 ? best : sites.size() - 1;
    }

    public String targetId() { return targetId; }
    public String targetName() { return targetName; }
    public int targetCellX() { return sites.get(current()).cellX(); }
    public int targetCellY() { return sites.get(current()).cellY(); }
    /** The zone id frozen at setup; it goes stale once the zone graph is rebuilt. */
    public int targetZoneId() { return sites.get(current()).zoneId(); }

    /**
     * The target's zone as the zone graph numbers it now. The graph is rebuilt when topology
     * changes (a wall or a furnishing destroyed), and rebuilding renumbers zones, so the id frozen
     * at setup can name another room; it is the fallback only when the target cell has no zone.
     */
    public int targetZoneId(BattleView sim) {
        return zoneAt(sim, sites.get(current()));
    }
    public int egressCellX() { return egressCellX; }
    public int egressCellY() { return egressCellY; }
    public float serviceProgress() { return progress[current()]; }
    public float serviceDuration() { return serviceDuration; }
    public boolean marineServicing() { return marineServicing; }
    /** Whether every site is done: the target seized, or every machine of the works wrecked. */
    public boolean targetSecured() { return doneCount == sites.size(); }

    /** Whether this raid is a disrupting raid's works rather than one target. */
    public boolean works() { return works; }
    /** Every site, in setup order. */
    public List<Site> sites() { return sites; }
    /** Whether the site at {@code index} (in {@link #sites} order) is done. */
    public boolean siteDone(int index) { return done[index]; }
    public int sitesDone() { return doneCount; }
    /** The fraction of sites done, 0 to 1: for a works, the share wrecked. */
    public float share() { return (float) doneCount / sites.size(); }
    /** Whether the way out is open: at least one site is done. */
    public boolean egressOpen() { return doneCount > 0; }

    public Phase phase() {
        if (complete) return Phase.COMPLETE;
        if (doneCount > 0) return Phase.EGRESS;
        return marineServicing || progress[current()] > 0f ? Phase.SERVICE : Phase.APPROACH;
    }
    /**
     * Raises the installation alarm from outside the target room: a mission whose garrison is roused by something
     * other than the target being worked (the Dust Ledger's shots, sightings and lift-off clock). The first raise
     * stands; a later one, from here or from servicing, changes nothing.
     */
    public void raiseAlarm(int tick) {
        if (alarmRaisedTick < 0) alarmRaisedTick = tick;
    }

    public TargetAlarm defenderAlarm() {
        return alarmRaisedTick >= 0 ? new TargetAlarm(true, alarmRaisedTick)
                : TargetAlarm.quiet();
    }

    @Override public boolean isComplete() { return complete; }
    @Override public boolean isFailed() { return failed; }

    /** The window the target may be served in, or null for one always open. */
    public ServiceWindow serviceWindow() { return window; }

    /** The sim tick of the last tick judged. */
    public int lastTick() { return lastTick; }
    @Override public String displayName() {
        return works ? "Wreck " + targetName + " and extract" : "Seize " + targetName + " and extract";
    }
}
