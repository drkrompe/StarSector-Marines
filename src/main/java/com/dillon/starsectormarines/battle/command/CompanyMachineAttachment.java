package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.mech.MechAttachmentService;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.VehicleAttachmentService;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The company's machines under mission command: while the commander, not the player, runs the Marine company (a
 * headless or evidence battle) and does not direct machines itself ({@link CommandStrategy#commandsMachines}), every
 * landed mech squad and vehicle of the company that follows no one is attached to
 * one of its infantry squads, as a player would attach it ({@code mechs-nouns.md}, attachment). Mission command
 * sends squads, never machines; attached, a machine goes where its squad goes and fights beside it.
 *
 * <p>Each machine goes to a squad mission command is sending somewhere (one whose directive owns an assignment), so
 * it goes forward rather than waiting with a held squad; among those, the one with the fewest followers, then the most
 * marines, then the lowest id, so the company's machines spread across its working squads. A machine whose squad is
 * lost, or held back while others are sent, is attached again on a later pulse.
 */
public final class CompanyMachineAttachment {

    /** Ticks between passes: attachment is a standing relation, not a per-tick decision. */
    static final int PULSE_TICKS = 30;

    private CompanyMachineAttachment() { }

    /** Attaches the company's unattached machines on a pulse, when mission command runs the company. Serial. */
    public static void tick(BattleSimulation sim) {
        CommandStrategy commander = sim.getCommander(Faction.MARINE);
        if (sim.getSimTickIndex() % PULSE_TICKS != 0 || commander == null || commander.commandsMachines()) return;
        List<Squad> squads = infantry(sim);
        if (squads.isEmpty() || sim.playerCommands(squads.get(0))) return;
        MechAttachmentService mechs = sim.getMechAttachmentService();
        VehicleAttachmentService vehicles = sim.getVehicleAttachmentService();
        Map<Integer, Integer> followers = new TreeMap<>();
        for (Squad squad : squads) followers.put(squad.id, 0);
        mechs.attachments().values().forEach(anchor -> followers.computeIfPresent(anchor, (k, n) -> n + 1));
        vehicles.attachments().values().forEach(anchor -> followers.computeIfPresent(anchor, (k, n) -> n + 1));
        List<Squad> sent = new ArrayList<>();
        for (Squad squad : squads) if (committed(sim, squad.id)) sent.add(squad);
        List<Squad> candidates = sent.isEmpty() ? squads : sent;
        for (Squad squad : sortedSquads(sim)) {
            if (squad.faction != Faction.MARINE || !squad.isMechSquad() || squad.rescuePickupMech
                    || live(sim, squad.id) == 0) continue;
            int current = mechs.anchorOf(squad.id);
            if (current != MechAttachmentService.NONE && (sent.isEmpty() || committed(sim, current))) continue;
            int anchor = pick(sim, candidates, followers);
            if (anchor == current) continue;
            if (current != MechAttachmentService.NONE) followers.computeIfPresent(current, (k, f) -> f - 1);
            mechs.requestAttach(squad.id, anchor);
            followers.merge(anchor, 1, Integer::sum);
        }
        ConvoyService convoy = sim.convoy();
        for (int i = 0, n = convoy.vehicleCount(); i < n; i++) {
            long hull = convoy.vehicleAt(i);
            if (convoy.faction(hull) != Faction.MARINE || !convoy.isPresent(hull)) continue;
            int current = vehicles.anchorOf(hull);
            if (current != VehicleAttachmentService.NONE && (sent.isEmpty() || committed(sim, current))) continue;
            VehicleMission mission = convoy.mission(hull);
            if (mission == null || mission.state != VehicleState.DEPLOYED) continue;
            int anchor = pick(sim, candidates, followers);
            if (anchor == current) continue;
            if (current != VehicleAttachmentService.NONE) followers.computeIfPresent(current, (k, f) -> f - 1);
            vehicles.requestAttach(hull, anchor);
            followers.merge(anchor, 1, Integer::sum);
        }
    }

    /** The squad with the fewest followers, then the most marines, then the lowest id. */
    static int pick(BattleSimulation sim, List<Squad> squads, Map<Integer, Integer> followers) {
        Squad best = null;
        int bestFollowers = Integer.MAX_VALUE, bestLive = -1;
        for (Squad squad : squads) {
            int f = followers.getOrDefault(squad.id, 0), alive = live(sim, squad.id);
            if (f < bestFollowers || (f == bestFollowers && alive > bestLive)) {
                best = squad;
                bestFollowers = f;
                bestLive = alive;
            }
        }
        return best.id;
    }

    /** Whether mission command is sending the squad somewhere: its directive owns an assignment. */
    private static boolean committed(BattleSimulation sim, int squadId) {
        CommandDirective directive = sim.getSquadCommandDirective(squadId);
        return directive != null && directive.ownsAssignment();
    }

    /** The company's infantry squads with someone alive, in id order. */
    private static List<Squad> infantry(BattleSimulation sim) {
        List<Squad> out = new ArrayList<>();
        for (Squad squad : sortedSquads(sim))
            if (squad.faction == Faction.MARINE && !squad.isMechSquad() && !squad.isDroneSquad()
                    && !squad.rescuePickupMech && live(sim, squad.id) > 0) out.add(squad);
        return out;
    }

    private static List<Squad> sortedSquads(BattleSimulation sim) {
        List<Squad> out = new ArrayList<>(sim.getSquads());
        out.sort((a, b) -> Integer.compare(a.id, b.id));
        return out;
    }

    private static int live(BattleSimulation sim, int squadId) {
        int alive = 0;
        for (int i = 0, n = sim.squadMemberCount(squadId); i < n; i++) {
            long member = sim.squadMemberAt(squadId, i);
            if (member != 0L && sim.world().hasPosition(member) && sim.world().isAlive(member)) alive++;
        }
        return alive;
    }
}
