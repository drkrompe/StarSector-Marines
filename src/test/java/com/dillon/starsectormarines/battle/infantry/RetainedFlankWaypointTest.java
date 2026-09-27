package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RetainedFlankWaypointTest {
    private static final RetainedFlankWaypoint.Cell WAYPOINT = new RetainedFlankWaypoint.Cell(15, 15);

    @Test
    void periodicReplansReuseUntilOriginalStaggeredDeadline() {
        for (int squadId : new int[]{0, 60, 61}) {
            Fixture fixture = new Fixture(squadId);
            fixture.remember();
            int deadline = 10 + 300 + Math.floorMod(squadId, 61);
            for (int tick = 11; tick < deadline; tick++) {
                assertEquals(WAYPOINT, fixture.lookup(tick));
            }
            assertNull(fixture.lookup(deadline), "hits must never renew the lifetime");
            assertNull(fixture.lookup(11), "an expired choice cannot resurrect on a later lookup");
        }
    }

    @Test
    void movementIsMeasuredFromOriginalAnchorsRatherThanPreviousHit() {
        for (int anchor = 0; anchor < 3; anchor++) {
            Fixture fixture = new Fixture(1);
            fixture.remember();
            for (int movement = 1; movement <= 3; movement++) {
                fixture.moveAnchor(anchor, movement, 0);
                assertEquals(WAYPOINT, fixture.lookup(10 + movement));
            }
            fixture.moveAnchor(anchor, 4, 0);
            assertNull(fixture.lookup(14));
            fixture.moveAnchor(anchor, 0, 0);
            assertNull(fixture.lookup(15), "moving back cannot resurrect a retired choice");
        }
    }

    @Test
    void diagonalDisplacementUsesDistanceRatherThanIndependentAxisBounds() {
        for (int anchor = 0; anchor < 3; anchor++) {
            Fixture fixture = new Fixture(1);
            fixture.remember();
            fixture.moveAnchor(anchor, 2, 2);
            assertNotNull(fixture.lookup(11));
            fixture.moveAnchor(anchor, 3, 3);
            assertNull(fixture.lookup(12));
        }
    }

    @Test
    void semanticAssignmentReissueSurvivesButDifferentContextRetires() {
        Fixture fixture = new Fixture(1);
        fixture.assignment = ObjectiveAssignment.support(1);
        fixture.remember();
        fixture.assignment = ObjectiveAssignment.support(1);
        assertEquals(WAYPOINT, fixture.lookup(11));
        fixture.assignment = ObjectiveAssignment.attackMove(1, 18, 18);
        assertNull(fixture.lookup(12));
        fixture.assignment = ObjectiveAssignment.support(1);
        assertNull(fixture.lookup(13));
    }

    @Test
    void contactIdentityAndMovementModeEachRetireTheChoice() {
        Fixture changedContact = new Fixture(1);
        changedContact.remember();
        changedContact.contactId++;
        assertNull(changedContact.lookup(11));

        Fixture changedMode = new Fixture(1);
        changedMode.remember();
        changedMode.cardinal = false;
        assertNull(changedMode.lookup(11));
    }

    @Test
    void gridReplacementOrDistantTopologyChangeRetiresUnchangedDestination() {
        Fixture replacement = new Fixture(1);
        replacement.remember();
        replacement.grid = openGrid();
        assertNull(replacement.lookup(11), "equal geometry in a different battle is not the same grid");

        Fixture topology = new Fixture(1);
        topology.remember();
        topology.grid.blockSharedEdge(1, 1, Direction.E);
        assertNull(topology.lookup(11), "a route edge can change while the endpoint remains legal");
    }

    @Test
    void blockedOrDoorwayDestinationsAreRetired() {
        Fixture blocked = new Fixture(1);
        blocked.remember();
        blocked.grid.setWalkable(WAYPOINT.x(), WAYPOINT.y(), false);
        assertNull(blocked.lookup(11));

        Fixture doorway = new Fixture(1);
        doorway.remember();
        doorway.grid.setDoorway(WAYPOINT.x(), WAYPOINT.y(), true);
        assertNull(doorway.lookup(11));
    }

    @Test
    void refusalAndInvalidEndpointsNeverAcquireLifetime() {
        Fixture fixture = new Fixture(1);
        for (int[] rejected : new int[][]{{2, 2}, {-1, 15}, {20, 15}, {14, 14}, {16, 16}}) {
            fixture.grid.setWalkable(14, 14, false);
            fixture.grid.setDoorway(16, 16, true);
            fixture.remember();
            fixture.remember(rejected[0], rejected[1]);
            assertNull(fixture.lookup(11), "a refusal also clears any previous accepted choice");
        }
    }

    @Test
    void explicitGoalExitAndClockRewindRetireWithoutSharingAcrossSquads() {
        Fixture fixture = new Fixture(1);
        fixture.remember();
        assertNull(new Fixture(2).lookup(11));
        fixture.retained.clear();
        assertNull(fixture.lookup(11));
        fixture.remember();
        assertNull(fixture.lookup(9));
        assertNull(fixture.lookup(11));
    }

    private static NavigationGrid openGrid() {
        NavigationGrid grid = new NavigationGrid(20, 20);
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    private static final class Fixture {
        final RetainedFlankWaypoint retained;
        NavigationGrid grid = openGrid();
        ObjectiveAssignment assignment;
        long contactId = 42L;
        int contactX = 10, contactY = 10;
        int originX = 2, originY = 2;
        int rawX = 15, rawY = 15;
        boolean cardinal = true;

        Fixture(int squadId) { retained = new RetainedFlankWaypoint(squadId); }

        void remember() { remember(WAYPOINT.x(), WAYPOINT.y()); }

        void remember(int x, int y) {
            retained.remember(grid, assignment, contactId, contactX, contactY,
                    originX, originY, rawX, rawY, cardinal, 10, x, y);
        }

        RetainedFlankWaypoint.Cell lookup(int tick) {
            return retained.lookup(grid, assignment, contactId, contactX, contactY,
                    originX, originY, rawX, rawY, cardinal, tick);
        }

        void moveAnchor(int anchor, int dx, int dy) {
            switch (anchor) {
                case 0 -> { contactX = 10 + dx; contactY = 10 + dy; }
                case 1 -> { originX = 2 + dx; originY = 2 + dy; }
                case 2 -> { rawX = 15 + dx; rawY = 15 + dy; }
                default -> throw new AssertionError("Unexpected anchor " + anchor);
            }
        }
    }
}
