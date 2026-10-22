package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.i18n.Strings;

import java.util.Collection;
import java.util.Map;

/**
 * Display names of the billet roles the built-in definitions issue.
 *
 * <p>A role is saved as its English literal ({@link SquadWeaponIssue#role()},
 * {@link FireTeamBillet#name()}), because it is copied into player-owned definitions
 * and saves. It is translated when shown, and only while it still equals one of the
 * built-in roles: a role an addon or a player wrote reads exactly as written.
 */
public final class BilletRoles {

    public static final String TEAM_LEADER = "Team Leader";
    public static final String RIFLEMAN = "Rifleman";

    private static final Map<String, String> KEYS = Map.ofEntries(
            Map.entry(TEAM_LEADER, "billetRoleTeamLeader"),
            Map.entry(RIFLEMAN, "billetRoleRifleman"),
            Map.entry("Security Rifleman", "billetRoleSecurityRifleman"),
            Map.entry("Assault Leader", "billetRoleAssaultLeader"),
            Map.entry("Breacher", "billetRoleBreacher"),
            Map.entry("Grenadier", "billetRoleGrenadier"),
            Map.entry("Anti-Armor", "billetRoleAntiArmor"),
            Map.entry("Marksman", "billetRoleMarksman"),
            Map.entry("Heavy Marksman", "billetRoleHeavyMarksman"),
            Map.entry("Automatic Rifleman", "billetRoleAutomaticRifleman"),
            // The first refit's plans saved these spellings before they were title-cased.
            Map.entry("Automatic rifleman", "billetRoleAutomaticRifleman"),
            Map.entry("Pulse Rifleman", "billetRolePulseRifleman"),
            Map.entry("Pulse rifleman", "billetRolePulseRifleman"),
            Map.entry("Sapper", "billetRoleSapper"),
            Map.entry("Point Defence", "billetRolePointDefence"),
            Map.entry("Interceptor", "billetRoleInterceptor"),
            Map.entry("Scout", "billetRoleScout"),
            Map.entry("Spotter", "billetRoleSpotter"),
            Map.entry("Security", "billetRoleSecurity"),
            Map.entry("Demolitions", "billetRoleDemolitions"),
            Map.entry("Marine", "billetRoleMarine"));

    private BilletRoles() {}

    /** The reader's name for {@code role}; anything not built in passes through unchanged. */
    public static String label(String role) {
        String key = role == null ? null : KEYS.get(role);
        if (key == null) return role;
        String text = Strings.get(key);
        return text.equals(key) ? role : text;
    }

    /** Every catalog key a built-in role reads through, for the catalog's own checks. */
    public static Collection<String> keys() {
        return KEYS.values();
    }
}
