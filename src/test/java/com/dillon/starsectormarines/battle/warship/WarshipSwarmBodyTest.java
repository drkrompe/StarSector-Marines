package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.combat.Interceptable;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a body is to point defence, and what fusing does to one. */
class WarshipSwarmBodyTest {

    private static VanillaWeapon gun() {
        return new VanillaWeapon("test", 100f, "HIGH_EXPLOSIVE", 1000f, 1, 0.1f, 1f, 400f, 0f, 60f,
                false, 0f, 0f, true, false, 100f, 0f, null, null, null, null, 0f, 0f, null, 0f,
                null, null, null, null, null, null, null);
    }

    private static WarheadStrike strike(float hitpoints) {
        BloomPath path = BloomPath.plan(1L, 0f, 0f, 10f, 0f, 1f, 0f, 0.3f, 0f);
        WarshipShell.Warhead warhead = new WarshipShell.Warhead(
                WarshipSystem.detonation(Faction.MARINE, 10f, 0f, 500f, 8f, 3f), 1f, 3f, 10f, 0f, path, hitpoints);
        return new WarheadStrike(gun(), Faction.MARINE, null, 0f, 0f, warhead, null);
    }

    @Test
    void aWarheadIsABodyPointDefenceCanSeeAndKill() {
        WarheadStrike s = strike(40f);
        Interceptable body = s;
        assertFalse(body.isIntercepted());
        assertEquals(40f, body.hitpoints(), 0f);
        s.advance(0.5f);
        assertTrue(body.speedCells() > 0f);
        assertEquals(s.path().x(0.5f), body.x(), 0f);
        body.intercept();
        assertTrue(body.isIntercepted());
    }

    @Test
    void aToughBodyIsHarderToStopThanASoftOne() {
        VanillaWeapon flak = new VanillaWeapon("flak", 0f, "FRAGMENTATION", 800f, 1, 0.1f, 1f, 700f, 0f, 60f,
                false, 0f, 0f, false, true, 0f, 0f, null, null, null, null, 0f, 0f, null, 0f,
                null, null, null, null, null, null, null);
        float soft = WarshipPointDefence.engagementKillChance(flak, 5f, 10f, 40f);
        float tough = WarshipPointDefence.engagementKillChance(flak, 5f, 10f, 500f);
        assertEquals(WarshipPointDefence.engagementKillChance(flak, 5f, 10f), soft, 1e-6f, "soft bodies are the old rule");
        assertTrue(tough < soft);
        assertEquals(1f, Interceptable.toughnessFactor(60f), 0f);
        assertEquals(0.5f, Interceptable.toughnessFactor(60f + Interceptable.TOUGHNESS_SCALE), 1e-5f);
    }

    @Test
    void aFusedRoundCarriesTheSumOfDamageAndArea() {
        WarshipShell shell = new WarshipShell(gun(), Faction.MARINE, null, 0f, 0f, 20f, 0f,
                MissilePath.straight(0f, 0f, 20f, 0f, 10f), 4f, 200f, 8f, 3f, List.of(), null, 50f, null, 0f);
        assertFalse(shell.carrier());
        shell.merge(200f, 4f, 3f);
        assertEquals(400f, shell.damage(), 1e-4f);
        assertEquals(5f, shell.damageRadius(), 1e-4f);
        assertEquals(5f, shell.blastCells(), 1e-4f);
        assertEquals(400f, shell.landing().damage, 1e-4f);
        assertEquals(5f, shell.landing().aoeRadius, 1e-4f);
    }

    @Test
    void aCarrierStandsForItsWarheadsAndHasNoLandingOfItsOwn() {
        BloomPath path = BloomPath.plan(1L, 0f, 0f, 10f, 0f, 1f, 0f, 0.3f, 0f);
        WarshipShell.Warhead w = new WarshipShell.Warhead(
                WarshipSystem.detonation(Faction.MARINE, 10f, 0f, 500f, 8f, 3f), 1f, 3f, 10f, 0f, path, 40f);
        WarshipShell carrier = new WarshipShell(gun(), Faction.MARINE, null, 0f, 0f, 20f, 0f,
                MissilePath.straight(0f, 0f, 20f, 0f, 10f), 4f, 0f, 8f, 3f, List.of(w, w, w), null, 250f, null, 0f);
        assertTrue(carrier.carrier());
        assertEquals(3, carrier.bodyCount());
        assertNull(carrier.landing());
        assertEquals(500f, carrier.damage(), 0f);
    }
}
