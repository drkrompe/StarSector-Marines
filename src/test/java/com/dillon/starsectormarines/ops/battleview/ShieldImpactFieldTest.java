package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.shield.ShieldDomeService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.render2d.BattleCamera;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The impact list a dome is drawn with: selection, placement, ageing and scaling by cost. */
class ShieldImpactFieldTest {

    private static final long DOME = 7L;
    private static final float CAPACITY = 1500f;

    private static BattleCamera camera() {
        BattleCamera camera = new BattleCamera(100, 100);
        camera.setViewport(0f, 0f, 800f, 800f, 8f);
        return camera;
    }

    private static ShieldDomeService.DomeView dome() {
        return new ShieldDomeService.DomeView(DOME, Faction.DEFENDER, 50f, 50f, 16f,
                0f, CAPACITY, false, 0f, 0f, 0f, 0f, null, 0, 0, 0, 0f, Faction.DEFENDER, 6f);
    }

    private static ShieldDomeDraw drawFor(BattleCamera camera) {
        ShieldDomeDraw draw = new ShieldDomeDraw();
        draw.sx = camera.cellToScreenX(50f);
        draw.sy = camera.cellToScreenY(50f);
        draw.radiusCells = 16f;
        draw.radiusPx = 16f * camera.cellPxSize();
        return draw;
    }

    private static ShieldDomeService.HitView hit(long dome, float x, float y, float cost, float age) {
        return new ShieldDomeService.HitView(Faction.DEFENDER, x, y, 0f, dome, cost, age);
    }

    @Test
    void anImpactIsPlacedInTheDomeFrameAgedAndScaled() {
        BattleCamera camera = camera();
        ShieldDomeDraw draw = drawFor(camera);
        ShieldImpactField.gather(draw, dome(), List.of(hit(DOME, 58f, 50f, 300f, 0.4f)), camera);

        assertEquals(1, draw.impactCount);
        assertEquals(8f / 16f, draw.impacts[0], 1e-4f, "eight cells east of centre is half a radius");
        assertEquals(0f, draw.impacts[1], 1e-4f);
        assertEquals(0.4f, draw.impacts[2], 1e-6f);
        assertEquals(ShieldImpactField.intensity(300f, CAPACITY), draw.impacts[3], 1e-6f);
    }

    @Test
    void impactsOfOtherDomesAndSpentImpactsAreSkipped() {
        BattleCamera camera = camera();
        ShieldDomeDraw draw = drawFor(camera);
        List<ShieldDomeService.HitView> hits = List.of(
                hit(99L, 52f, 50f, 100f, 0.1f),
                hit(DOME, 53f, 50f, 100f, ShieldDomeService.IMPACT_SECONDS + 0.1f),
                hit(DOME, 54f, 50f, 100f, 0.2f));
        ShieldImpactField.gather(draw, dome(), hits, camera);
        assertEquals(1, draw.impactCount);
        assertEquals(4f / 16f, draw.impacts[0], 1e-4f);
    }

    @Test
    void moreImpactsThanTheShaderTakesKeepsTheNewestAndDropsTheOldest() {
        BattleCamera camera = camera();
        ShieldDomeDraw draw = drawFor(camera);
        List<ShieldDomeService.HitView> hits = new ArrayList<>();
        int total = ShieldDomeDraw.MAX_IMPACTS + 5;
        for (int i = 0; i < total; i++) hits.add(hit(DOME, 40f + i * 0.25f, 50f, 50f, 0.1f));
        ShieldImpactField.gather(draw, dome(), hits, camera);

        assertEquals(ShieldDomeDraw.MAX_IMPACTS, draw.impactCount);
        float newestX = (40f + (total - 1) * 0.25f - 50f) / 16f;
        float oldestKeptX = (40f + 5 * 0.25f - 50f) / 16f;
        assertEquals(newestX, draw.impacts[0], 1e-4f, "newest first");
        assertEquals(oldestKeptX, draw.impacts[(draw.impactCount - 1) * 4], 1e-4f,
                "the five oldest did not make the list");
    }

    @Test
    void intensityRisesWithCostSaturatesAndNeverFallsBelowTheFloor() {
        float tiny = ShieldImpactField.intensity(1f, CAPACITY);
        float light = ShieldImpactField.intensity(CAPACITY * 0.02f, CAPACITY);
        float heavy = ShieldImpactField.intensity(CAPACITY * 0.08f, CAPACITY);
        float huge = ShieldImpactField.intensity(CAPACITY * 5f, CAPACITY);

        assertTrue(tiny >= ShieldImpactField.MIN_INTENSITY);
        assertTrue(tiny < light && light < heavy, tiny + " " + light + " " + heavy);
        assertEquals(1f, ShieldImpactField.intensity(
                CAPACITY * ShieldImpactField.FULL_INTENSITY_SHARE, CAPACITY), 1e-6f);
        assertEquals(1f, huge, 1e-6f);
        assertEquals(ShieldImpactField.MIN_INTENSITY, ShieldImpactField.intensity(0f, CAPACITY), 1e-6f);
        assertEquals(ShieldImpactField.MIN_INTENSITY, ShieldImpactField.intensity(100f, 0f), 1e-6f);
    }
}
