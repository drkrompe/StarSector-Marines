package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipOrder;
import java.util.ArrayList;
import java.util.List;

/** Mission-owned fire authorization. No enemy deletion or timed victory. */
public final class ColdAccountIntervention implements Objective {
    public enum Phase { EVIDENCE, WITHDRAW, WARNING, BOMBARD, COMPLETE, FAILED }
    public static final int WARNING_TICKS = 0;
    private final ColdAccountLayout layout;
    private final RaidObjective evidence;
    private final StoryBeatLog beats;
    private final List<int[]> walls = new ArrayList<>();
    private final WarshipOrder.FireOnArea strike;
    private Warship ship;
    private final BombardmentClearance clearance = new BombardmentClearance(WARNING_TICKS);
    private Phase phase = Phase.EVIDENCE;
    private int evidenceTick = -1, bombardTick = -1;
    private int remainingWalls, outsideWithdrawal;
    private boolean pendingArrivals;

    public ColdAccountIntervention(BattleSimulation sim, ColdAccountLayout layout, StoryBeatLog beats) {
        this.layout = layout; this.beats = beats;
        evidence = new RaidObjective("COLD-RECORDS", "pirate dispatch records", layout.evidenceX(),
                layout.evidenceY(), sim.getZoneGraph().zoneIdAt(layout.evidenceX(), layout.evidenceY()),
                layout.withdrawalX(), layout.withdrawalY());
        strike = new WarshipOrder.FireOnArea(layout.strikeX(), layout.strikeY(), layout.strikeRadius(), true, true);
        for (int y = 0; y < sim.getGrid().getHeight(); y++) for (int x = 0; x < sim.getGrid().getWidth(); x++)
            if (strike.contains(x + .5f, y + .5f) && sim.getTopology().isWall(x, y)) walls.add(new int[]{x,y});
        if (walls.isEmpty()) throw new IllegalStateException("Cold Account requires a destructible stronghold");
        remainingWalls = walls.size();
        sim.addObjective(evidence);
        sim.survivorPickup().configure(Faction.MARINE, layout.withdrawalX(), layout.withdrawalY());
    }
    public ColdAccountLayout layout() { return layout; }
    public Phase phase() { return phase; }
    public int evidenceTick() { return evidenceTick; }
    public int warningTick() { return clearance.warningTick(); }
    public int bombardTick() { return bombardTick; }
    public int initialWalls() { return walls.size(); }
    public int remainingWalls() { return remainingWalls; }
    public int outsideWithdrawal() { return outsideWithdrawal; }
    public boolean pendingArrivals() { return pendingArrivals; }
    public int shotsFired() { return ship == null ? 0 : ship.mounts().stream().filter(m -> !m.pointDefence()).mapToInt(m -> m.shotsFired).sum(); }
    private void beat(BattleSimulation sim, String key) {
        beats.emitOnce(sim.getSimTickIndex(), "corvus.cold_account." + key, StoryBeatLog.Audience.PLAYER);
    }
    /** Called before the weapons update so a hold order cancels even queued burst rounds. */
    public void advance(BattleSimulation sim) {
        if (isComplete() || isFailed()) return;
        if (!evidence.targetSecured()) return;
        int tick = sim.getSimTickIndex();
        if (ship == null) {
            evidenceTick = tick;
            beat(sim, "pirate_evidence_confirmed");
            float stationY = layout.strikeY();
            ship = fitShip(sim.getGrid().getWidth() + 30f, stationY);
            float stationX = layout.installationRight() + 1f
                    + (float) Math.hypot(ship.lengthCells, ship.widthCells) / 2f + 8f;
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
        if (ship.destroyed() || ship.departing() || ship.departed()
                || (ship.ordnanceRemaining() == 0 && (sim.warships().shells().isEmpty() && sim.warships().strikes().isEmpty())
                    && remainingWalls > walls.size() / 4)) {
            ship.order(WarshipOrder.HoldFire.INSTANCE);
            phase = Phase.FAILED; beat(sim, "intervention_failed"); return;
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
        pendingArrivals = hasPendingArrivals(sim);
        boolean authorized = clearance.authorize(tick, evidence.targetSecured(),
                survivors + sim.survivorPickup().survivingCount(Faction.MARINE), outsideWithdrawal,
                pendingArrivals, !ship.isMoving());
        if (clearance.warningTick() < 0) {
            ship.order(WarshipOrder.HoldFire.INSTANCE);
            phase = Phase.WITHDRAW; return;
        }
        if (remainingWalls <= walls.size() / 4 && shotsFired() > 0) {
            ship.order(WarshipOrder.HoldFire.INSTANCE);
            if ((sim.warships().shells().isEmpty() && sim.warships().strikes().isEmpty())
                    && sim.survivorPickup().complete(sim)) {
                phase = Phase.COMPLETE; beat(sim, "stronghold_destroyed");
            }
            return;
        }
        if (phase == Phase.WITHDRAW) {
            phase = Phase.WARNING; beat(sim, "company_clear_open_fire");
        }
        if (phase == Phase.WARNING && authorized) {
            if (bombardTick < 0) bombardTick = tick;
            phase = Phase.BOMBARD; beat(sim, "bombardment_authorized"); ship.order(strike);
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
            if (mission == null || mission.pickup != null || mission.state == ShuttleState.GONE) continue;
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
            case EVIDENCE -> "Secure pirate dispatch records to authorize Hegemony intervention";
            case WITHDRAW -> "Withdraw to the shuttle pickup; Onslaught holding fire (" + outsideWithdrawal
                    + " in firing area" + (pendingArrivals ? "; lift inbound" : "") + ")";
            case WARNING -> "Company clear of firing line: batteries engaging";
            case BOMBARD -> "Shuttle pickup / bombardment: stronghold walls " + remainingWalls + "/" + walls.size();
            case COMPLETE -> "Pirate evidence secured; company withdrawn; stronghold destroyed";
            case FAILED -> "Hegemony intervention failed to destroy the stronghold";
        };
    }
}
