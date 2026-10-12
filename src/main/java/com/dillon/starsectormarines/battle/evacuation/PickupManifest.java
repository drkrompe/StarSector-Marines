package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.sim.CombatTelemetryRow;
import com.dillon.starsectormarines.battle.unit.Faction;
import java.util.ArrayList;
import java.util.List;

/** Passengers remain aboard until their actual aircraft returns or is destroyed. */
public final class PickupManifest {
    public enum State { ABOARD, RECOVERED, LOST }
    public record Passenger(long entityId, Faction faction, String soldierId, CombatTelemetryRow telemetry) { }
    private final int capacity;
    private final boolean ownedBoat;
    private final List<Passenger> passengers = new ArrayList<>();
    private State state = State.ABOARD;
    public PickupManifest(int capacity) { this(capacity, false); }
    public PickupManifest(int capacity, boolean ownedBoat) {
        if (capacity <= 0) throw new IllegalArgumentException("Positive lift capacity required");
        this.capacity = capacity;
        this.ownedBoat = ownedBoat;
    }
    public boolean board(Passenger passenger) {
        if (state != State.ABOARD || full() || passengers.stream().anyMatch(p -> p.entityId() == passenger.entityId())) return false;
        passengers.add(passenger);
        return true;
    }
    public boolean lossInOwnedBoatLedger() { return ownedBoat && state == State.LOST; }
    public boolean full() { return passengers.size() >= capacity; }
    public List<Passenger> passengers() { return List.copyOf(passengers); }
    public State state() { return state; }
    public void recover() { if (state == State.ABOARD) state = State.RECOVERED; }
    public void lose() { if (state == State.ABOARD) state = State.LOST; }
}
