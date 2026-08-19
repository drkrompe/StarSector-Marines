package com.dillon.starsectormarines.battle.perception;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoiseEventBusTest {

    @Test
    void drainIsStableAndConsumesEachNoiseOnce() {
        AtomicInteger tick = new AtomicInteger(7);
        NoiseEventBus bus = new NoiseEventBus(tick::get);
        bus.post(8f, 3f, 1f, 20L, Faction.DEFENDER, NoiseKind.SHOT);
        bus.post(4f, 3f, 3f, 0L, Faction.DEFENDER, NoiseKind.DETONATION);
        bus.post(2f, 3f, 1f, 10L, Faction.DEFENDER, NoiseKind.SHOT);

        List<NoiseEvent> drained = bus.drain();

        assertEquals(List.of(10L, 20L, 0L), drained.stream()
                .map(NoiseEvent::sourceUnitId).toList());
        assertTrue(bus.drain().isEmpty());
    }

    @Test
    void detectionIgnoresWallsAndReturnsImperfectLowerConfidenceCell() {
        NavigationGrid grid = new NavigationGrid(20, 12);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) grid.setWalkableFloor(x, y);
            grid.setWalkable(7, y, false);
        }
        NoiseEvent event = new NoiseEvent(9.5f, 5.5f, 1.4f, 42L,
                Faction.DEFENDER, NoiseKind.SHOT, 11);

        NoiseDetection.Detection detection = NoiseDetection.detect(
                event, 3, 5.5f, 5.5f, grid);

        assertNotNull(detection);
        assertTrue(detection.confidence() >= NoiseDetection.MIN_AUDIO_CONFIDENCE);
        assertTrue(detection.confidence() <= NoiseDetection.MAX_AUDIO_CONFIDENCE);
        assertTrue(detection.cellX() != 9 || detection.cellY() != 5,
                "hearing must not reveal the exact muzzle cell");
    }
}
