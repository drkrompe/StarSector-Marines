package com.dillon.starsectormarines.battle.command.allied;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.allied.AlliedToolShed.Maker;
import com.dillon.starsectormarines.battle.mech.FactionMechLoadouts;
import com.dillon.starsectormarines.battle.mech.MechSpawnPlacement;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleRoutePlanner;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The makers most missions share, for {@link AlliedToolShed}: an armour pair driven to its place, a mech lance
 * walked to it, an ammunition truck as a {@link SupplyDelivery}. Each is set down on ground near the depot's cell and
 * handed to the mission's {@link AlliedCommand}; it counts as on the road until it is within {@link #ARRIVE} cells of
 * the request's cell or gone. What it does once there stays the mission's command's. An ammunition truck may keep to
 * an escort the mission names ({@link SupplyDelivery#escortedBy}).
 */
public final class AlliedMakers {

    private AlliedMakers() { }

    /** Cells from the request's cell a sent machine has arrived. */
    public static final int ARRIVE = 10;
    /** Cells round the depot's cell a machine may be set down. */
    static final int STAND_REACH = 8;
    /** Cells between machines of one dispatch, set down and stationed side by side. */
    static final int SPACING = 5;

    /** {@code count} hulls of {@code type}, stationed side by side at the request's cell. */
    public static Maker armour(AlliedCommand command, VehicleType type, int count) {
        return (sim, from, request) -> {
            VehicleClearance mask = sim.convoy().clearances().of(sim.getGrid(), type);
            List<Long> hulls = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int[] at = VehicleRoutePlanner.snapToMask(mask, from[0] + across(i, count), from[1], STAND_REACH);
                if (at == null || crowded(sim, at)) continue;
                long hull = command.deploy(sim, type, at[0], at[1], facing(at, request));
                if (hull == 0L) continue;
                command.station(sim, hull, request.x() + across(i, count), request.y());
                hulls.add(hull);
            }
            if (hulls.isEmpty()) return null;
            return s -> {
                for (long hull : hulls) {
                    if (!hullAlive(s, hull)) continue;
                    GroundBody body = s.convoy().body(hull);
                    if (Math.abs(body.x - request.x()) > ARRIVE || Math.abs(body.y - request.y()) > ARRIVE) return false;
                }
                return true;
            };
        };
    }

    /** {@code count} Hounds, each its own squad, walked to the request's cell. */
    public static Maker mechs(AlliedCommand command, String issuer, int count) {
        return (sim, from, request) -> {
            List<Long> mechs = new ArrayList<>();
            List<float[]> placed = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                MechSpawnPlacement.Point at = standable(sim, from[0] + across(i, count), from[1], placed);
                if (at == null) continue;
                placed.add(new float[]{at.x(), at.y()});
                int squad = sim.mintSquad(Faction.ALLY, UnitType.HEAVY_MECH);
                long mech = sim.spawn(new EntitySpec(request.toPlace() + " walker " + (i + 1), Faction.ALLY,
                        UnitType.HEAVY_MECH, at.cellX(), at.cellY()).atPosition(at.x(), at.y())
                        .mechVariant(MechVariant.HOUND).squad(squad));
                sim.world().attachMechLoadout(mech,
                        FactionMechLoadouts.create(MechVariant.HOUND, MechVariant.HOUND.defaultRole, "independent"));
                Squad s = sim.getSquad(squad);
                s.leaderId = mech;
                s.originalSize = 1;
                sim.claimSquadCommand(squad, CommandAuthority.GARRISON, issuer, request.toPlace() + " walker");
                command.stationMech(sim, mech, request.x() + across(i, count), request.y());
                mechs.add(mech);
            }
            if (mechs.isEmpty()) return null;
            return s -> {
                for (long mech : mechs) {
                    if (s.resolveUnit(mech) == 0L || !s.world().isAlive(mech)) continue;
                    if (Math.abs(s.world().x(mech) - request.x()) > ARRIVE
                            || Math.abs(s.world().y(mech) - request.y()) > ARRIVE) return false;
                }
                return true;
            };
        };
    }

    /**
     * An ammunition truck to the recipient the mission names for the request; on the road until it is delivered or
     * lost. The delivery is advanced here, from the shed's {@link AlliedToolShed#advance}, so nothing else need hold it.
     */
    public static Maker ammunition(AlliedCommand command, Function<AlliedRequest, SupplyDelivery.Recipient> recipient) {
        return ammunition(command, recipient, delivery -> { });
    }

    /**
     * As {@link #ammunition(AlliedCommand, Function)}, telling {@code sent} of each delivery that set out, so the mission
     * can follow its truck (escort it, report it, read whether it came).
     */
    public static Maker ammunition(AlliedCommand command, Function<AlliedRequest, SupplyDelivery.Recipient> recipient,
                                   Consumer<SupplyDelivery> sent) {
        return ammunition(command, recipient, null, sent);
    }

    /**
     * As {@link #ammunition(AlliedCommand, Function, Consumer)}, each truck keeping to the escort the mission names
     * ({@link SupplyDelivery#escortedBy}); {@code null} sends them unescorted.
     */
    public static Maker ammunition(AlliedCommand command, Function<AlliedRequest, SupplyDelivery.Recipient> recipient,
                                   SupplyDelivery.Escort escort, Consumer<SupplyDelivery> sent) {
        return (sim, from, request) -> {
            SupplyDelivery.Recipient to = recipient.apply(request);
            if (to == null) return null;
            SupplyDelivery delivery = SupplyDelivery.ammunition(to, s -> from).escortedBy(escort);
            if (delivery.advance(sim, command) == SupplyDelivery.State.WAITING) return null;
            sent.accept(delivery);
            return s -> delivery.advance(s, command).done();
        };
    }

    private static int across(int i, int count) {
        return Math.round((i - (count - 1) / 2f) * SPACING);
    }

    private static float facing(int[] at, AlliedRequest request) {
        return (float) Math.toDegrees(Math.atan2(request.y() - at[1], request.x() - at[0])) - 90f;
    }

    private static boolean hullAlive(BattleSimulation sim, long hull) {
        return sim.convoy().isVehicle(hull) && sim.convoy().structure(hull) > 0f;
    }

    /** Whether another hull stands too near this cell to set one down on it. */
    private static boolean crowded(BattleSimulation sim, int[] at) {
        for (long other : sim.convoy().entityIds()) {
            if (sim.convoy().structure(other) <= 0f) continue;
            if (sim.convoy().body(other).distanceTo(at[0] + .5f, at[1] + .5f) < SPACING - 1) return true;
        }
        return false;
    }

    private static MechSpawnPlacement.Point standable(BattleSimulation sim, int x, int y, List<float[]> placed) {
        for (int r = 0; r <= STAND_REACH; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    MechSpawnPlacement.Point at = MechSpawnPlacement.nearCell(sim.getGrid(), MechVariant.HOUND.radius,
                            x + dx, y + dy, (px, py) -> true, (px, py) -> clear(placed, px, py));
                    if (at != null) return at;
                }
            }
        }
        return null;
    }

    private static boolean clear(List<float[]> placed, float x, float y) {
        for (float[] p : placed) if (Math.hypot(p[0] - x, p[1] - y) < SPACING - 1) return false;
        return true;
    }
}
