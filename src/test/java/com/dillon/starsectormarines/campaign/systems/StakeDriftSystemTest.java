package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.HouseAmbition;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import com.dillon.starsectormarines.campaign.StakeLedger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StakeDriftSystemTest {

    @Test
    void weeklyDriftTakesFromStrongestWeakerLocalRival() {
        Fixture fixture = fixture(100, 70, 40);
        int amount = StakeDriftSystem.driftAmount(fixture.houseId, 7, 21);

        new StakeDriftSystem().tick(fixture.state, 21);

        assertEquals(100 + amount, share(fixture.state, fixture.houseId));
        assertEquals(70 - amount, share(fixture.state, fixture.rivalId));
        assertEquals(40, share(fixture.state, fixture.otherId));
        assertEquals(21, fixture.state.houseLastDriftTick[fixture.houseRow]);
    }

    @Test
    void contenderExpandsIntoUnclaimedWhenNoRivalIsWeaker() {
        Fixture fixture = fixture(40, 100, 70);
        int claimedBefore = StakeLedger.totalClaimedShare(fixture.state, 1, 7);
        int amount = StakeDriftSystem.driftAmount(fixture.houseId, 7, 21);

        new StakeDriftSystem().tick(fixture.state, 21);

        assertEquals(40 + amount, share(fixture.state, fixture.houseId));
        assertEquals(claimedBefore + amount,
                StakeLedger.totalClaimedShare(fixture.state, 1, 7));
        assertEquals(100, share(fixture.state, fixture.rivalId));
    }

    @Test
    void nonCadenceAndSameDayReentryDoNotMoveTwice() {
        Fixture fixture = fixture(100, 70, 40);
        StakeDriftSystem system = new StakeDriftSystem();

        system.tick(fixture.state, 20);
        assertEquals(100, share(fixture.state, fixture.houseId));

        system.tick(fixture.state, 21);
        int afterFirst = share(fixture.state, fixture.houseId);
        system.tick(fixture.state, 21);
        assertEquals(afterFirst, share(fixture.state, fixture.houseId));
    }

    @Test
    void invalidAmbitionInactiveHouseAndLostFootholdDoNotMove() {
        Fixture none = fixture(100, 70, 40);
        none.state.houseAmbition[none.houseRow] = HouseAmbition.NONE.toByte();
        Fixture dormant = fixture(100, 70, 40);
        dormant.state.houseStatus[dormant.houseRow] = HouseStatus.DORMANT.toByte();
        Fixture lost = fixture(0, 70, 40);

        StakeDriftSystem system = new StakeDriftSystem();
        system.tick(none.state, 21);
        system.tick(dormant.state, 21);
        system.tick(lost.state, 21);

        assertEquals(100, share(none.state, none.houseId));
        assertEquals(100, share(dormant.state, dormant.houseId));
        assertEquals(0, share(lost.state, lost.houseId));
    }

    @Test
    void amountIsDeterministicAndBounded() {
        int first = StakeDriftSystem.driftAmount(1L, 7, 21);
        assertEquals(first, StakeDriftSystem.driftAmount(1L, 7, 21));
        assertTrue(first >= StakeDriftSystem.MIN_DRIFT);
        assertTrue(first <= StakeDriftSystem.MAX_DRIFT);
    }

    private static int share(CampaignState state, long houseId) {
        return StakeLedger.shareOf(state, houseId, 1, 7);
    }

    private static Fixture fixture(int held, int rivalHeld, int otherHeld) {
        CampaignState state = new CampaignState();
        long house = house(state, "Ambitious");
        long rival = house(state, "Rival");
        long other = house(state, "Other");
        state.addStake(house, 1, 7, (short) held);
        state.addStake(rival, 1, 7, (short) rivalHeld);
        state.addStake(other, 1, 7, (short) otherHeld);
        int row = state.houseIndex(house);
        state.houseAmbition[row] = HouseAmbition.CONSOLIDATE_STAKE.toByte();
        state.houseAmbitionTarget[row] = 7L;
        return new Fixture(state, house, rival, other, row);
    }

    private static long house(CampaignState state, String name) {
        return state.addHouse(1, 1, HouseFlavor.FEUDAL, HouseRank.TIER_1,
                HouseStatus.ACTIVE, PatronArchetype.NEWCOMER, name);
    }

    private static final class Fixture {
        final CampaignState state;
        final long houseId;
        final long rivalId;
        final long otherId;
        final int houseRow;

        Fixture(CampaignState state, long houseId, long rivalId,
                long otherId, int houseRow) {
            this.state = state;
            this.houseId = houseId;
            this.rivalId = rivalId;
            this.otherId = otherId;
            this.houseRow = houseRow;
        }
    }
}
