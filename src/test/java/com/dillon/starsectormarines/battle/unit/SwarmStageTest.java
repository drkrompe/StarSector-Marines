package com.dillon.starsectormarines.battle.unit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A growth stage's size: on the grid, in a shove and in a capsule. */
class SwarmStageTest {

    @Test
    void everyStageFitsTheOneCellField() {
        for (UnitType type : new UnitType[]{UnitType.SWARM_RUNNER, UnitType.SWARM_BRUTE, UnitType.SWARM_SPITTER}) {
            for (SwarmStage stage : SwarmStage.values()) {
                assertTrue(stage.simRadius(type.radius) <= SwarmStage.SIM_RADIUS_CAP, type + " " + stage);
            }
            assertEquals(type.radius, SwarmStage.ADULT.simRadius(type.radius));
        }
    }

    @Test
    void anElderBruteIsCappedOnTheGridAndShovedAtFullSize() {
        assertEquals(SwarmStage.SIM_RADIUS_CAP,
                BodyRadius.resolve(null, null, null, UnitType.SWARM_BRUTE, SwarmStage.ELDER));
        float shove = BodyCapsule.radius(UnitType.SWARM_BRUTE, SwarmStage.SIM_RADIUS_CAP, SwarmStage.ELDER);
        assertEquals(UnitType.SWARM_BRUTE.radius * SwarmStage.ELDER.scale, shove, 1e-6f);
        assertTrue(shove > SwarmStage.SIM_RADIUS_CAP);
    }

    @Test
    void aRunnersCapsuleScalesWithItsStage() {
        for (SwarmStage stage : SwarmStage.values()) {
            float k = stage.scale;
            assertEquals(BodyCapsule.halfLength(UnitType.SWARM_RUNNER, null) * k,
                    BodyCapsule.halfLength(UnitType.SWARM_RUNNER, stage), 1e-6f);
            assertEquals(BodyCapsule.forwardOffset(UnitType.SWARM_RUNNER, null) * k,
                    BodyCapsule.forwardOffset(UnitType.SWARM_RUNNER, stage), 1e-6f);
            assertEquals(BodyCapsule.radius(UnitType.SWARM_RUNNER, 0f, null) * k,
                    BodyCapsule.radius(UnitType.SWARM_RUNNER, 0f, stage), 1e-6f);
        }
    }

    @Test
    void aBodyWithoutAStageIsAnAdultOfItsType() {
        assertEquals(UnitType.MARINE.radius, BodyRadius.resolve(null, null, null, UnitType.MARINE, null));
        assertEquals(UnitType.MARINE.radius, BodyCapsule.radius(UnitType.MARINE, UnitType.MARINE.radius, null));
    }
}
