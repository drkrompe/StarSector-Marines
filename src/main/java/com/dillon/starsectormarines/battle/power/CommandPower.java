package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;

import java.util.List;

/**
 * A battlefield ability the player can invoke during a battle. The fleet the
 * player commits to the detachment determines <em>which</em> powers exist (the
 * diegetic-spellbook model in {@code command-powers-nouns.md}); this abstraction
 * is the in-battle runtime contract every power satisfies.
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

    /** Stable identifier — keys cooldown state and the fleet capability catalog. */
    public final String id;

    /** Human-readable name for the power button; a constructor argument may be an {@code @key}. */
    public final String displayName;

    /** Command points debited from the player pool when this power fires. */
    public final float cpCost;

    /** Sim-seconds before this power can fire again after committing. */
    public final float cooldownSeconds;

    /** Maximum activations this battle; {@code 0} means unlimited. */
    public final int maxCharges;

    /** Campaign supplies consumed when the activation commits. */
    public final int supplyCost;

    /** Pre-battle command-deck capacity consumed when this power is slotted. */
    public final int slotWeight;

    protected CommandPower(String id, String displayName, float cpCost, float cooldownSeconds) {
        this(id, displayName, cpCost, cooldownSeconds, 0);
    }

    protected CommandPower(String id, String displayName, float cpCost,
                           float cooldownSeconds, int maxCharges) {
        this(id, displayName, cpCost, cooldownSeconds, maxCharges, 0);
    }

    protected CommandPower(String id, String displayName, float cpCost,
                           float cooldownSeconds, int maxCharges, int supplyCost) {
        this(id, displayName, cpCost, cooldownSeconds, maxCharges, supplyCost, 1);
    }

    protected CommandPower(String id, String displayName, float cpCost,
                           float cooldownSeconds, int maxCharges, int supplyCost,
                           int slotWeight) {
        this.id = id;
        this.displayName = Strings.resolve(displayName);
        this.cpCost = cpCost;
        this.cooldownSeconds = cooldownSeconds;
        this.maxCharges = Math.max(0, maxCharges);
        this.supplyCost = Math.max(0, supplyCost);
        this.slotWeight = Math.max(1, slotWeight);
    }

    /**
     * Enact the power at the targeted cell. Invoked by {@link CommandPowerSystem}
     * once an activation is committed — cost is already paid and the cooldown is
     * already running, so implementations only produce the effect.
     */
    public abstract void resolve(int cellX, int cellY, CommandPowerService service,
                                 BattleControl battle);

    /**
     * Whether this power can be committed at the targeted cell. The UI uses
     * the same predicate as the sim commit pass, so an invalid physical drop
     * remains armed instead of silently consuming the click or command points.
     */
    public boolean canTarget(int cellX, int cellY, BattleView battle) {
        return battle != null && battle.getGrid().inBounds(cellX, cellY);
    }

    /**
     * As {@link #canTarget(int, int, BattleView)}, for a power whose call carries a pick
     * ({@code selection}: indices the player chose, or {@code null} for the power's own
     * default). Only a power with a picker reads it.
     */
    public boolean canTarget(int cellX, int cellY, BattleView battle, List<Integer> selection) {
        return canTarget(cellX, cellY, battle);
    }

    /** As {@link #resolve(int, int, CommandPowerService, BattleControl)}, with the call's pick. */
    public void resolve(int cellX, int cellY, CommandPowerService service,
                        BattleControl battle, List<Integer> selection) {
        resolve(cellX, cellY, service, battle);
    }

    /**
     * The longest line, in cells, the player draws for this power, or {@code 0} (the default) for a power aimed at
     * one cell. A line power is aimed by pressing at its start and dragging to its end (or clicking each); the UI
     * cuts the drawn line to this length and commits it through {@link #canTargetLine} and {@link #resolveLine}.
     */
    public float lineMaxCells() {
        return 0f;
    }

    /** As {@link #canTarget(int, int, BattleView)}, for a line from {@code (ax, ay)} to {@code (bx, by)}. */
    public boolean canTargetLine(int ax, int ay, int bx, int by, BattleView battle) {
        return canTarget(ax, ay, battle) && battle.getGrid().inBounds(bx, by);
    }

    /** As {@link #resolve(int, int, CommandPowerService, BattleControl)}, for a committed line. */
    public void resolveLine(int ax, int ay, int bx, int by, CommandPowerService service, BattleControl battle) {
        resolve(ax, ay, service, battle);
    }

    /**
     * Calls this power still holds when it paces itself rather than spending charges
     * (Mech Support: machines still aboard), or {@code -1} for the service's own charge
     * count. {@code 0} reads as spent everywhere a charge count does.
     */
    public int availableCalls() {
        return -1;
    }

    /**
     * Whether this power can still put company forces on the field once paid for and off
     * cooldown: a reserve with a way down. Charges, supplies and the command-point pool are
     * the service's to judge; this answers only whether there is anyone left to send and
     * anything left to fly them. Most powers field nobody.
     */
    public boolean canStillField(BattleView battle) {
        return false;
    }

    /**
     * Radius in cells of the targeting-preview ring the UI draws while aiming
     * this power. {@code 0} (the default) means a point target — no ring.
     * Targeting shape is part of a power's contract, so it lives here rather
     * than in the view layer.
     */
    public float previewRadiusCells() {
        return 0f;
    }

    /**
     * What the power's card in the battle tray calls itself, given the charges left. Most powers
     * are their name; one backed by several named sources says which comes next.
     */
    public String cardLabel(int chargesRemaining) {
        return displayName;
    }
}
