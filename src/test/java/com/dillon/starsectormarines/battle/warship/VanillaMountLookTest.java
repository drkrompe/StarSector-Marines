package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.warship.VanillaMountLook.Kind;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaMountLookTest {

    /** The Arbalest's own spec, trimmed to the fields a mount look reads. */
    private static final String ARBALEST = """
            {
              "id":"arbalest", "specClass":"projectile",
              "turretSprite":"graphics/weapons/arbalest_turret_base.png",
              "turretGunSprite":"graphics/weapons/arbalest_turret_recoil.png",
              "hardpointSprite":"graphics/weapons/arbalest_hardpoint_base.png",
              "hardpointGunSprite":"graphics/weapons/arbalest_hardpoint_recoil.png",
              "visualRecoil":6.0,
              "renderHints":[RENDER_BARREL_BELOW],
              "turretOffsets":[13, 0], "turretAngleOffsets":[0],
              "hardpointOffsets":[28, 0], "hardpointAngleOffsets":[0],
              "barrelMode":"ALTERNATING", "animationType":"MUZZLE_FLASH"
            }""";

    private static List<Kind> kinds(List<VanillaMountLook.Layer> layers) {
        return layers.stream().map(VanillaMountLook.Layer::kind).toList();
    }

    private static VanillaMountLook look(String spec) throws Exception {
        return VanillaMountLook.fromSpec("w", new JSONObject(spec), null);
    }

    @Test
    void anArbalestDrawsItsBarrelUnderItsBaseOnEitherMount() throws Exception {
        VanillaMountLook look = look(ARBALEST);
        assertEquals(List.of(Kind.BARREL, Kind.BASE), kinds(look.layers(false)));
        assertEquals("graphics/weapons/arbalest_turret_recoil.png", look.layers(false).get(0).sprite());
        assertEquals("graphics/weapons/arbalest_hardpoint_recoil.png", look.layers(true).get(0).sprite());
        assertEquals("graphics/weapons/arbalest_hardpoint_base.png", look.layers(true).get(1).sprite());
        assertEquals(13f, look.barrels(false).get(0).forwardPx());
        assertEquals(28f, look.barrels(true).get(0).forwardPx());
    }

    @Test
    void underPlateComesFirstAndAnUnhintedBarrelGoesOverTheBase() throws Exception {
        VanillaMountLook look = look("""
                {"turretUnderSprite":"u.png", "turretSprite":"b.png", "turretGunSprite":"g.png",
                 "turretGlowSprite":"glow.png", "glowColor":[100,100,255,200], "visualRecoil":-10}""");
        assertEquals(List.of(Kind.UNDER, Kind.BASE, Kind.BARREL, Kind.GLOW), kinds(look.layers(false)));
        assertEquals(new Color(100, 100, 255, 200), look.glowColor());
        // A negative recoil pushes the barrel forward, as the mass drivers ask.
        assertEquals(-10f, look.recoilPx(0f, 1f), 1e-6);
        // The hardpoint set is the spec's own, and it names none here.
        assertTrue(look.layers(true).isEmpty());
    }

    @Test
    void aGunSpriteWithNoRecoilIsNotDrawn() throws Exception {
        VanillaMountLook look = look("""
                {"turretSprite":"b.png", "turretGunSprite":"g.png"}""");
        assertEquals(List.of(Kind.BASE), kinds(look.layers(false)));
    }

    @Test
    void anOpenRackShowsTheMissilesItHoldsAboveItsBase() throws Exception {
        VanillaMountLook look = VanillaMountLook.fromSpec("harpoon", new JSONObject("""
                {"turretSprite":"rack.png", "renderHints":[RENDER_LOADED_MISSILES],
                 "turretOffsets":[8, -6, 8, 6, 8, 0], "turretAngleOffsets":[0, 0, 0]}"""),
                new JSONObject("""
                {"sprite":"graphics/missiles/missile_harpoon.png", "size":[10,22], "center":[5,12]}"""));
        assertEquals(List.of(Kind.BASE, Kind.LOADED_MISSILES), kinds(look.layers(false)));
        assertNull(look.layers(false).get(1).sprite());
        assertEquals("graphics/missiles/missile_harpoon.png", look.missileSprite());
        assertEquals(-1f, look.missileCentreForwardPx(), 1e-6);
        assertEquals(3, look.barrels(false).size());
        assertEquals(-6f, look.barrels(false).get(0).leftPx());
    }

    @Test
    void barrelsAlternateUnlessLinked() throws Exception {
        VanillaMountLook alternating = look("""
                {"turretSprite":"b.png", "turretOffsets":[17, -3, 17, 3]}""");
        assertArrayEquals(new boolean[]{true, false}, alternating.firedBarrels(false, 1, 1));
        assertArrayEquals(new boolean[]{false, true}, alternating.firedBarrels(false, 2, 1));
        assertArrayEquals(new boolean[]{true, true}, alternating.firedBarrels(false, 4, 3));
        assertArrayEquals(new boolean[]{false, false}, alternating.firedBarrels(false, 0, 1));
        VanillaMountLook linked = look("""
                {"turretSprite":"b.png", "turretOffsets":[17, -3, 17, 3], "barrelMode":"LINKED"}""");
        assertArrayEquals(new boolean[]{true, true}, linked.firedBarrels(false, 1, 1));
        // A weapon naming no offsets fires from its pivot.
        assertEquals(1, look("{\"turretSprite\":\"b.png\"}").barrels(true).size());
    }

    @Test
    void aRotaryCannonTurnsItsFramesOnlyWhileFiring() throws Exception {
        VanillaMountLook look = look("""
                {"turretSprite":"graphics/weapons/rotary/rotary_cannon_turret_base00.png",
                 "numFrames":4, "frameRate":20}""");
        assertEquals("graphics/weapons/rotary/rotary_cannon_turret_base02.png",
                look.frameSprite(look.turret().base(), 2));
        assertEquals("graphics/weapons/rotary/rotary_cannon_turret_base00.png",
                look.frameSprite(look.turret().base(), 0));
        assertEquals("glow.png", look.frameSprite("glow.png", 3));
        assertEquals(3, look.frameAt(0.16f, 0.05f, 0.1f));
        assertEquals(0, look.frameAt(0.15f, 1000f, 0.1f));
        assertEquals("b01.png", look("{\"turretSprite\":\"b00.png\"}").frameSprite("b01.png", 2));
    }

    @Test
    void theBarrelKicksBackOnFiringAndRunsOutAgain() throws Exception {
        VanillaMountLook look = look(ARBALEST);
        assertEquals(6f, look.recoilPx(0f, 0.5f), 1e-6);
        float mid = look.recoilPx(0.2f, 0.5f);
        assertTrue(mid > 0f && mid < 6f, "part-way back: " + mid);
        assertEquals(0f, look.recoilPx(0.5f, 0.5f), 1e-6);
        assertEquals(0f, look.recoilPx(1000f, 0.5f), 1e-6);
        assertEquals(1f, look.glowLevel(0f, 1f), 1e-6);
        assertEquals(0f, look.glowLevel(1000f, 1f), 1e-6);
    }
}
