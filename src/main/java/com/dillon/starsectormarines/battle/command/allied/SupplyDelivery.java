package com.dillon.starsectormarines.battle.command.allied;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleRoutePlanner;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

/**
 * One delivery (`allied-tools.md`, The delivery hook): a truck sets out from an {@link AlliedDepot} and drives to a
 * named {@link Recipient}; its arrival resupplies the recipient through the recipient's own callback. A truck lost
 * on the road delivers nothing, and so does one whose recipient is gone before it arrives. Mission-neutral: what
 * "resupplied" means (a redoubt's guns back to their rate, rounds added to a barrage) is the recipient's.
 *
 * <p>While the depot cannot send, the delivery waits for it rather than setting out from somewhere else.
 */
public final class SupplyDelivery {

    /** Cells from the recipient's cell, on either axis, a truck has arrived at by default. */
    public static final int ARRIVE = 8;
    /** Cells round the depot's cell a truck may be set down on ground its footprint fits. */
    static final int STAND_REACH = 6;

    /** Who a delivery is for. */
    public interface Recipient {

        /** Named in briefings, beats and reviews. */
        String name();

        /** The cell the truck drives to, or {@code null} once the recipient is gone (lost, overrun, spent). */
        int[] where(BattleSimulation sim);

        /** Whether a truck standing at {@code (x, y)} has arrived; by default within {@link #ARRIVE} of {@link #where}. */
        default boolean reached(BattleSimulation sim, float x, float y) {
            int[] at = where(sim);
            return at != null && Math.abs(x - at[0]) <= ARRIVE && Math.abs(y - at[1]) <= ARRIVE;
        }

        /** The truck arrived: resupply. Called once. */
        void arrived(BattleSimulation sim);
    }

    /** Where a delivery stands. */
    public enum State {
        /** Not yet set out: the depot cannot send, or there was no ground to set the truck down on. */
        WAITING,
        ON_ROAD,
        DELIVERED,
        /** The truck was destroyed, or its recipient went before it arrived. */
        LOST;

        public boolean done() { return this == DELIVERED || this == LOST; }
    }

    private final Recipient recipient;
    private final AlliedDepot depot;
    private final VehicleType type;
    private long truck;
    private State state = State.WAITING;

    public SupplyDelivery(Recipient recipient, AlliedDepot depot, VehicleType type) {
        this.recipient = recipient;
        this.depot = depot;
        this.type = type;
    }

    /** An ammunition truck's delivery, the usual kind. */
    public static SupplyDelivery ammunition(Recipient recipient, AlliedDepot depot) {
        return new SupplyDelivery(recipient, depot, VehicleType.SUPPLY_TRUCK);
    }

    /** The rule: arrival delivers; a dead truck or a gone recipient before that loses it. */
    static State rule(State state, boolean truckAlive, boolean recipientThere, boolean arrived) {
        if (state.done() || state == State.WAITING) return state;
        if (arrived && truckAlive && recipientThere) return State.DELIVERED;
        if (!truckAlive || !recipientThere) return State.LOST;
        return State.ON_ROAD;
    }

    /**
     * One tick: sets the truck out once the depot can send, drives it under {@code command}, and on arrival calls the
     * recipient and takes the truck off the field.
     */
    public State advance(BattleSimulation sim, AlliedCommand command) {
        if (state.done()) return state;
        int[] to = recipient.where(sim);
        if (state == State.WAITING) {
            if (to == null) return state = State.LOST;
            int[] from = depot.dispatchCell(sim);
            if (from == null) return state;
            VehicleClearance mask = VehicleClearance.erode(sim.getGrid(), VehicleClearance.radiusForWidth(type.visualWidthCells));
            int[] at = VehicleRoutePlanner.snapToMask(mask, from[0], from[1], STAND_REACH);
            if (at == null) return state;
            float facing = (float) Math.toDegrees(Math.atan2(to[1] - at[1], to[0] - at[0])) - 90f;
            truck = command.deploy(sim, type, at[0], at[1], facing);
            if (truck == 0L) return state;
            command.station(sim, truck, to[0], to[1]);
            state = State.ON_ROAD;
        }
        boolean alive = alive(sim, truck);
        boolean arrived = false;
        if (alive && to != null) {
            GroundBody body = sim.convoy().body(truck);
            arrived = recipient.reached(sim, body.x, body.y);
        }
        state = rule(state, alive, to != null, arrived);
        if (state == State.DELIVERED) {
            recipient.arrived(sim);
            sim.convoy().despawn(truck);
        }
        return state;
    }

    public State state() { return state; }
    public Recipient recipient() { return recipient; }
    /** The truck, once set out; {@code 0} before. */
    public long truck() { return truck; }

    /** Where the truck is now, or {@code null} when it is not on the road. */
    public int[] truckCell(BattleSimulation sim) {
        if (state != State.ON_ROAD || !alive(sim, truck)) return null;
        GroundBody body = sim.convoy().body(truck);
        return new int[]{(int) body.x, (int) body.y};
    }

    private static boolean alive(BattleSimulation sim, long hull) {
        return hull != 0L && sim.convoy().isVehicle(hull) && sim.convoy().structure(hull) > 0f;
    }
}
