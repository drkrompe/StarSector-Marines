package com.dillon.starsectormarines;

/**
 * Dev-build toggles. Edit constants here for local testing; flip back to
 * {@code false} before shipping or sharing a build with someone who's going
 * to playtest the intended experience.
 *
 * <p>Kept as plain {@code public static final} booleans so the JIT can
 * dead-code the disabled branches at runtime — no perf cost when off.
 */
public final class DevConfig {

    /**
     * When {@code true}: bypass mission transport gating and let the employer
     * shoulder the full {@code requiredDrops} via cycling Aeroshuttles. Lets
     * us playtest any mission without curating a player fleet — every drop
     * arrives via employer transports, all of them cycling, so wave behavior
     * is exercised end-to-end without owning a single Valkyrie.
     *
     * <p>Production behavior: employer is capped by
     * {@code MissionGenerator.employerCoverageCap} (3/4/5 by risk), forcing
     * the player to supply transports for the bulk of any non-trivial
     * mission.
     */
    public static final boolean UNLIMITED_TRANSPORT = true;

    private DevConfig() {}
}
