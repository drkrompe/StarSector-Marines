package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A fortress owes none, one, or several of a thing, and the program is where
 * that is said.
 *
 * <p>Mission design is the reason. A depot with no motor pool and a forward
 * base with two airfields are the same generator asked a different question,
 * and the difference has to be expressible as a count rather than as a second
 * recipe. The count already existed on {@link FortressBuilding}; what did not
 * was any way to state one from outside, or to say it about an airfield at all.
 *
 * <p><b>Zero is the case worth pinning.</b> A program that merely fails to place
 * something is indistinguishable from one that never asked for it, so the
 * assertion is that asking for none emits none — not that none appear.
 */
class FortressProgramCountsTest {

    @Test
    void askingForNoneOfSomethingEmitsNoneOfIt() {
        FortressProgram base = FortressProgram.garrison();
        assertTrue(base.expanded().stream()
                        .anyMatch(b -> b.purpose() == RoomPurpose.VEHICLE_BAY),
                "the garrison program is supposed to owe a motor pool to begin with, "
                        + "so removing one proves nothing");

        FortressProgram noBay = base.with(RoomPurpose.VEHICLE_BAY, 0);
        assertEquals(0, noBay.expanded().stream()
                .filter(b -> b.purpose() == RoomPurpose.VEHICLE_BAY).count(),
                "a fortress ordered without a motor pool still owes one");
        assertTrue(noBay.floorArea() < base.floorArea(),
                "dropping a building did not make the program smaller, so the count is "
                        + "not reaching the sizing and the ward will be built for a "
                        + "building that is not coming");
    }

    @Test
    void askingForSeveralEmitsSeveral() {
        FortressProgram four = FortressProgram.garrison().with(RoomPurpose.BARRACKS, 4);
        assertEquals(4, four.expanded().stream()
                .filter(b -> b.purpose() == RoomPurpose.BARRACKS).count());
    }

    /**
     * The airfield is a lot rather than a building, so it is counted separately
     * — and it has to reach the sizing, because a lot the ward was not sized to
     * afford is taken out of the ground the buildings needed.
     */
    @Test
    void airfieldsAreCountedAndPaidFor() {
        FortressProgram one = FortressProgram.garrison();
        assertEquals(1, one.airfields());

        FortressProgram none = one.withAirfields(0);
        FortressProgram two = one.withAirfields(2);
        assertTrue(none.envelopeArea() < one.envelopeArea(),
                "a fortress with no airfield is still sized to hold one");
        assertTrue(two.envelopeArea() > one.envelopeArea(),
                "a second airfield costs no ground, so the ward will come up short by "
                        + "exactly what the second lot occupies");
        assertEquals(one.buildingGround(), two.buildingGround(),
                "airfields moved the ground the buildings need; a lot is not a building "
                        + "and must not be scaled by the packing slack");
    }

    /** A count for a purpose the program does not owe is a mistake, not a silent addition. */
    @Test
    void aCountForSomethingTheProgramDoesNotOweIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> FortressProgram.garrison().with(RoomPurpose.MEDICAL_RECEPTION, 2));
        assertThrows(IllegalArgumentException.class,
                () -> FortressProgram.garrison().with(RoomPurpose.BARRACKS, -1));
        assertThrows(IllegalArgumentException.class,
                () -> FortressProgram.garrison().withAirfields(-1));
    }

    /** The ward program is the garrison minus its keep, because the compound is the keep. */
    @Test
    void theWardProgramHasNoKeepOfItsOwn() {
        assertEquals(0, FortressProgram.ward().expanded().stream()
                .filter(b -> b.purpose() == RoomPurpose.KEEP_THRONE).count());
        assertEquals(1, FortressProgram.garrison().expanded().stream()
                .filter(b -> b.purpose() == RoomPurpose.KEEP_THRONE).count());
    }
}
