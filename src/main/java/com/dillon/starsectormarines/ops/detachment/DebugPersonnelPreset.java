package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.ExperienceTier;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.infantry.SoldierAptitude;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.unit.UnitRole;

/** Explicit, non-persistent personnel fixtures for debug-picker missions. */
public enum DebugPersonnelPreset {
    RECRUITS("Recruits"),
    MIXED("Mixed"),
    VETERANS("Veterans");

    public final String displayName;

    DebugPersonnelPreset(String displayName) {
        this.displayName = displayName;
    }

    public DebugPersonnelPreset next() {
        DebugPersonnelPreset[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    MarineLoadout loadout(int seat) {
        return switch (this) {
            case RECRUITS -> loadout(MarineWeapon.PULSE_RIFLE,
                    EquipmentGrade.SURPLUS, SoldierAptitude.LIMITED,
                    ExperienceTier.GREEN, null, LayeredArmorFamily.ARMORLESS);
            case MIXED -> mixed(seat);
            case VETERANS -> loadout(seat % 3 == 0
                            ? MarineWeapon.DMR : MarineWeapon.PULSE_RIFLE,
                    seat % 4 == 0 ? EquipmentGrade.MASTERWORK : EquipmentGrade.MILSPEC,
                    SoldierAptitude.EXCEPTIONAL, ExperienceTier.ELITE,
                    seat % 4 == 0 ? MarineSecondary.ROCKET_LAUNCHER : null,
                    LayeredArmorFamily.RED_ELITE);
        };
    }

    private static MarineLoadout mixed(int seat) {
        return switch (Math.floorMod(seat, 3)) {
            case 0 -> loadout(MarineWeapon.PULSE_RIFLE,
                    EquipmentGrade.SERVICE, SoldierAptitude.STEADY,
                    ExperienceTier.REGULAR, null, LayeredArmorFamily.CHARCOAL);
            case 1 -> loadout(MarineWeapon.SMG,
                    EquipmentGrade.SERVICE, SoldierAptitude.GIFTED,
                    ExperienceTier.REGULAR, null, LayeredArmorFamily.BLUE_SCOUT);
            default -> loadout(MarineWeapon.DMR,
                    EquipmentGrade.MILSPEC, SoldierAptitude.STEADY,
                    ExperienceTier.VETERAN, MarineSecondary.ROCKET_LAUNCHER,
                    LayeredArmorFamily.ARMY_GREEN);
        };
    }

    private static MarineLoadout loadout(MarineWeapon primary,
                                         EquipmentGrade grade,
                                         SoldierAptitude aptitude,
                                         ExperienceTier experience,
                                         MarineSecondary secondary,
                                         LayeredArmorFamily armor) {
        return new MarineLoadout(UnitRole.COMBATANT, null, primary, grade,
                new SoldierProfile(aptitude, experience.minimumXp), secondary,
                secondary != null ? secondary.startingAmmo : 0, null, armor);
    }
}
