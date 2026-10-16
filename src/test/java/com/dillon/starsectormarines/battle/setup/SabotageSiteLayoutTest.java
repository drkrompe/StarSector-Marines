package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest.Kind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The selection rules of the three-site layout, asked of a hand-laid map.
 *
 * <p>Whether a <em>generated</em> production map offers enough sites for the
 * selector to succeed is a property of finished maps and lives in
 * {@code SabotageSiteEvidence}.
 */
class SabotageSiteLayoutTest {

    private static final int WIDTH = 64;
    private static final int HEIGHT = 24;
    private static final int SEPARATION = 8;

    private static NavigationGrid openGrid() {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    private static MapResult map(NavigationGrid grid, List<PointOfInterest> pois) {
        return new MapResult(grid, new CellTopology(WIDTH, HEIGHT),
                1, 1, WIDTH - 2, HEIGHT - 2, pois, List.<Doodad>of());
    }

    private static PointOfInterest poi(Kind kind, int x, int y) {
        return new PointOfInterest(kind, x - 1, y - 1, x + 1, y + 1, x, y, x, y);
    }

    private static List<LandingPad> pads() {
        return List.of(LandingPad.fallback(2, 12));
    }

    private static SabotageSiteLayout select(MapResult map) {
        return SabotageSiteLayout.select(map, pads(), SEPARATION);
    }

    private static List<PointOfInterest> threeDefenderSites() {
        return new ArrayList<>(List.of(
                poi(Kind.LABORATORY, 40, 4),
                poi(Kind.DEPOT, 50, 12),
                poi(Kind.COMMS, 40, 20)));
    }

    @Test
    void threeDistinctSeparatedWalkableSitesAreNamedInOrder() {
        SabotageSiteLayout layout = select(map(openGrid(), threeDefenderSites()));

        assertEquals(SabotageSiteLayout.REQUIRED_SITE_COUNT, layout.sites().size());
        Set<String> ids = new HashSet<>();
        Set<String> cells = new HashSet<>();
        for (SabotageSiteLayout.Site site : layout.sites()) {
            assertTrue(ids.add(site.id()), "site ids must be distinct");
            assertTrue(cells.add(site.cellX() + "," + site.cellY()), "site cells must be distinct");
        }
        assertEquals(List.of("SAB-01", "SAB-02", "SAB-03"),
                layout.sites().stream().map(SabotageSiteLayout.Site::id).toList());
        for (int i = 0; i < 3; i++) {
            for (int j = i + 1; j < 3; j++) {
                SabotageSiteLayout.Site a = layout.sites().get(i);
                SabotageSiteLayout.Site b = layout.sites().get(j);
                int dx = a.cellX() - b.cellX();
                int dy = a.cellY() - b.cellY();
                assertTrue(dx * dx + dy * dy >= SEPARATION * SEPARATION,
                        "sites must satisfy the minimum separation");
            }
        }
    }

    @Test
    void selectionIsIndependentOfPointOfInterestOrder() {
        List<PointOfInterest> pois = threeDefenderSites();
        pois.add(poi(Kind.RESIDENTIAL, 56, 6));
        pois.add(poi(Kind.MEDICAL, 34, 12));
        SabotageSiteLayout reference = select(map(openGrid(), pois));

        for (long seed = 0; seed < 5; seed++) {
            List<PointOfInterest> shuffled = new ArrayList<>(pois);
            Collections.shuffle(shuffled, new Random(seed));
            assertEquals(reference, select(map(openGrid(), shuffled)),
                    "site selection drifted with list order");
        }
    }

    @Test
    void nonResidentialTargetsArePreferredOverFartherApartResidentialOnes() {
        List<PointOfInterest> pois = threeDefenderSites();
        pois.add(poi(Kind.RESIDENTIAL, 60, 2));
        pois.add(poi(Kind.RESIDENTIAL, 60, 22));
        pois.add(poi(Kind.RESIDENTIAL, 33, 12));

        SabotageSiteLayout layout = select(map(openGrid(), pois));

        for (SabotageSiteLayout.Site site : layout.sites()) {
            assertFalse(site.kind() == Kind.RESIDENTIAL,
                    "a residential site was chosen over a high-value target");
        }
    }

    @Test
    void siteInTheAttackersHalfIsNeverChosen() {
        List<PointOfInterest> pois = threeDefenderSites();
        pois.add(poi(Kind.ADMINISTRATIVE, 12, 12));

        SabotageSiteLayout layout = select(map(openGrid(), pois));

        for (SabotageSiteLayout.Site site : layout.sites()) {
            assertTrue(site.cellX() >= WIDTH / 2, site.id() + " must be in the defender half");
        }
    }

    @Test
    void siteWithAnUnstandableAnchorIsNeverChosen() {
        NavigationGrid grid = openGrid();
        grid.setWalkable(40, 20, false);
        // Three sites exist on paper but one anchor is not floor, so only two
        // can be offered and the layout must refuse rather than publish them.
        assertThrows(IllegalStateException.class,
                () -> select(map(grid, threeDefenderSites())));
    }

    @Test
    void siteMustBeReachableFromEveryLandingPad() {
        NavigationGrid grid = openGrid();
        // A wall straight across the map: the defender half is reachable from
        // the east of it and from nowhere on the west.
        for (int y = 0; y < HEIGHT; y++) grid.setWalkable(20, y, false);
        MapResult map = map(grid, threeDefenderSites());
        LandingPad west = LandingPad.fallback(2, 12);
        LandingPad east = LandingPad.fallback(30, 12);

        assertEquals(3, SabotageSiteLayout.select(map, List.of(east), SEPARATION).sites().size());
        assertThrows(IllegalStateException.class,
                () -> SabotageSiteLayout.select(map, List.of(west), SEPARATION));
        assertThrows(IllegalStateException.class,
                () -> SabotageSiteLayout.select(map, List.of(east, west), SEPARATION),
                "a site one pad cannot walk to was offered");
    }

    @Test
    void tooCloseSitesCannotBothBeChosen() {
        List<PointOfInterest> pois = new ArrayList<>(List.of(
                poi(Kind.LABORATORY, 40, 4),
                poi(Kind.DEPOT, 42, 4),
                poi(Kind.COMMS, 40, 20)));

        assertThrows(IllegalStateException.class, () -> select(map(openGrid(), pois)));
    }

    @Test
    void malformedMapFailsClosedInsteadOfPublishingPartialLayout() {
        NavigationGrid grid = new NavigationGrid(24, 12);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) grid.setWalkableFloor(x, y);
        }
        List<PointOfInterest> onlyTwo = List.of(
                poi(Kind.LABORATORY, 14, 3),
                poi(Kind.DEPOT, 20, 8));
        MapResult map = new MapResult(grid, new CellTopology(24, 12),
                1, 1, 22, 10, onlyTwo, List.<Doodad>of());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> SabotageSiteLayout.select(map,
                        List.of(LandingPad.fallback(1, 1)), 2));
        assertTrue(failure.getMessage().contains("exactly 3 charge sites"));
    }
}
