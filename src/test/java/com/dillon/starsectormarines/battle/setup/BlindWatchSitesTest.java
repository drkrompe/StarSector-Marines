package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.world.gen.BlindWatchSounding;
import com.dillon.starsectormarines.battle.world.gen.BlindWatchSounding.Cell;
import com.dillon.starsectormarines.battle.world.gen.BlindWatchSounding.Installation;
import com.dillon.starsectormarines.battle.world.gen.BlindWatchSounding.PostRole;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlindWatchSitesTest {
    private static final Cell SLIT = new Cell(0, 0), HAB = new Cell(1, 0), RIM_A = new Cell(2, 0), RIM_B = new Cell(3, 0),
            BOSS = new Cell(4, 0);

    private static Map<PostRole, List<Cell>> posts() {
        Map<PostRole, List<Cell>> posts = new EnumMap<>(PostRole.class);
        posts.put(PostRole.RIM, List.of(RIM_A, RIM_B));
        posts.put(PostRole.HAB, List.of(SLIT, HAB));
        posts.put(PostRole.WATCH_BOSS, List.of(BOSS));
        return posts;
    }

    @Test void theSlitsAndTheBossAreMannedFirstThenOnePostOfEachDutyInTurn() {
        assertEquals(Map.of(PostRole.HAB, List.of(SLIT)), BlindWatchSites.watch(posts(), Set.of(SLIT), 1));
        assertEquals(Map.of(PostRole.HAB, List.of(SLIT), PostRole.WATCH_BOSS, List.of(BOSS)),
                BlindWatchSites.watch(posts(), Set.of(SLIT), 2));
        assertEquals(Map.of(PostRole.RIM, List.of(RIM_A), PostRole.HAB, List.of(SLIT, HAB),
                PostRole.WATCH_BOSS, List.of(BOSS)), BlindWatchSites.watch(posts(), Set.of(SLIT), 4));
    }

    @Test void aWatchLargerThanThePostMansEveryPostOnce() {
        assertEquals(posts(), BlindWatchSites.watch(posts(), Set.of(SLIT), 40));
    }

    /** S1, S2 and S3 are the Sounding's mast, uplink and dish field, each planted at its installation's own ground. */
    @Test void eachChargeSiteStandsAtItsInstallation() throws Exception {
        DiskRegistries.install();
        var layout = BlindWatchSounding.generate(7150, new TargetProfile(0, 0, 0, 0, "", Set.of(), SurfacePalette.ROCK,
                SettlementLink.NONE));
        var sites = BlindWatchSequence.siteLayout(BlindWatchSites.from(layout, List.of())).sites();
        List<Installation> order = List.of(Installation.MAST, Installation.UPLINK, Installation.DISH_FIELD);
        for (int i = 0; i < order.size(); i++) {
            BlindWatchSounding.ChargeSite installation = layout.sites().get(order.get(i));
            var site = sites.get(i);
            assertEquals(installation.plant(), new Cell(site.cellX(), site.cellY()), site.id());
            // The plant cell is on the installation's ground or at its edge, never somewhere else on the map.
            var area = installation.area();
            assertTrue(site.cellX() >= area.left() - 1 && site.cellX() <= area.right() + 1
                    && site.cellY() >= area.bottom() - 1 && site.cellY() <= area.top() + 1, site.id() + " at " + area);
        }
    }
}
