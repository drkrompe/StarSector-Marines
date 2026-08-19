package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

/** A single operational heavy mech unloaded into its own commander-visible squad. */
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
                    guard.assignedObjective = ObjectiveAssignment.escort(
                            guard.id, context.mission.rescueGuardX,
                            context.mission.rescueGuardY);
                }
            }
        }
        MechVariant variant = context.mission.mechVariant != null
                ? context.mission.mechVariant : MechVariant.BULWARK;
        EntitySpec spec = new EntitySpec("support-" + context.nextUnitName(), context.faction,
                UnitType.HEAVY_MECH, cell[0], cell[1])
                .mechVariant(variant)
                .role(context.mission.rescuePickupMechTransport
                        ? UnitRole.GARRISON : UnitRole.COMBATANT)
                .squad(context.mission.squadId);
        long mech = context.spawn(spec);
        context.attachMechLoadout(mech,
                variant.createLoadout(variant.defaultRole));
        Squad squad = context.squad(context.mission.squadId);
        if (squad != null) {
            squad.leaderId = mech;
            squad.originalSize++;
        }
        return true;
    }
}
