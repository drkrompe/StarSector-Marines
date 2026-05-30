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
     * Soft cap on the persistent decal source list (bullet holes, craters,
     * rubble) tracked by {@code BattleSimulation}. FIFO eviction at the head
     * once full. The accumulator FBO is decoupled — once stamped, decals stay
     * on screen regardless of whether they're still in the source list — so
     * this cap only bounds the in-memory POJO list, not the visible scarring.
     *
     * <p>Raise on a beefy machine to keep more decals in the source list
     * (mostly matters for the shell-casing per-cell counter and any future
     * removal/fade logic). 10k was the old default; 25k is the new default.
     */
    public static final int DECAL_SOURCE_CAP = 25_000;

    /**
     * When {@code true}: the top-left tick-profile HUD overlay renders, with
     * a DUMP button that writes the current per-phase averages to
     * {@code saves/common/starsector_marines/debug/}. The sim-side phase
     * instrumentation in {@link com.dillon.starsectormarines.battle.sim.BattleSimulation#tick()}
     * is always-on (cost is a handful of {@code nanoTime} calls per tick) —
     * this flag only gates the overlay + dump button.
     *
     * <p>On while we're informing the data-oriented refactor (DoD,
     * cross-frame read/write buffers, ECS pivot). Flip off once we've cashed
     * in the data and the panel would just be eye candy.
     */
    public static final boolean PROFILE_TICK_PHASES = true;

    /**
     * When {@code true}: register the campaign-tier debug intel
     * ({@code CampaignDebugIntel}) at game load. Surfaces the houses graph
     * with promote/demote/reseed and a bypass-gating toggle. Flip off (or
     * delete the intel class) for prod builds — the underlying
     * {@link com.dillon.starsectormarines.campaign.CampaignStateScript} runs
     * either way.
     */
    public static final boolean CAMPAIGN_DEBUG_INTEL = true;

    /**
     * When {@code true}: mission generators ignore campaign-tier gating
     * (house rep, rank, flavor). Equivalent to the runtime toggle on the
     * debug intel ({@code debugBypassHouseGating}) but compile-time so it
     * survives across saves before campaign-tier wiring exists.
     *
     * <p>No-op until campaign-tier mission gating is actually wired into
     * {@code MissionGenerator}. Until then this flag exists as a forward
     * declaration so consumers can branch on it from day one.
     */
    public static final boolean BYPASS_HOUSE_GATING = false;

    /**
     * When {@code true}: prepend a synthetic "DEBUG — All Missions" client to
     * every planet's client list, exposing the full {@link com.dillon.starsectormarines.ops.MissionType}
     * × {@link com.dillon.starsectormarines.ops.RiskLevel} grid (5×3 = 15
     * missions) for playtesting any mission type at any difficulty without
     * waiting on the RNG to roll the combo you want.
     *
     * <p>The debug client bypasses {@code MissionGenerator.MAX_MISSIONS}; the
     * tactical map list gets long. That's the point — flip off for any build
     * showing the intended experience.
     */
    public static final boolean DEBUG_CLIENT = true;

    /**
     * When {@code true}: every battle grants the recon-ping command power for
     * free, regardless of fleet or employer. A dev convenience so the
     * command-power loop stays exercised while the roster is shallow.
     *
     * <p>Production behavior: recon ping must be <em>sourced</em> — a committed
     * ship with the right kit (Hi-Res Sensors / Surveying Equipment / an Apogee,
     * see {@code ops.detachment.PowerCatalog}) or an employer/contract that offers
     * it ({@code Mission.employerPowerIds}). Flip off to feel the real gating.
     */
    public static final boolean ALWAYS_GRANT_RECON_PING = true;

    private DevConfig() {}
}
