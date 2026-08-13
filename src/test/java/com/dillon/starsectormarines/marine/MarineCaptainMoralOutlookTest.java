package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarineCaptainMoralOutlookTest {

    @Test
    void resolvesOutlookExactlyOnceWithTraitAndCommendation() {
        MarineCaptain captain = captain();

        assertTrue(captain.resolveMoralOutlook(Trait.IDEALIST, 121));
        assertEquals(Trait.IDEALIST, captain.moralOutlookTrait());
        assertEquals(121, captain.moralOutlookDay());
        assertTrue(captain.hasResolvedMoralOutlook());
        assertEquals(1, countTrait(captain, Trait.IDEALIST));
        assertEquals(1, captain.commendations().size());
        assertTrue(captain.commendations().get(0).contains("idealistic"));

        assertFalse(captain.resolveMoralOutlook(Trait.IDEALIST, 122));
        assertFalse(captain.resolveMoralOutlook(Trait.CYNICAL, 122));
        assertEquals(1, countTrait(captain, Trait.IDEALIST));
        assertEquals(1, captain.commendations().size());
    }

    @Test
    void rejectsNonOutlookTraits() {
        MarineCaptain captain = captain();

        assertFalse(captain.resolveMoralOutlook(Trait.VETERAN, 90));
        assertNull(captain.moralOutlookTrait());
        assertEquals(-1, captain.moralOutlookDay());
        assertTrue(captain.traits().isEmpty());
        assertTrue(captain.commendations().isEmpty());
    }

    @Test
    void repairAdoptsOneLegacyTraitWithoutInventingCommendation() throws Exception {
        MarineCaptain captain = captain();
        captain.traits().add(Trait.CYNICAL);
        setField(captain, "moralOutlookDay", 0);

        readResolve(captain);

        assertEquals(Trait.CYNICAL, captain.moralOutlookTrait());
        assertEquals(0, captain.moralOutlookDay());
        assertTrue(captain.hasResolvedMoralOutlook());
        assertTrue(captain.commendations().isEmpty());
    }

    @Test
    void repairRestoresTraitFromPersistedAuthority() throws Exception {
        MarineCaptain captain = captain();
        setField(captain, "moralOutlookTrait", Trait.IDEALIST);
        setField(captain, "moralOutlookDay", 140);

        readResolve(captain);

        assertEquals(Trait.IDEALIST, captain.moralOutlookTrait());
        assertEquals(140, captain.moralOutlookDay());
        assertEquals(1, countTrait(captain, Trait.IDEALIST));
        assertTrue(captain.commendations().isEmpty());
    }

    @Test
    void repairFailsClosedForContradictoryTraits() throws Exception {
        MarineCaptain captain = captain();
        captain.traits().add(Trait.IDEALIST);
        captain.traits().add(Trait.CYNICAL);
        setField(captain, "moralOutlookTrait", Trait.IDEALIST);
        setField(captain, "moralOutlookDay", 140);

        readResolve(captain);

        assertNull(captain.moralOutlookTrait());
        assertEquals(-1, captain.moralOutlookDay());
        assertFalse(captain.hasResolvedMoralOutlook());
        assertTrue(captain.traits().contains(Trait.IDEALIST));
        assertTrue(captain.traits().contains(Trait.CYNICAL));
        assertTrue(captain.commendations().isEmpty());
    }

    @Test
    void repairNormalizesLegacyUnresolvedDay() throws Exception {
        MarineCaptain captain = captain();
        setField(captain, "moralOutlookDay", 0);

        readResolve(captain);

        assertNull(captain.moralOutlookTrait());
        assertEquals(-1, captain.moralOutlookDay());
        assertFalse(captain.hasResolvedMoralOutlook());
    }

    private static MarineCaptain captain() {
        return new MarineCaptain("Outlook Captain", null, Rank.PRIVATE, 0f);
    }

    private static long countTrait(MarineCaptain captain, Trait trait) {
        return captain.traits().stream().filter(candidate -> candidate == trait).count();
    }

    private static void setField(MarineCaptain captain, String name, Object value)
            throws Exception {
        Field field = MarineCaptain.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(captain, value);
    }

    private static void readResolve(MarineCaptain captain) throws Exception {
        Method method = MarineCaptain.class.getDeclaredMethod("readResolve");
        method.setAccessible(true);
        method.invoke(captain);
    }
}
