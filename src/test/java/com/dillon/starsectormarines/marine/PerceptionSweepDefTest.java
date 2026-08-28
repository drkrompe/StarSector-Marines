package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.unit.UnitType;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The sensor sweep's parse-time rules, pinned where an author meets them.
 *
 * <p>Nothing here asserts an authored balance number. The one test that reads
 * the shipped catalog asks a question about the <em>relationship</em> between
 * two authored numbers — that the sweep reads further than the suit's own eyes
 * do — which is the claim the story makes and which stays true however either
 * number is retuned.
 */
class PerceptionSweepDefTest {

    @Test
    void aSweepParsesItsAuthoredReadAndItsWallTolerance() throws JSONException {
        IntegralSystemDef system = IntegralSystemDef.parse(sweep(), "armor.test");

        assertEquals(IntegralSystemEffect.PERCEPTION_SWEEP, system.effect());
        assertSame(SpecialAiPolicy.APPROACHING_DEAD_GROUND, system.aiPolicy());

        PerceptionSweepSpec spec = system.perceptionSweep();
        assertNotNull(spec);
        assertEquals(30f, spec.revealRangeCells(), 1e-6f);
        assertEquals(6f, spec.wallReadRadiusCells(), 1e-6f);
        assertEquals(7f, system.approachingDeadGround().lookaheadCells(), 1e-6f);
    }

    /**
     * The bound that keeps a sweep a sweep. A read that carried through walls
     * as far as it carried through air would leave nothing for a wall to do at
     * the rim of its own disc, which is an x-ray rather than a sensor package.
     */
    @Test
    void aWallReadAsWideAsTheRangeIsRefused() throws JSONException {
        JSONObject xray = sweep().put("wallReadRadiusCells", 30.0);
        JSONException failure = assertThrows(JSONException.class,
                () -> IntegralSystemDef.parse(xray, "armor.test"));
        assertTrue(failure.getMessage().contains("wallReadRadiusCells"), failure.getMessage());
    }

    /** A sweep that only sees further and wider is legal; the wall read is optional depth, not a requirement. */
    @Test
    void aSweepMayAuthorNoWallToleranceAtAll() throws JSONException {
        IntegralSystemDef system = IntegralSystemDef.parse(
                sweep().put("wallReadRadiusCells", 0.0), "armor.test");
        assertEquals(0f, system.perceptionSweep().wallReadRadiusCells(), 1e-6f);
    }

    /** But it must say so. Silence about a wall read is an author who has not decided. */
    @Test
    void aSweepThatNeverMentionsWallsIsRefused() throws JSONException {
        JSONObject json = sweep();
        json.remove("wallReadRadiusCells");
        JSONException failure = assertThrows(JSONException.class,
                () -> IntegralSystemDef.parse(json, "armor.test"));
        assertTrue(failure.getMessage().contains("wallReadRadiusCells"), failure.getMessage());
    }

    /**
     * Looking at something is not a payload. An ammunition-gated sweep would be
     * a scout who can run out of looking, which is a stat with a counter on it
     * rather than a capability with a clock.
     */
    @Test
    void anAmmunitionGatedSweepIsRefused() throws JSONException {
        JSONObject json = sweep()
                .put("resource", "ammunition")
                .put("startingAmmo", 2);
        JSONException failure = assertThrows(JSONException.class,
                () -> IntegralSystemDef.parse(json, "armor.test"));
        assertTrue(failure.getMessage().contains("cooldown-gated"), failure.getMessage());
    }

    /** The policy is validated against the effect, both ways round. */
    @Test
    void aSweepMayOnlyDeclareTheDeadGroundMoment() throws JSONException {
        JSONObject crossing = sweep()
                .put("policy", SpecialAiPolicy.CROSSING_UNDER_FIRE.key)
                .put("threatRadiusCells", 12.0);
        JSONException onSweep = assertThrows(JSONException.class,
                () -> IntegralSystemDef.parse(crossing, "armor.test"));
        assertTrue(onSweep.getMessage().contains(SpecialAiPolicy.APPROACHING_DEAD_GROUND.key),
                onSweep.getMessage());

        JSONObject deadGroundOnAnAssist = new JSONObject()
                .put("id", "system.test-assist")
                .put("grade", "service")
                .put("displayName", "Test assist")
                .put("description", "A shove and a screen.")
                .put("effect", "breacher-assist")
                .put("resource", "cooldown")
                .put("policy", SpecialAiPolicy.APPROACHING_DEAD_GROUND.key)
                .put("lookaheadCells", 7.0)
                .put("durationSeconds", 3.0)
                .put("cooldownSeconds", 22.0)
                .put("moveSpeedMult", 1.4)
                .put("screenSoak", 15.0)
                .put("shieldedArcDegrees", 140.0);
        JSONException onAssist = assertThrows(JSONException.class,
                () -> IntegralSystemDef.parse(deadGroundOnAnAssist, "armor.test"));
        assertTrue(onAssist.getMessage().contains(SpecialAiPolicy.CROSSING_UNDER_FIRE.key),
                onAssist.getMessage());
    }

    /** A dead-ground policy with no authored lookahead is an author who has not judged the suit. */
    @Test
    void aDeadGroundPolicyWithoutItsLookaheadIsRefused() throws JSONException {
        JSONObject json = sweep();
        json.remove("lookaheadCells");
        JSONException failure = assertThrows(JSONException.class,
                () -> IntegralSystemDef.parse(json, "armor.test"));
        assertTrue(failure.getMessage().contains("lookaheadCells"), failure.getMessage());
    }

    /**
     * The story's premise, read off the catalog rather than asserted about it:
     * a sweep has to open ground the wearer could not otherwise reach, or it is
     * a window that shows what was already on screen. Both halves count — a
     * longer read, and a read that survives a nearby wall — so a pattern
     * satisfying either one passes.
     */
    @Test
    void everyCataloguedSweepReadsGroundItsWearerCouldNotOtherwiseSee() {
        MarineArmorCatalogRegistry catalog = MarineArmorCatalogRegistry.installed();
        assumeTrue(catalog != null, "armour catalog is not installed in this context");

        int inspected = 0;
        for (MarineArmorCatalogDef pattern : catalog.all()) {
            IntegralSystemDef system = pattern.integralSystem();
            if (system == null || system.perceptionSweep() == null) continue;
            inspected++;
            PerceptionSweepSpec sweep = system.perceptionSweep();
            float ordinarySight = UnitType.MARINE.visionRange;
            assertTrue(sweep.revealRangeCells() > ordinarySight
                            || sweep.wallReadRadiusCells() > 0f,
                    pattern.id() + " authors a sweep that reveals nothing its wearer's own"
                            + " sight does not already reach");
        }
        assumeTrue(inspected > 0, "no catalogued pattern carries a sweep in this context");
    }

    private static JSONObject sweep() throws JSONException {
        return new JSONObject()
                .put("id", "system.test-sweep")
                .put("grade", "milspec")
                .put("displayName", "Test sweep")
                .put("description", "One wide active return.")
                .put("effect", "perception-sweep")
                .put("resource", "cooldown")
                .put("policy", SpecialAiPolicy.APPROACHING_DEAD_GROUND.key)
                .put("lookaheadCells", 7.0)
                .put("durationSeconds", 4.0)
                .put("cooldownSeconds", 20.0)
                .put("revealRangeCells", 30.0)
                .put("wallReadRadiusCells", 6.0);
    }
}
