package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.infantry.SquadSupportKit;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Issues the mission's declared support kit to the marine squads a named roster has just been applied to. A named company
 * replaces every seat's equipment with what its soldiers were issued, so the kit a factory force rolls (see
 * {@link InfantryLoadoutRolls#playerSquad(int, java.util.Random, boolean, boolean)}) would otherwise never reach a campaign
 * battle. The mission supplies the tube and the radio the way it supplies mech lances and the air pool: to the squads its
 * kit names (counting squads in lift order), and only into a seat that carries no special of its own, so nothing a soldier
 * was issued is taken away.
 */
public final class SupportKitIssue {

    private SupportKitIssue() {}

    /** Issues the declared kit of {@code side} into the lift's unissued seats; returns how many items were issued. */
    public static int issue(BattleSimulation sim, Faction side) {
        SquadSupportKit kit = sim.squadSupport().forSide(side);
        if (kit.isNone() || sim.companyStartsOnGround()) return 0;
        List<ShuttleMission> missions = new ArrayList<>();
        for (long air : sim.getAirEntityIds()) {
            if (sim.world().airFaction(air) != side) continue;
            ShuttleMission mission = sim.world().mission(air);
            if (mission != null && mission.cycleLoadouts != null) missions.add(mission);
        }
        missions.sort(Comparator.comparingInt(m -> m.manifestOrdinal));
        SpecialEquipmentDef mortar = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FIELD_MORTAR_ID);
        SpecialEquipmentDef radio = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.COMMS_RADIO_ID);
        int ordinal = 0;
        int issued = 0;
        for (ShuttleMission mission : missions) {
            for (MarineLoadout[] cycle : mission.cycleLoadouts) {
                if (cycle == null) continue;
                // Mirrors InfantryLoadoutRolls.playerSquad: the launcher keeps the last seat, a tube the first, a radio the one before the launcher.
                if (kit.mortarTeam(ordinal) && cycle.length >= 2 && cycle[0].specialEquipmentId == null) {
                    cycle[0] = cycle[0].withSpecial(mortar.id(), mortar.startingAmmo());
                    issued++;
                }
                int radioSeat = cycle.length - 2;
                if (kit.observer(ordinal) && cycle.length >= 3 && cycle[radioSeat].specialEquipmentId == null) {
                    cycle[radioSeat] = cycle[radioSeat].withSpecial(radio.id(), radio.startingAmmo());
                    issued++;
                }
                ordinal++;
            }
            mission.marineLoadout = mission.cycleLoadouts[Math.min(Math.max(mission.currentCycle, 0), mission.cycleLoadouts.length - 1)];
        }
        return issued;
    }
}
