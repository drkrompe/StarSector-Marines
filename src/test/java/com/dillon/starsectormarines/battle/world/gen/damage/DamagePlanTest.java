package com.dillon.starsectormarines.battle.world.gen.damage;

import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan.*;
import org.junit.jupiter.api.Test;
import org.json.JSONObject;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DamagePlanTest {
    @Test void roundTripRetainsDisabledLayersAndErasure() throws Exception {
        var layer = new Layer("Old bombardment", false, List.of(new Stroke(Mode.RUIN, 3, List.of(new Point(4, 4))), new Stroke(Mode.ERASE, 1, List.of(new Point(3, 4)))));
        var plan = new DamagePlan(16, 16, 83, List.of(layer));
        assertEquals(plan, DamagePlan.read(new JSONObject(plan.json().toString())));
        assertThrows(IllegalArgumentException.class, () -> new DamagePlan(16, 16, 83, List.of(layer, layer)));
        assertThrows(IllegalArgumentException.class, () -> new DamagePlan(8, 8, 1, List.of(new Layer("bad", true, List.of(new Stroke(Mode.RUIN, 2, List.of(new Point(9, 0))))))));
    }
    @Test void aResampledStrokeHasTheSameCoverageAndErasingOnlyRemovesThisLayer() {
        var endpoints = new Layer("impact", true, List.of(new Stroke(Mode.RUIN, 2, List.of(new Point(2, 4), new Point(12, 4)))));
        var resampled = new Layer("impact", true, List.of(new Stroke(Mode.RUIN, 2, List.of(new Point(2, 4), new Point(7, 4), new Point(12, 4)))));
        var plan = new DamagePlan(16, 16, 1, List.of(endpoints));
        byte[] before = DamageBrush.rasterize(plan, endpoints);
        assertArrayEquals(before, DamageBrush.rasterize(plan, resampled));
        var erased = endpoints.append(new Stroke(Mode.ERASE, 1, List.of(new Point(7, 4))));
        byte[] after = DamageBrush.rasterize(plan, erased);
        assertEquals(0, after[4 * 16 + 7]); assertEquals(3, after[4 * 16 + 2]);
        assertArrayEquals(before, DamageBrush.rasterize(plan, endpoints));
        assertArrayEquals(new byte[256], DamageBrush.rasterize(plan, new Layer("impact", false, endpoints.strokes())));
    }
}
