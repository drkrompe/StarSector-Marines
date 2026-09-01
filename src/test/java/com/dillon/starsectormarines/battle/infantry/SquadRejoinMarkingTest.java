package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadFormUpSystem;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which arrivals are late.
 *
 * <p>Each case is one of the three conditions failing on its own, because the
 * rule is a conjunction and a test that only set up the marking case would say
 * nothing about the three ways a perfectly ordinary landing must <em>not</em>
 * acquire the state. The one that matters most is the third: the last marine of
 * a normal assembly lands beside squadmates who have not moved, and marking him
 * would put a rejoin on every full squad in every battle.
 */
public class SquadRejoinMarkingTest {

    private static final int LZ_X = 10;
    private static final int LZ_Y = 10;
    /** Comfortably past {@link InfantryCohesion#COHESION_RADIUS}. */
    private static final int ADVANCED_X = LZ_X + 40;

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
    public void aMarineLandingBesideAStillFormingSquadIsNotMarked() {
        Fixture f = Fixture.squadAt(LZ_X, LZ_Y, 4, 12);

        assertFalse(f.land(LZ_X, LZ_Y),
                "an ordinary second lift into a squad still waiting at its own"
                        + " landing zone is not a late arrival");
        assertFalse(f.squad.isRejoining(f.lastLanded));
    }

    @Test
    public void aMarineLandingFarFromASquadPastFormUpIsMarked() {
        Fixture f = Fixture.squadAt(ADVANCED_X, LZ_Y, 4, 12);
        f.squad.formUpElapsed = SquadFormUpSystem.FORM_UP_TIMEOUT;

        assertTrue(f.land(LZ_X, LZ_Y),
                "the squad gave up waiting and stepped off; this marine has to"
                        + " cross to it");
        assertTrue(f.squad.isRejoining(f.lastLanded));
    }

    @Test
    public void aMarineLandingBesideASquadPastFormUpIsNotMarked() {
        Fixture f = Fixture.squadAt(LZ_X, LZ_Y, 12, 12);

        assertFalse(f.land(LZ_X + 1, LZ_Y),
                "the last marine of a normal assembly lands inside cohesion of a"
                        + " squad that has not moved, which is an arrival rather"
                        + " than a late one");
    }

    @Test
    public void theFirstMarineOffTheFirstShuttleHasNobodyToRejoin() {
        Fixture f = Fixture.emptySquad();
        // Past the gate, so the answer comes from "nobody is standing there"
        // rather than from the squad still waiting.
        f.squad.formUpElapsed = SquadFormUpSystem.FORM_UP_TIMEOUT;

        assertFalse(f.land(LZ_X, LZ_Y),
                "there is no squad on the ground yet to close on");
    }

    @Test
    public void aGeneratedSquadNeverCarriesTheState() {
        Fixture f = Fixture.squadAt(ADVANCED_X, LZ_Y, 4, 12);
        f.squad.campaignSquadId = null;
        f.squad.expectedSize = 0;

        assertFalse(f.land(LZ_X, LZ_Y),
                "a squad that does not assemble across lifts has no form-up gate"
                        + " and would otherwise read as past it on every spawn");
    }

    @Test
    public void theKillSwitchLeavesEveryArrivalUnmarked() {
        Fixture f = Fixture.squadAt(ADVANCED_X, LZ_Y, 4, 12);
        f.squad.formUpElapsed = SquadFormUpSystem.FORM_UP_TIMEOUT;
        SquadRejoin.setEnabledForEvidence(false);

        assertFalse(f.land(LZ_X, LZ_Y),
                "a control run marks nobody, so the slot-assignment and arrival"
                        + " exclusions cannot fire either");
    }

    /**
     * A campaign squad already standing somewhere, and one more marine put down
     * at a named cell. The roster is the bare service rather than a whole
     * simulation: the rule reads member positions, the leader, and the squad's
     * own form-up fields, and nothing else in a battle bears on it.
     */
    private static final class Fixture {

        private final UnitRosterService roster =
                new UnitRosterService(new UnitSpatialIndex(128, 128), null);
        private final Squad squad;
        private final int squadId;
        private int landed;
        private long lastLanded;

        private Fixture(int expectedSize) {
            this.squadId = roster.mintSquad(Faction.MARINE, UnitType.MARINE);
            this.squad = roster.getSquad(squadId);
            squad.campaignSquadId = "cs-1";
            squad.campaignLabel = "Squad 01";
            squad.expectedSize = expectedSize;
        }

        static Fixture emptySquad() {
            return new Fixture(12);
        }

        /** {@code alreadyLanded} marines standing at ({@code x}, {@code y}), the first of them leading. */
        static Fixture squadAt(int x, int y, int alreadyLanded, int expectedSize) {
            Fixture fixture = new Fixture(expectedSize);
            for (int i = 0; i < alreadyLanded; i++) {
                long member = fixture.spawn(x + i % 3, y + i / 3);
                if (i == 0) fixture.squad.leaderId = member;
            }
            return fixture;
        }

        /** One more marine set down, through the production rule. */
        boolean land(int x, int y) {
            lastLanded = spawn(x, y);
            return SquadRejoin.markIfLateArrival(squad, lastLanded, x, y, roster);
        }

        private long spawn(int x, int y) {
            long id = roster.spawn(new EntitySpec("m" + landed, Faction.MARINE,
                    UnitType.MARINE, x, y).squad(squadId));
            // originalSize counts marines actually deboarded, and the payload
            // increments it before the spawn — so the fixture does too, or
            // formingUp reads a squad that landed nobody.
            landed++;
            squad.originalSize = landed;
            return id;
        }
    }
}
