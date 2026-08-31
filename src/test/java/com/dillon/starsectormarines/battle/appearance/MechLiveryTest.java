package com.dillon.starsectormarines.battle.appearance;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MechLiveryTest {

    @Test
    void exactCampaignFactionIdsResolveToAuthoredFamilies() {
        Map<String, MechLivery> expected = Map.ofEntries(
                Map.entry("hegemony", MechLivery.HEGEMONY),
                Map.entry("tritachyon", MechLivery.TRI_TACHYON),
                Map.entry("persean", MechLivery.PERSEAN_LEAGUE),
                Map.entry("luddic_church", MechLivery.LUDDIC_CHURCH),
                Map.entry("knights_of_ludd", MechLivery.KNIGHTS_OF_LUDD),
                Map.entry("luddic_path", MechLivery.LUDDIC_PATH),
                Map.entry("sindrian_diktat", MechLivery.SINDRIAN_DIKTAT),
                Map.entry("lions_guard", MechLivery.LIONS_GUARD),
                Map.entry("pirates", MechLivery.PIRATES),
                Map.entry("independent", MechLivery.INDEPENDENT));

        expected.forEach((id, livery) -> assertEquals(livery,
                MechLivery.forFactionId(id), id));
    }

    @Test
    void practicalAndSafetyFallbacksStayExplicit() {
        assertEquals(MechLivery.INDEPENDENT,
                MechLivery.forFactionId("mercenary"));
        assertEquals(MechLivery.INDEPENDENT,
                MechLivery.forFactionId("some_modded_human_faction"));
        assertEquals(MechLivery.BASE, MechLivery.forFactionId("remnant"));
        assertEquals(MechLivery.BASE, MechLivery.forFactionId("derelict"));
        assertEquals(MechLivery.BASE, MechLivery.forFactionId("omega"));
        assertEquals(MechLivery.BASE, MechLivery.forFactionId("  "));
        assertEquals(MechLivery.BASE, MechLivery.forFactionId(null));
    }
}
