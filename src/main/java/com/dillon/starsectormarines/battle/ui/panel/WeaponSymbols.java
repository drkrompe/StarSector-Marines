package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.MarineSecondary;
import com.dillon.starsectormarines.battle.MarineWeapon;
import com.dillon.starsectormarines.battle.UnitRole;

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
            case PULSE_RIFLE: return "RIF";
            case SMG:         return "SMG";
            case DMR:         return "DMR";
            default:          return w.name().substring(0, 3);
        }
    }

    public static Color primaryColor(MarineWeapon w) {
        return w != null ? w.tracerColor : DEFAULT_FG;
    }

    public static String secondaryAbbrev(MarineSecondary s) {
        if (s == null) return null;
        if (s == MarineSecondary.ROCKET_LAUNCHER) return "RKT";
        return s.name().substring(0, 3);
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
