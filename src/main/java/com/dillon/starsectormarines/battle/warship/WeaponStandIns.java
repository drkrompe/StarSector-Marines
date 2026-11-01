package com.dillon.starsectormarines.battle.warship;

import org.json.JSONObject;

import java.util.Locale;
import java.util.Map;

/**
 * The base-game weapon a modded one fires as (owner, 2026-11-01). A modded weapon may carry
 * scripted effects, files laid out its own way or sprites the battle cannot size, so rather than
 * fire it the battle fits the base-game weapon of the same role in its place, look and all: what a
 * player fits from another mod still arms the hull, and only weapons this mod has seen work fly.
 *
 * <p>The role is read from the modded weapon's own data: its mount category and size
 * ({@code .wpn} {@code type} and {@code size}), whether it is a beam, a missile or a gun, whether
 * it is point defence, its damage type and its burst. Stand-ins are drawn from the fits this mod
 * already flies (the Onslaught, the Paragon, the Wolf) where one of those covers the role.
 */
public final class WeaponStandIns {

    private WeaponStandIns() {}

    /** How a weapon delivers: a held beam, a guided or unguided missile, or anything else a barrel throws. */
    enum Form { GUN, BEAM, MISSILE }

    /** A weapon's role, as far as choosing its stand-in needs it. */
    record Role(String category, String size, Form form, boolean pointDefence, String damageType, int burst) {}

    /**
     * The base-game weapon id a modded weapon fires as, from its table row and its {@code .wpn}
     * and projectile specs (either spec may be null); null for a mount that fires nothing
     * (a decoration, a launch bay, a ship system).
     */
    public static String standInFor(Map<String, String> row, JSONObject wpn, JSONObject proj) {
        Role role = role(row, wpn, proj);
        return role == null ? null : standIn(role);
    }

    /** What {@link #standInFor} reads off the data; null for a mount that fires nothing. */
    static Role role(Map<String, String> row, JSONObject wpn, JSONObject proj) {
        Form form = wpn != null && "beam".equals(wpn.optString("specClass")) ? Form.BEAM
                : proj != null && "missile".equals(proj.optString("specClass")) ? Form.MISSILE
                : Form.GUN;
        String damage = upper(row.get("type"));
        String hints = row.getOrDefault("hints", "");
        boolean pd = hints.contains("PD") && !hints.contains("PD_ALSO");
        int burst = Math.max(1, (int) VanillaWeapon.number(row, "burst size", 1f));
        String size = upper(wpn == null ? null : wpn.optString("size", null));
        if (!size.equals("SMALL") && !size.equals("LARGE")) size = "MEDIUM";
        String category = upper(wpn == null ? null : wpn.optString("type", null));
        switch (category) {
            case "BALLISTIC", "ENERGY", "MISSILE" -> { }
            case "DECORATIVE", "LAUNCH_BAY", "SYSTEM", "STATION_MODULE" -> { return null; }
            // A hybrid, universal or built-in mount fires as what its weapon is.
            default -> category = form == Form.MISSILE ? "MISSILE"
                    : form == Form.BEAM || damage.equals("ENERGY") ? "ENERGY" : "BALLISTIC";
        }
        return new Role(category, size, form, pd, damage, burst);
    }

    /** The stand-in for a role: a base-game weapon of the same category and size. */
    static String standIn(Role r) {
        return switch (r.category()) {
            case "MISSILE" -> missile(r);
            case "ENERGY" -> r.form() == Form.BEAM ? beam(r) : energyGun(r);
            default -> ballistic(r);
        };
    }

    private static String ballistic(Role r) {
        return switch (r.size()) {
            case "SMALL" -> r.pointDefence() || r.damageType().equals("FRAGMENTATION") ? "vulcan"
                    : r.damageType().equals("HIGH_EXPLOSIVE") ? "lightmortar" : "lightac";
            case "LARGE" -> r.pointDefence() || r.damageType().equals("FRAGMENTATION") ? "devastator"
                    : r.damageType().equals("KINETIC") ? "gauss" : "hellbore";
            default -> r.pointDefence() ? "flak"
                    : r.damageType().equals("KINETIC") ? "heavyac" : "heavymauler";
        };
    }

    private static String beam(Role r) {
        return switch (r.size()) {
            case "SMALL" -> r.pointDefence() ? "pdlaser" : "taclaser";
            case "LARGE" -> r.pointDefence() ? "guardian"
                    : r.damageType().equals("HIGH_EXPLOSIVE") ? "hil" : "tachyonlance";
            default -> r.pointDefence() ? "heavyburst"
                    : r.damageType().equals("KINETIC") ? "gravitonbeam" : "phasebeam";
        };
    }

    private static String energyGun(Role r) {
        return switch (r.size()) {
            // A small point-defence gun keeps its job as the small PD laser.
            case "SMALL" -> r.pointDefence() ? "pdlaser" : "irpulse";
            case "LARGE" -> r.pointDefence() ? "guardian" : "autopulse";
            default -> r.pointDefence() ? "heavyburst" : "heavyblaster";
        };
    }

    private static String missile(Role r) {
        boolean rack = r.burst() >= 3;
        return switch (r.size()) {
            case "SMALL" -> r.damageType().equals("KINETIC") ? "sabot" : rack ? "annihilator" : "harpoon";
            case "LARGE" -> r.damageType().equals("KINETIC") ? "squall"
                    : r.damageType().equals("FRAGMENTATION") ? "pilum_large" : "cyclone";
            default -> r.damageType().equals("KINETIC") ? "sabotpod"
                    : r.damageType().equals("FRAGMENTATION") ? "pilum"
                    : rack ? "annihilatorpod" : "harpoonpod";
        };
    }

    private static String upper(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }
}
