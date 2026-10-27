package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.fixture.MarineSeatCommitment;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.infantry.SquadSupportKit;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        SpecialEquipmentDef satchel = kit.satchelEvery() > 0 ? SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID) : null;
        SpecialEquipmentDef smoke = kit.smokeEvery() > 0 ? SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SMOKE_GRENADE_ID) : null;
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
                if (kit.sapper(ordinal) && issueFirstFree(cycle, satchel)) issued++;
                if (kit.screener(ordinal) && issueFirstFree(cycle, smoke)) issued++;
                ordinal++;
            }
            mission.marineLoadout = mission.cycleLoadouts[Math.min(Math.max(mission.currentCycle, 0), mission.cycleLoadouts.length - 1)];
        }
        return issued;
    }

    /**
     * Issues a squad's sapper and screener items into the seats of a freshly rolled lift cycle, by the squad's ordinal across the
     * force: a satchel charge when {@code kit.sapper(ordinal)}, a smoke grenade when {@code kit.screener(ordinal)}, each into the first seat after
     * the tube-holder and before the launcher that carries no special of its own. The factory squad roll issues only the tube and the
     * radio, so a mission that declares sappers or smoke on a factory force must come through here or it declares them for nothing.
     * Returns how many items were issued.
     */
    public static int issueBreachKit(MarineLoadout[] cycle, SquadSupportKit kit, int ordinal) {
        int issued = 0;
        if (kit.sapper(ordinal) && issueFirstFree(cycle, SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID))) issued++;
        if (kit.screener(ordinal) && issueFirstFree(cycle, SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SMOKE_GRENADE_ID))) issued++;
        return issued;
    }

    /**
     * Issues the declared kit into a company that starts on the ground, where there is no lift to count squads by: squads
     * are counted in the order their first seat was committed, and the seats are returned with the kit applied under the
     * same rules as {@link #issue} (a tube to the first unissued seat, a radio to the one before the last).
     */
    public static List<MarineSeatCommitment> issueGround(SquadSupportKit kit, List<MarineSeatCommitment> seats) {
        if (kit.isNone()) return seats;
        Map<String, List<Integer>> squads = new LinkedHashMap<>();
        for (int i = 0; i < seats.size(); i++) {
            String squad = seats.get(i).campaignSquadId();
            squads.computeIfAbsent(squad == null ? "#" + i : squad, k -> new ArrayList<>()).add(i);
        }
        SpecialEquipmentDef mortar = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FIELD_MORTAR_ID);
        SpecialEquipmentDef radio = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.COMMS_RADIO_ID);
        SpecialEquipmentDef satchel = kit.satchelEvery() > 0 ? SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID) : null;
        SpecialEquipmentDef smoke = kit.smokeEvery() > 0 ? SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SMOKE_GRENADE_ID) : null;
        List<MarineSeatCommitment> issued = new ArrayList<>(seats);
        int ordinal = 0;
        for (List<Integer> squad : squads.values()) {
            if (kit.mortarTeam(ordinal) && squad.size() >= 2) {
                issueFirstFree(issued, squad, mortar);
            }
            if (kit.observer(ordinal) && squad.size() >= 3) {
                issueFirstFree(issued, squad.subList(squad.size() - 2, squad.size() - 1), radio);
            }
            if (kit.sapper(ordinal) && squad.size() >= 3) issueFirstFree(issued, squad.subList(1, squad.size() - 1), satchel);
            if (kit.screener(ordinal) && squad.size() >= 3) issueFirstFree(issued, squad.subList(1, squad.size() - 1), smoke);
            ordinal++;
        }
        return issued;
    }

    /** Puts the item in the first seat after the tube-holder and before the launcher that carries no special of its own. */
    private static boolean issueFirstFree(MarineLoadout[] cycle, SpecialEquipmentDef special) {
        if (special == null || cycle.length < 3) return false;
        for (int i = 1; i < cycle.length - 1; i++) {
            if (cycle[i].specialEquipmentId != null) continue;
            cycle[i] = cycle[i].withSpecial(special.id(), special.startingAmmo());
            return true;
        }
        return false;
    }

    private static void issueFirstFree(List<MarineSeatCommitment> seats, List<Integer> squad, SpecialEquipmentDef special) {
        for (int i : squad) {
            MarineLoadout loadout = seats.get(i).toLoadout();
            if (loadout.specialEquipmentId != null) continue;
            seats.set(i, MarineSeatCommitment.capture(loadout.withSpecial(special.id(), special.startingAmmo())));
            return;
        }
    }
}
