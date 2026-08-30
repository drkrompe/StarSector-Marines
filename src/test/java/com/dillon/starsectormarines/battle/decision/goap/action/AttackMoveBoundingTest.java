package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An attack move advances as a body and does not bound, and the beaten zone
 * that gates bounding elsewhere distinguishes an enemy that is relevant from
 * one that is dangerous.
 *
 * <p>Not bounding is a decision, not a measured win: over the canonical matrix
 * bounding attack moves moved neither fixture by a tick either way. The shared
 * machinery is still right there on {@link AbstractZoneAction}, and re-enabling
 * it is one line — but a claim about whether it helps needs a scene built to
 * ask that question, since these two whole-battle fixtures cannot see it.
 */
public class AttackMoveBoundingTest {

    private static final int W = 64;
    private static final int H = 32;
    private static final int DEST_X = 52;
    private static final int DEST_Y = 15;

    private record Fixture(BattleSimulation sim, Squad squad,
                           AttackMove action, List<Long> members) { }

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /** Squad at x=10; {@code threatX} places a pair of contacts astride the route. */
    private static Fixture fixture(int threatX) {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        List<Long> members = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            EntitySpec spec = new EntitySpec("m" + i, Faction.MARINE,
                    UnitType.MARINE, 10, 14 + i).squad(squadId);
            if (i == 0) {
                spec.primaryWeapon(WeaponRegistry.require(
                        WeaponRegistry.SQUAD_AUTOMATIC_ID));
            }
            long member = sim.spawn(spec);
            sim.world().setAttackRange(member, 30f);
            members.add(member);
        }
        squad.leaderId = members.get(0);
        squad.aliveMembers = 4;
        squad.originalSize = 4;
        squad.centroidX = 10.5f;
        squad.centroidY = 16f;
        squad.assignedObjective = ObjectiveAssignment.attackMove(
                squad.id, DEST_X, DEST_Y);

        if (threatX > 0) {
            sim.spawn(new EntitySpec("d0", Faction.DEFENDER,
                    UnitType.MARINE, threatX, 15));
            sim.spawn(new EntitySpec("d1", Faction.DEFENDER,
                    UnitType.MARINE, threatX + 2, 16));
        } else {
            // A scene needs both sides on the map or the simulation returns
            // without advancing a tick; keep one defender out of the fight.
            sim.spawn(new EntitySpec("far", Faction.DEFENDER,
                    UnitType.MARINE, 62, 30));
        }
        sim.advance(BattleSimulation.TICK_DT);

        AttackMove action = new AttackMove(DEST_X, DEST_Y);
        SquadPlan.Step step = new SquadPlan.Step(action);
        step.assignments.put(AbstractZoneAction.TEAM_A,
                new ArrayList<>(members.subList(0, 2)));
        step.assignments.put(AbstractZoneAction.TEAM_B,
                new ArrayList<>(members.subList(2, 4)));
        squad.currentPlan = new SquadPlan(List.of(step));
        return new Fixture(sim, squad, action, members);
    }

    @Test
    public void anAttackMoveInContactAdvancesAsABodyRatherThanBounding() {
        Fixture f = fixture(35);
        for (long member : f.members) {
            f.action.execute(member, f.squad, f.sim);
        }

        assertTrue(f.squad.advanceEngageCommitted,
                "contact astride the route still commits the advance");
        assertFalse(f.squad.boundingActive,
                "moving half a squad at a time up a long route is slower than "
                        + "the ground is dangerous");
    }

    @Test
    public void anUncontestedAttackMoveKeepsWalkingItsObjective() {
        Fixture f = fixture(0);
        for (long member : f.members) {
            f.action.execute(member, f.squad, f.sim);
        }

        assertFalse(f.squad.boundingActive);
        assertFalse(Paths.isEmpty(f.sim.world().path(f.squad.leaderId)),
                "the squad moves on its objective");
    }

    @Test
    public void theBeatenZoneSeparatesARelevantEnemyFromADangerousOne() {
        // The advance-threat score looks tens of cells down the route, so a
        // squad can be committed to a contact that cannot touch it. Bounding,
        // screening smoke and placing cover all exist because crossing ground
        // under fire is lethal, and are worth their cost only where that holds.
        Fixture near = fixture(35);
        Fixture far = fixture(44);
        long nearThreat = near.squad.advanceThreatId;
        long farThreat = far.squad.advanceThreatId;

        assertTrue(far.squad.advanceEngageCommitted,
                "the distant pair still commits the advance");
        assertTrue(near.sim.getTacticalScoring().threatReaches(nearThreat,
                        near.squad.centroidX, near.squad.centroidY,
                        AbstractZoneAction.BOUNDING_STRIDE),
                "a contact inside its own reach is dangerous");
        assertFalse(far.sim.getTacticalScoring().threatReaches(farThreat,
                        far.squad.centroidX, far.squad.centroidY,
                        AbstractZoneAction.BOUNDING_STRIDE),
                "one that commits the advance from beyond its reach is only relevant");
    }
}
