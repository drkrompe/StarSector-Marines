package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.squad.CampaignSquadTag;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.FactionUnitRoster;
import com.dillon.starsectormarines.battle.unit.UnitType;

/** The existing marine-by-marine shuttle unload, expressed as a payload. */
public enum InfantryPayload implements AirDeliveryPayload {
    INSTANCE;

    @Override
    public int unitsPerSortie(ShuttleType carrier) {
        return carrier.capacity;
    }

    @Override
    public boolean tryDeploy(AirDeliveryContext context) {
        int[] cell = context.findOpenDeboardCell();
        if (cell == null) return false;
        ShuttleMission mission = context.mission;
        UnitType type = mission.deboardUnitType != null
                ? mission.deboardUnitType
                : FactionUnitRoster.forFaction(context.faction).infantry();
        EntitySpec marine = new EntitySpec(context.nextUnitName(), context.faction, type, cell[0], cell[1]);
        int slot = mission.deboardedThisSortie;
        MarineLoadout loadout = mission.marineLoadout != null && slot < mission.marineLoadout.length
                ? mission.marineLoadout[slot] : null;
        if (loadout != null) loadout.seedInto(marine);
        CampaignSquadTag tag = loadout != null ? loadout.campaignSquad : null;
        if (tag != null) {
            // Campaign personnel group by (campaign squad, LZ), not by sortie, so a
            // squad that needs three lifts lands as one unit. Writing it back onto
            // the mission keeps the shuttle's rear-overwatch hover following this
            // squad; AirSystem's per-cycle reset is harmless because the next
            // deboard resolves the same squad out of the index again.
            mission.squadId = context.squadForCampaign(type, tag);
        } else if (mission.squadId == Squad.NO_SQUAD) {
            mission.squadId = context.mintSquad(type);
            if (mission.rescueMilitiaTransport) {
                Squad guard = context.squad(mission.squadId);
                if (guard != null) {
                    guard.rescuePickupGuard = true;
                    guard.assignedObjective = ObjectiveAssignment.escort(
                            guard.id, mission.rescueGuardX,
                            mission.rescueGuardY);
                }
            }
            if (mission.garrisonNode != null) {
                Squad garrison = context.squad(mission.squadId);
                if (garrison != null) garrison.assignHoldNode(mission.garrisonNode);
            }
            if (mission.assignNode != null) {
                Squad squad = context.squad(mission.squadId);
                if (squad != null) squad.assignedNode = mission.assignNode;
            }
        }
        marine.squad(mission.squadId);
        Squad squad = context.squad(mission.squadId);
        if (squad != null) squad.originalSize++;
        long unit = context.spawn(marine);
        if (squad != null) {
            // The campaign NCO takes the billet outright; otherwise leadership
            // still falls to whoever landed first.
            if (tag != null && tag.leader) squad.leaderId = unit;
            else if (squad.leaderId == 0L) squad.leaderId = unit;
        }
        return true;
    }
}
