package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.MarineArmorPattern;
import com.dillon.starsectormarines.marine.MarineArmory;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadArmorDoctrine;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.marine.SquadEquipmentResult;
import com.dillon.starsectormarines.marine.SquadWeaponDoctrine;
import com.dillon.starsectormarines.marine.SquadWeaponIssue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Builds the detached {@link MarineRoster} a debug mission deploys.
 *
 * <p><b>A real roster, not a parallel fixture type.</b> The roster this
 * returns is the same class the campaign persists — built in memory, never
 * registered with {@code MarineRosterScript}, never written back. That is
 * the whole point: {@code CampaignMarineDeployment.freezeSelection} then
 * runs against it unchanged, so a debug deployment carries
 * {@code CampaignSquadTag}s, its squads keep their identity across lifts,
 * the NCO takes the leader billet, and {@code SquadFormUpSystem}'s gate is
 * reachable. The previous per-seat fixture could do none of that, and the
 * two paths could drift apart silently. Now they cannot: the fixture is
 * built by {@link MarineRoster#createSquad} and
 * {@link MarineRoster#recruitToSquad}, outfitted through the same
 * allocation rules, and led by the same
 * {@code MarineRoster.refreshLeadership}.
 *
 * <p>A consequence worth keeping: the armory is stocked <em>first</em> and
 * kit is issued through {@code allocatePrimary} / {@code allocateSecondary}
 * / {@code allocateArmor}, so a loadout the armory would refuse is a
 * loadout this cannot produce.
 *
 * <p>See `c12-the-debug-company.md`.
 */
public final class DebugCompany {

    private DebugCompany() {}

    /** The company at {@code stage}, at that stage's own size. */
    public static MarineRoster roster(DebugCompanyStage stage) {
        DebugCompanyStage resolved = stage != null ? stage : DebugCompanyStage.FIRST_CONTRACT;
        return roster(resolved, resolved.squads);
    }

    /**
     * The company at {@code stage}, resized to {@code squads}. Size and
     * quality are independent: the dial answers "how many marines does this
     * mission need", which no fixed ladder can.
     */
    public static MarineRoster roster(DebugCompanyStage stage, int squads) {
        return roster(stage, squads, new Random());
    }

    /** Deterministic seam for tests and authored debug captures. */
    static MarineRoster roster(DebugCompanyStage stage, int squads, Random loadoutRandom) {
        DebugCompanyStage resolved = stage != null ? stage : DebugCompanyStage.FIRST_CONTRACT;
        int count = normalizeSquads(squads);
        Random rng = loadoutRandom != null ? loadoutRandom : new Random();
        List<SquadWeaponDoctrine> weapons = randomizedWeaponDoctrines(count, rng);
        List<SquadArmorDoctrine> armor = randomizedArmorDoctrines(count, rng);
        MarineRoster roster = new MarineRoster();
        stockArmory(roster.armory(), resolved, weapons, armor);
        for (int s = 0; s < count; s++) {
            MarineSquad squad = roster.createSquad();
            SquadWeaponDoctrine weaponDoctrine = weapons.get(s);
            SquadArmorDoctrine armorDoctrine = armor.get(s);
            for (int billet = 0; billet < MarineSquad.CAPACITY; billet++) {
                MarineSoldier recruit = roster.recruitToSquad(squad.id());
                if (recruit == null) break;
                recruit.addExperience(resolved.plan.experienceXp(billet));
            }
            SquadEquipmentResult result = roster.applySquadEquipment(
                    squad.id(), weaponDoctrine.id(), armorDoctrine.id());
            if (result != SquadEquipmentResult.APPLIED) {
                throw new IllegalStateException("Debug squad loadout refused: " + result);
            }
            upgradeWeaponGrades(roster, squad, resolved.plan);
        }
        // Experience is what decides who leads, and the last recruit's arrives
        // after that squad's final enlistment refresh. Re-derive once the whole
        // company is outfitted — the campaign does the same after awarding
        // post-mission XP.
        roster.refreshLeadership();
        return roster;
    }

    /** Debug size has no scenario ceiling; only negative input normalizes to zero. */
    public static int normalizeSquads(int squads) {
        return Math.max(0, squads);
    }

    /** Line squads in roster order — what the deployment selects, reserve excluded. */
    public static List<String> lineSquadIds(MarineRoster roster) {
        List<String> ids = new ArrayList<>();
        if (roster == null) return ids;
        for (MarineSquad squad : roster.squads()) {
            if (!squad.reserve()) ids.add(squad.id());
        }
        return ids;
    }

    /** Preserves the rolled doctrine while moving its weapons up the experience ladder. */
    private static void upgradeWeaponGrades(MarineRoster roster, MarineSquad squad,
                                            DebugBilletPlan plan) {
        boolean marksmanIssued = false;
        for (MarineSoldier soldier : roster.squadMembers(squad)) {
            boolean designatedMarksman = !marksmanIssued
                    && WeaponRegistry.DMR_ID.equals(soldier.primaryId());
            EquipmentGrade grade = debugGrade(
                    plan, soldier.primaryId(), designatedMarksman);
            marksmanIssued |= designatedMarksman;
            if (!roster.allocatePrimary(soldier.id(), soldier.primaryId(), grade)) {
                throw new IllegalStateException("Debug weapon grade refused for "
                        + soldier.primaryId() + " at " + grade);
            }
        }
    }

    /**
     * Unlocks and prints the exact randomized manifest. Allocation remains
     * inventory-checked: this debug fixture cannot silently bypass campaign
     * issue rules just because its squad doctrine was rolled in memory.
     */
    private static void stockArmory(MarineArmory armory, DebugCompanyStage stage,
                                    List<SquadWeaponDoctrine> weapons,
                                    List<SquadArmorDoctrine> armor) {
        armory.addFabricationMaterials(fabricationBudget(stage, weapons, armor));
        for (int squad = 0; squad < weapons.size(); squad++) {
            SquadWeaponDoctrine weaponDoctrine = weapons.get(squad);
            SquadArmorDoctrine armorDoctrine = armor.get(squad);
            boolean marksmanIssued = false;
            for (int billet = 0; billet < MarineSquad.CAPACITY; billet++) {
                SquadWeaponIssue issue = weaponDoctrine.issue(billet);
                String primary = issue.primaryId();
                boolean designatedMarksman = !marksmanIssued
                        && WeaponRegistry.DMR_ID.equals(primary);
                EquipmentGrade grade = debugGrade(
                        stage.plan, primary, designatedMarksman);
                marksmanIssued |= designatedMarksman;
                armory.unlockPrimary(primary, grade);
                printUpTo(() -> armory.ownedPrimary(primary, grade),
                        () -> armory.printPrimary(primary, grade), 1);
                String secondary = issue.specialEquipmentId();
                if (secondary != null) {
                    armory.unlockSecondary(secondary);
                    printUpTo(() -> armory.ownedSecondary(secondary),
                            () -> armory.printSecondary(secondary), 1);
                }
                MarineArmorPattern pattern = armorDoctrine.issue(billet);
                armory.unlockArmor(pattern);
                printUpTo(() -> armory.ownedArmor(pattern),
                        () -> armory.printArmor(pattern), 1);
            }
        }
    }

    /** Funds the rolled fixture manifest, saturating only at armory's int storage limit. */
    private static int fabricationBudget(DebugCompanyStage stage,
                                         List<SquadWeaponDoctrine> weapons,
                                         List<SquadArmorDoctrine> armor) {
        long total = 0L;
        for (int squad = 0; squad < weapons.size(); squad++) {
            boolean marksmanIssued = false;
            for (int billet = 0; billet < MarineSquad.CAPACITY; billet++) {
                SquadWeaponIssue issue = weapons.get(squad).issue(billet);
                boolean designatedMarksman = !marksmanIssued
                        && WeaponRegistry.DMR_ID.equals(issue.primaryId());
                EquipmentGrade grade = debugGrade(
                        stage.plan, issue.primaryId(), designatedMarksman);
                marksmanIssued |= designatedMarksman;
                if (!WeaponRegistry.STARTER_PRIMARY_ID.equals(issue.primaryId())) {
                    total += MarineArmory.primaryFabricationCost(grade);
                }
                if (issue.specialEquipmentId() != null) {
                    total += MarineArmory.secondaryFabricationCost(
                            issue.specialEquipmentId());
                }
                total += MarineArmory.armorFabricationCost(
                        armor.get(squad).issue(billet));
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static EquipmentGrade debugGrade(DebugBilletPlan plan, String primaryId,
                                              boolean designatedMarksman) {
        if (WeaponRegistry.STARTER_PRIMARY_ID.equals(primaryId)) {
            return EquipmentGrade.SERVICE;
        }
        if (plan == DebugBilletPlan.HARDENED) {
            return designatedMarksman ? EquipmentGrade.MASTERWORK : EquipmentGrade.MILSPEC;
        }
        if (plan == DebugBilletPlan.SEASONED && designatedMarksman) {
            return EquipmentGrade.MILSPEC;
        }
        return EquipmentGrade.SERVICE;
    }

    private static List<SquadWeaponDoctrine> randomizedWeaponDoctrines(
            int count, Random rng) {
        return shuffledBatches(SquadEquipmentDoctrines.weaponDoctrines(), count, rng);
    }

    private static List<SquadArmorDoctrine> randomizedArmorDoctrines(
            int count, Random rng) {
        return shuffledBatches(SquadEquipmentDoctrines.armorDoctrines(), count, rng);
    }

    /** A shuffle bag gives small debug companies variety without forbidding repeats at scale. */
    private static <T> List<T> shuffledBatches(List<T> catalog, int count, Random rng) {
        List<T> result = new ArrayList<>(Math.max(0, count));
        while (result.size() < count) {
            List<T> batch = new ArrayList<>(catalog);
            Collections.shuffle(batch, rng);
            int remaining = count - result.size();
            result.addAll(batch.subList(0, Math.min(remaining, batch.size())));
        }
        return result;
    }

    /**
     * Prints until the armory owns {@code additional} more than it did on
     * entry. Bails on a refused print rather than spinning — a refusal means
     * the recipe is locked or the budget is gone, both of which are bugs here
     * and neither of which a loop can fix.
     */
    private static void printUpTo(Owned owned, Print print, int additional) {
        int target = owned.count() + Math.max(0, additional);
        while (owned.count() < target) {
            if (!print.once()) return;
        }
    }

    @FunctionalInterface
    private interface Owned { int count(); }

    @FunctionalInterface
    private interface Print { boolean once(); }
}
