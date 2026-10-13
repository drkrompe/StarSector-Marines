package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.tender.TenderDirector.Picture;
import com.dillon.starsectormarines.battle.tender.TenderDirector.Plan;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.warship.MissionStance;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What an unpiloted tender chooses to build from its stance, on a frozen picture of open ground. */
class TenderDirectorTest {

    private static final float[] NONE = {};

    private static NavigationGrid open() {
        NavigationGrid grid = new NavigationGrid(160, 100);
        for (int x = 0; x < 160; x++) {
            for (int y = 0; y < 100; y++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    private static Picture picture(MissionStance stance, float[] focus, float[] friendlies, float[] hostiles,
                                   float[] turrets) {
        return new Picture(stance, focus == null ? List.of() : List.of(focus), friendlies, hostiles, turrets, NONE);
    }

    private static float dist(Plan p, float x, float y) {
        return (float) Math.hypot(p.siteX() - x, p.siteY() - y);
    }

    @Test
    void aTenderWithNoStanceIsNeverDirected() {
        Plan plan = TenderDirector.plan(picture(MissionStance.HOLD, new float[]{80f, 30f},
                new float[]{10f, 30f}, NONE, NONE), open());
        assertFalse(plan.hasSite());
        assertFalse(plan.hasStation());
    }

    @Test
    void anAttackingTenderBuildsJustAheadOfTheAdvanceWhenTheGroundIsClear() {
        Plan plan = TenderDirector.plan(picture(MissionStance.ATTACK, new float[]{100f, 30f},
                new float[]{20f, 30f, 12f, 34f}, new float[]{100f, 30f}, NONE), open());
        assertTrue(plan.hasSite());
        assertEquals(26.5f, plan.siteX(), 0.01f, "the lead plus the look-ahead, towards the objective");
        assertEquals(30.5f, plan.siteY(), 0.01f);
        assertTrue(plan.hasStation());
        assertTrue(plan.stationX() < 20f, "idle, it hovers behind the lead, not ahead of it");
    }

    @Test
    void anAttackingTenderFallsBackToTheMostForwardClearedGround() {
        // The lead is in contact; only ground the threat radius does not reach is worth a builder.
        Plan plan = TenderDirector.plan(picture(MissionStance.ATTACK, new float[]{100f, 30f},
                new float[]{50f, 30f}, new float[]{62f, 30f}, NONE), open());
        assertTrue(plan.hasSite());
        assertTrue(dist(plan, 62f, 30f) > TenderSystem.THREAT_CELLS, "not on contested ground");
        assertTrue(plan.siteX() > 62f - TenderSystem.THREAT_CELLS - 3f, "but as far forward as the ground allows");
        assertTrue(plan.siteX() < 50f, "behind the lead");
    }

    @Test
    void anAttackingTenderFollowsTheSquadNearestItsObjective() {
        Plan plan = TenderDirector.plan(new Picture(MissionStance.ATTACK,
                List.of(new float[]{140f, 20f}, new float[]{140f, 80f}),
                new float[]{100f, 80f, 20f, 20f}, NONE, NONE, NONE), open());
        assertTrue(plan.hasSite());
        assertTrue(plan.siteY() > 70f, "the southern squad is the one closest to taking its objective");
    }

    @Test
    void groundTwoTurretsAlreadyCoverIsNotBuiltOnAgain() {
        float[] turrets = {26f, 26f, 26f, 35f};
        Plan plan = TenderDirector.plan(picture(MissionStance.ATTACK, new float[]{100f, 30f},
                new float[]{20f, 30f}, NONE, turrets), open());
        assertTrue(plan.hasSite());
        int near = 0;
        for (int i = 0; i < turrets.length; i += 2) {
            float d = dist(plan, turrets[i], turrets[i + 1]);
            assertTrue(d > TenderDirector.SPACING_CELLS, "spaced from every turret");
            if (d <= TenderDirector.COVERED_RADIUS_CELLS) near++;
        }
        assertTrue(near < TenderDirector.COVERED_COUNT, "not on covered ground");
    }

    @Test
    void aDefendingTenderBuildsOnTheApproachTheEnemyIsOn() {
        Plan plan = TenderDirector.plan(picture(MissionStance.DEFEND, new float[]{50f, 50f},
                new float[]{50f, 50f}, new float[]{100f, 50f}, NONE), open());
        assertTrue(plan.hasSite());
        assertEquals(50f + TenderDirector.APPROACH_CELLS + 0.5f, plan.siteX(), 0.01f, "out along the approach");
        assertEquals(50.5f, plan.siteY(), 0.01f);
        assertTrue(plan.stationX() < 50f, "idle, it hovers on the far side of the held ground");
    }

    @Test
    void aDefendingTenderWithNoEnemyInSightBuildsOnTheHeldGround() {
        Plan plan = TenderDirector.plan(picture(MissionStance.DEFEND, new float[]{50f, 50f},
                NONE, NONE, NONE), open());
        assertTrue(plan.hasSite());
        assertEquals(50.5f, plan.siteX(), 0.01f);
        assertEquals(50.5f, plan.siteY(), 0.01f);
    }

    @Test
    void aDefendingTenderDoesNotBuildUnderTheEnemysGunsButStillTakesItsStation() {
        Plan plan = TenderDirector.plan(picture(MissionStance.DEFEND, new float[]{50f, 50f},
                NONE, new float[]{60f, 50f}, NONE), open());
        assertFalse(plan.hasSite(), "every approach cell is inside the threat radius");
        assertTrue(plan.hasStation());
    }

    @Test
    void aStanceWithNoObjectiveGroundHasNothingToDo() {
        Plan plan = TenderDirector.plan(picture(MissionStance.ATTACK, null,
                new float[]{20f, 30f}, NONE, NONE), open());
        assertFalse(plan.hasSite());
        assertFalse(plan.hasStation());
    }

    @Test
    void shutGroundIsSteppedAround() {
        NavigationGrid grid = open();
        for (int y = 0; y < 100; y++) grid.setTag(58, y, NavigationGrid.CellTag.WALKABLE, false);
        Plan plan = TenderDirector.plan(picture(MissionStance.DEFEND, new float[]{50f, 50f},
                NONE, new float[]{100f, 50f}, NONE), grid);
        assertTrue(plan.hasSite());
        assertTrue(grid.isWalkable((int) plan.siteX(), (int) plan.siteY()));
    }

    @Test
    void anAttackingTenderBuildsTheQuickestOptionAndADefendingOneTheHeaviestItCanPayFor() throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        Tender t = Tender.fit(Faction.MARINE, 20f, 20f, 0f);
        assertEquals(BuildOption.VULCAN_TURRET, TenderDirector.option(MissionStance.ATTACK, t));
        assertEquals(BuildOption.MORTAR_TURRET, TenderDirector.option(MissionStance.DEFEND, t));
        assertTrue(BuildOption.POINT_DEFENCE_POD.buildSeconds() < BuildOption.VULCAN_TURRET.buildSeconds(),
                "the pod is quicker than every turret, so only the turret kinds may be chosen");
        t.stock(2);
        assertTrue(TenderDirector.option(MissionStance.DEFEND, t).cost() <= 2, "a metered stock bounds the choice");
        t.stock(0);
        assertNull(TenderDirector.option(MissionStance.DEFEND, t), "nothing it can pay for");
    }
}
