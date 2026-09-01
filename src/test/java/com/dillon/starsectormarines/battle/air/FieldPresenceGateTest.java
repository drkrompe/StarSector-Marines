package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.FieldPresencePolicy;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FieldPresenceGateTest {

    @Test
    void oneSquadMayUseSeveralLiftsWhileTheNextWaitsForItsLoss() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(24);
        CampaignMarineDeployment deployment =
                CampaignMarineDeployment.freeze(roster, 24);

        try (BattleSimulation sim = openSimulation()) {
            sim.addObjective(new WaitingObjective(Faction.MARINE));
            sim.addObjective(new WaitingObjective(Faction.DEFENDER));
            List<Long> craft = List.of(
                    shuttle(sim, 7.5f), shuttle(sim, 10.5f), shuttle(sim, 13.5f));
            deployment.applyTo(sim, 0, FieldPresencePolicy.INFILTRATION);
            sim.setFieldPresencePolicy(FieldPresencePolicy.INFILTRATION);

            List<ShuttleMission> missions = craft.stream()
                    .map(id -> sim.world().mission(id)).toList();
            assertEquals(2, missions.get(0).totalCycles,
                    "whole-squad packing adds the partial fourth sortie");
            for (ShuttleMission mission : missions) {
                for (MarineLoadout[] cycle : mission.cycleLoadouts) {
                    assertEquals(1, Arrays.stream(cycle)
                            .map(loadout -> loadout.campaignSquad.squadId)
                            .distinct().count(),
                            "one covert sortie must never mix persistent squads");
                }
            }

            sim.advance(BattleSimulation.TICK_DT);
            assertEquals(ShuttleState.INCOMING, missions.get(0).state);
            assertEquals(ShuttleState.INCOMING, missions.get(1).state,
                    "both lifts carry the already-admitted first squad");
            assertEquals(ShuttleState.PENDING, missions.get(2).state,
                    "the second squad waits off-map");

            landNow(sim, craft.get(0));
            landNow(sim, craft.get(1));
            for (int i = 0; i < 420; i++) sim.advance(BattleSimulation.TICK_DT);

            String firstSquad = roster.squads().get(0).id();
            List<Long> firstSquadMembers = liveMembers(sim, firstSquad);
            assertEquals(12, firstSquadMembers.size());
            assertEquals(ShuttleState.PENDING, missions.get(2).state);

            for (long marine : firstSquadMembers) {
                sim.applyDamage(marine, 100_000f, 100_000f);
            }
            for (int i = 0; i < 10 && missions.get(2).state == ShuttleState.PENDING; i++) {
                sim.advance(BattleSimulation.TICK_DT);
            }

            assertNotEquals(ShuttleState.PENDING, missions.get(2).state,
                    "loss of the admitted squad releases its field slot");
        }
    }

    @Test
    void currentMissionDefaultsAreMissionShapedButOverridable() {
        assertEquals(FieldPresencePolicy.INFILTRATION,
                FieldPresencePolicy.defaultFor(
                        MissionType.SABOTAGE));
        assertEquals(FieldPresencePolicy.CAPTURE_TEAM,
                FieldPresencePolicy.defaultFor(
                        MissionType.RAID));
        assertEquals(FieldPresencePolicy.UNRESTRICTED,
                FieldPresencePolicy.defaultFor(
                        MissionType.CONQUEST));
    }

    private static long shuttle(BattleSimulation sim, float y) {
        return sim.spawnShuttle(ShuttleType.BUFFALO, Faction.MARINE,
                10.5f, y, -2f, y, 22f, y, 0f, 8);
    }

    private static void landNow(BattleSimulation sim, long craft) {
        ShuttleMission mission = sim.world().mission(craft);
        mission.state = ShuttleState.LANDED;
        sim.world().kinematics(craft).teleport(mission.lzX, mission.lzY, 0f);
    }

    private static List<Long> liveMembers(BattleSimulation sim, String squadId) {
        List<Long> members = new ArrayList<>();
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (squadId.equals(sim.identity().campaignSquadId(unit))) members.add(unit);
        }
        return members;
    }

    private static BattleSimulation openSimulation() {
        NavigationGrid grid = new NavigationGrid(20, 20);
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(20, 20));
    }

    private record WaitingObjective(Faction owningFaction) implements Objective {
        @Override public void tick(BattleView sim) {}
        @Override public boolean isComplete() { return false; }
        @Override public boolean isFailed() { return false; }
        @Override public String displayName() { return "Waiting"; }
    }
}
