package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MechBayPatternTest {

    private static final String SIROCCO = "mech-pattern:chassis:sirocco";

    @Test
    void newBayKnowsOnlyStarterPatterns() {
        MechBay bay = new MechBay();
        assertEquals(MechPattern.STARTER.size(), bay.knownPatternIds().size());
        for (MechPattern starter : MechPattern.STARTER) assertTrue(bay.knowsPattern(starter.id()));
        assertFalse(bay.knowsPattern(SIROCCO));
        assertFalse(bay.knowsPattern(null));
    }

    @Test
    void learningAddsAndIsIdempotent() {
        MechBay bay = new MechBay();
        assertTrue(bay.learnPattern(SIROCCO));
        assertTrue(bay.knowsPattern(SIROCCO));
        assertFalse(bay.learnPattern(SIROCCO));
        assertFalse(bay.learnPattern("garbage"));
        assertEquals(MechPattern.STARTER.size() + 1, bay.knownPatternIds().size());
    }

    @Test
    void canFabricateFollowsLearning() {
        MechBay bay = new MechBay();
        assertTrue(bay.canFabricate(MechVariant.HOUND));
        assertFalse(bay.canFabricate(MechVariant.SIROCCO));
        assertFalse(bay.canFabricate(MechWeaponComponent.LRM_15));
        assertFalse(bay.canFabricate((MechVariant) null));
        bay.learnPattern(SIROCCO);
        bay.learnPattern(MechPattern.of(MechWeaponComponent.LRM_15).id());
        assertTrue(bay.canFabricate(MechVariant.SIROCCO));
        assertTrue(bay.canFabricate(MechWeaponComponent.LRM_15));
    }

    @Test
    void legacyStarterFixtureKnowsEveryChassisAndWeaponButOnlyStockInternals() {
        MechBay bay = MechBay.legacyStarterFixture();
        for (MechPattern pattern : MechPattern.all()) {
            boolean upgrade = pattern.kind() == MechPattern.Kind.INTERNAL && !pattern.starter();
            assertEquals(!upgrade, bay.knowsPattern(pattern.id()), pattern.id());
        }
        assertTrue(bay.canFabricate(MechVariant.BULWARK));
    }

    @Test
    void learningResults() {
        MechBay bay = new MechBay();
        assertEquals(MechPatternLearning.Result.BAY_UNAVAILABLE,
                MechPatternLearning.learn(null, SIROCCO));
        assertEquals(MechPatternLearning.Result.UNKNOWN_PATTERN,
                MechPatternLearning.learn(bay, "garbage"));
        assertEquals(MechPatternLearning.Result.LEARNED, MechPatternLearning.status(bay, SIROCCO));
        assertFalse(bay.knowsPattern(SIROCCO), "status must not learn");
        assertEquals(MechPatternLearning.Result.LEARNED, MechPatternLearning.learn(bay, SIROCCO));
        assertEquals(MechPatternLearning.Result.ALREADY_KNOWN, MechPatternLearning.learn(bay, SIROCCO));
        assertEquals(MechPatternLearning.Result.ALREADY_KNOWN,
                MechPatternLearning.learn(bay, MechPattern.STARTER.get(0).id()));
    }
}
