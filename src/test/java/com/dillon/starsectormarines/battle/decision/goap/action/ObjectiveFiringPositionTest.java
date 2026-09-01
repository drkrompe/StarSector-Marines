package com.dillon.starsectormarines.battle.decision.goap.action;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link AbstractZoneAction#advanceIntoZone}'s objective-anchored firing
 * position: a not-yet-committed member that can see a target it cannot reach
 * gets one bounded chance to close that gap before falling back to marching
 * the plain objective route.
 *
 * <p>Written from a live dump of squad 148: ten marines converged on ~(42,20),
 * the sole observed contact sat at (67,3) — 28 to 33 cells out and, per
 * {@code assessAdvanceThreat}, correctly 19 cells off the squad's short
 * 6-cell objective hop, so the route threat never committed the squad. Nine
 * of the ten had a clear shot on a contact their rifles could not reach, and
 * with nothing else in range, the whole squad marched its objective hop
 * eating fire the entire way. {@link AbstractZoneAction#OBJECTIVE_FIRING_LEASH}
 * gives that marine one order-scoped chance to shoot back instead.
 */
public class ObjectiveFiringPositionTest {

    private static final int W = 60;
    private static final int H = 45;
    private static final int DEST_X = 48;
    private static final int DEST_Y = 40;
    private static final int MEMBER_X = 0;
    private static final int MEMBER_Y = 40;
    private static final float FIELD_RIFLE_RANGE = 22f;

    /**
     * The contact sits between the member and the objective and well off the
     * axis between them, which is the shape the dump had: 28 cells from the
     * destination, 15 off the route, and 28 from the member.
     *
     * <p>It was once placed 29 cells beyond the destination instead, which put
     * it 56 cells from the member — and a marine sees 36. That geometry was
     * only ever legal because target picking was omniscient: the member had a
     * clear <em>line</em> to something it could not see, and the test's own
     * account of the dump says the contact was 28 to 33 cells out. Perception
     * gating made the fixture disagree with its own documentation, so the
     * fixture moved.
     */
    private static final int CONTACT_X = 24;
    /** 28 cells off the destination: a leash cell reaches it. */
    private static final int NEAR_CONTACT_Y = 25;
    /** 33 cells off the destination: no cell inside the leash reaches it. */
    private static final int FAR_CONTACT_Y = 18;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static Squad marine(BattleSimulation sim) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        long member = sim.spawn(new EntitySpec("m0", Faction.MARINE,
                UnitType.MARINE, MEMBER_X, MEMBER_Y).squad(squadId));
        sim.world().setAttackRange(member, FIELD_RIFLE_RANGE);
        squad.leaderId = member;
        squad.aliveMembers = 1;
        squad.originalSize = 1;
        squad.centroidX = MEMBER_X + 0.5f;
        squad.centroidY = MEMBER_Y + 0.5f;
        return squad;
    }

    private static long defender(BattleSimulation sim, int x, int y) {
        return sim.spawn(new EntitySpec("d0", Faction.DEFENDER, UnitType.MARINE, x, y));
    }

    @Test
    public void outOfRangeVisibleTargetPullsMemberToAFiringSpotInsideTheObjectiveLeash() {
        BattleSimulation sim = openSim();
        Squad squad = marine(sim);
        // 28 cells off the destination — comfortably outside the field rifle's
        // 22-cell range from the destination itself, but within reach of a cell
        // up to 8 cells (OBJECTIVE_FIRING_LEASH) closer.
        defender(sim, CONTACT_X, NEAR_CONTACT_Y);
        sim.advance(BattleSimulation.TICK_DT);
        long member = squad.leaderId;

        new ProbeZoneAction().advance(member, squad, sim, DEST_X, DEST_Y);

        assertFalse(squad.advanceEngageCommitted,
                "the contact projects far outside the route corridor for this short hop, "
                        + "so the advance-threat leash never commits and must not be what's moving this member");
        int[] path = sim.world().path(member);
        assertFalse(Paths.isEmpty(path),
                "the member paths toward an improved firing spot rather than idling");
        int spotX = Paths.destX(path);
        int spotY = Paths.destY(path);
        assertTrue(spotX != DEST_X || spotY != DEST_Y,
                "the chosen cell is the firing spot, not the plain objective cell");
        assertTrue(TacticalScoring.cellDistance(spotX, spotY, DEST_X, DEST_Y)
                        <= AbstractZoneAction.OBJECTIVE_FIRING_LEASH,
                "the improvement stays inside the objective's own leash");
    }

    /**
     * The assertion that catches the creep regression: if the leash were ever
     * re-anchored on the member's own position instead of the destination, a
     * member this far from the objective would either find no legal spot at
     * all or — worse — be free to keep re-anchoring itself progressively
     * closer to the enemy every tick, which is the unbounded charge the
     * design note on {@link AbstractZoneAction#OBJECTIVE_FIRING_LEASH} warns
     * about. Anchored on the destination, the chosen spot is nowhere near
     * the member's own start — it is bounded by the order, not by how far
     * the member has already marched toward the fight.
     */
    @Test
    public void theChosenSpotIsBoundedByTheDestinationNotByHowCloseTheMemberHasWalked() {
        BattleSimulation sim = openSim();
        Squad squad = marine(sim);
        defender(sim, CONTACT_X, NEAR_CONTACT_Y);
        sim.advance(BattleSimulation.TICK_DT);
        long member = squad.leaderId;

        new ProbeZoneAction().advance(member, squad, sim, DEST_X, DEST_Y);

        int[] path = sim.world().path(member);
        assertFalse(Paths.isEmpty(path));
        int spotX = Paths.destX(path);
        int spotY = Paths.destY(path);
        assertTrue(TacticalScoring.cellDistance(spotX, spotY, DEST_X, DEST_Y)
                        <= AbstractZoneAction.OBJECTIVE_FIRING_LEASH,
                "anchored on the destination: never more than the leash away from it");
        assertTrue(TacticalScoring.cellDistance(spotX, spotY, MEMBER_X, MEMBER_Y)
                        > AbstractZoneAction.OBJECTIVE_FIRING_LEASH,
                "far outside what a member-anchored leash of the same radius could ever have reached "
                        + "from where this member actually started — the anchor is the order's destination");
    }

    @Test
    public void aTargetTooFarOffTheObjectiveIsLeftToTheOrdinaryRoute() {
        BattleSimulation sim = openSim();
        Squad squad = marine(sim);
        // 33 cells off the destination: even the closest cell the leash
        // allows (8 cells nearer) is still 25 cells from the target, beyond
        // the field rifle's reach. No legal spot exists — and the member can
        // still see it, so the refusal is the leash's rather than perception's.
        defender(sim, CONTACT_X, FAR_CONTACT_Y);
        sim.advance(BattleSimulation.TICK_DT);
        long member = squad.leaderId;

        new ProbeZoneAction().advance(member, squad, sim, DEST_X, DEST_Y);

        assertFalse(squad.advanceEngageCommitted);
        int[] path = sim.world().path(member);
        assertFalse(Paths.isEmpty(path), "the member still walks its objective route");
        assertEquals(DEST_X, Paths.destX(path));
        assertEquals(DEST_Y, Paths.destY(path));
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
