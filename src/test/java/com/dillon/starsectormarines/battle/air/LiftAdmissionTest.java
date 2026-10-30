package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.marine.BoatFitting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One machine against one carrier, by class and rating. */
class LiftAdmissionTest {

    @Test
    void aRatingAdmitsEveryClassAtOrBelowItsOwn() {
        for (LiftClass payload : LiftClass.values()) {
            for (LiftRating rating : LiftRating.values()) {
                LiftAdmission admission = LiftAdmission.check(payload, rating);
                boolean expected = rating.ordinal() >= payload.ordinal() + 1;
                assertEquals(expected, admission.admitted(), payload + " on " + rating);
                if (expected) {
                    assertNull(admission.refusal());
                } else {
                    assertEquals(rating == LiftRating.NONE
                                    ? LiftAdmission.Refusal.NO_LIFT
                                    : LiftAdmission.Refusal.UNDER_RATED,
                            admission.refusal(), payload + " on " + rating);
                }
            }
        }
    }

    @Test
    void weightClassesFollowTheDirectionDocTable() {
        assertEquals(LiftClass.LIGHT, MechVariant.HOUND.liftClass());
        assertEquals(LiftClass.MEDIUM, MechVariant.WARDEN.liftClass());
        assertEquals(LiftClass.HEAVY, MechVariant.BULWARK.liftClass());
        assertEquals(LiftClass.LIGHT, VehicleType.HEAVY_APC.liftClass());
        assertEquals(LiftClass.MEDIUM, VehicleType.FIRE_SUPPORT_APC.liftClass());
        assertEquals(LiftClass.MEDIUM, VehicleType.MILITIA_TECHNICAL.liftClass());
        assertEquals(LiftClass.HEAVY, VehicleType.BREAKTHROUGH_TANK.liftClass());
    }

    @Test
    void onlyTheValkyrieAndTheHermesLiftAsBuilt() {
        for (ShuttleType pattern : ShuttleType.values()) {
            LiftRating built = switch (pattern) {
                case VALKYRIE -> LiftRating.HEAVY;
                case HERMES -> LiftRating.LIGHT;
                default -> LiftRating.NONE;
            };
            assertEquals(built, pattern.liftRating(), pattern.name());
        }
    }

    @Test
    void aCradledBuffaloTakesAHoundAndRefusesABulwark() {
        FittedBoat buffalo = new FittedBoat(ShuttleType.BUFFALO,
                BoatFitting.STANDARD_PLATING, BoatFitting.STANDARD_DRIVE,
                BoatFitting.LIFT_CRADLE, "b1");

        assertTrue(buffalo.cradled());
        assertTrue(LiftAdmission.check(MechVariant.HOUND.liftClass(), buffalo).admitted());
        assertEquals(LiftAdmission.Refusal.UNDER_RATED,
                LiftAdmission.check(MechVariant.BULWARK.liftClass(), buffalo).refusal());
        assertEquals(LiftAdmission.Refusal.NO_LIFT,
                LiftAdmission.check(MechVariant.HOUND.liftClass(),
                        FittedBoat.standard(ShuttleType.BUFFALO)).refusal());
        assertFalse(FittedBoat.standard(ShuttleType.BUFFALO).cradled());
        assertTrue(LiftAdmission.check(LiftClass.HEAVY, ShuttleType.VALKYRIE).admitted());
    }
}
