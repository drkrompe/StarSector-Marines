package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.MechSupportPayload;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryReport;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryRow;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reuses the deployment aircraft for serial, capacity-limited survivor lifts. */
public final class SurvivorPickupSystem {
    private record Carrier(long originalId, ShuttleMission delivery, ShuttleType type, Airframe frame, boolean mech) { }
    private final List<Carrier> carriers = new ArrayList<>();
    private final List<PickupManifest> manifests = new ArrayList<>();
    private final Set<Long> observed = new HashSet<>();
    private Faction faction;
    private int x, y, nextBoardTick, nextCarrier;
    private boolean requested;
    private ShuttleMission active;
    private Carrier activeCarrier;
    private int landedTick = -1;

    /** Configure before deployment so the actual fitted aircraft can be retained. */
    public void configure(Faction faction, int x, int y) {
        if (this.faction != null) throw new IllegalStateException("Pickup already configured");
        this.faction = faction; this.x = x; this.y = y;
    }
    public void request() { requested = true; }
    public List<PickupManifest> manifests() { return List.copyOf(manifests); }
    public int survivingCount(Faction faction) {
        return manifests.stream().filter(m -> m.state() != PickupManifest.State.LOST)
                .mapToInt(m -> (int) m.passengers().stream().filter(p -> p.faction() == faction).count()).sum();
    }
    public int recoveredCount() {
        return manifests.stream().filter(m -> m.state() == PickupManifest.State.RECOVERED).mapToInt(m -> m.passengers().size()).sum();
    }
    public boolean surviving(long id) {
        return manifests.stream().filter(m -> m.state() != PickupManifest.State.LOST)
                .flatMap(m -> m.passengers().stream()).anyMatch(p -> p.entityId() == id);
    }
    public boolean complete(BattleView sim) {
        return requested && groundCount(sim, false) + groundCount(sim, true) == 0
                && manifests.stream().noneMatch(m -> m.state() == PickupManifest.State.ABOARD);
    }
    public int[] rampFor(long unit, BattleView sim) {
        if (active == null || active.state != ShuttleState.LANDED || !active.awaitingEvacuees
                || sim.identity().faction(unit) != faction
                || (sim.identity().type(unit) == UnitType.HEAVY_MECH) != activeCarrier.mech()) return null;
        return new int[]{x, y};
    }
    public void tick(BattleSimulation sim) {
        if (faction == null) return;
        for (long id : sim.getAirEntityIds()) {
            ShuttleMission m = sim.world().mission(id);
            if (m == null || m.pickup != null || sim.world().airFaction(id) != faction || !observed.add(id)) continue;
            Airframe frame = sim.world().airframe(id);
            ShuttleType type = frame instanceof FittedBoat b ? b.pattern() : frame instanceof ShuttleType s ? s : null;
            if (type != null && m.seatsPerSortie > 0) carriers.add(new Carrier(id, m, type, frame, m.payload == MechSupportPayload.INSTANCE));
        }
        if (!requested) return;
        if (active != null && active.pickup.state() != PickupManifest.State.ABOARD) {
            if (active.pickup.state() == PickupManifest.State.LOST) carriers.remove(activeCarrier);
            active = null; landedTick = -1;
        }
        if (active == null) {
            for (int i = 0; i < carriers.size(); i++) {
                Carrier c = carriers.get((nextCarrier + i) % carriers.size());
                if (c.delivery().state != ShuttleState.GONE || groundCount(sim, c.mech()) == 0) continue;
                if (sim.getAirLosses().stream().anyMatch(l -> l.frame() == c.frame())) continue;
                long craft = sim.spawnShuttle(c.type(), c.frame(), faction, x + .5f, y + .5f,
                        sim.getGrid().getWidth() + 20f, y + .5f, sim.getGrid().getWidth() + 20f, y + .5f, 0f, c.mech() ? 1 : c.type().capacity);
                active = sim.world().mission(craft);
                active.marinesRemaining = 0;
                active.awaitingEvacuees = true;
                active.pickup = new PickupManifest(c.mech() ? 1 : c.type().capacity, c.frame() instanceof FittedBoat b && b.boatId() != null);
                manifests.add(active.pickup);
                activeCarrier = c;
                nextCarrier = (nextCarrier + i + 1) % carriers.size();
                break;
            }
        }
        if (active == null || active.state != ShuttleState.LANDED) return;
        int tick = sim.getSimTickIndex();
        if (landedTick < 0) landedTick = tick;
        if (tick >= nextBoardTick && !active.pickup.full()) {
            long candidate = 0L;
            boolean candidateLeader = true;
            for (int i = 0; i < sim.liveUnitCount(); i++) {
                long u = sim.liveUnitAt(i);
                if (rampFor(u, sim) == null || Math.hypot(sim.world().x(u) - x - .5f, sim.world().y(u) - y - .5f) > 2.5f) continue;
                boolean leader = sim.squadOf(u) != null && sim.squadOf(u).leaderId == u;
                if (candidate == 0L || (candidateLeader && !leader)) { candidate = u; candidateLeader = leader; }
            }
            if (candidate != 0L) {
                final long boarded = candidate;
                CombatTelemetryRow row = CombatTelemetryReport.gather(sim.getEntityWorld(), sim.getBattleComponents())
                        .stream().filter(r -> r.entityId() == boarded).findFirst().orElse(null);
                if (active.pickup.board(new PickupManifest.Passenger(boarded, faction, (String) sim.getEntityWorld().getObject(boarded, sim.getBattleComponents().IDENTITY, BattleComponents.IDENTITY_CAMPAIGN_SOLDIER_ID), row))) {
                    sim.takeOffTheField(boarded);
                    nextBoardTick = tick + 24;
                }
            }
        }
        if (active.pickup.full() || groundCount(sim, activeCarrier.mech()) == 0 || tick - landedTick >= 90 * 30)
            active.awaitingEvacuees = false;
    }
    private int groundCount(BattleView sim, boolean mech) {
        int n = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long u = sim.liveUnitAt(i);
            if (sim.identity().faction(u) == faction && (sim.identity().type(u) == UnitType.HEAVY_MECH) == mech) n++;
        }
        return n;
    }
}
