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

    /**
     * FBO pixel resolution per nav-grid cell for the decal accumulator.
     * 32 = native (matches the 32px source sheets, no downsample at neutral
     * zoom). Drop to 16 for ~¼ VRAM at the cost of visible softness; raise
     * to 64 for sharp decals at max zoom at ×4 VRAM. On a 100×100 grid:
     * 16 → ~10 MB, 32 → ~40 MB, 64 → ~160 MB.
     *
     * <p>Promote to a real player-facing setting (mod_info.json options /
     * settings.json reader) once we have a settings UI to hang it off of.
     */
    public static final int DECAL_FBO_PX_PER_CELL = 32;

    /**
     * When {@code true}: every employer-supplied transport flies as a Valkyrie
     * instead of an Aeroshuttle. Pairs naturally with {@link #UNLIMITED_TRANSPORT}
     * — flipping both on guarantees a battlefield full of armed Valkyries with
     * the full A2G turret kit (Arbalests + Grenade Launcher), so any new
     * shuttle-mount behavior gets exercised without needing to field a player
     * Valkyrie yourself.
     *
     * <p>Production behavior: employer transports default to Aeroshuttles
     * (unarmed nimble drop craft), matching the "token force" flavor — the
     * player supplies the heavy lift.
     */
    public static final boolean FORCE_EMPLOYER_VALKYRIE = true;

    /**
     * When {@code > 0}: overrides {@link com.dillon.starsectormarines.ops.Mission#requiredDrops}
     * for every generated mission to this value. Clamps both the mission's
     * authored drop count and the employer's coverage roll, so the briefing
     * and battlefield both see the override. {@code 0} disables the override
     * and the per-(type, risk) table in {@code MissionGenerator.requiredDropsFor}
     * drives drops as usual.
     *
     * <p>Useful for iterating on shuttle / drop-flow behavior without sitting
     * through a CONQUEST-HIGH 40-drop wave to test one thing.
     */
    public static final int DROP_COUNT_OVERRIDE = 40;

    private DevConfig() {}
}
