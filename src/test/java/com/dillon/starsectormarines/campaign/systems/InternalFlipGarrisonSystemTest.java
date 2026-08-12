package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.GarrisonDefensePayload;
import com.dillon.starsectormarines.campaign.GarrisonDefenseTriggerType;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InternalFlipGarrisonSystemTest {

    @Test
    void changedMarketFactionArmsOnlyDispossessedPatron() {
        Fixture fixture = fixture();
        long eventKey = InternalFlipGarrisonSystem.eventKey("jangala", "new_owner");
        InternalFlipGarrisonSystem system = new InternalFlipGarrisonSystem(s ->
                Collections.singletonList(new InternalFlipGarrisonSystem.MarketOwnership(
                        eventKey, 7, 20)));

        system.tick(fixture.state, 42);

        GarrisonDefensePayload displaced = GarrisonDefensePayload.from(
                fixture.state, fixture.displacedGarrisonId);
        assertEquals(GarrisonDefenseTriggerType.INTERNAL_FLIP, displaced.triggerType);
        assertEquals(20, displaced.attackerFactionId);
        assertEquals(eventKey, displaced.eventKey);
        assertEquals(ContractState.ACTIVE, ContractState.fromByte(
                fixture.state.contractState[
                        fixture.state.contractIndex(fixture.currentOwnerGarrisonId)]));
        assertNull(GarrisonDefensePayload.from(
                fixture.state, fixture.currentOwnerGarrisonId));
    }

    @Test
    void resolvedOwnershipEventCannotRearmUntilFactionChangesAgain() {
        Fixture fixture = fixture();
        long eventKey = InternalFlipGarrisonSystem.eventKey("jangala", "new_owner");
        InternalFlipGarrisonSystem system = new InternalFlipGarrisonSystem(s ->
                Collections.singletonList(new InternalFlipGarrisonSystem.MarketOwnership(
                        eventKey, 7, 20)));
        system.tick(fixture.state, 42);
        int row = fixture.state.contractIndex(fixture.displacedGarrisonId);
        fixture.state.contractState[row] = ContractState.ACTIVE.toByte();
        fixture.state.contractDefenseTriggerType[row] = GarrisonDefenseTriggerType.NONE.toByte();

        system.tick(fixture.state, 43);

        assertEquals(ContractState.ACTIVE,
                ContractState.fromByte(fixture.state.contractState[row]));
        assertNull(GarrisonDefensePayload.from(
                fixture.state, fixture.displacedGarrisonId));
    }

    @Test
    void ownershipEventKeysAreStablePerMarketAndFaction() {
        long first = InternalFlipGarrisonSystem.eventKey("jangala", "hegemony");
        assertEquals(first, InternalFlipGarrisonSystem.eventKey("jangala", "hegemony"));
        assertNotEquals(first, InternalFlipGarrisonSystem.eventKey("jangala", "pirates"));
        assertNotEquals(first, InternalFlipGarrisonSystem.eventKey("asharu", "hegemony"));
        assertNotEquals(0L, first);
    }

    private static Fixture fixture() {
        CampaignState state = new CampaignState();
        long displaced = state.addHouse(7, 10, HouseFlavor.FEUDAL,
                HouseRank.TIER_1, HouseStatus.ACTIVE,
                PatronArchetype.ESTABLISHED, "Displaced");
        long currentOwner = state.addHouse(7, 20, HouseFlavor.CORPORATE,
                HouseRank.TIER_1, HouseStatus.ACTIVE,
                PatronArchetype.TIME_RUSHED, "Current Owner");
        long displacedGarrison = addGarrison(state, displaced);
        long currentOwnerGarrison = addGarrison(state, currentOwner);
        return new Fixture(state, displacedGarrison, currentOwnerGarrison);
    }

    private static long addGarrison(CampaignState state, long patronId) {
        long id = state.addContract(patronId, -1L, -1L,
                ContractType.GARRISON, ContractState.ACTIVE,
                10, 100, -1, (byte) 0, -1, 7, -1,
                0, 1_000, (byte) 25, (byte) 25, (byte) 100);
        state.contractMarinesCommitted[state.contractIndex(id)] = 80;
        return id;
    }

    private static final class Fixture {
        final CampaignState state;
        final long displacedGarrisonId;
        final long currentOwnerGarrisonId;

        Fixture(CampaignState state, long displacedGarrisonId,
                long currentOwnerGarrisonId) {
            this.state = state;
            this.displacedGarrisonId = displacedGarrisonId;
            this.currentOwnerGarrisonId = currentOwnerGarrisonId;
        }
    }
}
