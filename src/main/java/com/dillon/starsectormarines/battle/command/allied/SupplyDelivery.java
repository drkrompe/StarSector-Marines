package com.dillon.starsectormarines.battle.command.allied;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.TerrainCostField;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleRoutePlanner;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

/**
 * One delivery (`allied-tools.md`, The delivery hook): a truck sets out from an {@link AlliedDepot} and drives to a
 * named {@link Recipient}; its arrival resupplies the recipient through the recipient's own callback. A truck lost
 * on the road delivers nothing, and so does one whose recipient is gone before it arrives. Mission-neutral: what
 * "resupplied" means (a redoubt's guns back to their rate, rounds added to a barrage) is the recipient's.
 *
 * <p>A truck is set down facing along its way's first leg ({@link #facing}), the way its move order will drive.
 *
 * <p>While the depot cannot send, the delivery waits for it rather than setting out from somewhere else.
 *
 * <p>A delivery may be {@linkplain #escortedBy escorted}: the mission names the armed hull it has sent beside the
 * truck, and the truck keeps to it ({@link EscortPace}). It waits where it was set down until the escort is alongside,
 * the pair drives at the slower one's pace, and the truck holds when its escort falls behind. A truck the mission has
 * no escort for sets out alone, as an unescorted delivery does.
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

    /** The armed escort a mission has sent beside a delivery's truck: a hull, or a walker (a mech, a rifle team's leader). */
    public interface Escort {
        /** The hull or unit escorting {@code truck} now, or {@code 0} while none is. */
        long hull(BattleSimulation sim, long truck);
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
    private Escort escort;
    private EscortPace pace;

    public SupplyDelivery(Recipient recipient, AlliedDepot depot, VehicleType type) {
        this.recipient = recipient;
        this.depot = depot;
        this.type = type;
    }

    /** An ammunition truck's delivery, the usual kind. */
    public static SupplyDelivery ammunition(Recipient recipient, AlliedDepot depot) {
        return new SupplyDelivery(recipient, depot, VehicleType.SUPPLY_TRUCK);
    }

    /**
     * Has the truck keep to the escort the mission names ({@link EscortPace}). Set before the delivery sets out;
     * {@code null} sends it unescorted.
     */
    public SupplyDelivery escortedBy(Escort escort) {
        this.escort = escort;
        return this;
    }

    /** Cells along its way a truck is set down facing: the way's first point at least this far off. */
    static final float FACING_LEAD = 4f;

    /**
     * The heading a truck is set down on, in a hull's facing degrees: along its way's first leg, toward the way's first
     * point at least {@link #FACING_LEAD} cells off (its last point where none is that far), and toward its destination
     * where it has no way. Set down facing the destination as the crow flies, a truck at the edge of a street that runs
     * the other way had a bend from a standing start no trajectory could make, and stood there all battle.
     *
     * @param way the route's points ({@code way[0]} xs, {@code way[1]} ys), or {@code null}
     */
    public static float facing(float[][] way, float x, float y, float toX, float toY) {
        float aimX = toX, aimY = toY;
        if (way != null && way[0].length > 1) {
            int n = way[0].length;
            aimX = way[0][n - 1];
            aimY = way[1][n - 1];
            for (int i = 1; i < n; i++) {
                if (Math.hypot(way[0][i] - x, way[1][i] - y) >= FACING_LEAD) {
                    aimX = way[0][i];
                    aimY = way[1][i];
                    break;
                }
            }
        }
        return (float) Math.toDegrees(Math.atan2(aimY - y, aimX - x)) - 90f;
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
            int[] goal = VehicleRoutePlanner.snapToMask(mask, to[0], to[1], STAND_REACH);
            float[][] way = goal == null ? null : VehicleRoutePlanner.routeDrivable(at[0], at[1], goal[0], goal[1],
                    sim.getGrid(), TerrainCostField.from(sim.getTopology()), mask, type);
            float facing = facing(way, at[0] + .5f, at[1] + .5f, to[0] + .5f, to[1] + .5f);
            truck = command.deploy(sim, type, at[0], at[1], facing);
            if (truck == 0L) return state;
            state = State.ON_ROAD;
            if (escort == null) {
                command.station(sim, truck, to[0], to[1]);
            } else {
                pace = new EscortPace(sim.getSimTickIndex());
            }
        }
        if (pace != null && to != null && alive(sim, truck)) {
            boolean out = pace.setOut();
            pace.step(sim, truck, escort.hull(sim, truck));
            if (!out && pace.setOut()) command.station(sim, truck, to[0], to[1]);
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

    /** How the truck keeps to its escort, or {@code null} for an unescorted delivery or before it set out. */
    public EscortPace pace() { return pace; }
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
