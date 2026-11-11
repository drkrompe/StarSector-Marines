package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.ops.battleview.UnitLights.Kind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitLightsTest {

    @Test
    void aConeLightsAheadAndFadesToItsReach() {
        for (Kind kind : Kind.values()) {
            float near = UnitLights.pool(kind, 2f, 0f, 1f, 0f);
            float far = UnitLights.pool(kind, kind.reach * 0.8f, 0f, 1f, 0f);
            assertTrue(near > far && far > 0f, kind + ": brightest near the lamp, still lit near its reach");
            assertEquals(0f, UnitLights.pool(kind, kind.reach + 0.1f, 0f, 1f, 0f), kind + ": nothing past its reach");
        }
    }

    @Test
    void aConeLightsOnlyItsFacingBesidesTheGlowAtTheLamp() {
        Kind helmet = Kind.HELMET;
        assertEquals(0f, UnitLights.pool(helmet, -3f, 0f, 1f, 0f), "nothing behind");
        assertEquals(0f, UnitLights.pool(helmet, 0f, 3f, 1f, 0f), "nothing square to the side");
        assertTrue(UnitLights.pool(helmet, -0.5f, 0f, 1f, 0f) > 0f, "the lamp's own glow reaches its bearer's feet");
        assertTrue(UnitLights.pool(helmet, 0f, 3f, 0f, 1f) > 0f, "turned, it lights where it now faces");
    }

    @Test
    void aBodyLooksAlongTheBattlesBearings() {
        assertEquals(0f, UnitLights.forwardX(0f), 1e-6f, "facing 0 looks up the map");
        assertEquals(1f, UnitLights.forwardY(0f), 1e-6f);
        assertEquals(-1f, UnitLights.forwardX(90f), 1e-6f, "facing 90 looks west");
        assertEquals(0f, UnitLights.forwardY(90f), 1e-6f);
    }

    @Test
    void aConesEdgeIsSoft() {
        Kind flood = Kind.FLOOD;
        float halfAngle = (float) Math.acos(flood.cosHalf);
        float inside = UnitLights.pool(flood, 4f * (float) Math.cos(halfAngle * 0.98f),
                4f * (float) Math.sin(halfAngle * 0.98f), 1f, 0f);
        float axis = UnitLights.pool(flood, 4f, 0f, 1f, 0f);
        assertTrue(inside > 0f && inside < axis, "just inside the edge it is dimmer than on the axis");
    }
}
