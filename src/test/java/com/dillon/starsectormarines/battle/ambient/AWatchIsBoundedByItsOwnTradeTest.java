package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How many people a workplace holds is a question about its trade.
 *
 * <p>A room's capacity used to be its scarcest job, on the grounds that a bay
 * with eight berths and one terminal cannot occupy eight technicians on a
 * rotation that includes the terminal. The arithmetic is right and the
 * conclusion was not: the other jobs are things the same person also does in
 * the same room between machines, and nobody queues for the shortfall, because
 * a full job is passed over rather than waited at.
 *
 * <p>It cost an airfield most of its ground crew. Twelve places to service
 * aircraft, six boards to read them off, and the boards decided — so a field
 * that could occupy twelve hands stood six, and the number the caller asked for
 * was silently reduced by a fact about signage.
 */
class AWatchIsBoundedByItsOwnTradeTest {

    /** One room, big enough to hold whatever the tests put in it. */
    private record Bay(int id, RoomPurpose purpose, int centreX, int centreY)
            implements JobSite {
        @Override
        public boolean contains(int cellX, int cellY) {
            return Math.abs(cellX - centreX) <= 50 && Math.abs(cellY - centreY) <= 50;
        }
    }

    private static final Bay BAY = new Bay(2, RoomPurpose.VEHICLE_BAY, 0, 0);

    /**
     * A bay of {@code machines} stands worked from both flanks, plus one board
     * — the apron's own shape, which is where this was found.
     */
    private static Shift bayWith(int machines, int boards) {
        List<FixtureTask> work = new ArrayList<>();
        boolean[] berthed = new boolean[machines];
        for (int berth = 0; berth < machines; berth++) {
            int x = berth * 4;
            work.add(FixtureTask.servingBerth(x - 1, 0, berth, x, 0));
            work.add(FixtureTask.servingBerth(x + 1, 0, berth, x, 0));
            berthed[berth] = true;
        }
        for (int board = 0; board < boards; board++) {
            work.add(FixtureTask.at(board * 4, 6, Affordance.READOUT, board * 4, 7));
        }
        return Shift.of(CrewRole.MECH_TECH, List.of(BAY), work, berthed,
                AmbientThreatPolicy.HOSTILE_COMBATANT);
    }

    /** The trade decides: six stands worked from both flanks holds twelve. */
    @Test
    void aWorkplaceHoldsWhatItsOwnTradeCanOccupy() {
        assertEquals(12, bayWith(6, 6).capacity(),
                "a dozen places to work on aircraft did not hold a dozen technicians");
    }

    /**
     * And one board does not cap the room at one, which is the failure this
     * replaces in its purest form.
     */
    @Test
    void anIncidentalJobDoesNotCapTheRoom() {
        assertEquals(12, bayWith(6, 1).capacity(),
                "the room was capped by how many terminals it happened to have");
    }

    /**
     * The trade still bounds it, so this is a different rule rather than no
     * rule. Two stands is two machines' worth of work however many boards are
     * bolted to the wall.
     */
    @Test
    void theTradeStillBoundsIt() {
        assertEquals(4, bayWith(2, 9).capacity(),
                "a room with more signage than machines held more hands than work");
    }

    /**
     * Berthing is untouched: quarters hold the people who sleep in them, which
     * is a count of bunks and not of trades.
     */
    @Test
    void quartersAreStillCountedInBunks() {
        List<FixtureTask> work = new ArrayList<>();
        for (int bunk = 0; bunk < 5; bunk++) {
            work.add(FixtureTask.at(bunk, 0, Affordance.REST, bunk, 1));
        }
        work.add(FixtureTask.at(0, 3, Affordance.STOW, 0, 4));
        Bay quarters = new Bay(3, RoomPurpose.CREW_QUARTERS, 0, 0);

        Shift bill = Shift.of(CrewRole.MECH_TECH, List.of(quarters), work,
                new boolean[0], AmbientThreatPolicy.HOSTILE_COMBATANT);

        assertEquals(5, bill.capacity(), "a bunkroom is its racks, not its lockers");
    }
}
