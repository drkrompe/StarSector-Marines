package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.marine.MarineArmory;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadArmorPlan;
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
 * <p>A consequence worth keeping: the armory templates are collected
 * <em>first</em>, then each authored twelve-billet doctrine is issued as one
 * atomic squad operation. A loadout the ordinary armory would refuse is a
 * loadout this cannot produce; the fixture does not fabricate and reallocate
 * 2,400 individual inventory records to express 200 squad loadouts.
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
        List<SquadArmorPlan> armorPlans = randomizedArmorPlans(count, rng);
        MarineRoster roster = new MarineRoster();
        stockArmory(roster.armory(), resolved, weapons);
        stockArmor(roster.armory(), resolved);
        for (int s = 0; s < count; s++) {
            MarineSquad squad = roster.createSquad();
            SquadWeaponDoctrine weaponDoctrine = weapons.get(s);
            SquadArmorPlan armorPlan = armorPlans.get(s);
            List<MarineSoldier> recruits =
                    roster.recruitToSquad(squad.id(), MarineSquad.CAPACITY);
            if (recruits.size() != MarineSquad.CAPACITY) {
                throw new IllegalStateException("Debug squad complement was not filled");
            }
            SquadEquipmentResult result = roster.applySquadEquipmentVariant(
                    squad.id(), weaponDoctrine.id(), armorPlan.id(),
                    debugGrades(weaponDoctrine, resolved.plan));
            if (result != SquadEquipmentResult.APPLIED) {
                throw new IllegalStateException("Debug squad loadout refused: " + result);
            }
        }
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

    /** Exact grade overlay applied with the doctrine's three ordered fire teams. */
    private static List<EquipmentGrade> debugGrades(
            SquadWeaponDoctrine doctrine, DebugBilletPlan plan) {
        List<EquipmentGrade> grades = new ArrayList<>(MarineSquad.CAPACITY);
        boolean marksmanIssued = false;
        for (int billet = 0; billet < MarineSquad.CAPACITY; billet++) {
            String primaryId = doctrine.issue(billet).primaryId();
            boolean designatedMarksman = !marksmanIssued
                    && WeaponRegistry.DMR_ID.equals(primaryId);
            grades.add(debugGrade(plan, primaryId, designatedMarksman));
            marksmanIssued |= designatedMarksman;
        }
        return List.copyOf(grades);
    }

    /**
     * Collects the templates referenced by the randomized doctrines. The
     * detached fixture then uses the headless unlimited-cargo issue seam; it
     * does not mint legacy printed-inventory entries per marine.
     */
    private static void stockArmory(MarineArmory armory, DebugCompanyStage stage,
                                    List<SquadWeaponDoctrine> weapons) {
        for (int squad = 0; squad < weapons.size(); squad++) {
            SquadWeaponDoctrine weaponDoctrine = weapons.get(squad);
            List<EquipmentGrade> grades = debugGrades(weaponDoctrine, stage.plan);
            for (int billet = 0; billet < MarineSquad.CAPACITY; billet++) {
                SquadWeaponIssue issue = weaponDoctrine.issue(billet);
                String primary = issue.primaryId();
                armory.unlockPrimary(primary, grades.get(billet));
                String secondary = issue.specialEquipmentId();
                if (secondary != null) {
                    armory.unlockSecondary(secondary);
                }
            }
        }
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

    /**
     * Armour decides the experience band, so the draw is bounded by what the
     * stage has collected ({@code progression-nouns.md}). Flavour stays
     * randomized — which faction's kit at that ceiling — while the stage keeps
     * meaning what it says about company quality.
     */
    private static List<SquadArmorPlan> randomizedArmorPlans(
            int count, Random rng) {
        List<SquadArmorPlan> plans = SquadEquipmentDoctrines.armorPlans();
        if (plans.isEmpty()) {
            throw new IllegalStateException("No authored armour plans");
        }
        return shuffledBatches(plans, count, rng);
    }

    /**
     * What this company has collected: every pattern the catalog offers at or
     * below the stage's ceiling.
     *
     * <p>This is the access half of {@code role-and-access.md}, and it is the
     * only place the stage's tier appears. A plan is admissible at every stage —
     * a poor company runs the same sections as a rich one — and the ceiling
     * decides what those sections are wearing rather than which sections exist.
     * The rule it replaced admitted only doctrines whose <em>best</em> billet
     * matched the ceiling exactly, which is why a Hardened company could field
     * nothing but assault suits.
     */
    private static void stockArmor(MarineArmory armory, DebugCompanyStage stage) {
        MarineArmorCatalogRegistry catalog = MarineArmorCatalogRegistry.installed();
        if (catalog == null) return;
        for (MarineArmorCatalogDef pattern : catalog.all()) {
            if (pattern.tier() <= stage.plan.maxArmorTier()) armory.unlockArmor(pattern.id());
        }
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

}
