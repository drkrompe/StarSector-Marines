package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.render2d.BattleCamera;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurretShieldFieldTest {

    private static ShieldDomeDraw describe(float health, ShotEvent... shots) {
        BattleCamera camera = new BattleCamera(100, 100);
        camera.setViewport(0, 0, 800, 600, 10);
        ShieldDomeDraw draw = new ShieldDomeDraw();
        TurretShieldField.describe(draw, 50f, 50f, 2f, Faction.DEFENDER, health, List.of(shots),
                shot -> false, camera, camera.cellPxSize(), 1f);
        return draw;
    }

    @Test
    void aHostileRoundEndingInsideLightsTheRimOnTheShootersSide() {
        ShieldDomeDraw draw = describe(1f, new ShotEvent(40f, 50f, 50f, 50f, true, Faction.MARINE, 0.2f));
        assertEquals(1, draw.impactCount);
        assertTrue(draw.impacts[0] < -0.8f, "impact sits on the west rim, toward the shooter");
        assertEquals(0f, draw.impacts[1], 1e-4f);
        assertTrue(draw.hitFlash > 0.9f);
    }

    @Test
    void ownRoundsAndRoundsEndingElsewhereDoNotStrikeIt() {
        ShieldDomeDraw draw = describe(1f,
                new ShotEvent(40f, 50f, 50f, 50f, true, Faction.DEFENDER, 0.2f),
                new ShotEvent(40f, 50f, 60f, 60f, true, Faction.MARINE, 0.2f));
        assertEquals(0, draw.impactCount);
    }

    @Test
    void aFailingGeneratorDrawsTheShieldDownAndTakesNoHits() {
        ShieldDomeDraw draw = describe(0.1f, new ShotEvent(40f, 50f, 50f, 50f, true, Faction.MARINE, 0.2f));
        assertTrue(draw.overloaded);
        assertEquals(0, draw.impactCount);
    }
}
