package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.ExperienceTier;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.ShuttleArrivalPlan;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.EnlistedRank;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.ArmorIssueResolver;
import com.dillon.starsectormarines.marine.ArmorRole;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.marine.SquadArmorDoctrine;
import com.dillon.starsectormarines.marine.SquadArmorPlan;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.marine.SquadWeaponDoctrine;
import com.dillon.starsectormarines.ops.MarineArrivalPolicy;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.marine.SquadExperienceStandard;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeout;
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
    void stripesAndBandsFollowTheArmourEachSquadWasIssued() {
        // Experience is issued with the armour, so a stage no longer authors a
        // band directly: the armour a squad is issued does. What must hold is
        // that the two agree.
        //
        // The band is the SQUAD's, not the leader's own suit. Armour is issued
        // per role now, so a section's scout or weapons carrier may wear a
        // cheaper specialist pattern than its riflemen — reading one marine's
        // suit would hand out stripes on the accident of who happened to be
        // senior ({@code role-and-access.md}).
        MarineRoster established = DebugCompany.roster(DebugCompanyStage.ESTABLISHED);

        for (String squadId : DebugCompany.lineSquadIds(established)) {
            MarineSquad squad = established.squadById(squadId);
            MarineSoldier leader = established.squadLeader(squad);
            assertNotNull(leader);
            int squadBand = 0;
            for (String memberId : established.manningMemberIds(squad)) {
                squadBand = Math.max(squadBand, SquadExperienceStandard
                        .bandFor(established.soldierById(memberId)).minimumXp);
            }
            assertSame(squadBand >= ExperienceTier.VETERAN.minimumXp
                            ? EnlistedRank.SERGEANT : EnlistedRank.CORPORAL,
                    leader.enlistedRank(),
                    "stripes track the band the squad's issued kit fields");
        }
    }

    @Test
    void everySquadReceivesOneRandomizedAuthoredLoadout() {
        int catalogPass = Math.max(SquadEquipmentDoctrines.weaponDoctrines().size(),
                SquadEquipmentDoctrines.armorPlans().size());
        MarineRoster roster = DebugCompany.roster(
                DebugCompanyStage.VETERAN_COMPANY, catalogPass, new Random(7_211L));
        Set<String> weaponDoctrines = new HashSet<>();
        Set<String> armorDoctrines = new HashSet<>();

        for (MarineSquad squad : roster.squads()) {
            SquadWeaponDoctrine weapons = roster.armory()
                    .weaponDoctrineById(squad.weaponDoctrineId());
            SquadArmorDoctrine armor = roster.armory()
                    .armorDoctrineById(squad.armorDoctrineId());
            assertNotNull(weapons, "the card can resolve its authored weapon loadout");
            assertNotNull(armor, "the card can resolve its authored armor loadout");
            weaponDoctrines.add(weapons.id());
            armorDoctrines.add(armor.id());

            List<MarineSoldier> squadMembers = members(roster, squad);
            boolean masterworkMarksmanSeen = false;
            for (int billet = 0; billet < MarineSquad.CAPACITY; billet++) {
                MarineSoldier soldier = squadMembers.get(billet);
                assertEquals(weapons.issue(billet).primaryId(), soldier.primaryId());
                assertEquals(weapons.issue(billet).specialEquipmentId(),
                        soldier.specialEquipmentId());
                assertEquals(armor.issueId(billet), soldier.armorId());
                if (WeaponRegistry.STARTER_PRIMARY_ID.equals(soldier.primaryId())) {
                    assertSame(EquipmentGrade.SERVICE, soldier.primaryGrade());
                } else if (!masterworkMarksmanSeen
                        && WeaponRegistry.DMR_ID.equals(soldier.primaryId())) {
                    assertSame(EquipmentGrade.MASTERWORK, soldier.primaryGrade());
                    masterworkMarksmanSeen = true;
                } else {
                    assertSame(EquipmentGrade.MILSPEC, soldier.primaryGrade());
                }
            }
        }

        assertEquals(SquadEquipmentDoctrines.weaponDoctrines().size(),
                weaponDoctrines.size(),
                "the first shuffle bag exposes every faction-flavored weapon doctrine");
        // Every plan is admissible at every stage. Composition is not a function
        // of wealth: a poor company runs the same sections as a rich one and
        // wears worse kit doing it ({@code role-and-access.md}).
        Set<String> authored = new HashSet<>();
        for (SquadArmorPlan plan : SquadEquipmentDoctrines.armorPlans()) {
            authored.add(plan.id());
        }
        assertEquals(authored, armorDoctrines,
                "the first shuffle bag exposes every authored armour plan");
    }

    /**
     * A company that grows richer wears better kit in the same sections.
     *
     * <p>The invariant this replaced asked that every doctrine sit in some
     * stage's band, which was the old model's way of saying no authored loadout
     * was unreachable. Bands are gone: every plan is reachable at every stage,
     * so the question worth asking now is whether access actually does anything.
     * It compares the same plan issued at the lowest and highest stage ceilings
     * and requires the richer one to be at least as good in every billet.
     */
    @Test
    void aRicherCompanyWearsBetterKitInTheSameSection() {
        for (SquadArmorPlan plan : SquadEquipmentDoctrines.armorPlans()) {
            SquadArmorDoctrine poor = issuedAtCeiling(plan, DebugBilletPlan.STARTER_ISSUE);
            SquadArmorDoctrine rich = issuedAtCeiling(plan, DebugBilletPlan.HARDENED);
            for (int billet = 0; billet < MarineSquad.CAPACITY; billet++) {
                int poorTier = MarineArmorCatalogRegistry.require(poor.issueId(billet)).tier();
                int richTier = MarineArmorCatalogRegistry.require(rich.issueId(billet)).tier();
                assertTrue(richTier >= poorTier,
                        plan.id() + " billet " + billet + ": a hardened company is issued tier "
                                + richTier + " where a starter company gets tier " + poorTier);
            }
            assertNotEquals(poor.issueIds(), rich.issueIds(),
                    plan.id() + " issues identical kit at both ends of the ladder,"
                            + " so access is doing nothing");
        }
    }

    /**
     * The same section, at both ends of the ladder, is still the same section.
     * Access changes what a billet wears and never what job it does.
     */
    @Test
    void accessChangesTheKitAndNeverTheComposition() {
        for (SquadArmorPlan plan : SquadEquipmentDoctrines.armorPlans()) {
            for (DebugBilletPlan ceiling : DebugBilletPlan.values()) {
                SquadArmorDoctrine issued = issuedAtCeiling(plan, ceiling);
                for (int billet = 0; billet < MarineSquad.CAPACITY; billet++) {
                    ArmorRole wanted = plan.roleAt(billet);
                    ArmorRole worn = MarineArmorCatalogRegistry
                            .require(issued.issueId(billet)).role();
                    assertEquals(wanted, worn,
                            plan.id() + " at tier " + ceiling.maxArmorTier() + " billet "
                                    + billet + " wanted a " + wanted.key + " and was issued a "
                                    + worn.key);
                }
            }
        }
    }

    private static SquadArmorDoctrine issuedAtCeiling(SquadArmorPlan plan, DebugBilletPlan ceiling) {
        return ArmorIssueResolver.resolve(plan,
                pattern -> pattern.tier() <= ceiling.maxArmorTier());
    }


    @Test
    void theSquadDialResizesWithoutChangingQuality() {
        // The balance question CONQUEST HIGH raised — "how many marines does
        // this actually need" — is a size question, and the stage ladder can
        // only offer fixed points. Dialling size may roll different equipment,
        // but must not quietly change the company's experience quality.
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
    void twoHundredSquadLaunchSnapshotStaysWithinABoundedSetupBudget() {
        assertTimeout(Duration.ofSeconds(5), () -> {
            int requested = 200;
            MarineRoster roster = DebugCompany.roster(
                    DebugCompanyStage.FULL_STRENGTH, requested,
                    new Random(8_262_026L));
            Set<String> selected = new LinkedHashSet<>(
                    DebugCompany.lineSquadIds(roster));

            CampaignMarineDeployment frozen = CampaignMarineDeployment.freezeSelection(
                    roster, selected, requested * MarineSquad.CAPACITY);

            ShuttleArrivalPlan plan = new ShuttleArrivalPlan(
                    MarineArrivalPolicy.PAIRED_HALF_SQUAD, 0);
            ShuttleArrivalPlan.ResolvedManifest manifest = plan.resolveManifest(
                    List.of(new ShuttleAssignment(ShuttleType.VALKYRIE, 40, 6)),
                    frozen.size());
            try (BattleSimulation sim = BattleSetup.createConquest(
                    8_262_026L, manifest.assignments(), false,
                    OperationTier.REINFORCED, RiskLevel.LOW,
                    TargetProfile.NEUTRAL, FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                    plan)) {
                frozen.applyTo(sim);
            }

            assertEquals(requested * MarineSquad.CAPACITY, frozen.size());
            assertEquals(requested, selected.size());
        });
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
