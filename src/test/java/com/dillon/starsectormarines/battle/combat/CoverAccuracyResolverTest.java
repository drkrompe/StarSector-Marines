package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoverAccuracyResolverTest {

    private static final float EPS = 1e-6f;

    @Test
    void tuningLevelsAreCappedAtHeavyCover() {
        assertEquals(1f, CoverAccuracyResolver.accuracyMultiplier(0), EPS);
        assertEquals(0.85f, CoverAccuracyResolver.accuracyMultiplier(1), EPS);
        assertEquals(0.70f, CoverAccuracyResolver.accuracyMultiplier(2), EPS);
        assertEquals(0.55f, CoverAccuracyResolver.accuracyMultiplier(3), EPS);
        assertEquals(0.55f, CoverAccuracyResolver.accuracyMultiplier(20), EPS);
    }

    @Test
    void gridCoverOnlyProtectsTheFacingTowardTheShooter() {
        Fixture f = new Fixture();
        f.grid.setCoverAtFacing(4, 4, NavigationGrid.FACING_E, 1);

        assertEquals(0.85f, f.resolver.apply(1f, 4, 4, 8, 4), EPS,
                "east cover protects against an east-side shooter");
        assertEquals(1f, f.resolver.apply(1f, 4, 4, 0, 4), EPS,
                "the same cover leaves the west facing exposed");
    }

    @Test
    void neighboringDoodadCoverIsReadDirectionally() {
        Fixture f = new Fixture();
        f.doodads.addDoodad(new Doodad(5, 4,
                new TileManifest.TileFrame(0, 0), false, Doodad.COVER_MED));

        assertEquals(0.70f, f.resolver.apply(1f, 4, 4, 8, 4), EPS,
                "crate east of target lies between it and east-side fire");
        assertEquals(1f, f.resolver.apply(1f, 4, 4, 0, 4), EPS,
                "crate east of target cannot protect from west-side fire");
    }

    @Test
    void wallAndDoodadCoverCombineButDoNotExceedHeavyPenalty() {
        Fixture f = new Fixture();
        f.grid.setCoverAtFacing(4, 4, NavigationGrid.FACING_E, 1);
        f.doodads.addDoodad(new Doodad(5, 4,
                new TileManifest.TileFrame(0, 0), false, Doodad.COVER_MED));

        assertEquals(0.55f, f.resolver.apply(1f, 4, 4, 8, 4), EPS);
    }

    @Test
    void shooterSharingTargetCellDoesNotAcquireArbitraryEastCover() {
        Fixture f = new Fixture();
        f.grid.setCoverAtFacing(4, 4, NavigationGrid.FACING_E, 3);

        assertEquals(0.6f, f.resolver.apply(0.6f, 4, 4, 4, 4), EPS);
    }

    private static final class Fixture {
        final NavigationGrid grid = new NavigationGrid(9, 9);
        final DoodadService doodads = new DoodadService(grid);
        final CoverAccuracyResolver resolver = new CoverAccuracyResolver(grid, doodads);
    }
}
