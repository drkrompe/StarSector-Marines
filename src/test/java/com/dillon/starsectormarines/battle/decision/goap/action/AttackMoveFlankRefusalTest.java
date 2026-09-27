package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.infantry.ReinforceContact;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadAssaultPicture;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttackMoveFlankRefusalTest {
    @Test
    void refusedLeaderOriginFallsBackToObjectiveDespiteDispersedCentroidAndMemoReuse() {
        String before = System.getProperty(ReinforceContact.BUDGET_FLANK_SELECTION_PROPERTY);
        System.setProperty(ReinforceContact.BUDGET_FLANK_SELECTION_PROPERTY, "true");
        try {
            Fixture fixture = new Fixture(true);
            assertTrue(Math.abs(fixture.squad.centroidX - fixture.roster.world().x(fixture.leader)) > 1);
            assertArrayEquals(new int[]{35, 5}, fixture.action.maneuverAim(
                    fixture.squad, fixture.assault, fixture.view));
            assertTrue(fixture.squad.flankAim.refused());
            assertArrayEquals(new int[]{35, 5}, fixture.action.maneuverAim(
                    fixture.squad, fixture.assault, fixture.view));
        } finally {
            if (before == null) System.clearProperty(ReinforceContact.BUDGET_FLANK_SELECTION_PROPERTY);
            else System.setProperty(ReinforceContact.BUDGET_FLANK_SELECTION_PROPERTY, before);
        }
    }

    @Test
    void acceptedFlankStillUsesTheVerifiedWaypointInsteadOfTheObjective() {
        Fixture fixture = new Fixture(false);
        assertArrayEquals(new int[]{20, 34}, fixture.action.maneuverAim(
                fixture.squad, fixture.assault, fixture.view));
        assertFalse(fixture.squad.flankAim.refused());
    }

    private static final class Fixture {
        final NavigationGrid grid = new NavigationGrid(40, 50);
        final UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(40, 50), null);
        final Squad squad = new Squad(1, Faction.MARINE);
        final AttackMove action = new AttackMove(35, 5);
        final long leader;
        final SquadAssaultPicture assault;
        final BattleView view;

        Fixture(boolean blocked) {
            for (int y = 0; y < 50; y++) for (int x = 0; x < 40; x++) grid.setWalkableFloor(x, y);
            if (blocked) for (int y = 0; y < 50; y++) grid.setWalkable(10, y, false);
            leader = roster.spawn(new EntitySpec("leader", Faction.MARINE, UnitType.MARINE, 2, 30));
            long contact = roster.spawn(new EntitySpec("contact", Faction.DEFENDER, UnitType.MARINE, 20, 20));
            squad.leaderId = leader;
            squad.centroidX = 12.5f;
            squad.centroidY = 30.5f;
            assault = new SquadAssaultPicture(10, SquadAssaultPicture.Role.MANEUVER, contact, 2, 1, 0);
            view = (BattleView) Proxy.newProxyInstance(BattleView.class.getClassLoader(),
                    new Class<?>[]{BattleView.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getGrid" -> grid;
                        case "world" -> roster.world();
                        case "squad" -> roster.squad();
                        case "getSimTickIndex" -> 10;
                        case "resolveUnit" -> args[0];
                        case "isRiding" -> false;
                        default -> throw new AssertionError("Unexpected query " + method.getName());
                    });
        }
    }
}
