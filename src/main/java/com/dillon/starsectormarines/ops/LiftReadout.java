package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.LiftAdmission;
import com.dillon.starsectormarines.battle.air.LiftClass;
import com.dillon.starsectormarines.battle.air.LiftRating;
import com.dillon.starsectormarines.battle.air.VehicleSupportPayload;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.power.VehicleSupport;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.marine.CampaignVehicle;
import com.dillon.starsectormarines.i18n.Strings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The briefing's account of who lifts a drop, and why a machine cannot be
 * lifted by the company's own boat.
 *
 * <p>Reads the power as resolved; it decides nothing. The decision is
 * {@code PowerCatalog.assignCompanyLift} and, per call, the power itself, both
 * asking {@link LiftAdmission}.
 */
public final class LiftReadout {

    private LiftReadout() { }

    /** Lines for a drop power's hover sheet; empty for any other power. */
    public static List<String> lines(CommandPower power) {
        Map<String, LiftClass> payload = payloadOf(power);
        if (payload.isEmpty()) return List.of();
        FittedBoat carrier = power instanceof MechSupport mech ? mech.companyCarrier()
                : power instanceof VehicleSupport vehicles ? vehicles.companyCarrier() : null;
        if (carrier == null) return List.of();
        boolean shipLift = power instanceof MechSupport mech ? mech.syntheticLift()
                : ((VehicleSupport) power).syntheticLift();
        List<String> lines = new ArrayList<>();
        lines.add(Strings.format("liftCarrierCompany", carrierName(carrier),
                ratingName(carrier.liftRating())));
        for (Map.Entry<String, LiftClass> machine : payload.entrySet()) {
            LiftAdmission admission = LiftAdmission.check(machine.getValue(), carrier);
            if (admission.admitted()) continue;
            lines.add(refusal(machine.getKey(), admission, shipLift));
        }
        return List.copyOf(lines);
    }

    /** Why one machine is not lifted by the company carrier, and who lifts it instead. */
    static String refusal(String machine, LiftAdmission admission, boolean shipLift) {
        String needed = ratingName(LiftRating.of(admission.payload()));
        if (shipLift) {
            return Strings.format("liftRefusedShipLift", machine, needed,
                    ratingName(admission.rating()));
        }
        return admission.refusal() == LiftAdmission.Refusal.NO_LIFT
                ? Strings.format("liftRefusedNoLift", machine, needed)
                : Strings.format("liftRefusedUnderRated", machine, needed,
                ratingName(admission.rating()));
    }

    /** A boat as the briefing names it: pattern and hold. */
    public static String carrierName(FittedBoat boat) {
        return boat.pattern().displayName() + " / " + boat.lift().displayName();
    }

    /** A lift rating as the deck and the briefing name it. */
    public static String ratingName(LiftRating rating) {
        return Strings.get("liftRating" + (rating == null ? LiftRating.NONE : rating).name());
    }

    /** One line for the landing-craft card: how many boats fly machines only. */
    public static String cradledNote(List<FittedBoat> boats) {
        int cradled = 0;
        LiftRating best = LiftRating.NONE;
        for (FittedBoat boat : boats) {
            if (boat == null || !boat.machineOnly()) continue;
            cradled++;
            best = LiftRating.max(best, boat.liftRating());
        }
        return cradled == 0 ? null : Strings.format("liftCradledBoats", cradled, ratingName(best));
    }

    private static Map<String, LiftClass> payloadOf(CommandPower power) {
        Map<String, LiftClass> payload = new LinkedHashMap<>();
        if (power instanceof MechSupport mech) {
            for (MechDeploymentSpec deployment : mech.deployments()) {
                payload.putIfAbsent(deployment.variant().displayName(),
                        deployment.variant().liftClass());
            }
            for (VehicleDeploymentSpec deployment : mech.vehicles()) {
                payload.putIfAbsent(CampaignVehicle.typeName(deployment.type()),
                        deployment.type().liftClass());
            }
        } else if (power instanceof VehicleSupport vehicles) {
            if (vehicles.deployments().isEmpty()) {
                payload.put(CampaignVehicle.typeName(VehicleSupportPayload.FALLBACK),
                        VehicleSupportPayload.FALLBACK.liftClass());
            }
            for (VehicleDeploymentSpec deployment : vehicles.deployments()) {
                payload.putIfAbsent(CampaignVehicle.typeName(deployment.type()),
                        deployment.type().liftClass());
            }
        }
        return payload;
    }
}
