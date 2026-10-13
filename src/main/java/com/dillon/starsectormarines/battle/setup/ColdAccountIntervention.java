package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.story.ColdAccountStoryHooks;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipOrder;
import java.util.ArrayList;
import java.util.List;

/** Mission-owned fire authorization. No enemy deletion or timed victory. */
public final class ColdAccountIntervention implements Objective {
    public enum Phase { EVIDENCE, WITHDRAW, WARNING, BOMBARD, PATROL, AFTERMATH, COMPLETE, FAILED }
    public static final int WARNING_TICKS = 0;
    private final ColdAccountLayout layout;
    private final RaidObjective evidence;
    private final StoryBeatLog beats;
    private final ColdAccountStoryHooks approach;
    private final List<int[]> walls = new ArrayList<>();
    private final WarshipOrder.FireOnArea strike;
    private Warship ship;
    private final BombardmentClearance clearance = new BombardmentClearance(WARNING_TICKS);
    private Phase phase = Phase.EVIDENCE;
    private int evidenceTick = -1, bombardTick = -1;
    private int patrolTick = -1, departedTick = -1;
    private int remainingWalls, outsideWithdrawal;
    private final int installationRight;
    private boolean pendingArrivals;
    private boolean companyRecovered;
    private final StructuralDemolitionPlan demolition;
    private boolean batteriesPrepared;
    private float firingHeading = Float.NaN;
    private int preparedTick = -1, groundClearTick = -1;
    private static final int OPENING_SALVO_TICKS = 60;

    public ColdAccountIntervention(BattleSimulation sim, ColdAccountLayout layout, StoryBeatLog beats) {
        this.layout = layout; this.beats = beats;
        approach = new ColdAccountStoryHooks(beats);
        evidence = new RaidObjective("COLD-RECORDS", "dispatch office records", layout.evidenceX(),
                layout.evidenceY(), sim.getZoneGraph().zoneIdAt(layout.evidenceX(), layout.evidenceY()),
                layout.withdrawalX(), layout.withdrawalY());
        strike = new WarshipOrder.FireOnArea(layout.strikeX(), layout.strikeY(), layout.strikeRadius(), true, true);
        for (int y = 0; y < sim.getGrid().getHeight(); y++) for (int x = 0; x < sim.getGrid().getWidth(); x++)
            if (strike.contains(x + .5f, y + .5f) && sim.getTopology().isWall(x, y)) walls.add(new int[]{x,y});
        if (walls.isEmpty()) throw new IllegalStateException("Cold Account requires a destructible stronghold");
        remainingWalls = walls.size();
        installationRight = walls.stream().mapToInt(c -> c[0]).max().orElseThrow();
        demolition = new StructuralDemolitionPlan(strike, walls);
        sim.addObjective(evidence);
        sim.survivorPickup().configure(Faction.MARINE, layout.withdrawalX(), layout.withdrawalY());
    }
    public int installationRight() { return installationRight; }
    public ColdAccountLayout layout() { return layout; }
    public Phase phase() { return phase; }
    public int evidenceTick() { return evidenceTick; }
    public int warningTick() { return clearance.warningTick(); }
    public int bombardTick() { return bombardTick; }
    public int patrolTick() { return patrolTick; }
    public int departedTick() { return departedTick; }
    public int initialWalls() { return walls.size(); }
    public int remainingWalls() { return remainingWalls; }
    public int outsideWithdrawal() { return outsideWithdrawal; }
    public boolean pendingArrivals() { return pendingArrivals; }
    public boolean companyRecovered() { return companyRecovered; }
    public float firingHeading() { return firingHeading; }
    public int demolitionSector() { return demolition.sectorNumber(); }
    public int preparedTick() { return preparedTick; }
    public int groundClearTick() { return groundClearTick; }
    public int shotsFired() { return ship == null ? 0 : ship.mounts().stream().filter(m -> !m.pointDefence()).mapToInt(m -> m.shotsFired).sum(); }
    private void beat(BattleSimulation sim, String key) {
        beats.emitOnce(sim.getSimTickIndex(), "corvus.cold_account." + key, StoryBeatLog.Audience.PLAYER);
    }
    /** Called before the weapons update so a hold order cancels even queued burst rounds. */
    public void advance(BattleSimulation sim) {
        if (isComplete() || isFailed()) return;
        if (!evidence.targetSecured()) {
            boolean landed = false, nearDispatch = false;
            for (int i = 0; i < sim.liveUnitCount(); i++) {
                long unit = sim.liveUnitAt(i);
                if (sim.identity().faction(unit) != Faction.MARINE
                        || sim.identity().type(unit) != UnitType.MARINE) continue;
                landed = true;
                if (Math.hypot(sim.world().x(unit) - layout.evidenceX(),
                        sim.world().y(unit) - layout.evidenceY()) <= 14f) nearDispatch = true;
            }
            approach.observe(sim.getSimTickIndex(), landed, nearDispatch, false);
            return;
        }
        int tick = sim.getSimTickIndex();
        approach.observe(tick, false, false, true);
        pendingArrivals = hasPendingArrivals(sim);
        companyRecovered = !pendingArrivals && sim.survivorPickup().complete(sim);
        // The company's operation ends with its last occupied return flight.
        // Resolute's demolition and patrol continue on their own timetable.
        if (companyRecovered && sim.survivorPickup().recoveredCount() > 0) {
            phase = Phase.COMPLETE;
            beat(sim, "company_extracted");
            return;
        }
        if (phase == Phase.PATROL || phase == Phase.AFTERMATH) {
            if (ship.destroyed()) {
                phase = Phase.FAILED; beat(sim, "intervention_failed"); return;
            }
            if (ship.departed()) {
                if (departedTick < 0) { departedTick = tick; phase = Phase.AFTERMATH; }
            }
            return;
        }
        if (ship == null) {
            evidenceTick = tick;
            beat(sim, "pirate_evidence_confirmed");
            float stationY = layout.strikeY();
            ship = fitShip(sim.getGrid().getWidth() + 30f, stationY);
            float stationX = installationRight + 1f
                    + (float) Math.hypot(ship.lengthCells, ship.widthCells) / 2f + 8f;
            // A short eastern entry leaves time to lay the batteries while the company withdraws.
            ship.x = Math.min(ship.x, stationX + 12f);
            ship.stationTo(stationX, stationY);
            ship.limitOrdnance(1500);
            ship.stayFor(300f);
            ship.order(WarshipOrder.HoldFire.INSTANCE);
            sim.addWarship(ship);
            phase = Phase.WITHDRAW;
            sim.survivorPickup().request();
            beat(sim, "hegemony_arriving_hold_fire");
        }
        remainingWalls = (int) walls.stream().filter(c -> sim.getTopology().isWall(c[0], c[1])).count();
        var sector = demolition.advance(sim.getTopology()::isWall);
        if (ship.destroyed() || ship.departing() || ship.departed()
                || (ship.ordnanceRemaining() == 0 && (sim.warships().shells().isEmpty() && sim.warships().strikes().isEmpty())
                    && !demolition.complete())) {
            ship.order(WarshipOrder.HoldFire.INSTANCE);
            phase = Phase.FAILED; beat(sim, "intervention_failed"); return;
        }
        // Demolition and its final impacts own departure; company movement owns only recovery.
        if (demolition.complete() && shotsFired() > 0) {
            ship.order(WarshipOrder.HoldFire.INSTANCE);
            if (sim.warships().shells().isEmpty() && sim.warships().strikes().isEmpty()) {
                patrolTick = tick;
                phase = Phase.PATROL;
                beat(sim, "stronghold_destroyed");
                ship.departForward(sim.getGrid().getWidth(), sim.getGrid().getHeight());
            }
            return;
        }
        int survivors = 0;
        outsideWithdrawal = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.MARINE) continue;
            survivors++;
            float x = sim.world().x(unit), y = sim.world().y(unit);
            if (Math.hypot(x - layout.strikeX(), y - layout.strikeY())
                    <= layout.strikeRadius() + ColdAccountLayout.BLAST_MARGIN) outsideWithdrawal++;
        }
        if (groundClearTick < 0 && survivors + sim.survivorPickup().survivingCount(Faction.MARINE) > 0
                && outsideWithdrawal == 0) groundClearTick = tick;
        // Arrive nose-first, then train during the company's withdrawal. Triggers stay closed.
        if (!ship.isMoving() && !batteriesPrepared) {
            ship.groundFire(Warship.GroundFire.HOLD);
            ship.order(strike);
            batteriesPrepared = true;
            beat(sim, "batteries_preparing");
        }
        if (preparedTick < 0 && batteriesPrepared && ship.headingSettled() && ship.readyHeavyMounts() >= 2)
            preparedTick = tick;
        boolean authorized = clearance.authorize(tick, evidence.targetSecured(),
                survivors + sim.survivorPickup().survivingCount(Faction.MARINE), outsideWithdrawal,
                batteriesPrepared && !ship.isMoving() && ship.headingSettled()
                        && (bombardTick >= 0 || ship.readyHeavyMounts() >= 2));
        if (clearance.warningTick() < 0) {
            ship.groundFire(Warship.GroundFire.HOLD);
            phase = Phase.WITHDRAW; return;
        }
        if (phase == Phase.WITHDRAW) {
            phase = Phase.WARNING; beat(sim, "company_clear_open_fire");
        }
        if (phase == Phase.WARNING && authorized) {
            if (bombardTick < 0) {
                bombardTick = tick;
                firingHeading = ship.facingDegrees;
            }
            phase = Phase.BOMBARD; beat(sim, "bombardment_authorized");
            ship.order(new WarshipOrder.FireOnArea(strike.x(), strike.y(), strike.radius(), false, true));
        }
        if (phase == Phase.BOMBARD) {
            ship.groundFire(tick - bombardTick < OPENING_SALVO_TICKS
                    ? Warship.GroundFire.HEAVY : Warship.GroundFire.ALL);
            if (tick - bombardTick >= OPENING_SALVO_TICKS) {
                if (sector != null && !sector.equals(ship.order())) ship.order(sector);
                beat(sim, "demolition_sweep");
            }
        }
    }
    private static Warship fitShip(float x, float y) {
        try { return Warship.fit("onslaught", "onslaught_Standard", Faction.ALLY, x, y, 90f); }
        catch (Exception e) { throw new IllegalStateException("Cannot fit the authored Hegemony Onslaught", e); }
    }
    static boolean hasPendingArrivals(BattleView sim) {
        if (sim.pendingGroundArrivals(Faction.MARINE) > 0) return true;
        for (long id : sim.getAirEntityIds()) {
            if (sim.world().airFaction(id) != Faction.MARINE) continue;
            var mission = sim.world().mission(id);
            if (mission == null || mission.destroyed || mission.pickup != null || mission.state == ShuttleState.GONE) continue;
            if ((mission.marinesRemaining > 0 && mission.state != ShuttleState.DEPARTING)
                    || mission.currentCycle + 1 < mission.totalCycles) return true;
        }
        return false;
    }
    @Override public Faction owningFaction() { return Faction.MARINE; }
    @Override public void tick(BattleView sim) { }
    @Override public boolean isComplete() { return phase == Phase.COMPLETE; }
    @Override public boolean isFailed() { return phase == Phase.FAILED; }
    @Override public String displayName() {
        return switch (phase) {
            case EVIDENCE -> "Secure the dispatch office and recover proof of Sable Wake's operations";
            case WITHDRAW -> "Clear the bombardment zone, then reach shuttle pickup (" + outsideWithdrawal
                    + " still in danger)";
            case WARNING -> "Hegemony batteries engaging";
            case BOMBARD -> "Demolition sector " + demolition.sectorNumber() + "/" + demolition.sectorCount()
                    + "; shuttle pickup / stronghold walls " + remainingWalls + "/" + walls.size();
            case PATROL -> "HSS Resolute resuming patrol" + (companyRecovered ? "" : "; reach shuttle pickup");
            case AFTERMATH -> "Stronghold silenced" + (companyRecovered ? "" : "; recover remaining survivors at shuttle pickup");
            case COMPLETE -> "Pirate evidence secured; surviving company extracted";
            case FAILED -> "Hegemony intervention failed to destroy the stronghold";
        };
    }
}
