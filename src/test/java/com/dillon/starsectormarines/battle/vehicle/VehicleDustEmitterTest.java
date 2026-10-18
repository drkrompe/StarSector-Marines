package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.fx.EffectsService;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which ground a hull raises dust from, and that a puff's scatter replays. */
class VehicleDustEmitterTest {

    @Test void wheelsRaiseDustFromLooseGroundOnly() {
        assertTrue(VehicleDustEmitter.loose(GroundKind.DIRT));
        assertTrue(VehicleDustEmitter.loose(GroundKind.SAND));
        assertTrue(VehicleDustEmitter.loose(GroundKind.RUBBLE));
        assertFalse(VehicleDustEmitter.loose(GroundKind.STREET), "pavement raises nothing");
        assertFalse(VehicleDustEmitter.loose(GroundKind.INDOOR));
        assertFalse(VehicleDustEmitter.loose(GroundKind.WATER));
    }

    @Test void aSkirtSpraysWaterAndHazesOpenGroundButNotIndoors() {
        assertEquals(EffectsService.DUST_SPRAY, VehicleDustEmitter.skirtKind(GroundKind.WATER));
        assertEquals(EffectsService.DUST_SKIRT, VehicleDustEmitter.skirtKind(GroundKind.STREET));
        assertEquals(EffectsService.DUST_SKIRT, VehicleDustEmitter.skirtKind(GroundKind.DIRT));
        assertEquals(-1, VehicleDustEmitter.skirtKind(GroundKind.INDOOR));
    }

    @Test void scatterIsReplayableAndBounded() {
        for (int t = 0; t < 200; t++) {
            float j = VehicleDustEmitter.jitter(42L, t, 1);
            assertEquals(j, VehicleDustEmitter.jitter(42L, t, 1));
            assertTrue(j >= -1f && j <= 1f);
        }
    }
}
