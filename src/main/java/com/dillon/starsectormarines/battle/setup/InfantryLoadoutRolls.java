package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.ExperienceTier;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.infantry.SoldierAptitude;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineArmorPattern;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;

import java.util.Random;

/** Deterministic doctrine rolls for family, equipment quality, and personnel. */
public final class InfantryLoadoutRolls {

    private InfantryLoadoutRolls() {}

    /**
     * Builds the standard player-shuttle squad: randomized primary family,
     * equipment grade, and soldier profile, plus one launcher specialist when
     * the carrier has room. Shared by mission-start shuttles and the live
     * Marine Drop power so neither path falls back to an untagged line weapon.
     */
    public static MarineLoadout[] playerSquad(int capacity, Random rng) {
        MarineLoadout[] roster = new MarineLoadout[Math.max(0, capacity)];
        int rocketSlot = capacity > 1 ? capacity - 1 : -1;
        for (int i = 0; i < roster.length; i++) {
            WeaponDef primary = playerPrimary(rng);
            EquipmentGrade grade = playerEquipmentGrade(rng);
            SoldierProfile profile = playerProfile(rng);
            SpecialEquipmentDef secondary = i == rocketSlot
                    ? SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID)
                    : null;
            int ammo = secondary != null ? secondary.startingAmmo() : 0;
            roster[i] = new MarineLoadout(UnitRole.COMBATANT, null,
                    primary.id, grade, profile,
                    secondary != null ? secondary.id() : null, ammo);
        }
        return roster;
    }

    /** Builds a local/defender fireteam; elite high-risk regulars field one AMR carrier. */
    public static MarineLoadout[] defenderSquad(int capacity, UnitType type,
                                                 RiskLevel risk, Random rng) {
        MarineLoadout[] roster = new MarineLoadout[Math.max(0, capacity)];
        RiskLevel resolvedRisk = risk != null ? risk : RiskLevel.LOW;
        int amrSlot = resolvedRisk == RiskLevel.HIGH && type != UnitType.MILITIA
                && capacity >= 4 ? capacity - 1 : -1;
        int smokeSlot = resolvedRisk != RiskLevel.LOW && type != UnitType.MILITIA
                && capacity >= 4 ? capacity - 2 : -1;
        for (int i = 0; i < roster.length; i++) {
            SpecialEquipmentDef special = i == amrSlot
                    ? SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID)
                    : i == smokeSlot
                    ? SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SMOKE_GRENADE_ID)
                    : null;
            MarineArmorPattern armor = defenderArmor(type, resolvedRisk, rng);
            roster[i] = MarineLoadout.fromCatalog(UnitRole.COMBATANT, null,
                    defenderPrimary(type, rng),
                    defenderEquipmentGrade(type, resolvedRisk, rng),
                    defenderProfile(type, resolvedRisk, rng), special, null,
                    armor.layeredFamily(), armor.armorCapacity, armor.armorRating,
                    armor.moveSpeedMult, armor.incomingAccuracyMult);
        }
        return roster;
    }

    /**
     * Builds one defender from the battle-frozen faction profile. Risk still
     * owns eligibility/quality and the existing personnel curve; faction data
     * chooses the equipment identities within those authored bands.
     */
    public static MarineLoadout defenderLoadout(GroundRosterProfile roster,
                                                GroundRosterProfile.ForceTier tier,
                                                RiskLevel risk, Random rng) {
        GroundRosterProfile.Issue issue = roster.issue(tier);
        SpecialEquipmentDef special = issue.pickSpecialDef(risk, rng);
        MarineArmorCatalogDef armor = issue.pickArmorDef(risk, rng);
        return MarineLoadout.fromCatalog(UnitRole.COMBATANT, null,
                issue.pickPrimaryDef(rng), issue.pickGrade(risk, rng),
                defenderProfile(issue.unitType(), risk != null ? risk : RiskLevel.LOW, rng),
                special, null, armor.appearanceFamily(), armor.armorCapacity(), armor.armorRating(),
                armor.moveSpeedMult(), armor.incomingAccuracyMult());
    }

    /** One delivery manifest built from the same frozen profile as initial defenders. */
    public static MarineLoadout[] defenderSquad(int capacity, GroundRosterProfile roster,
                                                GroundRosterProfile.ForceTier tier,
                                                RiskLevel risk, Random rng) {
        MarineLoadout[] result = new MarineLoadout[Math.max(0, capacity)];
        for (int i = 0; i < result.length; i++) {
            result[i] = defenderLoadout(roster, tier, risk, rng);
        }
        return result;
    }

    /** Generic fallback armor for legacy/headless battles without a frozen faction roster. */
    public static MarineArmorPattern defenderArmor(
            UnitType type, RiskLevel risk, Random rng) {
        RiskLevel resolvedRisk = risk != null ? risk : RiskLevel.LOW;
        int roll = rng.nextInt(100);
        if (type == UnitType.MILITIA) {
            int fatigues = resolvedRisk == RiskLevel.LOW ? 35
                    : resolvedRisk == RiskLevel.MEDIUM ? 20 : 10;
            int security = resolvedRisk == RiskLevel.LOW ? 85
                    : resolvedRisk == RiskLevel.MEDIUM ? 75 : 55;
            if (roll < fatigues) return MarineArmorPattern.ARMORLESS;
            if (roll < security) return MarineArmorPattern.MILITIA;
            return resolvedRisk == RiskLevel.HIGH
                    ? MarineArmorPattern.ARMY_GREEN : MarineArmorPattern.CHARCOAL;
        }
        if (resolvedRisk == RiskLevel.LOW) {
            if (roll < 30) return MarineArmorPattern.MILITIA;
            if (roll < 85) return MarineArmorPattern.CHARCOAL;
            return MarineArmorPattern.ARMY_GREEN;
        }
        if (resolvedRisk == RiskLevel.MEDIUM) {
            if (roll < 15) return MarineArmorPattern.MILITIA;
            if (roll < 70) return MarineArmorPattern.CHARCOAL;
            if (roll < 90) return MarineArmorPattern.ARMY_GREEN;
            return MarineArmorPattern.RED_ELITE;
        }
        if (roll < 40) return MarineArmorPattern.CHARCOAL;
        if (roll < 70) return MarineArmorPattern.ARMY_GREEN;
        return MarineArmorPattern.RED_ELITE;
    }

    /** Weighted player primary roll: pulse workhorse, evenly split specialist slots. */
    public static WeaponDef playerPrimary(Random rng) {
        int r = rng.nextInt(4);
        if (r == 0) return WeaponRegistry.require(WeaponRegistry.SMG_ID);
        if (r == 1) return WeaponRegistry.require(WeaponRegistry.DMR_ID);
        return WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID);
    }

    public static EquipmentGrade playerEquipmentGrade(Random rng) {
        int r = rng.nextInt(100);
        if (r < 5) return EquipmentGrade.MASTERWORK;
        if (r < 35) return EquipmentGrade.MILSPEC;
        return EquipmentGrade.SERVICE;
    }

    public static SoldierProfile playerProfile(Random rng) {
        SoldierAptitude aptitude = aptitude(rng, 8, 62, 27);
        ExperienceTier experience = experience(rng, 10, 70, 18);
        return profileAtTier(aptitude, experience, rng);
    }

    public static WeaponDef defenderPrimary(UnitType type, Random rng) {
        int r = rng.nextInt(100);
        if (type == UnitType.MILITIA) {
            if (r < 40) return WeaponRegistry.require(WeaponRegistry.SMG_ID);
            if (r < 50) return WeaponRegistry.require(WeaponRegistry.DMR_ID);
            return WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID);
        }
        if (r < 20) return WeaponRegistry.require(WeaponRegistry.SMG_ID);
        if (r < 45) return WeaponRegistry.require(WeaponRegistry.DMR_ID);
        return WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID);
    }

    public static EquipmentGrade defenderEquipmentGrade(UnitType type, RiskLevel risk,
                                                          Random rng) {
        int r = rng.nextInt(100);
        boolean militia = type == UnitType.MILITIA;
        if (militia) {
            return switch (risk) {
                case LOW -> r < 75 ? EquipmentGrade.SURPLUS : EquipmentGrade.SERVICE;
                case MEDIUM -> r < 60 ? EquipmentGrade.SURPLUS
                        : r < 95 ? EquipmentGrade.SERVICE : EquipmentGrade.MILSPEC;
                case HIGH -> r < 40 ? EquipmentGrade.SURPLUS
                        : r < 85 ? EquipmentGrade.SERVICE
                        : r < 99 ? EquipmentGrade.MILSPEC : EquipmentGrade.MASTERWORK;
            };
        }
        return switch (risk) {
            case LOW -> r < 25 ? EquipmentGrade.SURPLUS
                    : r < 90 ? EquipmentGrade.SERVICE : EquipmentGrade.MILSPEC;
            case MEDIUM -> r < 10 ? EquipmentGrade.SURPLUS
                    : r < 75 ? EquipmentGrade.SERVICE
                    : r < 98 ? EquipmentGrade.MILSPEC : EquipmentGrade.MASTERWORK;
            case HIGH -> r < 5 ? EquipmentGrade.SURPLUS
                    : r < 55 ? EquipmentGrade.SERVICE
                    : r < 95 ? EquipmentGrade.MILSPEC : EquipmentGrade.MASTERWORK;
        };
    }

    public static SoldierProfile defenderProfile(UnitType type, RiskLevel risk, Random rng) {
        boolean militia = type == UnitType.MILITIA;
        SoldierAptitude aptitude = militia
                ? aptitude(rng, 25, 65, 9)
                : aptitude(rng, 8, 62, 27);
        ExperienceTier experience;
        if (militia) {
            experience = switch (risk) {
                case LOW -> experience(rng, 75, 24, 1);
                case MEDIUM -> experience(rng, 55, 38, 7);
                case HIGH -> experience(rng, 35, 48, 15);
            };
        } else {
            experience = switch (risk) {
                case LOW -> experience(rng, 25, 60, 14);
                case MEDIUM -> experience(rng, 12, 58, 27);
                case HIGH -> experience(rng, 5, 43, 43);
            };
        }
        return profileAtTier(aptitude, experience, rng);
    }

    private static SoldierAptitude aptitude(Random rng, int limited, int steady, int gifted) {
        int r = rng.nextInt(100);
        if (r < limited) return SoldierAptitude.LIMITED;
        if (r < limited + steady) return SoldierAptitude.STEADY;
        if (r < limited + steady + gifted) return SoldierAptitude.GIFTED;
        return SoldierAptitude.EXCEPTIONAL;
    }

    private static ExperienceTier experience(Random rng, int green, int regular, int veteran) {
        int r = rng.nextInt(100);
        if (r < green) return ExperienceTier.GREEN;
        if (r < green + regular) return ExperienceTier.REGULAR;
        if (r < green + regular + veteran) return ExperienceTier.VETERAN;
        return ExperienceTier.ELITE;
    }

    private static SoldierProfile profileAtTier(SoldierAptitude aptitude,
                                                 ExperienceTier tier, Random rng) {
        int nextMin = switch (tier) {
            case GREEN -> ExperienceTier.REGULAR.minimumXp;
            case REGULAR -> ExperienceTier.VETERAN.minimumXp;
            case VETERAN -> ExperienceTier.ELITE.minimumXp;
            case ELITE -> ExperienceTier.ELITE.minimumXp + 400;
        };
        int xp = tier.minimumXp + rng.nextInt(Math.max(1, nextMin - tier.minimumXp));
        return new SoldierProfile(aptitude, xp);
    }
}
