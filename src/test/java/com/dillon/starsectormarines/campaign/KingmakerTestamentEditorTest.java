package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KingmakerTestamentEditorTest {

    @Test
    void selectsOneStrongestWitnessPerFamilyInHistoricalOrder() {
        Fixture fixture = new Fixture();
        fixture.rescue(CampaignEventState.REFUSED, 100, 0, 45,
                MoralChoiceSource.CIVILIAN_RESCUE_REFUSED,
                -5, 0, -10, "Later Refuge");
        long strongestRescue = fixture.rescue(CampaignEventState.RESOLVED,
                100, 100, 10, MoralChoiceSource.CIVILIAN_RESCUE_SAVED,
                15, 0, 20, "First Refuge");
        long defector = fixture.defector(CampaignEventState.RESOLVED,
                DefectorAsylumOutcome.PROTECTED, 30,
                0, 20, 10, "Quiet Anchorage");
        long civilWar = fixture.incumbentCivilWar(40, "Old Capital");
        fixture.coronation();
        int row = fixture.seal(82, 15, -25, 10, -20);

        fixture.rescue(CampaignEventState.RESOLVED, 100, 100, 90,
                MoralChoiceSource.CIVILIAN_RESCUE_SAVED,
                15, 0, 20, "Afterward");
        KingmakerTestamentEditor.Draft draft = fixture.edit(row);

        assertEquals(3, draft.witnesses().size());
        assertEquals(strongestRescue, draft.witnesses().get(0).sourceId());
        assertEquals(defector, draft.witnesses().get(1).sourceId());
        assertEquals(civilWar, draft.witnesses().get(2).sourceId());
        assertEquals(MoralChoiceSource.CIVILIAN_RESCUE_SAVED,
                draft.witnesses().get(0).source());
        assertEquals(MoralChoiceSource.DEFECTOR_ASYLUM,
                draft.witnesses().get(1).source());
        assertEquals(MoralChoiceSource.CIVIL_WAR_INCUMBENT,
                draft.witnesses().get(2).source());
        assertTrue(draft.witnesses().get(0).text().contains("First Refuge"));
        assertFalse(draft.witnesses().get(0).text().contains("Later Refuge"));
        assertFalse(draft.witnesses().stream()
                .anyMatch(witness -> witness.sourceId() == fixture.mainChain));
        assertThrows(UnsupportedOperationException.class,
                () -> draft.witnesses().add(draft.witnesses().get(0)));
    }

    @Test
    void rescueWitnessesCoverEveryPersistedOutcomeBandWithoutCounts() {
        assertWitnessContains(rescueDraft(CampaignEventState.REFUSED,
                100, 0, MoralChoiceSource.CIVILIAN_RESCUE_REFUSED,
                -5, -10), "left the civilians aboard to their fate");
        assertWitnessContains(rescueDraft(CampaignEventState.RESOLVED,
                100, 30, MoralChoiceSource.CIVILIAN_RESCUE_SAVED,
                5, 5), "more were lost than saved");
        assertWitnessContains(rescueDraft(CampaignEventState.RESOLVED,
                100, 50, MoralChoiceSource.CIVILIAN_RESCUE_SAVED,
                10, 10), "brought many of its civilians back");
        assertWitnessContains(rescueDraft(CampaignEventState.RESOLVED,
                100, 100, MoralChoiceSource.CIVILIAN_RESCUE_SAVED,
                15, 20), "brought everyone still aboard out alive");
    }

    @Test
    void defectorAndPriorCivilWarWitnessesCoverLockedBranches() {
        assertWitnessContains(defectorDraft(CampaignEventState.REFUSED,
                DefectorAsylumOutcome.NONE, -5, 0, 0),
                "refused them before any promise was made");
        assertWitnessContains(defectorDraft(CampaignEventState.RESOLVED,
                DefectorAsylumOutcome.PROTECTED, 0, 20, 10),
                "tried to buy that promise back, you kept your word");
        assertWitnessContains(defectorDraft(CampaignEventState.RESOLVED,
                DefectorAsylumOutcome.BETRAYED, 0, -25, -10),
                "sold them back to Plotter when the price was named");

        Fixture claimant = new Fixture();
        claimant.claimantCivilWar(25, "Earlier Seat");
        claimant.coronation();
        assertWitnessContains(claimant.edit(claimant.seal(
                82, 0, 0, 0, -40)),
                "had already helped Pretender cast Regent from power");

        Fixture incumbent = new Fixture();
        incumbent.incumbentCivilWar(25, "Earlier Seat");
        incumbent.coronation();
        assertWitnessContains(incumbent.edit(incumbent.seal(
                82, 0, 0, 0, 0)),
                "stood with the old ruler and helped their order survive");
    }

    @Test
    void tiesUseLaterHistoryThenStableChoiceId() {
        Fixture laterWins = new Fixture();
        laterWins.rescue(CampaignEventState.RESOLVED, 100, 100, 10,
                MoralChoiceSource.CIVILIAN_RESCUE_SAVED,
                15, 0, 20, "Older Port");
        long later = laterWins.rescue(CampaignEventState.RESOLVED,
                100, 100, 20, MoralChoiceSource.CIVILIAN_RESCUE_SAVED,
                15, 0, 20, "Later Port");
        laterWins.coronation();
        KingmakerTestamentEditor.Draft laterDraft = laterWins.edit(
                laterWins.seal(82, 15, 0, 20, -20));
        assertEquals(later, laterDraft.witnesses().get(0).sourceId());

        Fixture idWins = new Fixture();
        long lowerId = idWins.rescue(CampaignEventState.RESOLVED,
                100, 100, 20, MoralChoiceSource.CIVILIAN_RESCUE_SAVED,
                15, 0, 20, "Lower Id Port");
        idWins.rescue(CampaignEventState.RESOLVED, 100, 100, 20,
                MoralChoiceSource.CIVILIAN_RESCUE_SAVED,
                15, 0, 20, "Higher Id Port");
        idWins.coronation();
        KingmakerTestamentEditor.Draft idDraft = idWins.edit(
                idWins.seal(82, 15, 0, 20, -20));
        assertEquals(lowerId, idDraft.witnesses().get(0).sourceId());
    }

    @Test
    void cutoffAndSourceValidationFailClosedWithoutDiscardingValidDraft() {
        Fixture fixture = new Fixture();
        long corrupt = fixture.rescue(CampaignEventState.REFUSED,
                100, 0, 20, MoralChoiceSource.CIVILIAN_RESCUE_REFUSED,
                5, 0, 10, "False Memory");
        long mistimed = fixture.rescue(CampaignEventState.RESOLVED,
                100, 100, 25, MoralChoiceSource.CIVILIAN_RESCUE_SAVED,
                15, 0, 20, "Wrong Day");
        fixture.state.eventResolvedTick[fixture.state.eventIndex(mistimed)] = 26;
        fixture.coronation();
        int row = fixture.seal(82, 0, 0, 0, -20);
        fixture.rescue(CampaignEventState.RESOLVED, 100, 100, 30,
                MoralChoiceSource.CIVILIAN_RESCUE_SAVED,
                15, 0, 20, "Too Late");

        KingmakerTestamentEditor.Draft draft = fixture.edit(row);

        assertTrue(draft.witnesses().isEmpty());
        assertFalse(draft.accusation().contains(String.valueOf(corrupt)));
        fixture.state.kingmakerTestamentMoralChoiceCount[row] =
                fixture.state.moralChoiceCount + 1;
        assertTrue(KingmakerTestamentEditor.edit(
                fixture.state, row, fixture.names).isEmpty());
    }

    @Test
    void missingCoronationOrDisplayIdentityRejectsTheDraft() {
        Fixture noCoronation = new Fixture();
        noCoronation.state.appendMoralChoice(
                MoralChoiceSource.CIVILIAN_RESCUE_REFUSED, 99L,
                (short) -5, (short) 0, (short) -10, (short) 0,
                10, 10);
        int row = noCoronation.seal(82, -5, 0, -10, -20);
        assertTrue(KingmakerTestamentEditor.edit(
                noCoronation.state, row, noCoronation.names).isEmpty());

        Fixture missingName = new Fixture();
        missingName.coronation();
        int namedRow = missingName.seal(82, 0, 0, 0, -20);
        KingmakerTestamentEditor.Names incomplete =
                new KingmakerTestamentEditor.Names() {
                    @Override
                    public String house(long houseId) {
                        return null;
                    }

                    @Override
                    public String market(int marketRegistryId) {
                        return "Mairaath";
                    }
                };
        assertTrue(KingmakerTestamentEditor.edit(
                missingName.state, namedRow, incomplete).isEmpty());
    }

    @Test
    void verdictPreservesContradictionAndUsesNoSystemVocabulary() {
        String mixed = KingmakerTestamentEditor.verdict(15, -25, 10, -20);
        String aligned = KingmakerTestamentEditor.verdict(15, 20, 10, -20);
        String political = KingmakerTestamentEditor.verdict(0, 0, 0, -20);
        String quiet = KingmakerTestamentEditor.verdict(9, -9, 0, 0);

        assertEquals("You became a commander who sold promises when the price "
                + "was high enough, yet answered when the helpless called.", mixed);
        assertEquals("You became a commander who kept faith when betrayal would "
                + "have paid and broke the order that stood in your way.", aligned);
        assertEquals("In the end, you became a commander who broke the order "
                + "that stood in your way.", political);
        assertEquals("In the end, your record denied me any simple name for what "
                + "you became.", quiet);
        assertNoSystemLeak(String.join(" ", mixed, aligned, political, quiet));
    }

    @Test
    void completeDraftContainsNoHiddenLabelsOrNumbers() {
        Fixture fixture = new Fixture();
        fixture.rescue(CampaignEventState.RESOLVED, 100, 100, 10,
                MoralChoiceSource.CIVILIAN_RESCUE_SAVED,
                15, 0, 20, "First Refuge");
        fixture.defector(CampaignEventState.RESOLVED,
                DefectorAsylumOutcome.BETRAYED, 30,
                0, -25, -10, "Quiet Anchorage");
        fixture.coronation();
        KingmakerTestamentEditor.Draft draft = fixture.edit(
                fixture.seal(82, 15, -25, 10, -20));
        StringBuilder text = new StringBuilder(draft.accusation())
                .append(' ').append(draft.verdict());
        for (KingmakerTestamentEditor.Witness witness : draft.witnesses()) {
            text.append(' ').append(witness.text());
        }

        assertNoSystemLeak(text.toString());
        assertFalse(text.toString().matches(".*\\d.*"));
    }

    private static KingmakerTestamentEditor.Draft rescueDraft(
            CampaignEventState state, int atRisk, int rescued,
            MoralChoiceSource source, int mercy, int stewardship) {
        Fixture fixture = new Fixture();
        fixture.rescue(state, atRisk, rescued, 20, source,
                mercy, 0, stewardship, "Refuge");
        fixture.coronation();
        return fixture.edit(fixture.seal(82, mercy, 0,
                stewardship, -20));
    }

    private static KingmakerTestamentEditor.Draft defectorDraft(
            CampaignEventState state, DefectorAsylumOutcome outcome,
            int mercy, int integrity, int stewardship) {
        Fixture fixture = new Fixture();
        fixture.defector(state, outcome, 20, mercy, integrity,
                stewardship, "Anchorage");
        fixture.coronation();
        return fixture.edit(fixture.seal(82, mercy, integrity,
                stewardship, -20));
    }

    private static void assertWitnessContains(
            KingmakerTestamentEditor.Draft draft, String expected) {
        assertEquals(1, draft.witnesses().size());
        assertTrue(draft.witnesses().get(0).text().contains(expected));
    }

    private static void assertNoSystemLeak(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        List<String> forbidden = List.of("mercy", "integrity", "stewardship",
                "institutionalism", "ruthless", "merciful", "expedient",
                "principled", "exploitative", "protective", "insurgent",
                "establishment", "contribution", "ledger", "axis");
        for (String word : forbidden) {
            assertFalse(lower.contains(word), "leaked internal word: " + word);
        }
    }

    private static final class Fixture {
        final CampaignState state = new CampaignState();
        final int mainMarket = state.marketRegistry.intern("Mairaath");
        final int sourceFaction = state.factionRegistry.intern("Old Compact");
        final int resultFaction = state.factionRegistry.intern("Claimant League");
        final long claimant = house("Aster");
        final long deposed = house("Boreal");
        final long plotter = house("Plotter");
        final long threatened = house("Threatened");
        final long mainChain = 700L;
        final KingmakerTestamentEditor.Names names =
                new KingmakerTestamentEditor.Names() {
                    @Override
                    public String house(long houseId) {
                        int row = state.houseIndex(houseId);
                        return row >= 0 ? state.houseDisplayName[row] : null;
                    }

                    @Override
                    public String market(int marketRegistryId) {
                        return state.marketRegistry.get(marketRegistryId);
                    }
                };

        long rescue(CampaignEventState eventState, int atRisk, int rescued,
                    int happened, MoralChoiceSource source,
                    int mercy, int integrity, int stewardship,
                    String marketName) {
            int market = state.marketRegistry.intern(marketName);
            long event = state.appendCampaignEvent(
                    CampaignEventType.CIVILIAN_RESCUE,
                    10_000L + state.eventCount, market,
                    Math.max(0, happened - 2), happened + 1,
                    10, 5, atRisk);
            int row = state.eventIndex(event);
            state.eventState[row] = eventState.toByte();
            if (eventState == CampaignEventState.REFUSED) {
                state.eventDecisionTick[row] = happened;
            } else {
                state.eventResolvedTick[row] = happened;
                state.eventCiviliansRescued[row] = rescued;
            }
            state.appendMoralChoice(source, event,
                    (short) mercy, (short) integrity, (short) stewardship,
                    (short) 0, happened, happened);
            return event;
        }

        long defector(CampaignEventState eventState,
                      DefectorAsylumOutcome outcome, int happened,
                      int mercy, int integrity, int stewardship,
                      String marketName) {
            int market = state.marketRegistry.intern(marketName);
            long event = state.appendCampaignEvent(
                    CampaignEventType.DEFECTOR_ASYLUM,
                    20_000L + state.eventCount, market,
                    Math.max(0, happened - 2), happened + 1,
                    10, 5, 0);
            int row = state.eventIndex(event);
            state.eventActorHouseId[row] = plotter;
            state.eventTargetHouseId[row] = threatened;
            state.eventState[row] = eventState.toByte();
            state.eventDefectorOutcome[row] = outcome.toByte();
            if (eventState == CampaignEventState.REFUSED) {
                state.eventDecisionTick[row] = happened;
            } else {
                state.eventResolvedTick[row] = happened;
            }
            state.appendMoralChoice(MoralChoiceSource.DEFECTOR_ASYLUM, event,
                    (short) mercy, (short) integrity, (short) stewardship,
                    (short) 0, happened, happened);
            return event;
        }

        long incumbentCivilWar(int happened, String marketName) {
            int market = state.marketRegistry.intern(marketName);
            long actor = house("Pretender");
            long target = house("Regent");
            long chain = state.addAutonomousChain(actor, target, market, -1,
                    HouseRank.TIER_3.toByte(), ChainArchetype.CIVIL_WAR,
                    (short) 180, (byte) 128, 1);
            int row = state.chainIndex(chain);
            state.chainState[row] = ChainState.FAILED.toByte();
            state.chainResolvedTick[row] = happened;
            state.chainPlayerAllegiance[row] =
                    CivilWarAllegiance.INCUMBENT.toByte();
            state.chainPlayerConsequenceState[row] =
                    CivilWarPlayerConsequenceState.APPLIED.toByte();
            state.appendMoralChoice(MoralChoiceSource.CIVIL_WAR_INCUMBENT,
                    chain, (short) 0, (short) 0, (short) 0, (short) 20,
                    happened, happened);
            return chain;
        }

        long claimantCivilWar(int happened, String marketName) {
            int market = state.marketRegistry.intern(marketName);
            long actor = house("Pretender");
            long target = house("Regent");
            long chain = state.addAutonomousChain(actor, target, market, -1,
                    HouseRank.TIER_3.toByte(), ChainArchetype.CIVIL_WAR,
                    (short) 180, (byte) 128, 1);
            int chainRow = state.chainIndex(chain);
            state.chainState[chainRow] = ChainState.RESOLVED.toByte();
            state.chainResolvedTick[chainRow] = happened - 1;
            long claim = state.prepareThroneClaim(chain, actor, sourceFaction,
                    resultFaction, market, happened - 1);
            int claimRow = state.throneClaimIndex(claim);
            state.throneClaimState[claimRow] = ThroneClaimState.APPLIED.toByte();
            state.throneClaimAppliedTick[claimRow] = happened;
            state.appendMoralChoice(MoralChoiceSource.CIVIL_WAR_CLAIMANT,
                    chain, (short) 0, (short) 0, (short) 0, (short) -20,
                    happened, happened);
            return chain;
        }

        void coronation() {
            state.appendMoralChoice(MoralChoiceSource.CIVIL_WAR_CLAIMANT,
                    mainChain, (short) 0, (short) 0, (short) 0, (short) -20,
                    81, 82);
        }

        int seal(int day, int mercy, int integrity, int stewardship,
                 int institutionalism) {
            long id = state.sealKingmakerTestament(500L, mainChain,
                    claimant, deposed, sourceFaction, resultFaction,
                    mainMarket, (short) 60, mercy, integrity, stewardship,
                    institutionalism, state.moralChoiceCount, day);
            return testamentRow(id);
        }

        KingmakerTestamentEditor.Draft edit(int testamentRow) {
            return KingmakerTestamentEditor.edit(state, testamentRow, names)
                    .orElseThrow();
        }

        private int testamentRow(long id) {
            for (int row = 0; row < state.kingmakerTestamentCount; row++) {
                if (state.kingmakerTestamentId[row] == id) return row;
            }
            return -1;
        }

        private long house(String name) {
            return state.addHouse(mainMarket, sourceFaction,
                    HouseFlavor.FEUDAL, HouseRank.TIER_3, HouseStatus.ACTIVE,
                    PatronArchetype.NEWCOMER, name);
        }
    }
}
