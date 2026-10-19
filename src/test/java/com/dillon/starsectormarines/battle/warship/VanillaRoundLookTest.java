package com.dillon.starsectormarines.battle.warship;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How a round's projectile spec resolves to what is drawn, and at what size. Specs abridged from the install's. */
class VanillaRoundLookTest {

    private static final String HEAVYAC = "{\"id\":\"heavyac_shot\",\"specClass\":\"projectile\","
            + "\"spawnType\":\"BALLISTIC\",\"length\":40.0,\"hitGlowRadius\":38,\"width\":6.5,"
            + "\"fringeColor\":[235,255,215,235],\"coreColor\":[225,255,205,200],"
            + "\"bulletSprite\":\"graphics/missiles/shell_large_green.png\"}";
    private static final String AMBLASTER = "{\"id\":\"amblaster_shot\",\"specClass\":\"projectile\","
            + "\"spawnType\":\"BALLISTIC_AS_BEAM\",\"length\":75.0,\"width\":9.5,"
            + "\"fringeColor\":[150,100,255,255],\"coreColor\":[255,255,255,255],"
            + "\"glowColor\":[150,100,255,75],\"glowRadius\":75,"
            + "\"bulletSprite\":\"graphics/missiles/shell_gauss_cannon.png\"}";
    private static final String PULSELASER = "{\"id\":\"pulselaser_shot\",\"specClass\":\"projectile\","
            + "\"spawnType\":\"BALLISTIC_AS_BEAM\",\"length\":90.0,\"width\":15.0,"
            + "\"fringeColor\":[0,200,0,255],\"coreColor\":[255,255,255,200]}";
    private static final String HARPOON = "{\"id\":\"harpoon_mrm\",\"specClass\":\"missile\","
            + "\"sprite\":\"graphics/missiles/missile_harpoon.png\",\"size\":[10,22],\"center\":[5,12],"
            + "\"engineSlots\":[{\"id\":\"ES1\",\"loc\":[-13,0],\"style\":\"CUSTOM\",\"styleSpec\":{"
            + "\"engineColor\":[100,165,255,255],\"contrailDuration\":1,\"contrailWidthMult\":1,"
            + "\"contrailWidthAddedFractionAtEnd\":2.5,\"contrailMaxSpeedMult\":\"0.5f\","
            + "\"contrailColor\":[100,165,255,75],\"type\":\"SMOKE\"},"
            + "\"width\":7.0,\"length\":40.0,\"angle\":180.0}]}";

    private static JSONObject spec(String json) {
        try {
            return new JSONObject(json);
        } catch (Exception e) {
            throw new IllegalArgumentException(json, e);
        }
    }

    @Test
    void aBallisticShellIsItsSpriteStretchedToTheSpecsLengthAndWidth() {
        VanillaRoundLook look = VanillaRoundLook.fromSpec(spec(HEAVYAC));
        assertEquals(VanillaRoundLook.Kind.SHELL, look.kind());
        assertEquals("graphics/missiles/shell_large_green.png", look.sprite());
        assertEquals(40f, look.lengthPx(), 1e-6f);
        assertEquals(6.5f, look.widthPx(), 1e-6f);
        assertNull(look.glowColor(), "a heavy autocannon names no glow of its own");
    }

    @Test
    void aBallisticAsBeamRoundIsAStreakEvenWhenItNamesABulletSprite() {
        VanillaRoundLook look = VanillaRoundLook.fromSpec(spec(AMBLASTER));
        assertEquals(VanillaRoundLook.Kind.STREAK, look.kind());
        assertNull(look.sprite());
        assertEquals(75f, look.lengthPx(), 1e-6f);
        assertEquals(9.5f, look.widthPx(), 1e-6f);
        assertEquals(new Color(150, 100, 255, 75), look.glowColor());
        assertEquals(75f, look.glowRadiusPx(), 1e-6f);
        assertEquals(VanillaRoundLook.Kind.STREAK, VanillaRoundLook.fromSpec(spec(PULSELASER)).kind());
    }

    @Test
    void theWeaponTableReadingNoLongerHandsAStreakItsBulletSprite() {
        VanillaWeapon blaster = VanillaWeapon.of("amblaster", Map.of(), null, spec(AMBLASTER));
        assertNull(blaster.projectileSprite());
        VanillaWeapon autocannon = VanillaWeapon.of("heavyac", Map.of(), null, spec(HEAVYAC));
        assertEquals("graphics/missiles/shell_large_green.png", autocannon.projectileSprite());
    }

    @Test
    void aProjectileWithNoSpriteAndNoSizeIsADefaultStreak() {
        VanillaRoundLook look = VanillaRoundLook.fromSpec(
                spec("{\"specClass\":\"projectile\",\"spawnType\":\"BALLISTIC\"}"));
        assertEquals(VanillaRoundLook.Kind.STREAK, look.kind());
        assertTrue(look.lengthPx() > 0f && look.widthPx() > 0f);
    }

    @Test
    void aMissileIsItsSizeAtItsCentreWithItsNozzleAndContrail() {
        VanillaRoundLook look = VanillaRoundLook.fromSpec(spec(HARPOON));
        assertEquals(VanillaRoundLook.Kind.MISSILE, look.kind());
        assertEquals(22f, look.lengthPx(), 1e-6f);
        assertEquals(10f, look.widthPx(), 1e-6f);
        assertEquals(-1f, look.spriteAheadPx(), 1e-6f, "the sprite's middle sits a pixel astern of its centre");
        assertEquals(13f, look.nozzleBehindPx(), 1e-6f);
        assertEquals(40f, look.flameLengthPx(), 1e-6f);
        assertEquals(7f, look.contrailWidthPx(), 1e-6f);
        assertEquals(2.5f, look.contrailGrowthAtEnd(), 1e-6f);
        assertEquals(new Color(100, 165, 255, 75), look.contrailColor());
        assertFalse(look.contrailGlow());
        assertEquals(0.5f, look.contrailDrift(), 1e-6f, "a float literal the spec writes as 0.5f still reads");
    }

    @Test
    void betweenHullsARoundIsDrawnAtTheHullsScaleSoItsProportionToAHullIsTheBaseGames() {
        VanillaRoundLook look = VanillaRoundLook.fromSpec(spec(HEAVYAC));
        float enforcerPx = 139f;
        float hullCells = WarshipScale.hullCells(enforcerPx);
        assertEquals(40f / enforcerPx, look.lengthCells(true) / hullCells, 1e-4f);
        assertEquals(6.5f / enforcerPx, look.widthCells(true) / hullCells, 1e-4f);
        // At the ground it keeps the shared density's slimmed size.
        assertEquals(40f * WarshipScale.cells(1f) * VanillaRoundLook.GROUND_ROUND_SCALE, look.lengthCells(false), 1e-4f);
        VanillaRoundLook blaster = VanillaRoundLook.fromSpec(spec(AMBLASTER));
        assertEquals(75f * WarshipScale.hullCells(1f), blaster.glowRadiusCells(true), 1e-4f);
    }

    @Test
    void aContrailHangsOnTheHullFightsClockBetweenHulls() {
        VanillaRoundLook look = VanillaRoundLook.fromSpec(spec(HARPOON));
        assertEquals(WarshipScale.HULL_TEMPO, look.contrailLifeSeconds(true), 1e-5f);
        assertEquals(1f, look.contrailLifeSeconds(false), 1e-6f);
    }
}
