package com.dillon.starsectormarines.battle.decision.goap.world;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the {@link Predicate#IN_RANGE_OF_TARGET} quorum mechanism in
 * {@link WorldStateBuilder#evalInRangeOfTarget}: a squad reads in-range only
 * once enough of its live members personally are, not the instant its single
 * longest-range member is. A live squad-148 dump showed the old any-member
 * predicate reading true off one anti-materiel rifleman while the other nine
 * marines sat 28-33 cells out and never fired a shot in 1500+ ticks.
 *
 * <p>Every squad member is placed the same distance from the believed
 * contact; {@code attackRange} is overridden per member after spawn so the
 * test controls exactly who is "in range" without fussing over geometry.
 */
public class InRangeOfTargetQuorumTest {

    private static final int W = 40;
    private static final int H = 10;
    private static final int TARGET_X = 0;
    private static final int TARGET_Y = 5;
    private static final int MEMBER_X = 10; // fixed cell-distance of 10 from the target for every member
    private static final float IN_RANGE = 15f;
    private static final float OUT_OF_RANGE = 5f;

    /** Open floor — no walls, LOS is straightforward. */
    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /**
     * Spawns an attacking squad of {@code memberRanges.length} members, all
     * {@link #MEMBER_X} cells from a single defender contact, each given the
     * corresponding {@code attackRange}, then drives the sim far enough for
     * the alert pass to publish the belief and populate {@code aliveMembers}.
     */
    private static Squad squadWithMemberRanges(BattleSimulation sim, float... memberRanges) {
        sim.spawn(new EntitySpec("d", Faction.DEFENDER, UnitType.MARINE, TARGET_X, TARGET_Y));

        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        for (int i = 0; i < memberRanges.length; i++) {
            long member = sim.spawn(new EntitySpec("m" + i, Faction.MARINE, UnitType.MARINE,
                    MEMBER_X, TARGET_Y).squad(squadId));
            sim.world().setAttackRange(member, memberRanges[i]);
        }

        // One tick is enough for the alert pass to observe the close, clear-LOS
        // defender and publish the squad's belief snapshot.
        sim.advance(1.0f / 30f);
        return sim.getSquad(squadId);
    }

    @Test
    public void oneLongRangeMemberInRangeRestOutReadsFalse() {
        BattleSimulation sim = openSim();
        // Ten members, one (index 0) in range — matches the squad-148 shape:
        // an any-member reading would have carried the whole squad on this
        // single rifleman. Quorum requires floor(10 * 0.5) == 5.
        Squad squad = squadWithMemberRanges(sim,
                IN_RANGE, OUT_OF_RANGE, OUT_OF_RANGE, OUT_OF_RANGE, OUT_OF_RANGE,
                OUT_OF_RANGE, OUT_OF_RANGE, OUT_OF_RANGE, OUT_OF_RANGE, OUT_OF_RANGE);

        WorldState s = WorldStateBuilder.build(squad, sim);
        assertFalse(s.get(Predicate.IN_RANGE_OF_TARGET),
                "one in-range member out of ten must not carry the squad's IN_RANGE_OF_TARGET fact");
    }

    @Test
    public void majorityInRangeReadsTrue() {
        BattleSimulation sim = openSim();
        // Four members, three in range — quorum is floor(4 * 0.5) == 2, met.
        Squad squad = squadWithMemberRanges(sim, IN_RANGE, IN_RANGE, IN_RANGE, OUT_OF_RANGE);

        WorldState s = WorldStateBuilder.build(squad, sim);
        assertTrue(s.get(Predicate.IN_RANGE_OF_TARGET),
                "three of four members in range clears the floor(4 * 0.5) == 2 quorum");
    }

    @Test
    public void twoMemberSquadOneInRangeReadsTrue() {
        BattleSimulation sim = openSim();
        // The stated small-squad rounding case: floor(2 * 0.5) == 1 already,
        // so a two-marine squad needs only one of its two members in range.
        Squad squad = squadWithMemberRanges(sim, IN_RANGE, OUT_OF_RANGE);

        WorldState s = WorldStateBuilder.build(squad, sim);
        assertTrue(s.get(Predicate.IN_RANGE_OF_TARGET),
                "a two-member squad's quorum is floor(2 * 0.5) == 1 — one in-range member must satisfy it");
    }

    @Test
    public void soloMemberOutOfRangeReadsFalse() {
        BattleSimulation sim = openSim();
        // The floor-to-minimum-1 clamp: floor(1 * 0.5) == 0 would otherwise
        // vacuously satisfy quorum with nobody in range. A solo survivor must
        // be in range itself.
        Squad squad = squadWithMemberRanges(sim, OUT_OF_RANGE);

        WorldState s = WorldStateBuilder.build(squad, sim);
        assertFalse(s.get(Predicate.IN_RANGE_OF_TARGET),
                "a solo out-of-range member must not vacuously satisfy the floor-to-zero quorum");
    }
}
