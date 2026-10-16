package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Missiles at hulls: a Sabot's warheads fan across the spec's arc toward the hull, and a missile leads a moving hull. */
class WarshipHullMissilesTest {

    @BeforeAll
    static void install() {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
    }

    @Test
    void aSabotsWarheadsFanEvenlyAcrossItsArcTowardTheAimPoint() {
        VanillaWeapon sabot = VanillaWeapons.get("sabot");
        MissileFlightSpec.Mirv mirv = sabot.flight().mirv();
        assertNotNull(mirv, "the Sabot is a multi-warhead missile in the base game's data");
        List<WarshipShell.Warhead> heads = WarshipShipFire.warheads(Faction.ALLY, mirv, 0f, 0f, 18f, 0f, 7L);
        assertEquals(mirv.count(), heads.size());
        float meanY = 0f;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (WarshipShell.Warhead h : heads) {
            assertEquals(18f, (float) Math.hypot(h.aimX(), h.aimY()), 1e-3f, "each flies as far as the aim point");
            meanY += h.aimY() / heads.size();
            minY = Math.min(minY, h.aimY());
            maxY = Math.max(maxY, h.aimY());
        }
        assertTrue(Math.abs(meanY) < 1f, "centred on the aim point: " + meanY);
        // The spec's 20-degree arc at 18 cells is about 6 cells across; the jitter widens or narrows it a little.
        assertTrue(maxY - minY > 4f && maxY - minY < 8f, "spread across the arc: " + (maxY - minY));
    }

    @Test
    void aMissileLeadsAMovingHullByItsOwnFlightTime() throws Exception {
        Warship hound = Warship.fit("hound_d_pirates", "hound_d_pirates_Standard", Faction.DEFENDER, 40f, 0f, 90f);
        hound.velocityX = 0f;
        hound.velocityY = 5f;
        float[] aim = WarshipShipFire.aimFor(0f, 0f, hound, VanillaWeapons.get("sabot"));
        float seconds = WarshipShipFire.missileSeconds(VanillaWeapons.get("sabot").flight(), 0f, 0f, aim[0], aim[1]);
        assertTrue(seconds > 0f);
        assertEquals(hound.centerY() + 5f * seconds, aim[1], 1.5f, "led by its velocity over about the missile's flight");
        assertEquals(hound.centerX(), aim[0], 1e-3f);
    }
}
