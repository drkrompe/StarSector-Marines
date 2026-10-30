package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.weapon.RotaryFeedDef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The rotary feed's one rule, on a synthetic gun with round numbers. */
class RotaryFeedTest {

    private static final float DT = 0.1f;
    /** One second to full speed, half a second of coast, two to spin down; 2 to 10 rounds a second. */
    private static final RotaryFeedDef GUN = new RotaryFeedDef(1f, 0.5f, 2f, 2f, 10f, 100, 4f, 0.5f);

    /** Holds (or not) for {@code seconds} and returns the rounds released, every one of them fired. */
    private static int run(RotaryFeed feed, boolean held, float seconds) {
        int fired = 0;
        for (int i = 0; i < Math.round(seconds / DT); i++) {
            int rounds = feed.advance(DT, held);
            for (int r = 0; r < rounds; r++) feed.spend();
            fired += rounds;
        }
        return fired;
    }

    @Test
    void theFirstRoundLeavesOnThePressAndTheRateRampsToFullOverTheSpinUp() {
        RotaryFeed feed = new RotaryFeed(GUN);
        assertEquals(1, feed.advance(DT, true), "the first round leaves on the press");
        feed.spend();
        assertEquals(0.1f, feed.spin(), 1e-5f);
        run(feed, true, 0.4f);
        assertEquals(0.5f, feed.spin(), 1e-5f);
        assertEquals(6f, feed.rate(), 1e-4f, "halfway up, halfway between first-turn and full rate");
        run(feed, true, 0.5f);
        assertEquals(1f, feed.spin(), 1e-5f);
        assertEquals(10, run(feed, true, 1f), 1, "a second at speed is ten rounds");

        RotaryFeed cold = new RotaryFeed(GUN);
        assertEquals(7, run(cold, true, 1f), 1, "the second from rest is fewer: the press and the ramp");
    }

    @Test
    void releasedBarrelsCoastAtSpeedThenSpinDown() {
        RotaryFeed feed = new RotaryFeed(GUN);
        run(feed, true, 1f);
        assertEquals(1f, feed.spin(), 1e-5f);
        run(feed, false, 0.5f);
        assertEquals(1f, feed.spin(), 1e-5f, "the coast holds full speed");
        assertEquals(1, feed.advance(DT, true), "a quick re-press fires at once");
        feed.spend();
        assertEquals(1f, feed.spin(), 1e-5f, "and at full rate");

        run(feed, false, 0.5f + 1f);
        assertEquals(0.5f, feed.spin(), 1e-4f, "past the coast, two seconds from full to rest");
        run(feed, false, 1.5f);
        assertEquals(0f, feed.spin());
        assertEquals(0, feed.advance(DT, false), "nothing fires while released");
    }

    @Test
    void aLongHoldEmptiesTheBeltAndThenFiresOnlyAsFastAsTheRefill() {
        RotaryFeed feed = new RotaryFeed(GUN);
        assertEquals(100, feed.beltRounds());
        run(feed, true, 20f);
        assertEquals(0, feed.beltRounds(), "ten rounds a second out, four in: the belt runs dry");
        int dry = run(feed, true, 5f);
        assertEquals(20, dry, 1, "a dry belt fires at the refill rate, four rounds a second");

        run(feed, false, 10f);
        assertEquals(40, feed.beltRounds(), 1, "the hopper refills it at four a second, firing or not");
        run(feed, false, 30f);
        assertEquals(100, feed.beltRounds(), "and never past the belt");
    }

    @Test
    void aRoundTheGatesRefuseUsesItsSlotRatherThanBuildingABacklog() {
        RotaryFeed feed = new RotaryFeed(GUN);
        run(feed, true, 1f);
        int asked = 0;
        for (int i = 0; i < 10; i++) asked += feed.advance(DT, true); // asked for, never spent
        assertEquals(10, asked, 1);
        assertEquals(1, feed.advance(DT, true), "unblocked, the next tick asks for one tick's worth, not the backlog");
    }

    @Test
    void anAutonomousGunnerKeepsAReserveAndEasesOffBeforeTheBeltRunsDry() {
        RotaryFeed feed = new RotaryFeed(GUN);
        int lowest = feed.beltRounds();
        for (int i = 0; i < 400; i++) {
            feed.demand(7L);
            int rounds = feed.advance(DT, false);
            for (int r = 0; r < rounds; r++) feed.spend();
            lowest = Math.min(lowest, feed.beltRounds());
        }
        assertTrue(lowest >= 18, "the gunner stops below a fifth of the belt, never emptying it: " + lowest);
        assertTrue(feed.beltRounds() < 30, "and fires down to its reserve again once the hopper has refilled it");
        assertEquals(1f, feed.spin(), 1e-5f, "holding the reserve, the barrels stay at speed");
        assertEquals(7L, feed.leaseTarget());

        RotaryFeed lapsed = new RotaryFeed(GUN);
        lapsed.demand(7L);
        run(lapsed, false, RotaryFeed.AI_LEASE_SECONDS + DT);
        assertFalse(lapsed.leased(), "an unrenewed demand lapses in a few ticks");
        assertEquals(0L, lapsed.leaseTarget());
    }

    @Test
    void holdingKeepsTheBarrelsTurningWithoutFiring() {
        RotaryFeed feed = new RotaryFeed(GUN);
        run(feed, true, 1f);
        int belt = feed.beltRounds();
        for (int i = 0; i < 30; i++) {
            feed.hold();
            assertEquals(0, feed.advance(DT, false));
        }
        assertEquals(1f, feed.spin(), 1e-5f);
        assertTrue(feed.beltRounds() > belt, "holding spends nothing; the hopper keeps filling");
    }

    @Test
    void theRatedRateIsWhatAnEngagementGetsNotThePeak() {
        float engagement = RotaryFeedDef.RATED_ENGAGEMENT_SECONDS;
        assertEquals(4f + 100 * 0.8f / engagement, GUN.ratedRoundsPerSecond(), 1e-4f);
        RotaryFeedDef bottomless = new RotaryFeedDef(1f, 0.5f, 2f, 2f, 10f, 10_000, 4f, 0.5f);
        assertEquals(10f, bottomless.ratedRoundsPerSecond(), 1e-4f, "never past the full rate");
    }

    @Test
    void theBetterChaingunIsBetterOnEveryUpgradeAxisAndOtherwiseTheSameGun() {
        MechWeaponComponent stockGun = MechWeaponComponent.CHAINGUN;
        MechWeaponComponent betterGun = MechWeaponComponent.CHAINGUN_HF;
        RotaryFeedDef stock = stockGun.rotary();
        RotaryFeedDef better = betterGun.rotary();
        assertNotNull(stock, stockGun + " fires while held");
        assertNotNull(better, betterGun + " fires while held");
        assertTrue(better.spinUpSeconds() < stock.spinUpSeconds(), "spins up faster");
        assertTrue(better.refillPerSecond() > stock.refillPerSecond(), "refills faster");
        assertTrue(betterGun.weaponDef().damage > stockGun.weaponDef().damage, "hits harder");
        assertEquals(stockGun.mountFamily, betterGun.mountFamily);
        assertEquals(stockGun.centrelineOnly(), betterGun.centrelineOnly());
        assertEquals(stockGun.slotCost, betterGun.slotCost);
        for (MechMountSlot slot : new MechMountSlot[]{MechMountSlot.ARMS, MechMountSlot.LEFT_ARM}) {
            assertTrue(new MechWeaponMount(slot, betterGun).isRotary(), "rotary on " + slot);
        }
        assertNull(MechWeaponComponent.LINEAR_CANNON.rotary(), "a cannon keeps its trigger pull");
    }

    /** No spin-up: rest to full at once; 5 rounds a second off a belt of 10, refilled at 1 a second. */
    private static final RotaryFeedDef INSTANT = new RotaryFeedDef(0f, 0f, 0f, 5f, 5f, 10, 1f, 0.5f);

    @Test
    void aGunWithNoSpinUpFiresAtItsFullRateFromThePressAndIsAtRestWhenLetGo() {
        assertTrue(INSTANT.instant());
        assertFalse(GUN.instant());
        RotaryFeed feed = new RotaryFeed(INSTANT);
        assertEquals(1, feed.advance(DT, true), "the first round leaves on the press");
        feed.spend();
        assertEquals(1f, feed.spin(), "at speed on the press");
        assertEquals(5f, feed.rate(), 1e-5f);
        assertEquals(5, run(feed, true, 1f), 1, "a second held is the full rate");
        run(feed, false, DT);
        assertEquals(0f, feed.spin(), "let go, nothing to coast or spin down");
        assertThrows(IllegalArgumentException.class, () -> new RotaryFeedDef(-1f, 0f, 0f, 5f, 5f, 10, 1f, 0.5f));
    }

    @Test
    void aShortMagazineEmptiesInABurstAndThenTheLoaderSetsThePace() {
        RotaryFeed feed = new RotaryFeed(INSTANT);
        int burst = run(feed, true, 2.5f);
        assertTrue(burst >= 10 && burst <= 13, "the magazine and what the loader adds over the burst: " + burst);
        assertEquals(5, run(feed, true, 5f), 1, "held dry, one round a second: the loader's rate");
    }

    @Test
    void anAutonomousGunnerOnAShortMagazineKeepsFiringAtTheLoadersPace() {
        RotaryFeed feed = new RotaryFeed(INSTANT);
        int fired = 0;
        for (int i = 0; i < 200; i++) {
            feed.demand(7L);
            int rounds = feed.advance(DT, false);
            for (int r = 0; r < rounds; r++) feed.spend();
            fired += rounds;
        }
        // Twenty seconds: the magazine down to its reserve, then the loader's twenty rounds less the reserve it holds back.
        assertTrue(fired >= 24 && fired <= 30, "never stalls on its reserve: " + fired);
        assertTrue(feed.beltRounds() >= 1, "and never empties the magazine");
    }

    @Test
    void theLightMachineGunAndTheAutocannonsAreHeldTriggerGunsWithNoSpinUp() {
        for (MechWeaponComponent gun : new MechWeaponComponent[]{MechWeaponComponent.LIGHT_MACHINE_GUN,
                MechWeaponComponent.BASTION_AUTOCANNON, MechWeaponComponent.MUSTER_AUTOGUN}) {
            assertNotNull(gun.rotary(), gun + " fires while held");
            assertTrue(gun.rotary().instant(), gun + " has no spin-up");
            assertEquals(gun.projectilesPerTrigger / gun.rotary().ratedRoundsPerSecond(), gun.cycleSeconds(), 1e-5f,
                    "its cycle is read from its feed");
        }
    }
}
