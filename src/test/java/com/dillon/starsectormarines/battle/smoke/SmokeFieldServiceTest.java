package com.dillon.starsectormarines.battle.smoke;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.marine.SmokeGrenadeSpec;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmokeFieldServiceTest {

    private static final SmokeGrenadeSpec SPEC =
            new SmokeGrenadeSpec(8f, 0.8f, 0.5f, 1.8f, 1.6f, 1f);

    @Test
    void grenadeArcBecomesNeutralTemporaryOpacityWithoutBlockingMovementOrBullets() {
        NavigationGrid grid = openGrid(10, 7);
        SmokeFieldService smoke = new SmokeFieldService(grid);

        smoke.launch(1L, Faction.MARINE, 2.5f, 3.5f, 5.5f, 3.5f, SPEC);
        smoke.tick(0.49f);
        assertTrue(grid.hasLineOfSight(1, 3, 8, 3));
        assertEquals(1, smoke.throwsInFlight().size());

        smoke.tick(0.02f);
        assertFalse(grid.hasLineOfSight(1, 3, 8, 3));
        assertTrue(grid.isWalkable(5, 3), "smoke never changes navigation");
        assertEquals(-1, (int) grid.firstWallOnLine(1, 3, 8, 3),
                "smoke is not a physical ballistic wall");
        assertTrue(grid.hasLineOfFire(1.5f, 3.5f, 8.5f, 3.5f),
                "smoke costs a shot accuracy, it never forbids one");
        assertTrue(grid.smokeDepthOnLine(1.5f, 3.5f, 8.5f, 3.5f) > 0,
                "the screened lane must price as obscured");
        assertEquals(Faction.MARINE, smoke.activeFields().get(0).sourceFaction());

        smoke.tick(1.01f);
        assertTrue(grid.hasLineOfSight(1, 3, 8, 3));
        assertTrue(smoke.activeFields().isEmpty());
    }

    @Test
    void overlappingCloudsReferenceCountOpacityUntilTheLastFieldExpires() {
        NavigationGrid grid = openGrid(10, 7);
        SmokeFieldService smoke = new SmokeFieldService(grid);

        smoke.launch(1L, Faction.MARINE, 2.5f, 3.5f, 5.5f, 3.5f, SPEC);
        smoke.tick(0.51f);
        smoke.tick(0.35f);
        smoke.launch(2L, Faction.DEFENDER, 7.5f, 3.5f, 5.5f, 3.5f, SPEC);
        smoke.tick(0.51f);
        assertEquals(2, smoke.activeFields().size());

        smoke.tick(0.16f);
        assertEquals(1, smoke.activeFields().size());
        assertFalse(grid.hasLineOfSight(1, 3, 8, 3),
                "the defender cloud obeys exactly the same neutral opacity rule");

        smoke.tick(0.85f);
        assertTrue(grid.hasLineOfSight(1, 3, 8, 3));
    }

    @Test
    void generatedSmokeAssetsShipWithTransparentCorners() throws Exception {
        for (Path path : new Path[]{
                Path.of("mod/graphics/ui/armory/special-smoke-grenades.png"),
                Path.of("mod/graphics/battle/fx/smoke-field-puff.png")}) {
            assertTrue(Files.isRegularFile(path));
            BufferedImage image = ImageIO.read(path.toFile());
            assertTrue(image.getColorModel().hasAlpha(), path + " must preserve alpha");
            assertEquals(0, image.getRGB(0, 0) >>> 24,
                    path + " must not carry a baked background");
        }
    }

    private static NavigationGrid openGrid(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }
}
