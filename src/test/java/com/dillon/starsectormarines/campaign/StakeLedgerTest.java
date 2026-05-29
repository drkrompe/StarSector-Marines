package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link StakeLedger#seizeShare} and its query helpers — the
 * stake-transfer primitive the player path (and the coming drift / chain loops)
 * route every stake move through.
 *
 * <p>Fixtures use one market slot (1) and one industry slot (7); houses are bare
 * rows added via {@link CampaignState#addHouse}, only their ids matter here.
 */
public class StakeLedgerTest {

    private static final int MARKET = 1;
    private static final int INDUSTRY = 7;

    private static long house(CampaignState s, String name) {
        return s.addHouse(MARKET, 1, HouseFlavor.FEUDAL, HouseRank.TIER_1,
                HouseStatus.ACTIVE, PatronArchetype.NEWCOMER, name);
    }

    @Test
    public void zeroSumTransferWhenLoserHoldsEnough() {
        CampaignState s = new CampaignState();
        long target = house(s, "Target");
        long patron = house(s, "Patron");
        s.addStake(target, MARKET, INDUSTRY, (short) 100);

        int gained = StakeLedger.seizeShare(s, target, patron, MARKET, INDUSTRY, 20);

        assertEquals(20, gained, "winner gains the full requested amount when the loser can cover it");
        assertEquals(80, StakeLedger.shareOf(s, target, MARKET, INDUSTRY), "loser drops by exactly the amount taken");
        assertEquals(20, StakeLedger.shareOf(s, patron, MARKET, INDUSTRY), "winner holds the moved share");
        assertEquals(100, StakeLedger.totalClaimedShare(s, MARKET, INDUSTRY), "pure zero-sum: claimed total unchanged");
    }

    @Test
    public void topsUpFromUnclaimedWhenLoserIsLight() {
        CampaignState s = new CampaignState();
        long target = house(s, "Target");
        long patron = house(s, "Patron");
        s.addStake(target, MARKET, INDUSTRY, (short) 5); // loser can only cover 5 of 20

        int gained = StakeLedger.seizeShare(s, target, patron, MARKET, INDUSTRY, 20);

        assertEquals(20, gained, "5 from the loser + 15 from the open pool = full amount");
        assertEquals(0, StakeLedger.shareOf(s, target, MARKET, INDUSTRY), "loser is drained to the tombstone floor");
        assertEquals(20, StakeLedger.shareOf(s, patron, MARKET, INDUSTRY));
    }

    @Test
    public void claimsPurelyFromUnclaimedWhenLoserHoldsNothing() {
        CampaignState s = new CampaignState();
        long target = house(s, "Target");
        long patron = house(s, "Patron");
        // Target holds no stake in this industry — a strike still plants a foothold.
        int gained = StakeLedger.seizeShare(s, target, patron, MARKET, INDUSTRY, 20);

        assertEquals(20, gained);
        assertEquals(20, StakeLedger.shareOf(s, patron, MARKET, INDUSTRY));
        assertEquals(0, StakeLedger.shareOf(s, target, MARKET, INDUSTRY));
    }

    @Test
    public void boundedByOpenPoolWhenLoserMissingAndPieNearlyFull() {
        CampaignState s = new CampaignState();
        long other  = house(s, "Other");
        long target = house(s, "Target");
        long patron = house(s, "Patron");
        s.addStake(other, MARKET, INDUSTRY, (short) 250); // only 5 unclaimed left

        int gained = StakeLedger.seizeShare(s, target, patron, MARKET, INDUSTRY, 20);

        assertEquals(5, gained, "cannot exceed the open pool when there's no rival share to take");
        assertEquals(255, StakeLedger.totalClaimedShare(s, MARKET, INDUSTRY), "claimed total respects the 255 ceiling");
    }

    @Test
    public void revivesTombstonedRowRatherThanDuplicating() {
        CampaignState s = new CampaignState();
        long a = house(s, "A");
        long b = house(s, "B");
        s.addStake(a, MARKET, INDUSTRY, (short) 30);

        // Drain A to a tombstone (this legitimately creates B's row), then move share back to A.
        StakeLedger.seizeShare(s, a, b, MARKET, INDUSTRY, 30);
        assertEquals(0, StakeLedger.shareOf(s, a, MARKET, INDUSTRY), "A is tombstoned at zero");
        int rowsAfterDrain = s.stakeCount; // A's tombstone + B's new row

        StakeLedger.seizeShare(s, b, a, MARKET, INDUSTRY, 10);

        assertEquals(10, StakeLedger.shareOf(s, a, MARKET, INDUSTRY), "A's tombstoned row is revived in place");
        assertEquals(rowsAfterDrain, s.stakeCount, "reviving a tombstone appends no duplicate row");
    }

    @Test
    public void selfTransferAndNonPositiveAmountAreNoOps() {
        CampaignState s = new CampaignState();
        long a = house(s, "A");
        s.addStake(a, MARKET, INDUSTRY, (short) 40);

        assertEquals(0, StakeLedger.seizeShare(s, a, a, MARKET, INDUSTRY, 20), "self-transfer moves nothing");
        assertEquals(0, StakeLedger.seizeShare(s, a, house(s, "B"), MARKET, INDUSTRY, 0), "zero amount moves nothing");
        assertEquals(40, StakeLedger.shareOf(s, a, MARKET, INDUSTRY), "share untouched by no-op transfers");
    }

    @Test
    public void newWinnerRowResolvesViaIdIndex() {
        CampaignState s = new CampaignState();
        long target = house(s, "Target");
        long patron = house(s, "Patron");
        s.addStake(target, MARKET, INDUSTRY, (short) 50);

        StakeLedger.seizeShare(s, target, patron, MARKET, INDUSTRY, 20);

        int row = StakeLedger.findStake(s, patron, MARKET, INDUSTRY);
        assertTrue(row >= 0, "winner's new stake row exists");
        assertEquals(row, s.stakeIndex(s.stakeId[row]), "id->index map covers the newly created stake");
    }

    @Test
    public void unclaimedReflectsClaimedTotal() {
        CampaignState s = new CampaignState();
        long a = house(s, "A");
        assertEquals(255, StakeLedger.unclaimedShare(s, MARKET, INDUSTRY), "empty industry is fully open");
        s.addStake(a, MARKET, INDUSTRY, (short) 200);
        assertEquals(55, StakeLedger.unclaimedShare(s, MARKET, INDUSTRY));
    }
}
