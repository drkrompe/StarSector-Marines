package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.battle.infantry.ExperienceTier;
import com.dillon.starsectormarines.campaign.systems.StationedStrengthSystem;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.SquadExperienceStandard;
import com.fs.starfarer.api.combat.StatBonus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Coverage for stationed strength ({@code contracts-nouns.md}, law 11). */
class StationedStrengthTest {

    private static final float EPSILON = 0.0001f;
    private static final int SEATS = 4;

    /**
     * The read-back is the plain inverse of the write: everything
     * {@link StationedStrengthSystem} put on the market's ground-defence stat,
     * and nothing else that happens to be sitting on it.
     */
    @Test
    void theTotalAtAMarketIsOurOwnFlatModifiersScaledAsVanillaScalesThem() {
        StatBonus groundDefence = new StatBonus();
        groundDefence.modifyFlat(StationedStrengthSystem.MODIFIER_ID_PREFIX + "41",
                18f, "Stationed mercenary detachment");
        groundDefence.modifyFlat(StationedStrengthSystem.MODIFIER_ID_PREFIX + "77",
                12f, "Stationed mercenary detachment");
        // Vanilla's own base value for the colony, which is not ours to subtract.
        groundDefence.modifyFlat("population_and_infrastructure", 200f, "Base value");
        // And an industry's multiplier, which is not a flat modifier at all.
        groundDefence.modifyMult("ground_defenses", 2f, "Ground Defenses");

        // 30 flat, doubled by the industry, because that is what vanilla adds.
        assertEquals(60f, StationedStrength.totalIn(groundDefence), EPSILON);
    }

    /** A market with nothing stationed contributes nothing, and neither does none. */
    @Test
    void aMarketWithNothingStationedTotalsZero() {
        StatBonus groundDefence = new StatBonus();
        groundDefence.modifyFlat("population_and_infrastructure", 200f, "Base value");

        assertEquals(0f, StationedStrength.totalIn(groundDefence), EPSILON);
        assertEquals(0f, StationedStrength.totalIn(null), EPSILON);
        assertEquals(0f, StationedStrength.totalAt(null), EPSILON);
    }

    /**
     * The number really is inside {@code MarketCMD.getDefenderStr}, which is
     * {@code stat.computeEffective(0f)} — the reason it has to come back out
     * before the market's strength becomes a militia headcount.
     */
    @Test
    void whatWeWroteIsInsideVanillasOwnDefenderStrength() {
        StatBonus groundDefence = new StatBonus();
        groundDefence.modifyFlat("population_and_infrastructure", 200f, "Base value");
        float withoutUs = groundDefence.computeEffective(0f);
        groundDefence.modifyFlat(StationedStrengthSystem.MODIFIER_ID_PREFIX + "41",
                18f, "Stationed mercenary detachment");

        assertEquals(withoutUs + 18f, groundDefence.computeEffective(0f), EPSILON);
        assertEquals(18f, StationedStrength.totalIn(groundDefence), EPSILON);
    }

    @Test
    void theSeatLadderStartsAtOneCargoMarineAndRisesWithTheBand() {
        assertEquals(1.0f, StationedStrength.seatWorth(ExperienceTier.GREEN), EPSILON,
                "anchored to MarketCMD.MARINES_IN_MARKET_CARGO_DEFENSE_BONUS");
        assertEquals(1.5f, StationedStrength.seatWorth(ExperienceTier.REGULAR), EPSILON);
        assertEquals(2.0f, StationedStrength.seatWorth(ExperienceTier.VETERAN), EPSILON);
        assertEquals(3.0f, StationedStrength.seatWorth(ExperienceTier.ELITE), EPSILON);
        assertEquals(StationedStrength.GREEN_SEAT_WORTH,
                StationedStrength.seatWorth(null), EPSILON,
                "an unresolved band fields recruits rather than throwing");
        assertTrue(StationedStrength.seatWorth(ExperienceTier.VETERAN)
                        > StationedStrength.seatWorth(ExperienceTier.GREEN),
                "twelve Veterans must outweigh twelve cargo marines; a Green squad must not");
    }

    @Test
    void aBoundDetachmentIsWorthItsSeatsAtTheBandItDeploysAt() {
        CampaignState state = new CampaignState();
        MarineRoster roster = stationedSquad(state);
        ExperienceTier issued = issuedBand(roster);

        assertEquals(ExperienceTier.REGULAR, issued, "fixture: recruits are issued tier-2 armour");
        assertEquals(SEATS * StationedStrength.seatWorth(issued),
                StationedStrength.valueFor(state, 0, roster), EPSILON);
    }

    @Test
    void onlyActiveSeatsStandALine() {
        CampaignState state = new CampaignState();
        MarineRoster roster = stationedSquad(state);
        List<MarineSoldier> members = roster.squadMembers(roster.squads().get(0));
        roster.applySoldierOutcome(Map.of(
                members.get(0).id(), MarineSoldierStatus.WIA,
                members.get(1).id(), MarineSoldierStatus.KIA), 0f, 30f);

        assertEquals((SEATS - 2) * StationedStrength.seatWorth(issuedBand(roster)),
                StationedStrength.valueFor(state, 0, roster), EPSILON,
                "a WIA marine holds his billet but does not stand a line");
    }

    @Test
    void aBoundDetachmentOverridesTheCommittedCount() {
        CampaignState state = new CampaignState();
        MarineRoster roster = stationedSquad(state);
        state.contractMarinesCommitted[0] = 300;

        assertEquals(SEATS * StationedStrength.seatWorth(issuedBand(roster)),
                StationedStrength.valueFor(state, 0, roster), EPSILON,
                "the named detachment is what is actually standing there");
    }

    @Test
    void aLegacyCountOnlyAssignmentIsValuedAtTheGreenSeat() {
        CampaignState state = garrisonRow(new CampaignState());
        state.contractMarinesCommitted[0] = 30;

        assertEquals(30 * StationedStrength.GREEN_SEAT_WORTH,
                StationedStrength.valueFor(state, 0, new MarineRoster()), EPSILON,
                "a roster with nothing bound to the contract is a count-only assignment");
        assertEquals(30 * StationedStrength.GREEN_SEAT_WORTH,
                StationedStrength.valueFor(state, 0, null), EPSILON,
                "no roster at all takes the same path");
    }

    @Test
    void anOutOfRangeRowIsWorthNothing() {
        CampaignState state = garrisonRow(new CampaignState());

        assertEquals(0f, StationedStrength.valueFor(state, 1, null), EPSILON);
        assertEquals(0f, StationedStrength.valueFor(state, -1, null), EPSILON);
        assertEquals(0f, StationedStrength.valueFor(null, 0, null), EPSILON);
    }

    private static ExperienceTier issuedBand(MarineRoster roster) {
        return SquadExperienceStandard.bandFor(
                roster.squadMembers(roster.squads().get(0)).get(0));
    }

    /** A Garrison row with one squad of {@link #SEATS} bound to it. */
    private static MarineRoster stationedSquad(CampaignState state) {
        garrisonRow(state);
        MarineRoster roster = new MarineRoster();
        MarineCaptain captain = new MarineCaptain("Garrison Lead", null, Rank.LIEUTENANT, 0f);
        roster.add(captain);
        roster.ensureActiveSoldiers(SEATS);
        MarineSquad squad = roster.squads().get(0);
        assertTrue(roster.bindStationing(state.contractId[0], captain.id(), List.of(squad.id())));
        return roster;
    }

    private static CampaignState garrisonRow(CampaignState state) {
        state.addContract(1L, -1L, -1L, ContractType.GARRISON,
                ContractState.ACTIVE, 10, 500, -1, (byte) 0,
                -1, state.marketRegistry.intern("jangala"), -1, 0, 1_000,
                (byte) 25, (byte) 25, (byte) 100);
        return state;
    }
}
