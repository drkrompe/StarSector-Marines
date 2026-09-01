package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractEligibility;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import com.dillon.starsectormarines.ops.OperationTier;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractGeneratorTest {

    private static final float OFFER_CHANCE = 0.05f;

    @Test
    void sameStateAndDayProduceSameTierOneOffers() {
        CampaignState first = twoActivePatrons();
        CampaignState second = twoActivePatrons();
        int day = firstOfferDay(first.houseId[0]);

        ContractGenerator generator = new ContractGenerator();
        generator.tick(first, day);
        generator.tick(second, day);

        assertTrue(first.contractCount > 0);
        assertEquals(first.contractCount, second.contractCount);
        for (int i = 0; i < first.contractCount; i++) {
            assertEquals(first.contractPatronHouseId[i], second.contractPatronHouseId[i]);
            assertEquals(first.contractTargetHouseId[i], second.contractTargetHouseId[i]);
            assertEquals(first.contractOfferExpiresTick[i], second.contractOfferExpiresTick[i]);
            ContractType type = ContractType.fromByte(first.contractType[i]);
            assertTrue(type == ContractType.STRIKE || type == ContractType.ESCORT);
            assertEquals(ContractState.OFFERED, ContractState.fromByte(first.contractState[i]));
            assertNotEquals(first.contractPatronHouseId[i], first.contractTargetHouseId[i]);
            assertEquals(1, first.contractPhasesTotal[i] & 0xFF);
            assertEquals(OperationTier.FIRST_CONTRACT,
                    OperationTier.fromPersistedByte(first.contractOperationTier[i]));
            assertEquals(type == ContractType.ESCORT ? 30_000 : 25_000,
                    first.contractBasePayout[i]);
            assertEquals(0, first.contractRetainerPerMonth[i]);
            int salvage = type == ContractType.ESCORT ? 10 : 60;
            assertEquals(salvage, first.contractSalvageBaseline[i] & 0xFF);
            assertEquals(salvage, first.contractSalvageNegotiated[i] & 0xFF);
            assertEquals(100, first.contractCashMultiplier[i] & 0xFF);
        }
    }

    @Test
    void existingOfferPreventsSecondOfferForPatron() {
        CampaignState state = twoActivePatrons();
        long patronId = state.houseId[0];
        addOpenOffer(state, patronId, state.houseId[1]);

        new ContractGenerator().tick(state, firstOfferDay(patronId));

        assertEquals(1, openOffersFor(state, patronId));
    }

    @Test
    void globalOfferCapStopsGeneration() {
        CampaignState state = twoActivePatrons();
        for (int i = 0; i < 20; i++) {
            addOpenOffer(state, 10_000L + i, 20_000L + i);
        }

        new ContractGenerator().tick(state, firstOfferDay(state.houseId[0]));

        assertEquals(20, state.contractCount);
    }

    @Test
    void tierTwoCanGenerateTargetlessStationingOffer() {
        CampaignState state = new CampaignState();
        long patronId = state.addHouse(1, 1, HouseFlavor.CORPORATE, HouseRank.TIER_2,
                HouseStatus.ACTIVE, PatronArchetype.ESTABLISHED, "Stationing Patron");
        state.playerMrbRep = ContractEligibility.TIER_2_MRB_REQUIRED;
        int day = firstStationingOfferDay(patronId);

        new ContractGenerator().tick(state, day);

        assertEquals(1, state.contractCount);
        ContractType type = ContractType.fromByte(state.contractType[0]);
        assertTrue(type.isStationing());
        assertEquals(-1L, state.contractTargetHouseId[0]);
        assertEquals(0, state.contractBasePayout[0]);
        assertEquals(0, state.contractRetainerPerMonth[0]);
        assertEquals(0, state.contractPhasesTotal[0] & 0xFF);
    }

    @Test
    void mrbGatePreventsTierTwoGenerationUntilUnlocked() {
        CampaignState state = new CampaignState();
        long patronId = state.addHouse(1, 1, HouseFlavor.CORPORATE, HouseRank.TIER_2,
                HouseStatus.ACTIVE, PatronArchetype.ESTABLISHED, "Locked Patron");
        int day = firstStationingOfferDay(patronId);

        new ContractGenerator().tick(state, day);
        assertEquals(0, state.contractCount);

        state.playerMrbRep = ContractEligibility.TIER_2_MRB_REQUIRED;
        new ContractGenerator().tick(state, day);
        assertEquals(1, state.contractCount);
    }

    @Test
    void existingOfferAtMarketBlocksAnotherPatronThere() {
        CampaignState state = new CampaignState();
        int origin = state.marketRegistry.intern("origin");
        int target = state.marketRegistry.intern("target");
        long incumbent = state.addHouse(origin, 1, HouseFlavor.CORPORATE,
                HouseRank.TIER_1, HouseStatus.ACTIVE,
                PatronArchetype.ESTABLISHED, "Incumbent");
        long candidate = state.addHouse(origin, 1, HouseFlavor.FEUDAL,
                HouseRank.TIER_1, HouseStatus.ACTIVE,
                PatronArchetype.ESTABLISHED, "Candidate");
        long targetHouse = state.addHouse(target, 2, HouseFlavor.UNDERWORLD,
                HouseRank.TIER_1, HouseStatus.ACTIVE,
                PatronArchetype.ESTABLISHED, "Target");
        addOpenOffer(state, incumbent, targetHouse, origin);

        ContractGenerator generator = new ContractGenerator(new TestMarkets()
                .add("origin", "system-a").add("target", "system-b"));
        generator.tick(state, firstOfferDay(candidate));

        assertEquals(1, openOffersAt(state, origin));
    }

    @Test
    void threeOffersAcrossSystemBlockAFourthMarket() {
        CampaignState state = new CampaignState();
        TestMarkets markets = new TestMarkets();
        int[] occupied = new int[3];
        for (int i = 0; i < occupied.length; i++) {
            occupied[i] = state.marketRegistry.intern("occupied-" + i);
            markets.add("occupied-" + i, "crowded");
            addOpenOffer(state, 10_000L + i, 20_000L + i, occupied[i]);
        }
        int candidateMarket = state.marketRegistry.intern("candidate");
        int targetMarket = state.marketRegistry.intern("target");
        markets.add("candidate", "crowded").add("target", "elsewhere");
        long candidate = state.addHouse(candidateMarket, 1, HouseFlavor.CORPORATE,
                HouseRank.TIER_1, HouseStatus.ACTIVE,
                PatronArchetype.ESTABLISHED, "Candidate");
        state.addHouse(targetMarket, 2, HouseFlavor.FEUDAL,
                HouseRank.TIER_1, HouseStatus.ACTIVE,
                PatronArchetype.ESTABLISHED, "Target");

        new ContractGenerator(markets).tick(state, firstOfferDay(candidate));

        assertEquals(3, openOffersInSystem(state, markets, "crowded"));
        assertEquals(0, openOffersAt(state, candidateMarket));
    }

    @Test
    void crowdedSystemDoesNotBlockAnotherSystem() {
        CampaignState state = new CampaignState();
        TestMarkets markets = new TestMarkets();
        for (int i = 0; i < 3; i++) {
            int occupied = state.marketRegistry.intern("occupied-" + i);
            markets.add("occupied-" + i, "crowded");
            addOpenOffer(state, 10_000L + i, 20_000L + i, occupied);
        }
        int candidateMarket = state.marketRegistry.intern("candidate");
        int targetMarket = state.marketRegistry.intern("target");
        markets.add("candidate", "open").add("target", "elsewhere");
        long candidate = state.addHouse(candidateMarket, 1, HouseFlavor.CORPORATE,
                HouseRank.TIER_1, HouseStatus.ACTIVE,
                PatronArchetype.ESTABLISHED, "Candidate");
        state.addHouse(targetMarket, 2, HouseFlavor.FEUDAL,
                HouseRank.TIER_1, HouseStatus.ACTIVE,
                PatronArchetype.ESTABLISHED, "Target");

        new ContractGenerator(markets).tick(state, firstOfferDay(candidate));

        assertEquals(1, openOffersAt(state, candidateMarket));
    }

    private static CampaignState twoActivePatrons() {
        CampaignState state = new CampaignState();
        state.addHouse(1, 1, HouseFlavor.CORPORATE, HouseRank.TIER_1,
                HouseStatus.ACTIVE, PatronArchetype.TIME_RUSHED, "First Patron");
        state.addHouse(2, 1, HouseFlavor.FEUDAL, HouseRank.TIER_1,
                HouseStatus.ACTIVE, PatronArchetype.ESTABLISHED, "Second Patron");
        return state;
    }

    private static int firstOfferDay(long patronId) {
        for (int day = 0; day < 10_000; day++) {
            long seed = ((long) day << 32) ^ patronId;
            if (new Random(seed).nextFloat() < OFFER_CHANCE) return day;
        }
        throw new AssertionError("No deterministic offer day found");
    }

    private static int firstStationingOfferDay(long patronId) {
        for (int day = 0; day < 100_000; day++) {
            long seed = ((long) day << 32) ^ patronId;
            Random random = new Random(seed);
            if (random.nextFloat() < OFFER_CHANCE && random.nextFloat() < 0.20f) return day;
        }
        throw new AssertionError("No deterministic stationing offer day found");
    }

    private static void addOpenOffer(CampaignState state, long patronId, long targetId) {
        addOpenOffer(state, patronId, targetId, 0);
    }

    private static void addOpenOffer(CampaignState state, long patronId, long targetId,
                                     int marketSlot) {
        state.addContract(patronId, targetId, -1L,
                ContractType.STRIKE, ContractState.OFFERED,
                0, -1, 30, (byte) 1, -1, marketSlot, -1,
                25_000, 0, (byte) 60, (byte) 60, (byte) 100);
    }

    private static int openOffersFor(CampaignState state, long patronId) {
        int count = 0;
        for (int i = 0; i < state.contractCount; i++) {
            if (state.contractPatronHouseId[i] == patronId
                    && ContractState.fromByte(state.contractState[i]) == ContractState.OFFERED) {
                count++;
            }
        }
        return count;
    }

    private static int openOffersAt(CampaignState state, int marketSlot) {
        int count = 0;
        for (int i = 0; i < state.contractCount; i++) {
            if (state.contractMarketId[i] == marketSlot
                    && ContractState.fromByte(state.contractState[i]) == ContractState.OFFERED) {
                count++;
            }
        }
        return count;
    }

    private static int openOffersInSystem(CampaignState state, TestMarkets markets,
                                          String systemId) {
        int count = 0;
        for (int i = 0; i < state.contractCount; i++) {
            if (ContractState.fromByte(state.contractState[i]) != ContractState.OFFERED) continue;
            String marketId = state.marketRegistry.get(state.contractMarketId[i]);
            if (systemId.equals(markets.systemId(marketId))) count++;
        }
        return count;
    }

    private static final class TestMarkets implements ContractGenerator.MarketSource {
        private final Map<String, String> systems = new HashMap<>();

        TestMarkets add(String marketId, String systemId) {
            systems.put(marketId, systemId);
            return this;
        }

        @Override
        public String systemId(String marketId) {
            return systems.get(marketId);
        }
    }
}
