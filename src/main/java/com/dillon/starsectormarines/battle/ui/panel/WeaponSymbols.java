package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;

import java.awt.Color;

/**
 * 3-letter HUD abbreviations for a marine's gear, plus their tracer color.
 * Centralized so future weapons land in one file and i18n (if we ever localize
 * the HUD readout strings) has a single hook.
 */
public final class WeaponSymbols {

    public static final Color DEFAULT_FG     = new Color(0xC8, 0xE0, 0xFF);
    public static final Color SECONDARY_FG   = new Color(0xFF, 0x9A, 0x40);
    public static final Color ROLE_BADGE_FG  = new Color(0xFF, 0xD0, 0x70);
    public static final Color SECONDARY_EMPTY = new Color(0x66, 0x50, 0x40);

    private WeaponSymbols() {}

    public static String primaryAbbrev(MarineWeapon w) {
        if (w == null) return "RIF";
        switch (w) {
            case FIELD_RIFLE: return "FLD";
            case PULSE_RIFLE: return "RIF";
            case SMG:         return "SHD";
            case SQUAD_AUTOMATIC: return "SAW";
            case DMR:         return "DMR";
            default:          return w.name().substring(0, 3);
        }
    }

    public static String primaryAbbrev(MarineWeapon w, EquipmentGrade grade) {
        EquipmentGrade resolved = grade != null ? grade : EquipmentGrade.SERVICE;
        return primaryAbbrev(w) + "-" + resolved.tierMark();
    }

    public static Color primaryColor(MarineWeapon w) {
        return w != null ? w.tracerColor() : DEFAULT_FG;
    }

    public static String secondaryAbbrev(MarineSecondary s) {
        return specialAbbrev(s != null ? s.specialDef() : null);
    }

    public static String specialAbbrev(SpecialEquipmentDef special) {
        if (special == null) return null;
        if (SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID.equals(special.id())) return "RKT";
        if (SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID.equals(special.id())) return "AMR";
        if (SpecialEquipmentRegistry.SMOKE_GRENADE_ID.equals(special.id())) return "SMK";
        if (SpecialEquipmentRegistry.SATCHEL_CHARGE_ID.equals(special.id())) return "SAT";
        if (SpecialEquipmentRegistry.FRAG_GRENADE_ID.equals(special.id())) return "FRG";
        String compact = special.displayName().replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        return compact.substring(0, Math.min(3, compact.length()));
    }

    /** Returns null for COMBATANT (no badge) so callers can skip the draw cheaply. */
    public static String roleBadge(UnitRole role) {
        if (role == null) return null;
        switch (role) {
            case PLANTER:       return "P";
            case KIT_RETRIEVER: return "K";
            case VIP:           return "V";
            default:            return null;
        }
    }
}
