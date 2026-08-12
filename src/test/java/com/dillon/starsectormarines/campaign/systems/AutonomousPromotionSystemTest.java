package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AutonomousPromotionSystemTest {

    @Test
    void strictHomeMarketMajorityAddsOneDailyProgress() {
        Fixture fixture = fixture((short) 130, (short) 100);

        new AutonomousPromotionSystem().tick(fixture.state, 20);

        assertEquals(1, fixture.state.housePromotionProgress[fixture.houseRow]);
        assertEquals(1, AutonomousPromotionSystem.stakeBasedDelta(
                fixture.state, fixture.houseRow));
    }

    @Test
    void exactHalfOrMinorityAddsNoProgress() {
        Fixture half = fixture((short) 100, (short) 100);
        Fixture minority = fixture((short) 99, (short) 101);

        new AutonomousPromotionSystem().tick(half.state, 20);
        new AutonomousPromotionSystem().tick(minority.state, 20);

        assertEquals(0, half.state.housePromotionProgress[half.houseRow]);
        assertEquals(0, minority.state.housePromotionProgress[minority.houseRow]);
    }

    @Test
    void holdingsOutsideHomeMarketDoNotCount() {
        Fixture fixture = fixture((short) 40, (short) 60);
        fixture.state.addStake(fixture.houseId, 2, 9, (short) 255);

        new AutonomousPromotionSystem().tick(fixture.state, 20);

        assertEquals(0, fixture.state.housePromotionProgress[fixture.houseRow]);
    }

    @Test
    void majorityCrossesRankThresholdThroughSharedPromotionPolicy() {
        Fixture fixture = fixture((short) 130, (short) 100);
        fixture.state.housePromotionProgress[fixture.houseRow] = 99;

        new AutonomousPromotionSystem().tick(fixture.state, 20);

        assertEquals(HouseRank.TIER_2,
                HouseRank.fromByte(fixture.state.houseRank[fixture.houseRow]));
        assertEquals(0, fixture.state.housePromotionProgress[fixture.houseRow]);
    }

    @Test
    void inactiveAndTierFourHousesNeverAccrue() {
        Fixture inactive = fixture((short) 130, (short) 100);
        inactive.state.houseStatus[inactive.houseRow] = HouseStatus.DORMANT.toByte();
        Fixture terminal = fixture((short) 130, (short) 100);
        terminal.state.houseRank[terminal.houseRow] = HouseRank.TIER_4.toByte();

        new AutonomousPromotionSystem().tick(inactive.state, 20);
        new AutonomousPromotionSystem().tick(terminal.state, 20);

        assertEquals(0, inactive.state.housePromotionProgress[inactive.houseRow]);
        assertEquals(0, terminal.state.housePromotionProgress[terminal.houseRow]);
    }

    private static Fixture fixture(short held, short rivalHeld) {
        CampaignState state = new CampaignState();
        long house = state.addHouse(1, 1, HouseFlavor.FEUDAL, HouseRank.TIER_1,
                HouseStatus.ACTIVE, PatronArchetype.NEWCOMER, "Majority");
        long rival = state.addHouse(1, 1, HouseFlavor.CORPORATE, HouseRank.TIER_1,
                HouseStatus.ACTIVE, PatronArchetype.ESTABLISHED, "Rival");
        state.addStake(house, 1, 7, held);
        state.addStake(rival, 1, 7, rivalHeld);
        return new Fixture(state, house, state.houseIndex(house));
    }

    private static final class Fixture {
        final CampaignState state;
        final long houseId;
        final int houseRow;

        Fixture(CampaignState state, long houseId, int houseRow) {
            this.state = state;
            this.houseId = houseId;
            this.houseRow = houseRow;
        }
    }
}
