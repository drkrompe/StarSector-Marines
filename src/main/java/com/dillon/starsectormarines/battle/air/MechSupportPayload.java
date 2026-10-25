package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechSpawnPlacement;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

/** One or more operational mechs unloaded into one commander-visible squad. */
public enum MechSupportPayload implements AirDeliveryPayload {
    INSTANCE;

    @Override
    public int unitsPerSortie(ShuttleType carrier) {
        return 1;
    }

    @Override
    public boolean tryDeploy(AirDeliveryContext context) {
        return deploy(context, deploymentForDeboard(context.mission));
    }

    /**
     * Sets {@code deployment} down into the sortie's one mech squad, minting it on the first
     * machine. Shared with {@link ArmorDropPayload}, whose mixed drop unloads mechs the same way.
     */
    static boolean deploy(AirDeliveryContext context, MechDeploymentSpec deployment) {
        MechVariant variant = deployment.variant();
        MechSpawnPlacement.Point point = context.findOpenMechPosition(variant.radius);
        if (point == null) return false;
        if (context.mission.squadId == Squad.NO_SQUAD) {
            context.mission.squadId = context.mintSquad(UnitType.HEAVY_MECH);
            context.claimSquadCommand(context.mission.squadId,
                    context.mission.commandClaim);
            if (context.mission.rescuePickupMechTransport) {
                Squad guard = context.squad(context.mission.squadId);
                if (guard != null) {
                    guard.rescuePickupGuard = true;
                    guard.rescuePickupMech = true;
                    guard.rescuePatrolCells = context.mission.rescuePatrolCells != null
                            ? context.mission.rescuePatrolCells.clone() : null;
                    guard.rescuePatrolIndex = -1;
                    context.assignSquadCommand(ObjectiveAssignment.escort(
                                    guard.id, context.mission.rescueGuardX,
                                    context.mission.rescueGuardY),
                            CommandAuthority.PAYLOAD, "rescue-pickup-support",
                            "born rescue pickup mech guard");
                }
            }
        }
        String name = landedName(deployment, "support-" + context.nextUnitName());
        EntitySpec spec = new EntitySpec(name, context.faction,
                UnitType.HEAVY_MECH, point.cellX(), point.cellY())
                .atPosition(point.x(), point.y())
                .role(context.mission.rescuePickupMechTransport
                        ? UnitRole.GARRISON : UnitRole.COMBATANT)
                .squad(context.mission.squadId);
        deployment.applyTo(spec);
        long mech = context.spawn(spec);
        MechLoadoutComponent loadout = deployment.createLoadout(deployment.role());
        deployment.applyTo(loadout);
        loadout.installMissileReplenisher(deployment.missileReplenisher());
        context.attachMechLoadout(mech, loadout);
        context.carryMechWear(mech, loadout, deployment.wear());
        context.tagCompanyMech(deployment.campaignId(), loadout);
        Squad squad = context.squad(context.mission.squadId);
        if (squad != null) {
            if (squad.leaderId == 0L) squad.leaderId = mech;
            squad.originalSize++;
        }
        return true;
    }

    /**
     * The landed mech's visible name: the callsign frozen into its deployment, or
     * {@code fallback} for a debug, legacy or unnamed deployment. The fallback is always drawn
     * by the caller, so naming a mech never shifts the unit-name sequence of later actors.
     */
    static String landedName(MechDeploymentSpec deployment, String fallback) {
        String name = deployment.displayName();
        return name == null || name.isBlank() ? fallback : name;
    }

    /**
     * Which machine of the lance leaves next. A carrier that flies several
     * sorties is a cradled boat lifting one machine per trip, so the trip
     * number counts too; a single sortie unloads the lance in order.
     */
    static int machineIndex(ShuttleMission mission) {
        return mission.currentCycle + mission.deboardedThisSortie;
    }

    private static MechVariant variantForDeboard(ShuttleMission mission) {
        if (mission.mechVariants != null && mission.mechVariants.length > 0) {
            int index = Math.min(machineIndex(mission),
                    mission.mechVariants.length - 1);
            MechVariant variant = mission.mechVariants[index];
            if (variant != null) return variant;
        }
        return mission.mechVariant != null ? mission.mechVariant : MechVariant.BULWARK;
    }

    private static MechDeploymentSpec deploymentForDeboard(ShuttleMission mission) {
        if (mission.mechDeployments != null && mission.mechDeployments.length > 0) {
            int index = Math.min(machineIndex(mission),
                    mission.mechDeployments.length - 1);
            MechDeploymentSpec deployment = mission.mechDeployments[index];
            if (deployment != null) return deployment;
        }
        return MechDeploymentSpec.standard(variantForDeboard(mission));
    }
}
