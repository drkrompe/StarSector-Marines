package com.dillon.starsectormarines.battle.air.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Pure tests for the {@link EngineVoice} flyweight — the (engine style, hull
 * size) → vanilla {@code sfx_engines} clip mapping, its fallbacks, and the
 * flyweight identity guarantee. No {@code Global}, no game install: the mapping
 * is data-only, so it tests directly.
 */
class EngineVoiceTest {

    @Test
    void coreTiersMapOneToOne() {
        assertEquals("marines_engine_lotek_frigate",  EngineVoice.forSpec("LOW_TECH", "FRIGATE").loopSoundId);
        assertEquals("marines_engine_midtek_frigate", EngineVoice.forSpec("MIDLINE", "FRIGATE").loopSoundId);
        assertEquals("marines_engine_hitek_fighter",  EngineVoice.forSpec("HIGH_TECH", "FIGHTER").loopSoundId);
        assertEquals("marines_engine_omega_capital",  EngineVoice.forSpec("OMEGA", "CAPITAL_SHIP").loopSoundId);
        assertEquals("marines_engine_dweller_cruiser", EngineVoice.forSpec("DWELLER", "CRUISER").loopSoundId);
    }

    @Test
    void hullSizesMapToSuffix() {
        assertEquals(EngineVoice.Size.FIGHTER,   EngineVoice.forSpec("MIDLINE", "FIGHTER").size);
        assertEquals(EngineVoice.Size.FRIGATE,   EngineVoice.forSpec("MIDLINE", "FRIGATE").size);
        assertEquals(EngineVoice.Size.DESTROYER, EngineVoice.forSpec("MIDLINE", "DESTROYER").size);
        assertEquals(EngineVoice.Size.CRUISER,   EngineVoice.forSpec("MIDLINE", "CRUISER").size);
        assertEquals(EngineVoice.Size.CAPITAL,   EngineVoice.forSpec("MIDLINE", "CAPITAL_SHIP").size);
    }

    @Test
    void unmappedStylesFallBackByFeel() {
        // Boss / faction styles with no dedicated engine clip resolve to a real tier.
        assertEquals(EngineVoice.Tier.LOTEK, EngineVoice.forSpec("ONSLAUGHT_MKI", "CAPITAL_SHIP").tier);
        assertEquals(EngineVoice.Tier.HITEK, EngineVoice.forSpec("THREAT", "CRUISER").tier);
        assertEquals(EngineVoice.Tier.HITEK, EngineVoice.forSpec("ATTACK_SWARM", "FIGHTER").tier);
        assertEquals(EngineVoice.Tier.MIDTEK, EngineVoice.forSpec("COBRA_BOMBER", "FIGHTER").tier);
    }

    @Test
    void nullsAndUnknownsDefaultToMidtekFrigate() {
        assertEquals("marines_engine_midtek_frigate", EngineVoice.forSpec(null, null).loopSoundId);
        assertEquals("marines_engine_midtek_frigate", EngineVoice.forSpec("NONSENSE", "ALSO_NONSENSE").loopSoundId);
        assertEquals("marines_engine_midtek_frigate", EngineVoice.DEFAULT.loopSoundId);
    }

    /** Dweller ships no fighter-size clip; the resolver clamps to the dweller frigate so the id names a real file. */
    @Test
    void dwellerFighterClampsToFrigate() {
        EngineVoice v = EngineVoice.forSpec("DWELLER", "FIGHTER");
        assertEquals(EngineVoice.Tier.DWELLER, v.tier);
        assertEquals(EngineVoice.Size.FRIGATE, v.size);
        assertEquals("marines_engine_dweller_frigate", v.loopSoundId);
    }

    /** Flyweight: same (tier, size) always returns the same interned instance, including via the clamp. */
    @Test
    void sameSpecReturnsSameInstance() {
        assertSame(EngineVoice.forSpec("HIGH_TECH", "CRUISER"),
                   EngineVoice.forSpec("HIGH_TECH", "CRUISER"));
        // THREAT and HIGH_TECH both resolve to hitek — share the instance for a given size.
        assertSame(EngineVoice.forSpec("HIGH_TECH", "FRIGATE"),
                   EngineVoice.forSpec("THREAT", "FRIGATE"));
        assertSame(EngineVoice.forSpec("DWELLER", "FIGHTER"),
                   EngineVoice.forSpec("DWELLER", "FRIGATE"));
    }
}
