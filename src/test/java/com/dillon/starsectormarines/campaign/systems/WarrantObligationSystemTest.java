package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import com.dillon.starsectormarines.campaign.WarrantObligationLedger;
import com.dillon.starsectormarines.marine.ArmorTradition;
import com.dillon.starsectormarines.marine.HegemonyAuxiliaryWarrant;
import com.dillon.starsectormarines.marine.MilitaryWarrants;
import com.fs.starfarer.api.campaign.RepLevel;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The period clock, loyalty, suspension, restoration and revocation. */
class WarrantObligationSystemTest {

    private static final int PERIOD = WarrantObligationSystem.PERIOD_DAYS;

    private final CampaignState state = new CampaignState();
    private final Map<String, RepLevel> relations = new HashMap<>();
    private final WarrantObligationSystem system = new WarrantObligationSystem(relations::get);
    private long hegemonyHouse;
    private long otherHouse;

    private void enrol() {
        hegemonyHouse = state.addHouse(1, state.factionRegistry.intern("hegemony"), HouseFlavor.FEUDAL,
                HouseRank.TIER_1, HouseStatus.ACTIVE, PatronArchetype.NEWCOMER, "Hegemony procurement");
        otherHouse = state.addHouse(2, state.factionRegistry.intern("pirates"), HouseFlavor.FEUDAL,
                HouseRank.TIER_1, HouseStatus.ACTIVE, PatronArchetype.NEWCOMER, "Pirate house");
        relations.put("hegemony", RepLevel.FAVORABLE);
        state.auxiliaryWarrant().offer(0);
        HegemonyAuxiliaryWarrant.accept(state, 0);
        system.tick(state, 0);
    }

    private WarrantObligationLedger.Term term() {
        return state.warrantObligations().term(ArmorTradition.HEGEMONY.key);
    }

    private void complete(long patron, long target, int day) {
        state.addContract(patron, target, -1L, ContractType.STRIKE, ContractState.COMPLETED, day, -1, -1,
                (byte) 1, -1, 1, -1, 1000, 0, (byte) 0, (byte) 0, (byte) 100);
    }

    private void runTo(int from, int to) {
        for (int day = from; day <= to; day++) system.tick(state, day);
    }

    @Test
    void aLoyalPeriodIsHonouredWithoutServiceAndOpensTheAuxiliarySecondStage() {
        enrol();
        runTo(1, PERIOD);
        assertEquals(WarrantObligationLedger.Status.ACTIVE, term().status());
        assertEquals(1, term().honouredPeriods());
        assertEquals(PERIOD, term().periodStart());
        assertTrue(state.auxiliaryWarrant().secondStage());
        assertTrue(MilitaryWarrants.warrantedCardIds(state).contains(HegemonyAuxiliaryWarrant.STAGE_TWO));
        assertEquals(0, state.contractCount, "the issuer never offers a service call");
    }

    @Test
    void aSuspensionLiftsAfterOneLoyalPeriodAndNotBefore() {
        enrol();
        relations.put("hegemony", RepLevel.NEUTRAL);
        system.tick(state, 3);
        assertEquals(WarrantObligationLedger.Status.SUSPENDED, term().status());

        runTo(4, 3 + PERIOD);
        assertEquals(WarrantObligationLedger.Status.SUSPENDED, term().status(),
                "a period spent below the floor does not restore");
        assertEquals(3 + PERIOD, term().periodStart());

        relations.put("hegemony", RepLevel.FAVORABLE);
        runTo(4 + PERIOD, 3 + 2 * PERIOD);
        assertEquals(WarrantObligationLedger.Status.ACTIVE, term().status());
        assertTrue(MilitaryWarrants.withheldCardIds(state).isEmpty());
    }

    @Test
    void aContractAgainstTheIssuerSuspendsAtOnce() {
        enrol();
        complete(otherHouse, hegemonyHouse, 4);
        system.tick(state, 5);
        assertEquals(WarrantObligationLedger.Status.SUSPENDED, term().status());
        system.tick(state, 6);
        assertFalse(term().loyaltyBroken(), "one contract is counted once");
    }

    @Test
    void fallingBelowTheFloorSuspendsAndHostileRevokes() {
        enrol();
        relations.put("hegemony", RepLevel.NEUTRAL);
        system.tick(state, 3);
        assertEquals(WarrantObligationLedger.Status.SUSPENDED, term().status());

        relations.put("hegemony", RepLevel.HOSTILE);
        system.tick(state, 4);
        assertEquals(WarrantObligationLedger.Status.REVOKED, term().status());
        relations.put("hegemony", RepLevel.FAVORABLE);
        complete(hegemonyHouse, otherHouse, 6);
        runTo(5, PERIOD * 2);
        assertEquals(WarrantObligationLedger.Status.REVOKED, term().status(), "a loyal period never lifts a revocation");
        assertTrue(MilitaryWarrants.withheldCardIds(state).contains(HegemonyAuxiliaryWarrant.STAGE_ONE));
    }

    @Test
    void aFreshStandingGrantReplacesARevokedWarrant() {
        enrol();
        relations.put("hegemony", RepLevel.HOSTILE);
        system.tick(state, 1);
        assertEquals(WarrantObligationLedger.Status.REVOKED, term().status());

        relations.put("hegemony", RepLevel.COOPERATIVE);
        state.playerMrbRep = 100_000;
        new StandingWarrantSystem(relations::get).tick(state, 40);
        assertEquals(WarrantObligationLedger.Status.ACTIVE, term().status());
        assertEquals(40, term().periodStart());
        assertTrue(MilitaryWarrants.withheldCardIds(state).isEmpty());
    }
}
