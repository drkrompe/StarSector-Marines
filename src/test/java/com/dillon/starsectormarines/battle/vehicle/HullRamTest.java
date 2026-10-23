package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.unit.SwarmStage;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HullRamTest {
    private static final float MAN = .1f, BRUTE = 3f, PICKUP = 2.5f, TANK = 45f;

    @Test void aTankThroughARiflemanHurtsHimAndNotItself() {
        HullRam.Exchange hit = HullRam.exchange(3f, TANK, MAN);
        assertTrue(hit.bodyDamage() > 15f, "real impact damage: " + hit.bodyDamage());
        assertEquals(0f, hit.hullDamage());
        assertTrue(hit.hullDv() < .05f, "the tank barely notices");
    }

    @Test void aPickupIntoABruteIsBroughtUpShortAndHurtMoreThanTheBrute() {
        HullRam.Exchange light = HullRam.exchange(3f, PICKUP, BRUTE);
        HullRam.Exchange heavy = HullRam.exchange(3f, TANK, BRUTE);
        assertTrue(light.hullDv() > 1.5f);
        assertTrue(light.hullDamage() > light.bodyDamage());
        assertTrue(heavy.bodyDamage() > 3f * light.bodyDamage(), "mass decides what the Brute takes");
        assertEquals(0f, heavy.hullDamage());
    }

    @Test void damageGrowsWithClosingSpeedFromNothingAtACrawl() {
        assertEquals(0f, HullRam.exchange(.8f, TANK, MAN).bodyDamage());
        float slow = HullRam.exchange(2f, TANK, MAN).bodyDamage(), fast = HullRam.exchange(3f, TANK, MAN).bodyDamage();
        assertTrue(slow > 0f && fast > slow);
        assertSame(HullRam.Exchange.NONE, HullRam.exchange(0f, TANK, MAN));
    }

    @Test void onlyTheHullsOwnTravelCloses() {
        assertEquals(0f, HullRam.closing(0f, -3f), "a body running into a parked hull is not rammed");
        assertEquals(2f, HullRam.closing(2f, -3f), "running at the bow adds nothing");
        assertEquals(.5f, HullRam.closing(2f, 1.5f), 1e-6f, "running away takes some of it off");
    }

    @Test void theShoveIsSplitByMass() {
        assertTrue(HullRam.shoveShare(PICKUP, MAN) > .95f);
        assertTrue(HullRam.shoveShare(PICKUP, HullRam.bodyTonnes(UnitType.SWARM_MATRIARCH, SwarmStage.ADULT, 1f)) < .1f);
        assertEquals(.5f, HullRam.shoveShare(10f, 10f));
    }

    @Test void swarmMassFollowsTheStage() {
        assertTrue(HullRam.bodyTonnes(UnitType.SWARM_RUNNER, SwarmStage.WHELP, 1f)
                < HullRam.bodyTonnes(UnitType.SWARM_RUNNER, SwarmStage.ELDER, 1f));
        assertEquals(3f, HullRam.bodyTonnes(UnitType.SWARM_BRUTE, null, 1f));
        assertEquals(15f, HullRam.bodyTonnes(UnitType.HEAVY_MECH, null, 1.5f), 1e-5f);
    }

    @Test void aMechAgainstATankResolvesByWeightLikeAnyPair() {
        float hound = HullRam.bodyTonnes(UnitType.HEAVY_MECH, null, MechVariant.HOUND.relativeMass);
        float bulwark = HullRam.bodyTonnes(UnitType.HEAVY_MECH, null, MechVariant.BULWARK.relativeMass);
        assertTrue(HullRam.shoveShare(TANK, hound) > .8f, "a tank shoves a light mech aside");
        assertTrue(HullRam.shoveShare(PICKUP, bulwark) < .2f, "a pickup barely moves a heavy one");
        assertTrue(HullRam.shoveShare(bulwark, hound) > .5f, "the lighter machine yields");
        HullRam.Exchange stomp = HullRam.exchange(2f, hound, MAN);
        assertTrue(stomp.bodyDamage() > 0f && stomp.hullDamage() == 0f, "a walking mech hurts a man and not itself");
        HullRam.Exchange intoTank = HullRam.exchange(HullRam.machineClosing(2f, -1.5f), hound, TANK);
        assertTrue(intoTank.hullDamage() > intoTank.bodyDamage(), "the mech pays more than the tank");
    }

    @Test void bothMachinesDriveIntoTheBlow() {
        assertEquals(3.5f, HullRam.machineClosing(2f, -1.5f), 1e-6f);
        assertEquals(0f, HullRam.machineClosing(1f, 2f));
    }
}
