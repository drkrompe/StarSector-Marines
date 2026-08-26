package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.ExperienceTier;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.EnlistedRank;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The debug company as a real {@link MarineRoster} — `c12-the-debug-company.md`.
 *
 * <p>The point of the story is that a debug mission stops being a special
 * case: it fields squads with identity, NCO leaders and campaign-legal kit,
 * so the deployment-identity law in {@code company-view-nouns.md} and the
 * shipped C8 lift behavior are reachable from
 * it. These tests hold that line at the seam that would silently regress —
 * a fixture that stops producing tags still deploys, it just quietly stops
 * testing anything.
 */
class DebugCompanyTest {

    private static List<MarineSoldier> members(MarineRoster roster, MarineSquad squad) {
        return roster.squadMembers(squad);
    }

    @Test
    void everyStageFieldsWholeSquads() {
        for (DebugCompanyStage stage : DebugCompanyStage.values()) {
            MarineRoster roster = DebugCompany.roster(stage);
            List<String> line = DebugCompany.lineSquadIds(roster);

            assertEquals(stage.squads, line.size(), stage + " squad count");
            for (String id : line) {
                MarineSquad squad = roster.squadById(id);
                assertEquals(MarineSquad.CAPACITY, members(roster, squad).size(),
                        stage + " fills every billet");
                assertNotNull(roster.squadLeader(squad),
                        stage + " squad has an NCO in the leader billet");
            }
            assertEquals(stage.marines(), roster.activeSoldiers().size());
        }
    }

    @Test
    void theCompanyCarriesItsIdentityThroughTheFreeze() {
        MarineRoster roster = DebugCompany.roster(DebugCompanyStage.ESTABLISHED);
        Set<String> line = new LinkedHashSet<>(DebugCompany.lineSquadIds(roster));

        CampaignMarineDeployment frozen = CampaignMarineDeployment.freezeSelection(
                roster, line, DebugCompanyStage.ESTABLISHED.marines());

        assertEquals(DebugCompanyStage.ESTABLISHED.marines(), frozen.size());
        Set<String> squadIds = new HashSet<>();
        int leaders = 0;
        for (int seat = 0; seat < frozen.size(); seat++) {
            var tag = frozen.seat(seat).campaignSquad;
            assertNotNull(tag, "every debug seat carries a squad tag");
            squadIds.add(tag.squadId);
            if (tag.leader) leaders++;
            assertEquals(MarineSquad.CAPACITY, tag.strength);
        }
        assertEquals(DebugCompanyStage.ESTABLISHED.squads, squadIds.size());
        assertEquals(DebugCompanyStage.ESTABLISHED.squads, leaders,
                "exactly one leader per squad, not one per company");
    }

    @Test
    void firstContractIsTheCampaignOpening() {
        MarineRoster debug = DebugCompany.roster(DebugCompanyStage.FIRST_CONTRACT);
        MarineRoster campaign = new MarineRoster();
        campaign.bootstrapInitialComplement(MarineSquad.CAPACITY);

        assertEquals(campaign.activeSoldiers().size(), debug.activeSoldiers().size());
        for (MarineSoldier soldier : debug.activeSoldiers()) {
            assertEquals(ExperienceTier.GREEN, soldier.profile().experienceTier(),
                    "the opening company has seen nothing yet");
        }
        MarineSquad squad = debug.squadById(DebugCompany.lineSquadIds(debug).get(0));
        assertSame(EnlistedRank.CORPORAL, debug.squadLeader(squad).enlistedRank(),
                "a green squad is led by a corporal — sergeant needs veteran service");
    }

    @Test
    void laterStagesPromoteAndRearm() {
        MarineRoster established = DebugCompany.roster(DebugCompanyStage.ESTABLISHED);
        MarineSquad squad = established.squadById(
                DebugCompany.lineSquadIds(established).get(0));

        assertSame(EnlistedRank.SERGEANT, established.squadLeader(squad).enlistedRank(),
                "an established company's squad leader has veteran service");

        MarineRoster veterans = DebugCompany.roster(DebugCompanyStage.VETERAN_COMPANY);
        int elite = 0;
        for (MarineSoldier soldier : veterans.activeSoldiers()) {
            if (soldier.profile().experienceTier() == ExperienceTier.ELITE) elite++;
        }
        assertEquals(DebugCompanyStage.VETERAN_COMPANY.squads, elite,
                "one elite sergeant per squad");
    }

    @Test
    void theArmoryActuallyIssuesTheStagesKit() {
        // The kit is allocated through the real inventory-checked path, so an
        // under-stocked armory would show up as marines quietly holding the
        // starter rifle rather than as a failure. Assert the scarce items
        // landed: the graded marksman rifle in every squad.
        MarineRoster roster = DebugCompany.roster(DebugCompanyStage.VETERAN_COMPANY);
        int masterworkDmrs = 0;
        for (MarineSoldier soldier : roster.activeSoldiers()) {
            if (soldier.primaryDef() == WeaponRegistry.require(WeaponRegistry.DMR_ID)
                    && soldier.primaryGrade() == EquipmentGrade.MASTERWORK) masterworkDmrs++;
        }
        assertEquals(DebugCompanyStage.VETERAN_COMPANY.squads, masterworkDmrs,
                "one masterwork DMR per squad — the armory was stocked for the plan");
    }

    @Test
    void theSquadDialResizesWithoutChangingQuality() {
        // The balance question CONQUEST HIGH raised — "how many marines does
        // this actually need" — is a size question, and the stage ladder can
        // only offer fixed points. Dialling size must not quietly re-roll the
        // company's quality.
        MarineRoster small = DebugCompany.roster(DebugCompanyStage.VETERAN_COMPANY, 2);
        MarineRoster large = DebugCompany.roster(DebugCompanyStage.VETERAN_COMPANY, 20);

        assertEquals(2, DebugCompany.lineSquadIds(small).size());
        assertEquals(20, DebugCompany.lineSquadIds(large).size());
        assertEquals(20 * MarineSquad.CAPACITY, large.activeSoldiers().size());
        for (MarineRoster roster : List.of(small, large)) {
            MarineSquad first = roster.squadById(DebugCompany.lineSquadIds(roster).get(0));
            assertSame(EnlistedRank.SERGEANT, roster.squadLeader(first).enlistedRank());
        }
    }

    @Test
    void theDialHasNoAuthoredSquadCeiling() {
        assertEquals(9999, DebugCompany.normalizeSquads(9999));
        assertEquals(0, DebugCompany.normalizeSquads(-5));

        int requested = 41;
        MarineRoster expanded = DebugCompany.roster(
                DebugCompanyStage.FULL_STRENGTH, requested);
        assertEquals(requested * MarineSquad.CAPACITY,
                expanded.activeSoldiers().size(),
                "the detached fixture must cross the former forty-squad ceiling");
        assertTrue(DebugCompanyStage.FULL_STRENGTH.summary(Integer.MAX_VALUE)
                        .contains("25769803764 marines"),
                "the uncapped control's readout must not overflow");
    }

    @Test
    void aFullStrengthCompanyFillsAConquestHighManifest() {
        // 34 squads is 408 marines against 480 seats of lift, so every marine
        // the stage fields has somewhere to sit.
        DebugCompanyStage stage = DebugCompanyStage.FULL_STRENGTH;
        MarineRoster roster = DebugCompany.roster(stage);
        Set<String> line = new LinkedHashSet<>(DebugCompany.lineSquadIds(roster));

        CampaignMarineDeployment frozen = CampaignMarineDeployment.freezeSelection(
                roster, line, 40 * 12);

        assertEquals(stage.marines(), frozen.size());
        Set<String> squadIds = new HashSet<>();
        int leaders = 0;
        for (int seat = 0; seat < frozen.size(); seat++) {
            var tag = frozen.seat(seat).campaignSquad;
            assertNotNull(tag);
            squadIds.add(tag.squadId);
            if (tag.leader) leaders++;
        }
        assertEquals(stage.squads, squadIds.size());
        assertEquals(stage.squads, leaders);
    }

    @Test
    void theTopStagesReportOutgrowingTheirOfficer() {
        // Not a bug to fix here — the finding. A CONQUEST at HIGH risk wants
        // more squads than Rank.COLONEL may command, so the fixture says so
        // rather than silently capping.
        assertTrue(DebugCompanyStage.FULL_STRENGTH.exceedsCommandCap(),
                "34 squads is past a Colonel's 24");
        assertTrue(DebugCompanyStage.REINFORCED.exceedsCommandCap(),
                "17 squads is past a Lt. Colonel's 16");
        for (DebugCompanyStage stage : List.of(DebugCompanyStage.FIRST_CONTRACT,
                DebugCompanyStage.ESTABLISHED, DebugCompanyStage.VETERAN_COMPANY)) {
            assertTrue(!stage.exceedsCommandCap(),
                    stage + " still fits its officer's command");
        }
    }

    @Test
    void eachBuildIsItsOwnDetachedRoster() {
        MarineRoster first = DebugCompany.roster(DebugCompanyStage.FIRST_CONTRACT);
        MarineRoster second = DebugCompany.roster(DebugCompanyStage.FIRST_CONTRACT);

        assertNotSame(first, second);
        Set<String> firstIds = new HashSet<>();
        for (MarineSoldier soldier : first.activeSoldiers()) firstIds.add(soldier.id());
        for (MarineSoldier soldier : second.activeSoldiers()) {
            assertTrue(!firstIds.contains(soldier.id()),
                    "two builds share no personnel — nothing is global here");
        }
    }

    @Test
    void everyMarineIsFitToDeploy() {
        for (DebugCompanyStage stage : DebugCompanyStage.values()) {
            MarineRoster roster = DebugCompany.roster(stage);
            for (MarineSoldier soldier : roster.activeSoldiers()) {
                assertSame(MarineSoldierStatus.ACTIVE, soldier.status(),
                        stage + " fixture has no walking wounded");
            }
            assertEquals(stage.marines(), roster.lineReadySoldiers().size(),
                    stage + " puts its whole company on the line");
        }
    }
}
