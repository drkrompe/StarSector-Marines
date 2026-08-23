package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechVariant;
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
        int[] cell = context.findOpenDeboardCell();
        if (cell == null) return false;
        if (context.mission.squadId == Squad.NO_SQUAD) {
            context.mission.squadId = context.mintSquad(UnitType.HEAVY_MECH);
            if (context.mission.rescuePickupMechTransport) {
                Squad guard = context.squad(context.mission.squadId);
                if (guard != null) {
                    guard.rescuePickupGuard = true;
                    guard.rescuePickupMech = true;
                    guard.rescuePatrolCells = context.mission.rescuePatrolCells != null
                            ? context.mission.rescuePatrolCells.clone() : null;
                    guard.rescuePatrolIndex = -1;
                    guard.assignedObjective = ObjectiveAssignment.escort(
                            guard.id, context.mission.rescueGuardX,
                            context.mission.rescueGuardY);
                }
            }
        }
        MechDeploymentSpec deployment = deploymentForDeboard(context.mission);
        MechVariant variant = deployment.variant();
        EntitySpec spec = new EntitySpec("support-" + context.nextUnitName(), context.faction,
                UnitType.HEAVY_MECH, cell[0], cell[1])
                .mechVariant(variant)
                .role(context.mission.rescuePickupMechTransport
                        ? UnitRole.GARRISON : UnitRole.COMBATANT)
                .squad(context.mission.squadId);
        long mech = context.spawn(spec);
        MechLoadoutComponent loadout = variant.createLoadout(deployment.role());
        loadout.installMissileReplenisher(deployment.missileReplenisher());
        context.attachMechLoadout(mech, loadout);
        Squad squad = context.squad(context.mission.squadId);
        if (squad != null) {
            if (squad.leaderId == 0L) squad.leaderId = mech;
            squad.originalSize++;
        }
        return true;
    }

    private static MechVariant variantForDeboard(ShuttleMission mission) {
        if (mission.mechVariants != null && mission.mechVariants.length > 0) {
            int index = Math.min(mission.deboardedThisSortie,
                    mission.mechVariants.length - 1);
            MechVariant variant = mission.mechVariants[index];
            if (variant != null) return variant;
        }
        return mission.mechVariant != null ? mission.mechVariant : MechVariant.BULWARK;
    }

    private static MechDeploymentSpec deploymentForDeboard(ShuttleMission mission) {
        if (mission.mechDeployments != null && mission.mechDeployments.length > 0) {
            int index = Math.min(mission.deboardedThisSortie,
                    mission.mechDeployments.length - 1);
            MechDeploymentSpec deployment = mission.mechDeployments[index];
            if (deployment != null) return deployment;
        }
        return MechDeploymentSpec.standard(variantForDeboard(mission));
    }
}
