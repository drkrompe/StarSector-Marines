package com.dillon.starsectormarines.battle.warship;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VanillaBeamLookTest {

    /** The Tachyon Lance's own spec, trimmed to the fields a look reads. */
    private static final String TACHYON_LANCE = """
            {
              "specClass":"beam", "id":"tachyonlance", "type":"ENERGY", "size":"LARGE",
              "turretSprite":"graphics/weapons/tachyon_lance_turret_base.png",
              "turretGlowSprite":"graphics/weapons/tachyon_lance_turret_glow.png",
              "fringeColor":[85,25,215,255],
              "coreColor":[255,255,255,255],
              "glowColor":[165,100,255,235],
              "width":25.0,
              "textureType":ROUGH,
              "textureScrollSpeed":292.0,
              "pixelsPerTexel":5.0,
            }""";

    @Test
    void aBeamSpecCarriesItsOwnColoursWidthAndTextures() throws Exception {
        VanillaBeamLook look = VanillaBeamLook.fromSpec("tachyonlance", new JSONObject(TACHYON_LANCE));
        assertEquals(new Color(85, 25, 215, 255), look.fringe());
        assertEquals(Color.WHITE, look.core());
        assertEquals(new Color(165, 100, 255, 235), look.glow());
        assertEquals(25f, look.widthPx(), 1e-6);
        assertEquals(292f, look.scrollSpeed(), 1e-6);
        assertEquals("graphics/fx/beam_rough2_fringe.png", look.fringeTexture());
        assertEquals("graphics/fx/beam_rough2_core.png", look.coreTexture());
        assertEquals("graphics/weapons/tachyon_lance_turret_base.png", look.turretSprite());
    }

    @Test
    void anExplicitTexturePairIsUsedAsGiven() throws Exception {
        VanillaBeamLook look = VanillaBeamLook.fromSpec("x", new JSONObject("""
                {"specClass":"beam","textureType":["graphics/fx/a.png","graphics/fx/b.png"]}"""));
        assertEquals("graphics/fx/a.png", look.fringeTexture());
        assertEquals("graphics/fx/b.png", look.coreTexture());
    }

    @Test
    void aProjectileHasNoBeamLook() throws Exception {
        assertNull(VanillaBeamLook.fromSpec("autopulse",
                new JSONObject("{\"specClass\":\"projectile\"}")));
    }

    @Test
    void anUnresolvableWeaponIsAbsentNotAnError() {
        VanillaData.useSource(new VanillaData.Source() {
            @Override public JSONObject json(String path) throws Exception {
                throw new java.io.FileNotFoundException(path);
            }

            @Override public java.util.List<java.util.Map<String, String>> csv(String path) {
                return java.util.List.of();
            }
        });
        VanillaWeaponLooks.clearCache();
        try {
            assertNull(VanillaWeaponLooks.beam("nonesuch"));
        } finally {
            VanillaData.useSource(null);
            VanillaWeaponLooks.clearCache();
        }
    }
}
