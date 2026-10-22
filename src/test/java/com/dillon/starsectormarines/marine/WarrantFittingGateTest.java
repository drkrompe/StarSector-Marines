package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.CampaignState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A suspended warrant withholds its cards: no squad is newly given one, a squad already
 * holding one keeps it, and a card no warrant names is never refused.
 */
class WarrantFittingGateTest {

    private static final String SHEET = SquadEquipmentDoctrines.HEGEMONY_AUXILIARY_SECTION_ARMOR;
    private static final String SUIT = "armor.arbalest";

    private final MarineRoster roster = new MarineRoster();
    private MarineSquad squad;

    @BeforeEach
    void setUp() {
        roster.bootstrapInitialComplement(MarineSquad.CAPACITY);
        squad = roster.squads().stream().filter(s -> !s.reserve()).findFirst().orElseThrow();
        for (String pattern : List.of(SUIT, "armor.pathfinder", "armor.line")) roster.armory().unlockArmor(pattern);
        roster.armory().acquireArmorPlan(SHEET);
        assertNotNull(roster.armory().armorDoctrineById(SHEET), "the section sheet can be issued");
    }

    private SquadEquipmentResult fit() {
        return roster.applySquadEquipment(squad.id(), SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS, SHEET);
    }

    private Set<String> suspendedHegemonyWarrant() {
        CampaignState state = new CampaignState();
        HegemonyAuxiliaryWarrant.offer(state, 0);
        HegemonyAuxiliaryWarrant.accept(state, 0);
        state.warrantObligations().open(ArmorTradition.HEGEMONY.key, 0).suspend(5);
        return MilitaryWarrants.withheldCardIds(state);
    }

    @Test
    void aSuspendedWarrantRefusesANewFitting() {
        roster.setWithheldCards(suspendedHegemonyWarrant());
        String before = roster.soldierById(squad.memberIds().get(3)).armorId();
        assertEquals(SquadEquipmentResult.WARRANT_SUSPENDED, fit());
        assertEquals(before, roster.soldierById(squad.memberIds().get(3)).armorId(), "nothing is fitted");
    }

    @Test
    void aSquadAlreadyHoldingTheCardKeepsIt() {
        roster.setWithheldCards(Set.of());
        assertEquals(SquadEquipmentResult.APPLIED, fit());
        List<String> wearing = roster.manningMemberIds(squad).stream()
                .filter(id -> SUIT.equals(roster.soldierById(id).armorId())).toList();
        assertFalse(wearing.isEmpty());

        roster.setWithheldCards(suspendedHegemonyWarrant());
        assertEquals(SquadEquipmentResult.APPLIED, fit(), "a refit of the same card takes nothing away");
        assertTrue(wearing.stream().allMatch(id -> SUIT.equals(roster.soldierById(id).armorId())));
    }

    @Test
    void aCardNoWarrantNamesIsNeverWithheld() {
        CampaignState state = new CampaignState();
        state.warrantObligations().open(ArmorTradition.HEGEMONY.key, 0).revoke(1);
        Set<String> withheld = MilitaryWarrants.withheldCardIds(state);
        assertTrue(withheld.isEmpty(), "a revoked term with no warrant row withholds nothing");
        roster.setWithheldCards(withheld);
        assertEquals(SquadEquipmentResult.APPLIED, fit());
    }

    @Test
    void aWithheldCardIsNotWarrantedForAcquisition() {
        CampaignState state = new CampaignState();
        HegemonyAuxiliaryWarrant.offer(state, 0);
        HegemonyAuxiliaryWarrant.accept(state, 0);
        assertEquals(Set.of(HegemonyAuxiliaryWarrant.STAGE_ONE), MilitaryWarrants.warrantedCardIds(state));
        state.warrantObligations().open(ArmorTradition.HEGEMONY.key, 0).suspend(3);
        assertTrue(MilitaryWarrants.warrantedCardIds(state).isEmpty());
        assertEquals(Set.of(HegemonyAuxiliaryWarrant.STAGE_ONE), MilitaryWarrants.withheldCardIds(state));
    }
}
