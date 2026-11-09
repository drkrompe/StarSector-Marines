package com.dillon.starsectormarines.battle.combat.fx;

import com.dillon.starsectormarines.battle.weapon.fx.WeaponFxDef;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImpactDecalsTest {

    private static WeaponFxDef impact(String... kinds) throws Exception {
        StringBuilder layers = new StringBuilder();
        for (String kind : kinds) {
            if (layers.length() > 0) layers.append(',');
            layers.append("{\"kind\":\"").append(kind).append("\",\"radius\":0.5,\"lifetime\":0.5}");
        }
        return WeaponFxDef.parse("fx.test", new JSONObject("{\"impact\":[" + layers + "]}"));
    }

    private static List<Decal> mark(ImpactMark mark, boolean isWall, float headingDeg) {
        List<Decal> out = new ArrayList<>();
        ImpactDecals.spawnMark(out::add, new Random(7), mark, 4f, 5f, isWall, headingDeg, 1f);
        return out;
    }

    @Test
    void aWeaponNamingNoMarkIsReadFromItsImpactLayers() throws Exception {
        assertEquals(ImpactMark.SHELL, ImpactDecals.markOf(null, impact("glow", "explosion", "smoke")));
        assertEquals(ImpactMark.ROCKET, ImpactDecals.markOf(null, impact("glow", "fire", "smoke")));
        assertEquals(ImpactMark.HEAVY_ROUND, ImpactDecals.markOf(null, impact("glow", "dust", "smoke")));
        assertEquals(ImpactMark.SMALL_ARMS, ImpactDecals.markOf(null, impact("glow", "dust")));
        assertEquals(ImpactMark.NONE, ImpactDecals.markOf(null, null));
    }

    @Test
    void aWallTakesWallMarksAndTheGroundTakesGroundMarks() {
        Decal wall = mark(ImpactMark.HEAVY_ROUND, true, 0f).get(0);
        Decal ground = mark(ImpactMark.HEAVY_ROUND, false, 0f).get(0);
        assertTrue(DecalKind.HEAVY_WALL.owns(wall.decalIndex));
        assertTrue(DecalKind.HEAVY_GOUGE.owns(ground.decalIndex));

        List<Decal> shellOnWall = mark(ImpactMark.SHELL, true, 0f);
        assertTrue(shellOnWall.stream().noneMatch(d -> DecalKind.SHELL_CRATER.owns(d.decalIndex)),
                "a shell never craters a wall");
        assertTrue(shellOnWall.stream().anyMatch(d -> DecalKind.BLAST.owns(d.decalIndex)));
        assertTrue(mark(ImpactMark.SHELL, false, 0f).stream().anyMatch(d -> DecalKind.SHELL_CRATER.owns(d.decalIndex)));
    }

    @Test
    void aDirectionalMarkLiesAlongTheRoundsFlight() {
        Decal gouge = mark(ImpactMark.HEAVY_ROUND, false, 135f).get(0);
        assertEquals(135f, gouge.rotationDeg, 8f);
        assertEquals(4f, gouge.x, 0f);
        assertEquals(5f, gouge.y, 0f);
    }

    @Test
    void aMarkOfNothingLeavesNothing() {
        assertTrue(mark(ImpactMark.NONE, false, 0f).isEmpty());
        assertTrue(mark(ImpactMark.NONE, true, 0f).isEmpty());
    }
}
