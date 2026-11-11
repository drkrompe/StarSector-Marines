package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsLightArtTest {

    @Test
    void aBattleWithARuinLoadsItsFixturesAndThePlacedSheetsOverlays() {
        List<String> paths = RuinsLightArt.paths(Set.of("graphics/doodads/ruins-forge.png", "graphics/doodads/rocks.png"));
        assertTrue(paths.contains(RuinsLightArt.overlayPath("graphics/doodads/ruins-forge.png")));
        assertTrue(paths.stream().noneMatch(p -> p.contains("emissive/ruins-rooms")), "an unplaced sheet's overlay is not loaded");
        assertTrue(paths.contains(RuinsLightArt.fixturePath("ruins-ceiling-strip", false)));
        assertTrue(paths.contains(RuinsLightArt.fixturePath("ruins-ceiling-strip", true)));
    }

    @Test
    void aBattleWithNoRuinLoadsNone() {
        assertEquals(List.of(), RuinsLightArt.paths(Set.of("graphics/doodads/rocks.png")));
        assertEquals(List.of(), RuinsLightArt.paths(null));
    }
}
