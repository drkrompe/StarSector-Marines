package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.infantry.RepositionToCover;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.ContactInitiative;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Doctrine;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.ForceBalance;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Motion;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Posture;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Sector;
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

    private static BattleSimulation splitSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
            grid.setWalkable(16, y, false);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static Squad marineSquad(BattleSimulation sim, int size) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
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

    private static void observeContacts(BattleSimulation sim) {
        sim.advance(BattleSimulation.TICK_DT);
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
        observeContacts(sim);

        TacticalScoring.AdvanceThreat threat = sim.getTacticalScoring()
                .assessAdvanceThreat(squad, DEST_X, DEST_Y, sim.getSimTickIndex());

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
    public void hiddenUnrememberedEnemyDoesNotAffectLocalThreat() {
        BattleSimulation sim = splitSim();
        Squad squad = marineSquad(sim, 4);
        defender(sim, "hidden-route", 22, 15);
        observeContacts(sim);

        TacticalScoring.AdvanceThreat threat = sim.getTacticalScoring()
                .assessAdvanceThreat(squad, DEST_X, DEST_Y, sim.getSimTickIndex());

        assertTrue(squad.believedContacts().isEmpty(),
                "the separating wall prevents the squad from identifying the enemy");
        assertEquals(0f, threat.weight(), 0.001f,
                "an unremembered live enemy on the geometric route is not AI knowledge");
        assertEquals(0f, sim.getTacticalScoring().believedHostilePresenceWithin(
                squad, 10, 15, 30f), 0.001f,
                "the guard-post presence primitive uses the same honest belief authority");
    }

    @Test
    public void flankAndRetreatingContactsDoNotStopHealthySquad() {
        BattleSimulation flankSim = openSim();
        Squad flankSquad = marineSquad(flankSim, 4);
        defender(flankSim, "flank", 20, 25);
        observeContacts(flankSim);

        TacticalScoring.AdvanceThreat flank = flankSim.getTacticalScoring()
                .assessAdvanceThreat(flankSquad, DEST_X, DEST_Y, flankSim.getSimTickIndex());
        assertTrue(flank.weight() < AbstractZoneAction.ADVANCE_RELEASE_THRESHOLD,
                "a lone contact near the outer edge of the corridor is shots-of-opportunity only");

        BattleSimulation retreatSim = openSim();
        Squad retreatSquad = marineSquad(retreatSim, 4);
        long retreating = defender(retreatSim, "retreating", 20, 15);
        retreatSim.setPath(retreating, new int[]{20, 15, 40, 15});
        observeContacts(retreatSim);

        TacticalScoring.AdvanceThreat retreat = retreatSim.getTacticalScoring()
                .assessAdvanceThreat(retreatSquad, DEST_X, DEST_Y, retreatSim.getSimTickIndex());
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
        observeContacts(sim);
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
        observeContacts(sim);
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
    public void committedRoomEntryKeepsMovingThroughRouteContact() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (x != 32) grid.setWalkableFloor(x, y);
            }
        }
        grid.setWalkableFloor(32, 15);
        grid.setDoorway(32, 15, true);
        BattleSimulation sim = new BattleSimulation(
                grid, new CellTopology(W, H));
        Squad squad = marineSquad(sim, 4);
        defender(sim, "d0", 20, 15);
        defender(sim, "d1", 22, 16);
        observeContacts(sim);
        long leader = squad.leaderId;
        sim.world().setAttackRange(leader, 30f);
        int targetZone = sim.getZoneGraph().zoneIdAt(40, 15);
        EnterZone action = EnterZone.committedForZone(
                sim.getZoneGraph().zoneById(targetZone), sim.getGrid());

        action.execute(leader, squad, sim);

        assertFalse(Paths.isEmpty(sim.world().path(leader)),
                "a committed final hop must not park outside the room on contact");
        assertEquals(targetZone, sim.getZoneGraph().zoneIdAt(
                Paths.destX(sim.world().path(leader)),
                Paths.destY(sim.world().path(leader))));
    }

    @Test
    public void committedOutOfRangeMemberMovesToFiringCellInsideAxisLeash() {
        BattleSimulation sim = openSim();
        Squad squad = marineSquad(sim, 4);
        defender(sim, "d0", 24, 15);
        defender(sim, "d1", 26, 16);
        observeContacts(sim);
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

    @Test
    public void staleDoctrineHoldWithoutActionableEvidenceResumesObjectivePath() {
        BattleSimulation sim = openSim();
        Squad squad = marineSquad(sim, 4);
        long enemy = defender(sim, "stale-flank", 20, 25);
        long leader = squad.leaderId;
        sim.world().setAttackRange(leader, 5f);
        sim.world().setTargetId(leader, enemy);
        squad.contactPicture = new SquadContactPicture(sim.getSimTickIndex(),
                Posture.ADVANCING, 1f, 0f, 1, 0, 0.6f, 4,
                ForceBalance.FAVORABLE, Sector.RIGHT_FLANK, Motion.UNKNOWN,
                enemy, 20, 25, 0.6f, Doctrine.HOLD, 0, 4, 0, 1,
                ContactInitiative.NONE);

        new ProbeZoneAction().advance(leader, squad, sim, DEST_X, DEST_Y);

        assertFalse(squad.advanceEngageCommitted);
        assertFalse(Paths.isEmpty(sim.world().path(leader)),
                "stale doctrine memory may guide aim but must not plant the advance");
        assertEquals(DEST_X, Paths.destX(sim.world().path(leader)));
        assertEquals(DEST_Y, Paths.destY(sim.world().path(leader)));
    }

    @Test
    public void partialFiringLineHoldsShooterWhileSquadmatesCloseOnPrimary() {
        BattleSimulation sim = openSim();
        Squad squad = marineSquad(sim, 4);
        long primary = defender(sim, "direct", 24, 15);
        long stale = defender(sim, "stale-target", 42, 28);
        observeContacts(sim);
        long shooter = squad.leaderId;
        long mover = sim.squadMemberAt(squad.id, 1);
        sim.world().setAttackRange(shooter, 30f);
        sim.world().setAttackRange(mover, 6f);
        sim.world().setTargetId(mover, stale);
        squad.contactPicture = new SquadContactPicture(sim.getSimTickIndex(),
                Posture.ADVANCING, 1f, 0f, 1, 1, 1f, 4,
                ForceBalance.FAVORABLE, Sector.FRONT, Motion.LATERAL,
                primary, 24, 15, 1f, Doctrine.HOLD, 1, 4, 1, 1,
                ContactInitiative.PROSECUTE);

        ProbeZoneAction action = new ProbeZoneAction();
        action.advance(shooter, squad, sim, DEST_X, DEST_Y);
        action.advance(mover, squad, sim, DEST_X, DEST_Y);

        assertTrue(Paths.isEmpty(sim.world().path(shooter)),
                "the marine with a firing line holds rather than advancing in lockstep");
        assertEquals(primary, sim.combat().fireTargetId(shooter));
        assertEquals(FireStance.STANCED.ordinal(), fireStance(sim, shooter));
        assertEquals(primary, sim.targetOf(mover),
                "shared contact intel replaces the mover's stale individual target");
        assertFalse(Paths.isEmpty(sim.world().path(mover)));
        assertTrue(Paths.destX(sim.world().path(mover)) != DEST_X
                        || Paths.destY(sim.world().path(mover)) != DEST_Y,
                "the non-shooter establishes a bounded firing position instead of idling");
        assertTrue(TacticalScoring.cellDistance(Paths.destX(sim.world().path(mover)),
                        Paths.destY(sim.world().path(mover)),
                        Math.round(squad.centroidX - 0.5f),
                        Math.round(squad.centroidY - 0.5f))
                        <= AbstractZoneAction.ADVANCE_LEASH_MAX);
    }

    @Test
    public void receivedContactStaggersShooterIntoCoverWithoutResumingObjective() {
        BattleSimulation sim = openSim();
        Squad squad = marineSquad(sim, 4);
        long primary = defender(sim, "approaching", 24, 15);
        observeContacts(sim);
        long shooter = squad.leaderId;
        sim.world().setAttackRange(shooter, 30f);
        sim.getGrid().setCoverAtFacing(9, 14, NavigationGrid.FACING_E, 2);
        sim.setPath(shooter, new int[]{10, 14, DEST_X, DEST_Y});
        squad.contactPicture = new SquadContactPicture(sim.getSimTickIndex(),
                Posture.ADVANCING, 1f, 0f, 1, 1, 1f, 4,
                ForceBalance.FAVORABLE, Sector.FRONT, Motion.APPROACHING,
                primary, 24, 15, 1f, Doctrine.HOLD, 1, 4, 1, 1,
                ContactInitiative.RECEIVE);

        ProbeZoneAction action = new ProbeZoneAction();
        action.advance(shooter, squad, sim, DEST_X, DEST_Y);

        assertTrue(Paths.isEmpty(sim.world().path(shooter)),
                "first contact suppresses the old objective path");
        assertEquals(primary, sim.combat().fireTargetId(shooter));
        assertEquals(FireStance.STANCED.ordinal(), fireStance(sim, shooter));
        assertEquals(1, fireReposition(sim, shooter),
                "committed fire requests the existing post-shot cover adjustment");

        assertTrue(RepositionToCover.tryReposition(shooter, sim));
        int[] coverPath = sim.world().path(shooter);
        assertFalse(Paths.isEmpty(coverPath));
        assertEquals(9, Paths.destX(coverPath));
        assertEquals(14, Paths.destY(coverPath));
        float beforeX = sim.world().x(shooter);

        action.advance(shooter, squad, sim, DEST_X, DEST_Y);

        assertFalse(Paths.isEmpty(sim.world().path(shooter)),
                "the contact hold preserves its cooldown-marked cover path");
        assertEquals(9, Paths.destX(sim.world().path(shooter)));
        assertEquals(14, Paths.destY(sim.world().path(shooter)));
        assertTrue(sim.world().x(shooter) < beforeX,
                "the shooter advances the local cover move instead of freezing on its first firing cell");
    }

    private static int fireStance(BattleSimulation sim, long member) {
        return sim.getRoster().entityWorld().getInt(member,
                sim.getRoster().components().COMBAT, BattleComponents.COMBAT_FIRE_STANCE);
    }

    private static int fireReposition(BattleSimulation sim, long member) {
        return sim.getRoster().entityWorld().getInt(member,
                sim.getRoster().components().COMBAT, BattleComponents.COMBAT_FIRE_REPOSITION);
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
