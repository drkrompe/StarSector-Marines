package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.LiftClass;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.air.VehicleSupportPayload;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

import java.util.List;

/**
 * Calls a shootable heavy transport that sets an armoured chassis down on the
 * field for the player to command.
 *
 * <p>The delivery is the ordinary one — the carrier flies in, is exposed on the
 * way, and can be shot down with the vehicle still aboard. What is different is
 * what it leaves behind: not a squad that takes objectives on its own, but a
 * vehicle holding position until it is given somewhere to go.
 *
 * <p>Clearance five, like the mech it shares a carrier with, because an APC's
 * footprint needs somewhere to stand rather than somewhere to stand up.
 *
 * <p>Like Mech Support, a committed cradled company boat that lifts the
 * vehicle flies the call instead of the synthetic Valkyrie.
 */
public final class VehicleSupport extends AirDeliveryPower {

    public static final String ID = "vehicle_support";

    /** The vehicle every call sets down. */
    public static final VehicleType DELIVERED = VehicleType.HEAVY_APC;

    public VehicleSupport() {
        this(null, true);
    }

    /**
     * @param companyCarrier a committed cradled company boat that may fly the
     *     calls, or {@code null}
     * @param syntheticLift whether a mission, employer or hull also supplies a
     *     heavy carrier for calls the boat cannot fly
     */
    public VehicleSupport(FittedBoat companyCarrier, boolean syntheticLift) {
        super(ID, "Armour Support", 3f, 2, 0, 2,
                ShuttleType.VALKYRIE, VehicleSupportPayload.INSTANCE, 5,
                companyCarrier, syntheticLift);
    }

    @Override
    protected List<LiftClass> nextLoad(CommandPowerService service) {
        return List.of(DELIVERED.liftClass());
    }
}
