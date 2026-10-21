package com.dillon.starsectormarines.battle.appearance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import org.junit.jupiter.api.Test;

class WorkMotionTest {

    @Test
    void theFixtureRefinesWhatAJobLooksLike() {
        assertEquals(WorkMotion.WELD, WorkMotion.of(Affordance.SERVICE, null));
        assertEquals(WorkMotion.STIR, WorkMotion.of(Affordance.COOK, "doodad.galley-range"));
        assertEquals(WorkMotion.CHOP, WorkMotion.of(Affordance.COOK, "doodad.galley-prep-bench"));
        assertEquals(WorkMotion.CONSOLE, WorkMotion.of(Affordance.COOK, "doodad.galley-servery-counter"));
        assertEquals(WorkMotion.WRENCH, WorkMotion.of(Affordance.TEND, "ship-engineering.generator"));
        assertEquals(WorkMotion.CONSOLE, WorkMotion.of(Affordance.TEND, "ship-engineering.control"));
        // A ward bed is an assembly: the bed and its linen share the cell.
        assertEquals(WorkMotion.TREAT,
                WorkMotion.of(Affordance.TREAT, "sickbay-clinic.bed sickbay-clinic.linen-high"));
        assertEquals(WorkMotion.CONSOLE, WorkMotion.of(Affordance.TREAT, "sickbay-clinic.station"));
        assertEquals(WorkMotion.RUN, WorkMotion.of(Affordance.EXERCISE, "ship-gym.treadmill"));
        assertEquals(WorkMotion.ROW, WorkMotion.of(Affordance.EXERCISE, "ship-gym.rower"));
        assertEquals(WorkMotion.ROW, WorkMotion.of(Affordance.EXERCISE, "doodad.gym-ergometer"));
        assertEquals(WorkMotion.PRESS, WorkMotion.of(Affordance.EXERCISE, "ship-gym.bench-press"));
        assertEquals(WorkMotion.LIFT, WorkMotion.of(Affordance.EXERCISE, null));
    }

    @Test
    void everyHandsOnJobHasAMotionAndNoOtherJobDoes() {
        for (Affordance job : Affordance.values()) {
            WorkMotion motion = WorkMotion.of(job, null);
            if (job.handsOnFixture()) {
                assertTrue(motion.handsOn(), job + " is hands-on but plays " + motion);
            } else if (job != Affordance.EXERCISE) {
                assertEquals(WorkMotion.NONE, motion, job + " has no hands on a fixture");
            }
        }
    }

    @Test
    void onlyAWalkBetweenStowageStopsCarriesACrate() {
        assertEquals(WorkMotion.CARRY, WorkMotion.between(Affordance.STOW, Affordance.STOW));
        assertEquals(WorkMotion.NONE, WorkMotion.between(Affordance.STOW, Affordance.MESS));
        assertEquals(WorkMotion.NONE, WorkMotion.between(null, Affordance.STOW));
    }

    @Test
    void theMotionRidesInTheFlagsWithoutDisturbingThem() {
        int flags = LayeredAppearance.FLAG_AT_STATION | LayeredAppearance.FLAG_MOVING;
        for (WorkMotion motion : WorkMotion.values()) {
            int packed = LayeredAppearance.withWorkMotion(flags, motion);
            assertEquals(motion, LayeredAppearance.workMotion(packed));
            assertEquals(flags, LayeredAppearance.withWorkMotion(packed, WorkMotion.NONE));
        }
    }
}
