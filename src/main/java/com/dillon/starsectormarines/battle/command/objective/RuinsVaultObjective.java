package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Ruins Expedition's objective ({@code ruins-expedition.md}, The Ruins mission family): breach the vault, get its
 * haul to a lift point, board it.
 *
 * <p>The vault is the family's one source. The company holds it uncontested for the grade's breach time, shorter once
 * power reaches the vault's seal ({@link #setVaultPowered}). The breach releases the haul, which moves under the
 * escorted-package rule: only with a settled escort within its leash, lost to sustained hostile control while
 * unattended, abandoned if nobody escorts it for long. It boards at a lift point under the hold rule. These are
 * Extraction's own rules ({@link UncontestedHold}, {@link EscortedRoute}), shared rather than copied.
 *
 * <p>A lift point is the landing area, always, or a landing court or an entrance of the interior once the company has
 * held it (an entrance is held on its apron); once held, it stays the company's. Until the haul leaves the vault, it is bound for the available lift
 * point with the shortest route; once it moves, it keeps that one.
 *
 * <p>The mission plays in two {@link RuinsPhase phases} ({@code ruins-expedition.md}, The entrance and the pull-out
 * point). In the {@link RuinsPhase#APPROACH} the company fights up to the interior; taking an entrance's apron
 * makes it the <em>entry</em> and starts the {@link RuinsPhase#DELVE} for good. The {@link #pullOutPoint() pull-out
 * point} is the landing in the approach and the entry in the delve. Nothing retakes an entrance: in the delve the
 * occupant is inside, and the danger lies between the company and the entry.
 *
 * <p>Won when the haul boards. Lost when it is lost or abandoned; a wiped-out or withdrawn company loses through the
 * battle's ordinary defeat.
 */
public final class RuinsVaultObjective implements ExtractionPayloadObjective {

    public static final int VAULT_RADIUS = ExtractionObjective.SOURCE_RADIUS;
    public static final int ESCORT_RADIUS = ExtractionObjective.ESCORT_RADIUS;
    public static final int INTERDICTION_RADIUS = ExtractionObjective.INTERDICTION_RADIUS;
    public static final int LIFT_RADIUS = ExtractionObjective.EGRESS_RADIUS;
    public static final float SECONDS_PER_ROUTE_CELL = ExtractionObjective.DEFAULT_SECONDS_PER_ROUTE_CELL;
    public static final float BOARDING_SECONDS = ExtractionObjective.DEFAULT_BOARDING_DURATION;
    public static final float LOSS_SECONDS = ExtractionObjective.DEFAULT_LOSS_DURATION;
    public static final float ABANDONMENT_SECONDS = ExtractionObjective.DEFAULT_ABANDONMENT_DURATION;
    /** How long the company holds a landing court, or an entrance's apron, before it is the company's. */
    public static final float COURT_HOLD_SECONDS = 3f;

    /** The two phases of a ruins battle: outside up to the interior, then inside from the entry. */
    public enum RuinsPhase {
        /** Landfall and the fight across the open ruins, until the company takes an entrance. */
        APPROACH,
        /** Inside, from the first entrance taken: the delve, the breach, the egress and the lift. */
        DELVE
    }

    /**
     * Where the company leaves from if it pulls out: the landing in the approach, the entry in the delve.
     *
     * @param liftPoint the index in {@link #liftPoints()}
     * @param radius    the extraction circle round {@code cellX, cellY}
     */
    public record PullOutPoint(String name, int liftPoint, int cellX, int cellY, int radius) { }

    /**
     * A place the haul can board.
     *
     * @param name  what the map and rail call it
     * @param kind  the landing area (a lift point from the start), a landing court or an entrance of the interior
     *              (each once held, an entrance on its apron)
     * @param route the walkable route from the vault (its first cell) to this point (its last), as cell pairs; an
     *              entrance's route ends on its apron
     */
    public record LiftPoint(String name, Kind kind, int[] route) {
        public enum Kind { LANDING, COURT, ENTRANCE }

        public LiftPoint {
            if (name == null || name.isBlank() || kind == null || route == null || route.length < 4
                    || (route.length & 1) != 0) {
                throw new IllegalArgumentException("a lift point needs a name, a kind and a route from the vault");
            }
        }

        /** The landing area or a landing court. */
        public LiftPoint(String name, boolean landingArea, int[] route) {
            this(name, landingArea ? Kind.LANDING : Kind.COURT, route);
        }

        public boolean landingArea() { return kind == Kind.LANDING; }
        public boolean entrance() { return kind == Kind.ENTRANCE; }
    }

    private final String haulId;
    private final String haulName;
    private final String vaultName;
    private final int vaultZoneId;
    private final float breachSeconds;
    private final float poweredBreachSeconds;
    private final List<LiftPoint> liftPoints;
    private final List<EscortedRoute> routes = new ArrayList<>();
    private final List<UncontestedHold> courts = new ArrayList<>();
    /** Whether each lift point's lift has power; all of them until the site's works say otherwise. */
    private final boolean[] liftPowered;
    private final int landing;
    private final UncontestedHold breach;
    private final UncontestedHold boarding = new UncontestedHold(BOARDING_SECONDS);

    private int destination;
    private int entry = -1;
    private float entrySeconds = -1f;
    private boolean powered;
    private boolean complete;
    private boolean failed;
    private Failure failure = Failure.NONE;
    private int alarmTick = -1;
    private int controllingSquadId = -1;
    private boolean escortPresent;
    private boolean contested;

    /**
     * @param vaultZoneId          the vault's zone at setup, used when its cell has none
     * @param breachSeconds        the grade's breach with the seal unpowered
     * @param poweredBreachSeconds the grade's breach once power reaches the seal
     * @param liftPoints           the landing area and the landing courts; exactly one is the landing area, and every
     *                             route starts at the same vault cell
     */
    public RuinsVaultObjective(String haulId, String haulName, String vaultName, int vaultZoneId,
                               float breachSeconds, float poweredBreachSeconds, List<LiftPoint> liftPoints) {
        if (haulId == null || haulId.isBlank() || haulName == null || haulName.isBlank()
                || vaultName == null || vaultName.isBlank()) {
            throw new IllegalArgumentException("stable haul and vault identity required");
        }
        if (vaultZoneId < 0 || poweredBreachSeconds <= 0f || breachSeconds < poweredBreachSeconds
                || liftPoints == null || liftPoints.stream().filter(LiftPoint::landingArea).count() != 1) {
            throw new IllegalArgumentException("a vault zone, breach times and exactly one landing area required");
        }
        int vaultX = liftPoints.get(0).route()[0];
        int vaultY = liftPoints.get(0).route()[1];
        for (LiftPoint point : liftPoints) {
            if (point.route()[0] != vaultX || point.route()[1] != vaultY) {
                throw new IllegalArgumentException("every lift point's route starts at the vault");
            }
        }
        this.haulId = haulId;
        this.haulName = haulName;
        this.vaultName = vaultName;
        this.vaultZoneId = vaultZoneId;
        this.breachSeconds = breachSeconds;
        this.poweredBreachSeconds = poweredBreachSeconds;
        this.liftPoints = List.copyOf(liftPoints);
        this.liftPowered = new boolean[liftPoints.size()];
        Arrays.fill(this.liftPowered, true);
        this.breach = new UncontestedHold(breachSeconds);
        for (LiftPoint point : this.liftPoints) {
            routes.add(new EscortedRoute(point.route(), SECONDS_PER_ROUTE_CELL, LOSS_SECONDS, ABANDONMENT_SECONDS,
                    ESCORT_RADIUS, INTERDICTION_RADIUS));
            courts.add(point.landingArea() ? null : new UncontestedHold(COURT_HOLD_SECONDS));
        }
        int found = 0;
        while (!this.liftPoints.get(found).landingArea()) found++;
        this.landing = found;
        this.destination = nearestAvailable();
    }

    @Override public Faction owningFaction() { return Faction.MARINE; }

    /** Routes power to the vault's seal: the breach takes the powered time from now on. Set by the site's works. */
    public void setVaultPowered(boolean powered) {
        this.powered = powered;
        if (!breach.held()) breach.setDuration(powered ? poweredBreachSeconds : breachSeconds);
    }

    @Override
    public void tick(BattleView sim) {
        if (complete || failed) return;
        if (entry >= 0) entrySeconds += BattleSimulation.TICK_DT;
        EscortedRoute haul = routes.get(destination);
        if (haul.index() == 0) {
            tickCourts(sim);
            destination = nearestAvailable();
            haul = routes.get(destination);
        }
        if (!breach.held()) {
            tickBreach(sim);
            return;
        }
        if (!haul.arrived()) {
            Failure result = haul.tick(sim);
            report(haul.contested(), haul.escortPresent(), haul.controllingSquadId());
            if (result != Failure.NONE) fail(result);
            return;
        }
        boolean boarded = boarding.tick(sim, haul.endX(), haul.endY(), LIFT_RADIUS, -1);
        report(boarding.contested(), boarding.escortPresent(), boarding.controllingSquadId());
        if (boarded) {
            complete = true;
            report(contested, false, -1);
        }
    }

    private void tickBreach(BattleView sim) {
        int zone = sim.getZoneGraph().zoneIdAt(sourceCellX(), sourceCellY());
        breach.tick(sim, sourceCellX(), sourceCellY(), VAULT_RADIUS, zone >= 0 ? zone : vaultZoneId);
        report(breach.contested(), breach.escortPresent(), breach.controllingSquadId());
        if (escortPresent && alarmTick < 0) alarmTick = sim.getSimTickIndex();
    }

    /**
     * Landing courts and entrances the company holds become lift points, its own for the rest of the battle. The first
     * entrance taken is the entry and starts the delve.
     */
    private void tickCourts(BattleView sim) {
        for (int i = 0; i < liftPoints.size(); i++) {
            UncontestedHold court = courts.get(i);
            if (court == null || court.held()) continue;
            EscortedRoute route = routes.get(i);
            boolean taken = court.tick(sim, route.endX(), route.endY(), LIFT_RADIUS, -1);
            if (taken && entry < 0 && liftPoints.get(i).entrance()) {
                entry = i;
                entrySeconds = 0f;
            }
        }
    }

    /** The available lift point with the shortest route from the vault; the first in order on a tie. */
    private int nearestAvailable() {
        int best = -1;
        for (int i = 0; i < liftPoints.size(); i++) {
            if (!available(i)) continue;
            if (best < 0 || routes.get(i).cellCount() < routes.get(best).cellCount()) best = i;
        }
        return best;
    }

    private boolean available(int liftPoint) {
        if (!liftPowered[liftPoint]) return false;
        UncontestedHold court = courts.get(liftPoint);
        return court == null || court.held();
    }

    /**
     * Whether a lift point's lift has power: a landing court on an empty ruins site lifts only while the site's works
     * feed it ({@code ruins-expedition.md}, Works). Every lift point has power until the works say otherwise.
     */
    public void setLiftPowered(int liftPoint, boolean powered) { liftPowered[liftPoint] = powered; }

    private void report(boolean contested, boolean escortPresent, int controllingSquadId) {
        this.contested = contested;
        this.escortPresent = escortPresent;
        this.controllingSquadId = controllingSquadId;
    }

    private void fail(Failure reason) {
        failed = true;
        failure = reason;
        report(contested, false, -1);
    }

    /** The approach until the company first takes an entrance, the delve from then on. */
    public RuinsPhase ruinsPhase() { return entry < 0 ? RuinsPhase.APPROACH : RuinsPhase.DELVE; }

    /** The first entrance the company took, as an index in {@link #liftPoints()}, or {@code -1} before it has. */
    public int entry() { return entry; }

    /** Where the company leaves from if it pulls out now. */
    public PullOutPoint pullOutPoint() {
        int point = entry >= 0 ? entry : landing;
        return new PullOutPoint(liftPoints.get(point).name(), point, liftPointCellX(point), liftPointCellY(point),
                LIFT_RADIUS);
    }

    /** Whether the boat may come down at this lift point now. */
    public boolean liftPointAvailable(int liftPoint) { return available(liftPoint); }
    public List<LiftPoint> liftPoints() { return liftPoints; }
    /** The lift point the haul is bound for. */
    public int destination() { return destination; }
    public boolean vaultPowered() { return powered; }
    public boolean breached() { return breach.held(); }
    public float breachProgress() { return breach.progress(); }
    public float breachDuration() { return breach.duration(); }
    public boolean contested() { return contested; }
    public float boardingProgress() { return boarding.progress(); }
    public float boardingDuration() { return boarding.duration(); }
    /** How far the haul has come along its bound route, in route cells. */
    public int haulRouteIndex() { return routes.get(destination).index(); }
    public int haulRouteCellCount() { return routes.get(destination).cellCount(); }
    public float haulLossProgress() { return routes.get(destination).lossProgress(); }
    public float haulLossDuration() { return routes.get(destination).lossDuration(); }
    public float haulAbandonmentProgress() { return routes.get(destination).abandonmentProgress(); }
    public float haulAbandonmentDuration() { return routes.get(destination).abandonmentDuration(); }
    /** The end cell of a lift point's route: where the boat comes down. */
    public int liftPointCellX(int liftPoint) { return routes.get(liftPoint).endX(); }
    public int liftPointCellY(int liftPoint) { return routes.get(liftPoint).endY(); }
    /** How long the company has held an untaken landing court or entrance apron, or 0 for the landing area. */
    public float courtHoldProgress(int liftPoint) {
        UncontestedHold court = courts.get(liftPoint);
        return court == null ? 0f : court.progress();
    }

    @Override public String payloadId() { return haulId; }
    @Override public String payloadName() { return haulName; }
    @Override public Kind payloadKind() { return Kind.PACKAGE; }
    @Override public String sourceName() { return vaultName; }
    @Override public String egressName() { return liftPoints.get(destination).name(); }
    @Override public int sourceCellX() { return routes.get(0).cellX(0); }
    @Override public int sourceCellY() { return routes.get(0).cellY(0); }
    @Override public int egressCellX() { return routes.get(destination).endX(); }
    @Override public int egressCellY() { return routes.get(destination).endY(); }
    @Override public int payloadCellX() { return routes.get(destination).cellX(); }
    @Override public int payloadCellY() { return routes.get(destination).cellY(); }
    @Override public int corridorGuideCellX() {
        EscortedRoute haul = routes.get(destination);
        return haul.cellX(Math.min(haul.index() + 6, haul.cellCount() - 1));
    }
    @Override public int corridorGuideCellY() {
        EscortedRoute haul = routes.get(destination);
        return haul.cellY(Math.min(haul.index() + 6, haul.cellCount() - 1));
    }
    @Override public int initialElements() { return 1; }
    @Override public int activeElements() { return complete || failure == Failure.LOST ? 0 : 1; }
    @Override public int boardedElements() { return complete ? 1 : 0; }
    @Override public int lostElements() { return failure == Failure.LOST ? 1 : 0; }
    @Override public float normalizedProgress() {
        if (complete) return 1f;
        if (!breach.held()) return 0.15f * breach.progress() / breach.duration();
        EscortedRoute haul = routes.get(destination);
        if (!haul.arrived()) return 0.15f + 0.75f * haul.index() / Math.max(1, haul.cellCount() - 1);
        return 0.9f + 0.1f * boarding.progress() / boarding.duration();
    }
    @Override public Phase extractionPhase() {
        if (complete) return Phase.COMPLETE;
        if (failed) return Phase.FAILED;
        if (!breach.held()) return breach.progress() > 0f ? Phase.SECURING : Phase.AT_SOURCE;
        return routes.get(destination).arrived() ? Phase.BOARDING : Phase.IN_TRANSIT;
    }
    @Override public Failure failureReason() { return failure; }
    @Override public boolean alarmActive() { return alarmTick >= 0; }
    @Override public int alarmRaisedTick() { return alarmTick; }
    @Override public int controllingSquadId() { return controllingSquadId; }
    @Override public boolean escortPresent() { return escortPresent; }
    @Override public boolean isComplete() { return complete; }
    @Override public boolean isFailed() { return failed; }
    @Override public String displayName() { return "Breach " + vaultName + " and lift out " + haulName; }

    /** Seconds since the company took the entry, or {@code -1} in the approach. */
    public float secondsSinceEntry() { return entrySeconds; }
}
