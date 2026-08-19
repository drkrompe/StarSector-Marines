package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Story 19 coverage for the route threat score, retreat discount, hysteresis, and EnterZone commit-vs-press behavior. */
public class AdvanceThreatLeashTest {

    private static final int W = 64;
    private static final int H = 32;
    private static final int DEST_X = 50;
    private static final int DEST_Y = 15;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static Squad marineSquad(BattleSimulation sim, int size) {
        Squad squad = new Squad(7, Faction.MARINE);
        List<Long> members = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            members.add(sim.spawn(new EntitySpec("m" + i, Faction.MARINE,
                    UnitType.MARINE, 10, 14 + i).squad(squad.id)));
        }
        squad.leaderId = members.get(0);
        squad.aliveMembers = size;
        squad.originalSize = size;
        squad.centroidX = 10.5f;
        squad.centroidY = 14.5f + (size - 1) * 0.5f;
        return squad;
    }

    private static long defender(BattleSimulation sim, String name, int x, int y) {
        return sim.spawn(new EntitySpec(name, Faction.DEFENDER, UnitType.MARINE, x, y));
    }

    @Test
    public void freshContactsAstrideRouteSaturateThreatScore() {
        BattleSimulation sim = openSim();
        Squad squad = marineSquad(sim, 4);
        long first = defender(sim, "d0", 20, 15);
        defender(sim, "d1", 22, 16);

        TacticalScoring.AdvanceThreat threat = sim.getTacticalScoring()
                .assessAdvanceThreat(squad, DEST_X, DEST_Y);

        assertEquals(1f, threat.weight(), 0.001f,
                "two route contacts against four friends reach the half-force parity point");
        assertEquals(2, threat.foes());
        assertEquals(4, threat.friends());
        assertEquals(first, threat.primaryThreatId(), "nearer equal-weight contact wins the tie");
        assertFalse(threat.primaryRetreating());
        assertTrue(Math.abs(threat.axisAnchorY() - DEST_Y) <= 1,
                "leash anchor projects onto the advance axis");
    }

    @Test
    public void flankAndRetreatingContactsDoNotStopHealthySquad() {
        BattleSimulation flankSim = openSim();
        Squad flankSquad = marineSquad(flankSim, 4);
        defender(flankSim, "flank", 20, 25);

        TacticalScoring.AdvanceThreat flank = flankSim.getTacticalScoring()
                .assessAdvanceThreat(flankSquad, DEST_X, DEST_Y);
        assertTrue(flank.weight() < AbstractZoneAction.ADVANCE_RELEASE_THRESHOLD,
                "a lone contact near the outer edge of the corridor is shots-of-opportunity only");

        BattleSimulation retreatSim = openSim();
        Squad retreatSquad = marineSquad(retreatSim, 4);
        long retreating = defender(retreatSim, "retreating", 20, 15);
        retreatSim.setPath(retreating, new int[]{20, 15, 40, 15});

        TacticalScoring.AdvanceThreat retreat = retreatSim.getTacticalScoring()
                .assessAdvanceThreat(retreatSquad, DEST_X, DEST_Y);
        assertTrue(retreat.primaryRetreating());
        assertEquals(0.1f, retreat.weight(), 0.001f,
                "one retreating contact contributes 0.2 force against the four-friend parity force of 2");
    }

    @Test
    public void commitReleaseHysteresisDampsThresholdCrossing() {
        assertFalse(AbstractZoneAction.shouldCommitAdvance(false, 0.54f));
        assertTrue(AbstractZoneAction.shouldCommitAdvance(false, 0.55f));
        assertTrue(AbstractZoneAction.shouldCommitAdvance(true, 0.30f));
        assertFalse(AbstractZoneAction.shouldCommitAdvance(true, 0.29f));
    }

    @Test
    public void weakContactPressesWithMovingFire() {
        BattleSimulation sim = openSim();
        Squad squad = marineSquad(sim, 4);
        long enemy = defender(sim, "weak", 20, 15);
        long leader = squad.leaderId;
        sim.world().setAttackRange(leader, 30f);

        new ProbeZoneAction().advance(leader, squad, sim, DEST_X, DEST_Y);

        assertEquals(0.5f, squad.advanceEngageWeight, 0.001f);
        assertFalse(squad.advanceEngageCommitted);
        assertFalse(Paths.isEmpty(sim.world().path(leader)),
                "pressing member keeps a route path toward the objective");
        assertEquals(enemy, sim.combat().fireTargetId(leader));
        assertEquals(FireStance.MOVING.ordinal(), fireStance(sim, leader));
    }

    @Test
    public void realRouteContactHaltsThenAutoReleasesWhenContactRetreats() {
        BattleSimulation sim = openSim();
        Squad squad = marineSquad(sim, 4);
        long first = defender(sim, "d0", 20, 15);
        long second = defender(sim, "d1", 22, 16);
        long leader = squad.leaderId;
        sim.world().setAttackRange(leader, 30f);
        sim.setPath(leader, new int[]{10, 14, DEST_X, DEST_Y});

        new ProbeZoneAction().advance(leader, squad, sim, DEST_X, DEST_Y);

        assertTrue(squad.advanceEngageCommitted);
        assertEquals(AbstractZoneAction.ADVANCE_LEASH_MAX,
                squad.advanceEngageLeash, 0.001f);
        assertTrue(Paths.isEmpty(sim.world().path(leader)),
                "in-range committed contact halts objective movement");
        assertEquals(first, sim.combat().fireTargetId(leader));
        assertEquals(FireStance.STANCED.ordinal(), fireStance(sim, leader));

        sim.setPath(first, new int[]{20, 15, 40, 15});
        sim.setPath(second, new int[]{22, 16, 42, 16});
        squad.advanceThreatTick = -1; // stand-in for the next sim tick

        new ProbeZoneAction().advance(leader, squad, sim, DEST_X, DEST_Y);

        assertEquals(0.2f, squad.advanceEngageWeight, 0.001f);
        assertFalse(squad.advanceEngageCommitted,
                "retreat discount drops the score below release without a teardown action");
        assertFalse(Paths.isEmpty(sim.world().path(leader)),
                "released squad resumes its objective path immediately");
    }

    @Test
    public void committedOutOfRangeMemberMovesToFiringCellInsideAxisLeash() {
        BattleSimulation sim = openSim();
        Squad squad = marineSquad(sim, 4);
        defender(sim, "d0", 24, 15);
        defender(sim, "d1", 26, 16);
        long leader = squad.leaderId;
        sim.world().setAttackRange(leader, 6f);

        new ProbeZoneAction().advance(leader, squad, sim, DEST_X, DEST_Y);

        assertTrue(squad.advanceEngageCommitted);
        int[] path = sim.world().path(leader);
        assertFalse(Paths.isEmpty(path));
        int pathDestX = Paths.destX(path);
        int pathDestY = Paths.destY(path);
        assertTrue(TacticalScoring.cellDistance(pathDestX, pathDestY,
                        squad.advanceThreatAnchorX, squad.advanceThreatAnchorY)
                        <= squad.advanceEngageLeash,
                "contact prosecution stays inside the weight-scaled off-axis leash");
        assertTrue(pathDestX != DEST_X || pathDestY != DEST_Y,
                "committed member takes a firing position instead of blindly following the objective path");
    }

    private static int fireStance(BattleSimulation sim, long member) {
        return sim.getRoster().entityWorld().getInt(member,
                sim.getRoster().components().COMBAT, BattleComponents.COMBAT_FIRE_STANCE);
    }

    private static final class ProbeZoneAction extends AbstractZoneAction {
        private ProbeZoneAction() { super(-1); }
        @Override public String name() { return "ProbeZoneAction"; }
        @Override public ActionStatus execute(long member, Squad squad, BattleControl sim) {
            return ActionStatus.RUNNING;
        }

        private void advance(long member, Squad squad, BattleControl sim, int destX, int destY) {
            advanceIntoZone(member, squad, sim, destX, destY, true);
        }
    }
}
