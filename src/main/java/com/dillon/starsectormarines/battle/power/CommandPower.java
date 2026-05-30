package com.dillon.starsectormarines.battle.power;

/**
 * A battlefield ability the player can invoke during a battle. The fleet the
 * player brought to the planet determines <em>which</em> powers exist (the
 * diegetic-spellbook design, roadmap S2); this abstraction is the in-battle
 * runtime contract every power satisfies.
 *
 * <p>A power carries its static cost/pacing metadata ({@link #cpCost},
 * {@link #cooldownSeconds}) and a single {@link #resolve} hook. The lifecycle
 * around it lives outside the power: {@code TARGETING} is a view-layer state
 * ({@code ops.BattleScreen}); {@code CommandPowerService}/{@code CommandPowerSystem}
 * own {@code AVAILABLE -> COMMITTED -> COOLDOWN}. By the time {@link #resolve}
 * runs, command points are already debited and the cooldown already started —
 * the power just enacts its effect.
 *
 * <p>Powers are stateless and shared: per-power runtime state (cooldown timer)
 * lives in {@link CommandPowerService}, keyed by {@link #id}. A concrete power
 * holds only immutable tuning.
 */
public abstract class CommandPower {

    /** Stable identifier — keys cooldown state and the S2 fleet-mapping table. */
    public final String id;

    /** Human-readable name for the power button. */
    public final String displayName;

    /** Command points debited from the player pool when this power fires. */
    public final float cpCost;

    /** Sim-seconds before this power can fire again after committing. */
    public final float cooldownSeconds;

    protected CommandPower(String id, String displayName, float cpCost, float cooldownSeconds) {
        this.id = id;
        this.displayName = displayName;
        this.cpCost = cpCost;
        this.cooldownSeconds = cooldownSeconds;
    }

    /**
     * Enact the power at the targeted cell. Invoked by {@link CommandPowerSystem}
     * once an activation is committed — cost is already paid and the cooldown is
     * already running, so implementations only produce the effect (for
     * {@link ReconPing}, register a transient reveal on {@code service}).
     */
    public abstract void resolve(int cellX, int cellY, CommandPowerService service);

    /**
     * Radius in cells of the targeting-preview ring the UI draws while aiming
     * this power. {@code 0} (the default) means a point target — no ring.
     * Targeting shape is part of a power's contract, so it lives here rather
     * than in the view layer.
     */
    public float previewRadiusCells() {
        return 0f;
    }
}
