package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.marine.MarineArmorPattern;
import com.dillon.starsectormarines.marine.MarineArmory;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSquad;

import java.util.ArrayList;
import java.util.List;

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
        DebugCompanyStage resolved = stage != null ? stage : DebugCompanyStage.FIRST_CONTRACT;
        int count = normalizeSquads(squads);
        MarineRoster roster = new MarineRoster();
        stockArmory(roster.armory(), resolved, count);
        for (int s = 0; s < count; s++) {
            MarineSquad squad = roster.createSquad();
            for (int billet = 0; billet < MarineSquad.CAPACITY; billet++) {
                MarineSoldier recruit = roster.recruitToSquad(squad.id());
                if (recruit == null) break;
                outfit(roster, resolved, recruit, billet);
            }
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

    /** Applies the stage's billet plan to one marine. */
    private static void outfit(MarineRoster roster, DebugCompanyStage stage,
                               MarineSoldier soldier, int billet) {
        DebugBilletPlan plan = stage.plan;
        soldier.addExperience(plan.experienceXp(billet));
        String primary = plan.primaryId(billet);
        EquipmentGrade grade = plan.grade(billet);
        if (primary != null && grade != null) {
            roster.allocatePrimary(soldier.id(), primary, grade);
        }
        String secondary = plan.specialEquipmentId(billet);
        if (secondary != null) roster.allocateSecondary(soldier.id(), secondary);
        MarineArmorPattern armor = plan.armor(billet);
        if (armor != null) roster.allocateArmor(soldier.id(), armor);
    }

    /**
     * Unlocks and prints everything the stage's billet plan will ask for,
     * once per squad. Allocation is inventory-checked, so under-stocking here
     * shows up as marines quietly holding the starter rifle rather than as a
     * failure — hence printing against the plan rather than a guessed number.
     */
    private static void stockArmory(MarineArmory armory, DebugCompanyStage stage, int squads) {
        armory.addFabricationMaterials(fabricationBudget(stage, squads));
        DebugBilletPlan plan = stage.plan;
        for (int billet = 0; billet < MarineSquad.CAPACITY; billet++) {
            String primary = plan.primaryId(billet);
            EquipmentGrade grade = plan.grade(billet);
            if (primary != null && grade != null) {
                armory.unlockPrimary(primary, grade);
                printUpTo(() -> armory.ownedPrimary(primary, grade),
                        () -> armory.printPrimary(primary, grade), squads);
            }
            String secondary = plan.specialEquipmentId(billet);
            if (secondary != null) {
                armory.unlockSecondary(secondary);
                printUpTo(() -> armory.ownedSecondary(secondary),
                        () -> armory.printSecondary(secondary), squads);
            }
            MarineArmorPattern armor = plan.armor(billet);
            if (armor != null) {
                armory.unlockArmor(armor);
                printUpTo(() -> armory.ownedArmor(armor),
                        () -> armory.printArmor(armor), squads);
            }
        }
    }

    /** Funds the requested fixture scale, saturating only at armory's int storage limit. */
    private static int fabricationBudget(DebugCompanyStage stage, int squads) {
        long perSquad = 0L;
        for (int billet = 0; billet < MarineSquad.CAPACITY; billet++) {
            EquipmentGrade grade = stage.plan.grade(billet);
            if (stage.plan.primaryId(billet) != null && grade != null) {
                perSquad += MarineArmory.primaryFabricationCost(grade);
            }
            String secondary = stage.plan.specialEquipmentId(billet);
            if (secondary != null) {
                perSquad += MarineArmory.secondaryFabricationCost(secondary);
            }
            MarineArmorPattern armor = stage.plan.armor(billet);
            if (armor != null) {
                perSquad += MarineArmory.armorFabricationCost(armor);
            }
        }
        return (int) Math.min(Integer.MAX_VALUE,
                perSquad * Math.max(0L, squads));
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
