package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.ExperienceTier;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.infantry.SoldierAptitude;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.Random;

/** Deterministic doctrine rolls for family, equipment quality, and personnel. */
public final class InfantryLoadoutRolls {

    private InfantryLoadoutRolls() {}

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

    public static MarineWeapon defenderPrimary(UnitType type, Random rng) {
        int r = rng.nextInt(100);
        if (type == UnitType.MILITIA) {
            if (r < 40) return MarineWeapon.SMG;
            if (r < 50) return MarineWeapon.DMR;
            return MarineWeapon.PULSE_RIFLE;
        }
        if (r < 20) return MarineWeapon.SMG;
        if (r < 45) return MarineWeapon.DMR;
        return MarineWeapon.PULSE_RIFLE;
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
