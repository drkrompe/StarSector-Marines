package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.MechSupportPayload;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.evacuation.SurvivorPickupFleet.Carrier;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryReport;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryRow;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reuses every available deployment aircraft for capacity-limited survivor lifts. */
public final class SurvivorPickupSystem {
    private final SurvivorPickupFleet fleet = new SurvivorPickupFleet();
    private final List<PickupManifest> manifests = new ArrayList<>();
    private final Set<Long> observed = new HashSet<>();
    private Faction faction;
    private int x, y;
    private boolean requested;

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
        Carrier c = fleet.ramp(unit);
        if (c == null || sim.identity().faction(unit) != faction) return null;
        return new int[]{(int) c.pickup.lzX, (int) c.pickup.lzY};
    }
    public void tick(BattleSimulation sim) {
        if (faction == null) return;
        for (long id : sim.getAirEntityIds()) {
            ShuttleMission m = sim.world().mission(id);
            if (m == null || m.pickup != null || sim.world().airFaction(id) != faction || !observed.add(id)) continue;
            Airframe frame = sim.world().airframe(id);
            ShuttleType type = frame instanceof FittedBoat b ? b.pattern() : frame instanceof ShuttleType s ? s : null;
            if (type != null && m.seatsPerSortie > 0) fleet.register(new Carrier(m, type, frame, m.payload == MechSupportPayload.INSTANCE));
        }
        if (!requested) return;
        fleet.dispatch(groundCount(sim, false), groundCount(sim, true), c -> launch(sim, c));
        var waiting = new ArrayList<SurvivorPickupFleet.Waiting>();
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long u = sim.liveUnitAt(i);
            if (sim.identity().faction(u) == faction)
                waiting.add(new SurvivorPickupFleet.Waiting(u, sim.identity().type(u) == UnitType.HEAVY_MECH,
                        sim.world().x(u), sim.world().y(u)));
        }
        fleet.assignRamps(waiting);
        for (Carrier c : fleet.carriers()) board(sim, c);
    }

    private ShuttleMission launch(BattleSimulation sim, Carrier c) {
        var occupied = fleet.carriers().stream().filter(other -> other.pickup != null)
                .map(other -> new int[]{(int) other.pickup.lzX, (int) other.pickup.lzY}).toList();
        int[] pad = PickupLandingSlots.find(sim.getGrid(), x, y, occupied,
                (px, py) -> sim.getTopology().getBuildingId(px, py) == 0);
        if (pad == null) return null;
        long craft = sim.spawnShuttle(c.type, c.frame, faction, pad[0] + .5f, pad[1] + .5f,
                sim.getGrid().getWidth() + 20f, pad[1] + .5f,
                sim.getGrid().getWidth() + 20f, pad[1] + .5f, 0f, c.capacity());
        ShuttleMission pickup = sim.world().mission(craft);
        pickup.marinesRemaining = 0;
        pickup.awaitingEvacuees = true;
        pickup.pickup = new PickupManifest(c.capacity(), c.frame instanceof FittedBoat b && b.boatId() != null);
        manifests.add(pickup.pickup);
        return pickup;
    }

    private void board(BattleSimulation sim, Carrier c) {
        ShuttleMission active = c.pickup;
        if (active == null || active.state != ShuttleState.LANDED) return;
        int tick = sim.getSimTickIndex();
        if (c.landedTick < 0) c.landedTick = tick;
        if (c.readyToBoard(tick)) {
            long candidate = 0L;
            boolean candidateLeader = true;
            for (int i = 0; i < sim.liveUnitCount(); i++) {
                long u = sim.liveUnitAt(i);
                if (fleet.ramp(u) != c || Math.hypot(sim.world().x(u) - active.lzX,
                        sim.world().y(u) - active.lzY) > 2.5f) continue;
                boolean leader = sim.squadOf(u) != null && sim.squadOf(u).leaderId == u;
                if (candidate == 0L || (candidateLeader && !leader)) { candidate = u; candidateLeader = leader; }
            }
            if (candidate != 0L) {
                final long boarded = candidate;
                CombatTelemetryRow row = CombatTelemetryReport.gather(sim.getEntityWorld(), sim.getBattleComponents())
                        .stream().filter(r -> r.entityId() == boarded).findFirst().orElse(null);
                if (active.pickup.board(new PickupManifest.Passenger(boarded, faction, (String) sim.getEntityWorld().getObject(boarded, sim.getBattleComponents().IDENTITY, BattleComponents.IDENTITY_CAMPAIGN_SOLDIER_ID), row))) {
                    sim.takeOffTheField(boarded);
                    c.boarded(tick);
                }
            }
        }
        if (active.pickup.full() || groundCount(sim, c.mech) == 0 || tick - c.landedTick >= 90 * 30)
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
