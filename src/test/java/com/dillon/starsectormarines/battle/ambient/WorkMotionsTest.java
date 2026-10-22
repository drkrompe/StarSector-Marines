package com.dillon.starsectormarines.battle.ambient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.appearance.WorkMotion;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import org.junit.jupiter.api.Test;

class WorkMotionsTest {

    @Test
    void theFixtureRefinesWhatAJobLooksLike() {
        assertEquals(WorkMotion.WELD, WorkMotions.forJob(Affordance.SERVICE, null));
        assertEquals(WorkMotion.STIR, WorkMotions.forJob(Affordance.COOK, "doodad.galley-range"));
        assertEquals(WorkMotion.CHOP, WorkMotions.forJob(Affordance.COOK, "doodad.galley-prep-bench"));
        assertEquals(WorkMotion.CONSOLE, WorkMotions.forJob(Affordance.COOK, "doodad.galley-servery-counter"));
        assertEquals(WorkMotion.WRENCH, WorkMotions.forJob(Affordance.TEND, "ship-engineering.generator"));
        assertEquals(WorkMotion.CONSOLE, WorkMotions.forJob(Affordance.TEND, "ship-engineering.control"));
        // A ward bed is an assembly: the bed and its linen share the cell.
        assertEquals(WorkMotion.TREAT,
                WorkMotions.forJob(Affordance.TREAT, "sickbay-clinic.bed sickbay-clinic.linen-high"));
        assertEquals(WorkMotion.CONSOLE, WorkMotions.forJob(Affordance.TREAT, "sickbay-clinic.station"));
        assertEquals(WorkMotion.RUN, WorkMotions.forJob(Affordance.EXERCISE, "ship-gym.treadmill"));
        assertEquals(WorkMotion.ROW, WorkMotions.forJob(Affordance.EXERCISE, "ship-gym.rower"));
        assertEquals(WorkMotion.ROW, WorkMotions.forJob(Affordance.EXERCISE, "doodad.gym-ergometer"));
        assertEquals(WorkMotion.PRESS, WorkMotions.forJob(Affordance.EXERCISE, "ship-gym.bench-press"));
        assertEquals(WorkMotion.LIFT, WorkMotions.forJob(Affordance.EXERCISE, null));
        // Asleep only in a bed long enough to lie in.
        assertEquals(WorkMotion.SLEEP,
                WorkMotions.forJob(Affordance.REST, "barracks-bunks.bed barracks-bunks.bedding-low"));
        assertEquals(WorkMotion.SLEEP, WorkMotions.forJob(Affordance.REST, "doodad.residential-bed-v"));
        assertEquals(WorkMotion.NONE, WorkMotions.forJob(Affordance.REST, "doodad.chest-1"));
    }

    @Test
    void everyHandsOnJobHasAMotionAndNoOtherJobDoes() {
        for (Affordance job : Affordance.values()) {
            WorkMotion motion = WorkMotions.forJob(job, null);
            if (job.handsOnFixture()) {
                assertTrue(motion.handsOn(), job + " is hands-on but plays " + motion);
            } else if (job != Affordance.EXERCISE && job != Affordance.LAP) {
                assertEquals(WorkMotion.NONE, motion, job + " has no hands on a fixture");
            }
        }
    }

    @Test
    void aStopWithNoJobOnTheBoardIsShownByWhatItIsDoing() {
        assertEquals(WorkMotion.CONSOLE, WorkMotions.forActivity(AmbientActivity.WORKING));
        assertEquals(WorkMotion.LIFT, WorkMotions.forActivity(AmbientActivity.EXERCISING));
        assertEquals(WorkMotion.NONE, WorkMotions.forActivity(AmbientActivity.INSPECTING));
        assertEquals(WorkMotion.NONE, WorkMotions.forActivity(AmbientActivity.FIRING_PRIMARY));
    }

    @Test
    void onlyAWalkBetweenStowageStopsCarriesACrate() {
        assertEquals(WorkMotion.CARRY, WorkMotions.between(Affordance.STOW, Affordance.STOW));
        assertEquals(WorkMotion.NONE, WorkMotions.between(Affordance.STOW, Affordance.MESS));
        assertEquals(WorkMotion.NONE, WorkMotions.between(null, Affordance.STOW));
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
