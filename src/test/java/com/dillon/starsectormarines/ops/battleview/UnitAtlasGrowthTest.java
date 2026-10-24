package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.QuadMesh;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule that keeps unit art from silently falling out of the unit atlas: an image drawn in a layer the atlas
 * gathers, by any emit path, that the atlas does not hold is noted once and placed by the next growth. The atlas was
 * once planned from a hand-kept list, and the turret collars and the downed poses each cost a bind per body for as
 * long as nobody added them to it.
 */
class UnitAtlasGrowthTest {

    /** A {@link SpriteAtlas} gathering {@code UNITS}, with images stated rather than loaded. */
    private static final class Fixture extends SpriteAtlas {
        Fixture() {
            super("test atlas", 256, 1024);
        }

        @Override
        public boolean gathers(RenderLayer layer) {
            return layer == RenderLayer.UNITS;
        }

        @Override
        protected int growthHeadroom(int side) {
            return side / 4;
        }

        boolean seed(List<Source> sources) {
            return plan(sources, 4);
        }

        List<SpriteAPI> missed() {
            return takeMissed();
        }

        Growth add(List<Source> sources) {
            return extend(sources);
        }
    }

    @Test
    void everyEmitPathInAGatheredLayerNotesAnImageTheAtlasLacks() {
        Fixture atlas = seeded();
        DrawList out = new DrawList();
        out.setSpriteAtlas(atlas);
        SpriteAPI sprite = sprite();
        SpriteAPI glow = sprite();
        SpriteAPI part = sprite();
        SpriteAPI sheet = sprite();
        SpriteAPI meshed = sprite();

        out.addSprite(RenderLayer.UNITS, sprite, 0, 0, 8, 8, 0, 1, 1, 1, 1);
        out.addAdditiveSprite(RenderLayer.UNITS, glow, 0, 0, 8, 8, 0, 1, 1, 1, 1);
        out.addImageRegion(RenderLayer.UNITS, part, 32, 32, 0, 0, 16, 16, 0, 0, 8, 8, 0, 1, 1, 1, 1);
        out.addSheetQuad(RenderLayer.UNITS, sheet, 0, 0, 16, 16, 0, 0, 8, 8, 30, 1, 1, 1, 1);
        QuadMesh mesh = new QuadMesh();
        mesh.add(meshed, 0, 0, 8, 8, 0, 1, 1, 1, 1);
        out.addMesh(RenderLayer.UNITS, mesh, 0, 0, 1, 1);
        // Drawn again, the same images are not noted twice.
        out.addSprite(RenderLayer.UNITS, sprite, 0, 0, 8, 8, 0, 1, 1, 1, 1);

        assertEquals(List.of(sprite, glow, part, sheet, meshed), atlas.missed());
        assertTrue(atlas.missed().isEmpty(), "each image is noted once for the atlas's life");
    }

    @Test
    void anImageDrawnOutsideAGatheredLayerOrAlreadyHeldIsNotNoted() {
        List<SpriteAPI> seeds = new ArrayList<>();
        Fixture atlas = seeded(seeds);
        DrawList out = new DrawList();
        out.setSpriteAtlas(atlas);

        out.addSprite(RenderLayer.GROUND, sprite(), 0, 0, 8, 8, 0, 1, 1, 1, 1);
        out.addSprite(RenderLayer.UNITS, seeds.get(0), 0, 0, 8, 8, 0, 1, 1, 1, 1);
        out.addSprite(RenderLayer.UNITS, atlas.sheet(), 0, 0, 8, 8, 0, 1, 1, 1, 1);

        assertTrue(atlas.missed().isEmpty());
    }

    @Test
    void growthPlacesTheNotedImageAndChangesTheLayoutToken() {
        List<SpriteAPI> seeds = new ArrayList<>();
        Fixture atlas = seeded(seeds);
        Object before = atlas.layoutToken();
        SpriteAPI late = sprite();

        // Not built yet, so nothing is lost by laying the whole atlas out again.
        assertEquals(SpriteAtlas.Growth.RELAID, atlas.add(List.of(new SpriteAtlas.Source(late, 40, 24))));

        SpriteAtlas.Placement placed = atlas.placement(late);
        assertNotNull(placed);
        assertEquals(40, placed.width());
        assertEquals(24, placed.height());
        for (SpriteAPI seed : seeds) assertNotNull(atlas.placement(seed), "the seed survives a relayout");
        assertNotSame(before, atlas.layoutToken(), "a cached mesh address must be resolved again");
        assertEquals(SpriteAtlas.Growth.NONE, atlas.add(List.of(new SpriteAtlas.Source(late, 40, 24))));
    }

    @Test
    void anImageNoSideCanHoldIsReportedAndTheAtlasKeepsWhatItHas() {
        List<SpriteAPI> seeds = new ArrayList<>();
        Fixture atlas = seeded(seeds);
        SpriteAPI huge = sprite();

        assertEquals(SpriteAtlas.Growth.NONE, atlas.add(List.of(new SpriteAtlas.Source(huge, 2000, 2000, "huge"))));

        assertNull(atlas.placement(huge));
        assertTrue(atlas.exclusions().get(SpriteAtlas.Exclusion.NO_ROOM).contains("huge"));
        for (SpriteAPI seed : seeds) assertNotNull(atlas.placement(seed));
    }

    @Test
    void theUnitAtlasGathersOnlyTheUnitLayers() {
        UnitAtlas atlas = new UnitAtlas();
        assertTrue(atlas.gathers(RenderLayer.UNITS));
        assertTrue(atlas.gathers(RenderLayer.UNIT_SHADOWS));
        assertFalse(atlas.gathers(RenderLayer.GROUND));
        assertFalse(atlas.gathers(RenderLayer.SHOTS));
    }

    private static Fixture seeded() {
        return seeded(new ArrayList<>());
    }

    private static Fixture seeded(List<SpriteAPI> seeds) {
        Fixture atlas = new Fixture();
        List<SpriteAtlas.Source> sources = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            SpriteAPI seed = sprite();
            seeds.add(seed);
            sources.add(new SpriteAtlas.Source(seed, 32, 32));
        }
        assertTrue(atlas.seed(sources));
        return atlas;
    }

    private static SpriteAPI sprite() {
        return (SpriteAPI) Proxy.newProxyInstance(
                SpriteAPI.class.getClassLoader(), new Class<?>[]{SpriteAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWidth", "getHeight", "getTextureWidth", "getTextureHeight" -> 1f;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "sprite";
                    default -> null;
                });
    }
}
