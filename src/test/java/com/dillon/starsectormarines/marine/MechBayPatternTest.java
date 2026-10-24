package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MechBayPatternTest {

    private static final String WARDEN = "mech-pattern:chassis:warden";

    @Test
    void newBayKnowsOnlyStarterPatterns() {
        MechBay bay = new MechBay();
        assertEquals(MechPattern.STARTER.size(), bay.knownPatternIds().size());
        for (MechPattern starter : MechPattern.STARTER) assertTrue(bay.knowsPattern(starter.id()));
        assertFalse(bay.knowsPattern(WARDEN));
        assertFalse(bay.knowsPattern(null));
    }

    @Test
    void learningAddsAndIsIdempotent() {
        MechBay bay = new MechBay();
        assertTrue(bay.learnPattern(WARDEN));
        assertTrue(bay.knowsPattern(WARDEN));
        assertFalse(bay.learnPattern(WARDEN));
        assertFalse(bay.learnPattern("garbage"));
        assertEquals(MechPattern.STARTER.size() + 1, bay.knownPatternIds().size());
    }

    @Test
    void canFabricateFollowsLearning() {
        MechBay bay = new MechBay();
        assertTrue(bay.canFabricate(MechVariant.HOUND));
        assertFalse(bay.canFabricate(MechVariant.WARDEN));
        assertFalse(bay.canFabricate(MechWeaponComponent.LRM_15));
        assertFalse(bay.canFabricate((MechVariant) null));
        bay.learnPattern(WARDEN);
        bay.learnPattern(MechPattern.of(MechWeaponComponent.LRM_15).id());
        assertTrue(bay.canFabricate(MechVariant.WARDEN));
        assertTrue(bay.canFabricate(MechWeaponComponent.LRM_15));
    }

    @Test
    void legacyStarterFixtureKnowsEveryChassisAndWeaponButOnlyStockInternalsAndNoKits() {
        MechBay bay = MechBay.legacyStarterFixture();
        for (MechPattern pattern : MechPattern.all()) {
            boolean upgrade = (pattern.kind() == MechPattern.Kind.INTERNAL || pattern.kind() == MechPattern.Kind.KIT)
                    && !pattern.starter();
            assertEquals(!upgrade, bay.knowsPattern(pattern.id()), pattern.id());
        }
        assertTrue(bay.canFabricate(MechVariant.BULWARK));
    }

    @Test
    void learningResults() {
        MechBay bay = new MechBay();
        assertEquals(MechPatternLearning.Result.BAY_UNAVAILABLE,
                MechPatternLearning.learn(null, WARDEN));
        assertEquals(MechPatternLearning.Result.UNKNOWN_PATTERN,
                MechPatternLearning.learn(bay, "garbage"));
        assertEquals(MechPatternLearning.Result.LEARNED, MechPatternLearning.status(bay, WARDEN));
        assertFalse(bay.knowsPattern(WARDEN), "status must not learn");
        assertEquals(MechPatternLearning.Result.LEARNED, MechPatternLearning.learn(bay, WARDEN));
        assertEquals(MechPatternLearning.Result.ALREADY_KNOWN, MechPatternLearning.learn(bay, WARDEN));
        assertEquals(MechPatternLearning.Result.ALREADY_KNOWN,
                MechPatternLearning.learn(bay, MechPattern.STARTER.get(0).id()));
    }
}
