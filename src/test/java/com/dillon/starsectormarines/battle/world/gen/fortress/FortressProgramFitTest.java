package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A program authored for an installation is trimmed to the map it lands on,
 * and what it gives up is stated rather than whatever the packer happened to
 * drop.
 *
 * <p>The garrison program's envelope is around five thousand cells against a
 * 144x80 map's eleven and a half thousand, so an unfitted garrison takes half a
 * skirmish map and leaves nothing to approach it through. Trimming it is one
 * decision and dropping buildings at the packer is another: the packer's answer
 * depends on the shape a claim happened to grow into, so the same world would
 * lose a different building on every seed.
 */
class FortressProgramFitTest {

    private static final FortressProgram GARRISON = FortressProgram.garrison();

    /**
     * The ladder, written out as the programs it passes through.
     *
     * <p>Stated independently of {@link FortressProgram#fittedTo} rather than
     * derived from it, so it is the order that is pinned and not merely the
     * arithmetic.
     */
    private static final List<FortressProgram> RUNGS = List.of(
            GARRISON,
            GARRISON.withAirfields(0),
            GARRISON.withAirfields(0).with(RoomPurpose.BARRACKS, 2),
            GARRISON.withAirfields(0).with(RoomPurpose.BARRACKS, 2)
                    .with(RoomPurpose.CONTROL_ROOM, 2),
            GARRISON.withAirfields(0).with(RoomPurpose.BARRACKS, 2)
                    .with(RoomPurpose.CONTROL_ROOM, 2)
                    .with(RoomPurpose.STOCKROOM, 1).with(RoomPurpose.PARTS_CAGE, 1),
            GARRISON.withAirfields(0).with(RoomPurpose.BARRACKS, 2)
                    .with(RoomPurpose.CONTROL_ROOM, 2)
                    .with(RoomPurpose.STOCKROOM, 1).with(RoomPurpose.PARTS_CAGE, 1)
                    .with(RoomPurpose.ARMORY, 1),
            GARRISON.withAirfields(0).with(RoomPurpose.BARRACKS, 1)
                    .with(RoomPurpose.CONTROL_ROOM, 2)
                    .with(RoomPurpose.STOCKROOM, 1).with(RoomPurpose.PARTS_CAGE, 1)
                    .with(RoomPurpose.ARMORY, 1));

    /** A program that fits is the program, untouched and not merely equal to it. */
    @Test
    void aProgramThatFitsIsHandedBackUnchanged() {
        assertSame(GARRISON, GARRISON.fittedTo(GARRISON.envelopeArea()),
                "a garrison that fits the ground it was given was still rebuilt, so a "
                        + "mission's authored program is not the one that gets built");
        assertSame(GARRISON, GARRISON.fittedTo(Integer.MAX_VALUE));
    }

    /**
     * Each rung is reached in turn, and no further.
     *
     * <p>Fed exactly the ground one rung needs, the fit stops on that rung: the
     * budget is the instrument, and walking it one step at a time is what shows
     * the order rather than the destination.
     */
    @Test
    void theLadderIsWalkedInOrder() {
        for (int rung = 0; rung < RUNGS.size(); rung++) {
            FortressProgram expected = RUNGS.get(rung);
            assertEquals(expected, GARRISON.fittedTo(expected.envelopeArea()),
                    "given exactly the ground rung " + rung + " needs, the fit came out "
                            + "somewhere else on the ladder");
        }
    }

    /** The airfield goes first, because a lot is the single largest thing a garrison owes. */
    @Test
    void theAirfieldIsGivenUpBeforeAnyBuilding() {
        FortressProgram trimmed = GARRISON.fittedTo(GARRISON.envelopeArea() - 1);
        assertEquals(0, trimmed.airfields(),
                "the first thing given up was not the airfield lot");
        assertEquals(GARRISON.floorArea(), trimmed.floorArea(),
                "a building was given up before the airfield was, so the ladder is not in "
                        + "the order it is written in");
    }

    /** Every rung is smaller than the one above it, or the ladder does not converge. */
    @Test
    void everyRungIsSmallerThanTheOneAboveIt() {
        for (int rung = 1; rung < RUNGS.size(); rung++) {
            assertTrue(RUNGS.get(rung).envelopeArea() < RUNGS.get(rung - 1).envelopeArea(),
                    "rung " + rung + " needs as much ground as rung " + (rung - 1)
                            + ", so walking the ladder buys nothing");
        }
    }

    /**
     * What a garrison is, is never traded away.
     *
     * <p>A budget of one cell exhausts the ladder, and the program that comes
     * back is still an installation: somewhere to command from, a way in, a
     * motor pool and somewhere to eat. Over budget at the end is allowed on
     * purpose — the packer records what it could not place, where a program
     * trimmed past this would be a garrison nobody could recognise.
     */
    @Test
    void whatMakesItAGarrisonSurvivesAnyBudget() {
        FortressProgram floor = GARRISON.fittedTo(1);
        for (RoomPurpose purpose : List.of(RoomPurpose.KEEP_THRONE, RoomPurpose.KEEP_ENTRY,
                RoomPurpose.VEHICLE_BAY, RoomPurpose.MESS_HALL)) {
            assertEquals(1, floor.countOf(purpose), "a garrison trimmed to nothing gave up "
                    + "its " + purpose + ", which is part of what makes it a garrison");
        }
        assertEquals(RUNGS.get(RUNGS.size() - 1), floor,
                "the bottom of the ladder is not the program the ladder is written to reach");
    }

    /**
     * A rung never raises a count.
     *
     * <p>A mission that ordered one barrack block has said so, and a ladder
     * trimming the program is the last thing that should hand it a second.
     */
    @Test
    void aRungNeverAddsToAProgramAlreadyBelowIt() {
        FortressProgram lean = GARRISON.with(RoomPurpose.BARRACKS, 1)
                .with(RoomPurpose.CONTROL_ROOM, 1).withAirfields(0);
        FortressProgram fitted = lean.fittedTo(1);
        assertEquals(1, fitted.countOf(RoomPurpose.BARRACKS),
                "the ladder raised a barrack count it was supposed to be lowering");
        assertEquals(1, fitted.countOf(RoomPurpose.CONTROL_ROOM),
                "the ladder raised a guard-post count it was supposed to be lowering");
    }

    /** A purpose the program does not owe is skipped, not ordered into existence. */
    @Test
    void aPurposeTheProgramDoesNotOweIsSkipped() {
        FortressProgram noStores = GARRISON.with(RoomPurpose.STOCKROOM, 0);
        FortressProgram fitted = noStores.fittedTo(1);
        assertEquals(0, fitted.countOf(RoomPurpose.STOCKROOM),
                "trimming a program gave it back a store room it had been ordered without");
    }
}
