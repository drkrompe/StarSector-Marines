package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a marine carrying the rejoin state does with his tick.
 *
 * <p>The return value <em>is</em> the discriminator here, unlike
 * {@code LaneSidestepTest}: this reflex consumes the tick when it acts, because
 * the whole point is that the squad's step — and the opportunity-fire
 * initiation inside it — does not run for somebody who is not with the squad
 * yet. So each case pins the boolean and then what came of it.
 *
 * <p>The squad's leader stands at the west end of an empty arena and the late
 * arrival at the east end, well past {@link InfantryCohesion#COHESION_RADIUS}.
 * Nothing else is on the map unless the case puts it there, so the only thing
 * that can move this marine is the reflex.
 */
public class SquadRejoinReflexTest {

    private static final int W = 80;
    private static final int H = 24;
    private static final int ROW = 12;
    private static final int LEADER_X = 6;
    private static final int LATE_X = 60;
    /** Inside the cohesion radius of the leader — a marine who has arrived. */
    private static final int CLOSE_X = LEADER_X + 4;

    private boolean previouslyEnabled;

    @BeforeEach
    public void enableTheState() {
        previouslyEnabled = SquadRejoin.isEnabled();
        SquadRejoin.setEnabledForEvidence(true);
    }

    @AfterEach
    public void restoreTheDefault() {
        SquadRejoin.setEnabledForEvidence(previouslyEnabled);
    }

    @Test
    public void aLateArrivalWalksTowardsItsLeader() {
        Fixture f = Fixture.late(LATE_X);

        float before = f.lateX();
        assertTrue(SquadRejoin.rejoin(f.late, f.squad, f.sim),
                "a marine still crossing to his squad spends the tick doing that,"
                        + " not executing a step authored somewhere else");
        assertTrue(f.lateX() < before,
                "and the tick moved him west, towards the leader");
        assertFalse(Paths.isEmpty(f.sim.world().path(f.late)),
                "the pull authored a path rather than merely declining");
    }

    @Test
    public void aVisibleEnemyIsNotWhereHeGoes() {
        // Due east, the opposite way from the leader, and further off than he
        // could shoot from where he stands.
        Fixture f = Fixture.lateWithEnemyAt(LATE_X, LATE_X + 16, ROW);

        float before = f.lateX();
        assertTrue(SquadRejoin.rejoin(f.late, f.squad, f.sim));
        assertTrue(f.lateX() < before,
                "the rejoin closes on the squad; it does not acquire a contact"
                        + " and set off after it");
    }

    @Test
    public void reachingCohesionRetiresTheState() {
        Fixture f = Fixture.late(CLOSE_X);

        assertFalse(SquadRejoin.rejoin(f.late, f.squad, f.sim),
                "back inside cohesion, the marine is an ordinary member again and"
                        + " his step runs this very tick");
        assertFalse(f.squad.isRejoining(f.late), "and the state is gone");
    }

    @Test
    public void anEnemyInRangeWithAClearShotIsReturnedFireFromWhereHeStands() {
        Fixture f = Fixture.lateWithEnemyAt(LATE_X, LATE_X + 3, ROW);
        // The engagement escape inside the cohesion pull is per-member and keys
        // on the marine's own target, which a rejoining marine only has because
        // something registered him against it.
        f.sim.combat().setTargetId(f.late, f.enemy);
        float before = f.lateX();

        assertTrue(SquadRejoin.rejoin(f.late, f.squad, f.sim),
                "still rejoining — a firefight is not cohesion");
        assertEquals(before, f.lateX(),
                "but he holds his ground rather than walking away from a target"
                        + " he can hit");
        assertTrue(f.sim.combat().fireTargetId(f.late) != 0L,
                "and he shoots back; return fire is not initiating contact");
    }

    @Test
    public void theKillSwitchDeclinesAndLeavesTheStateAlone() {
        Fixture f = Fixture.late(LATE_X);
        SquadRejoin.setEnabledForEvidence(false);

        assertFalse(SquadRejoin.rejoin(f.late, f.squad, f.sim),
                "a control run hands every tick straight to the step");
        assertTrue(f.squad.isRejoining(f.late),
                "and does not rewrite squad state on its way past");
    }

    /**
     * A leader at the west end, one marine marked rejoining at the east end, and
     * nothing else unless the case asks for it.
     *
     * <p>One tick is played before a case reads anything, because the spatial
     * index and the occupancy map are filled by the tick rather than by the
     * spawn, and a marine who cannot be seen by a range query cannot return
     * fire. That tick also runs the ordinary dispatch, which will have run this
     * very reflex once — so the late arrival's path is cleared afterwards and
     * the state re-marked, leaving each case to measure its own call.
     */
    private static final class Fixture {

        private final BattleSimulation sim;
        private final Squad squad;
        private final long late;
        private final long enemy;

        private Fixture(int lateX, int enemyX, int enemyY, boolean withEnemy) {
            NavigationGrid grid = new NavigationGrid(W, H);
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
            }
            sim = new BattleSimulation(grid, new CellTopology(W, H));
            sim.setMissionCompletionEnabled(false);
            int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
            squad = sim.getSquad(squadId);
            // The leader is planted: a case measuring where the late arrival
            // went relative to him cannot have him walking off after the enemy.
            EntitySpec leaderSpec = new EntitySpec("leader", Faction.MARINE,
                    UnitType.MARINE, LEADER_X, ROW).squad(squadId);
            leaderSpec.moveSpeed = 0f;
            long leader = sim.spawn(leaderSpec);
            late = sim.spawn(new EntitySpec("late", Faction.MARINE,
                    UnitType.MARINE, lateX, ROW).squad(squadId));
            squad.leaderId = leader;
            squad.originalSize = 2;
            squad.aliveMembers = 2;
            if (withEnemy) {
                EntitySpec spec = new EntitySpec("enemy", Faction.DEFENDER,
                        UnitType.MARINE, enemyX, enemyY);
                spec.moveSpeed = 0f;
                enemy = sim.spawn(spec);
            } else {
                enemy = 0L;
            }
            squad.markRejoining(late);
            sim.advance(BattleSimulation.TICK_DT);
            sim.clearPath(late);
            sim.combat().setTargetId(late, 0L);
            squad.markRejoining(late);
        }

        static Fixture late(int lateX) {
            return new Fixture(lateX, 0, 0, false);
        }

        static Fixture lateWithEnemyAt(int lateX, int enemyX, int enemyY) {
            return new Fixture(lateX, enemyX, enemyY, true);
        }

        float lateX() {
            return sim.world().x(late);
        }
    }
}
