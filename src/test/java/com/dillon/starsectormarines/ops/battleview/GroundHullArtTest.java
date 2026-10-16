package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundHullArtTest {

    @Test
    void remasteredHullsResolveToShippedArtAndOthersKeepTheirOwn() {
        assertEquals("graphics/battle/ships/venture.png", GroundHullArt.path("graphics/ships/venture/venture.png"));
        assertEquals("graphics/battle/ships/hound_pirates.png", GroundHullArt.path("graphics\\ships\\hound\\hound_pirates.png"));
        assertEquals("graphics/ships/wolf/wolf.png", GroundHullArt.path("graphics/ships/wolf/wolf.png"));
        for (String vanilla : new String[]{"graphics/ships/venture/venture.png", "graphics/ships/hound/hound_pirates.png"})
            assertTrue(Files.isRegularFile(Path.of("mod").resolve(GroundHullArt.path(vanilla))), vanilla + " ships its remaster");
    }
}
