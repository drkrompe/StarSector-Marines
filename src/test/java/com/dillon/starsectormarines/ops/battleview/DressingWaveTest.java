package com.dillon.starsectormarines.ops.battleview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.ship.CoreDressing;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckSide;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckZone;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipCore;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The dressing wave's order and its end, on a hand-made deck: an engine room at the west end of a long passage. */
class DressingWaveTest {

    private static final int W = 40, H = 9;

    /**
     * Floor everywhere inside a one-cell hull, an engine room over x 1-5, a
     * bridge over x 34-38, and a bulkhead at x 20 open only at its south end,
     * so the north side of the passage beyond it is near in a straight line
     * and far on foot.
     */
    private static CellTopology topology() {
        var topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            topology.setGroundKind(x, y, GroundKind.INDOOR);
            if (x == 0 || y == 0 || x == W - 1 || y == H - 1) topology.setWall(x, y, true);
        }
        for (int y = 1; y < H - 2; y++) topology.setWall(20, y, true);
        return topology;
    }

    private static DeckGraph graph() {
        var engine = new DeckGraph.Compartment(1, RoomShape.rectangle(5, 7), 1, 1,
                RoomPose.CANONICAL, DeckSide.PORT, DeckZone.MIDSHIPS, RoomPurpose.ENGINE_ROOM, List.of());
        var bridge = new DeckGraph.Compartment(2, RoomShape.rectangle(5, 7), 34, 1,
                RoomPose.CANONICAL, DeckSide.PORT, DeckZone.FORE, RoomPurpose.BRIDGE, List.of());
        return new DeckGraph(List.of(engine, bridge), List.of());
    }

    private static DressingWave wave(CoreDressing from, CoreDressing to, long seed) {
        return new DressingWave(from, to, 10f, 4f, null, topology(), graph(), seed);
    }

    @Test
    void powerComesBackOutwardFromTheEngineRoomAlongThePassage() {
        var wave = wave(CoreDressing.DEFICIT, CoreDressing.DEFICIT.with(ShipCore.POWER, true), 3);
        float mid = 10f + 1.8f;
        float near = wave.weight(ShipCore.POWER, 8.5f, 4.5f, mid), far = wave.weight(ShipCore.POWER, 30.5f, 4.5f, mid);
        assertTrue(near > far, "near " + near + " far " + far);
        // Compute starts from the bridge at the other end.
        var compute = wave(CoreDressing.DEFICIT, CoreDressing.DEFICIT.with(ShipCore.COMPUTE, true), 3);
        assertTrue(compute.weight(ShipCore.COMPUTE, 30.5f, 4.5f, mid) > compute.weight(ShipCore.COMPUTE, 8.5f, 4.5f, mid));
    }

    @Test
    void theWaveWalksRoundABulkheadRatherThanThroughIt() {
        var wave = wave(CoreDressing.DEFICIT, CoreDressing.RESTORED, 3);
        // Both just past the bulkhead at x 20; the north cell is reached only by walking round its south end.
        float t = 10f + 2.2f;
        float south = wave.weight(ShipCore.POWER, 21.5f, 7.5f, t), north = wave.weight(ShipCore.POWER, 21.5f, 1.5f, t);
        assertTrue(south >= north, "south " + south + " north " + north);
        float southEarly = firstLight(wave, 21.5f, 7.5f), northEarly = firstLight(wave, 21.5f, 1.5f);
        assertTrue(southEarly < northEarly, "south " + southEarly + " north " + northEarly);
    }

    private static float firstLight(DressingWave wave, float x, float y) {
        for (float t = 10f; t <= 14f; t += .01f) if (wave.weight(ShipCore.POWER, x, y, t) > 0f) return t;
        return Float.MAX_VALUE;
    }

    @Test
    void theSameDeckAndSeedPlayTheSameWave() {
        var a = wave(CoreDressing.DEFICIT, CoreDressing.RESTORED, 9);
        var b = wave(CoreDressing.DEFICIT, CoreDressing.RESTORED, 9);
        for (float t = 9f; t <= 15f; t += .37f) for (int x = 1; x < W - 1; x += 3) for (ShipCore core : CoreDressing.DRESSED) {
            assertEquals(a.weight(core, x + .5f, 3.5f, t), b.weight(core, x + .5f, 3.5f, t));
            assertEquals(a.flicker(core, x + .5f, 3.5f, t, 4), b.flicker(core, x + .5f, 3.5f, t, 4));
        }
    }

    @Test
    void whenItHasRunEveryPlaceShowsExactlyTheTarget() {
        CoreDressing to = CoreDressing.DEFICIT.with(ShipCore.POWER, true).with(ShipCore.STRUCTURE, true);
        var wave = wave(CoreDressing.DEFICIT.with(ShipCore.COMPUTE, true), to, 5);
        assertEquals(0f, wave.progress(10f));
        assertFalse(wave.done(13.9f));
        assertTrue(wave.done(14f));
        assertEquals(1f, wave.progress(14f));
        for (float t : new float[]{14f, 20f}) for (int y = 0; y < H; y++) for (int x = 0; x < W; x++)
            for (ShipCore core : CoreDressing.DRESSED) {
                assertEquals(to.restored(core) ? 0f : 1f, wave.deficit(core, x + .5f, y + .5f, t), core + " at " + x + "," + y);
                assertEquals(1f, wave.weight(core, x + .5f, y + .5f, t));
            }
    }

    @Test
    void aCoreThatDoesNotChangeHoldsStillThroughout() {
        var wave = wave(CoreDressing.DEFICIT, CoreDressing.DEFICIT.with(ShipCore.POWER, true), 5);
        assertTrue(wave.moves(ShipCore.POWER));
        assertFalse(wave.moves(ShipCore.LIFE_SUPPORT));
        for (float t = 10f; t <= 14f; t += .25f) for (int x = 1; x < W - 1; x += 2) {
            assertEquals(1f, wave.deficit(ShipCore.LIFE_SUPPORT, x + .5f, 4.5f, t));
            float power = wave.deficit(ShipCore.POWER, x + .5f, 4.5f, t);
            assertTrue(power >= 0f && power <= 1f);
            float flicker = wave.flicker(ShipCore.POWER, x + .5f, 4.5f, t, 2);
            assertTrue(flicker >= 0f && flicker <= wave.weight(ShipCore.POWER, x + .5f, 4.5f, t));
        }
    }

    @Test
    void aWaveNeedsADuration() {
        assertThrows(IllegalArgumentException.class,
                () -> new DressingWave(CoreDressing.DEFICIT, CoreDressing.RESTORED, 0f, 0f, null, topology(), graph(), 1));
    }
}
