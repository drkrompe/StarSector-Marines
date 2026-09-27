package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadAlertLevel;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plan construction and ownership on a tiny grid, without advancing a battle. */
class ReinforceContactRetentionTest {
    private String previousRetention;

    @BeforeEach
    void enableRetention() {
        previousRetention = System.getProperty(ReinforceContact.RETAIN_FLANK_PLANS_PROPERTY);
        System.setProperty(ReinforceContact.RETAIN_FLANK_PLANS_PROPERTY, "true");
    }

    @AfterEach
    void restoreControls() {
        if (previousRetention == null) System.clearProperty(ReinforceContact.RETAIN_FLANK_PLANS_PROPERTY);
        else System.setProperty(ReinforceContact.RETAIN_FLANK_PLANS_PROPERTY, previousRetention);
        TickInnerProfile.releaseCurrentThread();
    }

    @Test
    void repeatedPlanConstructionReusesDestinationWithoutRepeatingRouteProofs() {
        Fixture fixture = new Fixture();
        fixture.replan();
        assertTrue(fixture.profile.countOf(TickInnerProfile.Bucket.PATHFIND) > 0);
        FlankApproach first = (FlankApproach) fixture.squad.currentPlan.currentStep().action;
        fixture.tick += 65;
        fixture.replan();
        assertEquals(1, fixture.profile.countOf(TickInnerProfile.Bucket.FLANK_PLAN_REUSE));
        assertEquals(0, fixture.profile.countOf(TickInnerProfile.Bucket.PATHFIND));
        FlankApproach next = (FlankApproach) fixture.squad.currentPlan.currentStep().action;
        assertEquals(first.waypointX(), next.waypointX());
        assertEquals(first.waypointY(), next.waypointY());
        fixture.tick += 65;
        fixture.replan();
        assertEquals(1, fixture.profile.countOf(TickInnerProfile.Bucket.FLANK_PLAN_REUSE),
                "the returned replacement plan must become the next retained owner");
    }

    @Test
    void replacementCompletionAndGoalExitEachRequireFreshSelection() {
        for (int interruption = 0; interruption < 3; interruption++) {
            Fixture fixture = new Fixture();
            fixture.replan();
            switch (interruption) {
                case 0 -> fixture.squad.currentPlan = new SquadPlan(List.of(
                        new SquadPlan.Step(new FlankApproach(24, 11))));
                case 1 -> {
                    fixture.squad.currentPlan.advance();
                    assertEquals(0f, ReinforceContact.INSTANCE.relevance(
                            WorldState.EMPTY.with(Predicate.HAS_TARGET, true), fixture.squad, fixture.view));
                }
                case 2 -> fixture.squad.currentGoal = EliminateEnemiesGoal.INSTANCE;
                default -> throw new AssertionError();
            }
            fixture.tick++;
            fixture.replan();
            fixture.assertFreshSelection();
        }
    }

    @Test
    void topologyChangeAndDisabledControlRequireFreshSelection() {
        Fixture fixture = new Fixture();
        fixture.replan();
        fixture.grid.blockSharedEdge(0, 0, Direction.E);
        fixture.tick++;
        fixture.replan();
        fixture.assertFreshSelection();

        System.setProperty(ReinforceContact.RETAIN_FLANK_PLANS_PROPERTY, "false");
        fixture.tick++;
        fixture.replan();
        fixture.assertFreshSelection();

        System.setProperty(ReinforceContact.RETAIN_FLANK_PLANS_PROPERTY, "true");
        fixture.tick++;
        fixture.replan();
        fixture.assertFreshSelection();
    }

    private static final class Fixture {
        final NavigationGrid grid = new NavigationGrid(40, 40);
        final Squad squad = new Squad(1, Faction.MARINE);
        final TickInnerProfile profile = new TickInnerProfile();
        final BattleView view;
        int tick = 10;

        Fixture() {
            for (int y = 0; y < 40; y++) {
                for (int x = 0; x < 40; x++) grid.setWalkableFloor(x, y);
            }
            squad.centroidX = squad.centroidY = 3.5f;
            squad.lastSeenEnemyX = 24;
            squad.lastSeenEnemyY = 3;
            squad.alertLevel = SquadAlertLevel.SUSPICIOUS;
            view = (BattleView) Proxy.newProxyInstance(BattleView.class.getClassLoader(),
                    new Class<?>[]{BattleView.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getGrid" -> grid;
                        case "getSquads" -> List.of(squad);
                        case "getSimTickIndex" -> tick;
                        case "resolveUnit" -> 0L;
                        case "squadMemberCount" -> 0;
                        default -> throw new AssertionError("Unexpected query: " + method.getName());
                    });
        }

        void replan() {
            profile.reset();
            TickInnerProfile.setCurrent(profile);
            squad.currentPlan = ReinforceContact.INSTANCE.customPlan(squad, view);
            squad.currentGoal = ReinforceContact.INSTANCE;
        }

        void assertFreshSelection() {
            assertEquals(0, profile.countOf(TickInnerProfile.Bucket.FLANK_PLAN_REUSE));
            assertEquals(1, profile.countOf(TickInnerProfile.Bucket.FLANK_PLAN_SELECT));
            assertTrue(profile.countOf(TickInnerProfile.Bucket.PATHFIND) > 0);
        }
    }
}
