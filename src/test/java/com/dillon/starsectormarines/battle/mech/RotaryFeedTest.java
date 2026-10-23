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
        for (MechWeaponComponent[] tier : new MechWeaponComponent[][]{
                {MechWeaponComponent.CHAINGUN, MechWeaponComponent.CHAINGUN_HF},
                {MechWeaponComponent.NOSE_CHAINGUN, MechWeaponComponent.NOSE_CHAINGUN_HF}}) {
            RotaryFeedDef stock = tier[0].rotary();
            RotaryFeedDef better = tier[1].rotary();
            assertNotNull(stock, tier[0] + " fires while held");
            assertNotNull(better, tier[1] + " fires while held");
            assertTrue(better.spinUpSeconds() < stock.spinUpSeconds(), "spins up faster");
            assertTrue(better.refillPerSecond() > stock.refillPerSecond(), "refills faster");
            assertTrue(tier[1].weaponDef().damage > tier[0].weaponDef().damage, "hits harder");
            assertEquals(tier[0].mountFamily, tier[1].mountFamily);
            assertEquals(tier[0].centrelineOnly(), tier[1].centrelineOnly());
            assertEquals(tier[0].slotCost, tier[1].slotCost);
            assertTrue(new MechWeaponMount(tier[0].centrelineOnly() ? MechMountSlot.ARMS : MechMountSlot.LEFT_ARM,
                    tier[1]).isRotary());
        }
        assertNull(MechWeaponComponent.MUSTER_AUTOGUN.rotary(), "other burst guns keep their trigger pull");
    }
}
