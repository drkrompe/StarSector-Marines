package com.dillon.starsectormarines.battle.power;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * State owner for the player's in-battle command powers — the command-point
 * pool, the roster of available powers, their cooldown timers, the queue of
 * activations the UI has requested, and the transient effects in flight. Ticked
 * by {@link CommandPowerSystem}; follows the {@code *Service} convention
 * (state owner, mutated by a stateless system) — sibling to
 * {@code command.BattleResources} and {@code command.CommanderService}.
 *
 * <p>Command points are a single <em>player</em> scalar in S1 (powers are
 * player-only); generalize to a per-faction pool if an enemy commander ever
 * gets powers. The available-power roster is hardcoded to the always-on
 * {@link ReconPing} in S1; the S2 fleet&rarr;powers resolver will populate it
 * from real fleet composition.
 *
 * <p>Threading: {@link #requestActivation} is called from the input pass
 * ({@code BattleScreen.processInput}) and the queue is drained from the sim tick
 * ({@code BattleScreen.advance -> sim.advance}). Both run on the game's main
 * thread, in order, every frame — no concurrent access, so a plain list is
 * safe. (The sim's parallelism is internal to {@code sim.advance} and joins
 * before it returns.)
 */
public final class CommandPowerService {

    /** A transient fog reveal produced by a {@link ReconPing} activation. The
     *  view layer projects each live ping into the vision pass every frame;
     *  {@link CommandPowerSystem} ages {@link #remainingSeconds} down and drops
     *  expired pings, at which point the reveal lapses. */
    public static final class ActivePing {
        public final int cellX;
        public final int cellY;
        public final int radius;
        public float remainingSeconds;

        ActivePing(int cellX, int cellY, int radius, float remainingSeconds) {
            this.cellX = cellX;
            this.cellY = cellY;
            this.radius = radius;
            this.remainingSeconds = remainingSeconds;
        }
    }

    /** A UI-requested activation awaiting resolution on the next tick drain. */
    static final class PendingActivation {
        final String powerId;
        final int cellX;
        final int cellY;

        PendingActivation(String powerId, int cellX, int cellY) {
            this.powerId = powerId;
            this.cellX = cellX;
            this.cellY = cellY;
        }
    }

    // ---- S1 placeholder tuning (capacity scaling is S5) ----
    private static final float STARTING_COMMAND_POINTS = 4f;
    private static final float MAX_COMMAND_POINTS = 10f;
    private static final float REGEN_PER_SECOND = 0.5f;

    private float commandPoints = STARTING_COMMAND_POINTS;

    /** Available powers, keyed by id. Insertion-ordered for a stable UI layout. */
    private final Map<String, CommandPower> powers = new LinkedHashMap<>();

    /** Per-power cooldown remaining (sim-seconds); absent / {@code <= 0} = ready. */
    private final Map<String, Float> cooldowns = new LinkedHashMap<>();

    private final List<PendingActivation> pending = new ArrayList<>();
    private final List<ActivePing> activePings = new ArrayList<>();

    public CommandPowerService() {
        // Roster starts empty; the battle setup injects the resolved powers via
        // BattleSimulation#setCommandPowers -> setPowers (the fleet -> powers
        // resolver, ops.detachment). An empty roster hides the power UI.
    }

    public void register(CommandPower power) {
        powers.put(power.id, power);
    }

    /**
     * Replace the available-power roster wholesale — called once at battle setup
     * (before the first tick) with the detachment-resolved powers. Clears any
     * prior roster + cooldown state so a re-setup can't leave stale timers.
     */
    public void setPowers(List<CommandPower> resolved) {
        powers.clear();
        cooldowns.clear();
        if (resolved != null) {
            for (CommandPower power : resolved) register(power);
        }
    }

    // ---- reads (UI + view layer) ----

    public float getCommandPoints() { return commandPoints; }

    public float getMaxCommandPoints() { return MAX_COMMAND_POINTS; }

    public CommandPower getPower(String id) { return powers.get(id); }

    public List<CommandPower> getAvailablePowers() {
        return new ArrayList<>(powers.values());
    }

    /** Cooldown remaining for a power in sim-seconds; {@code 0} when ready. */
    public float getCooldownRemaining(String powerId) {
        Float v = cooldowns.get(powerId);
        return v == null ? 0f : Math.max(0f, v);
    }

    /** True if the power exists, is off cooldown, and the pool can pay for it. */
    public boolean canActivate(CommandPower power) {
        return power != null
                && getCooldownRemaining(power.id) <= 0f
                && commandPoints >= power.cpCost;
    }

    /** Live transient reveals — projected into the fog by the view layer. */
    public List<ActivePing> getActivePings() {
        return Collections.unmodifiableList(activePings);
    }

    // ---- activation request (input pass) ----

    /**
     * Queue a power activation at the targeted cell. Called from the input pass;
     * the actual commit (cost + cooldown + {@link CommandPower#resolve}) happens
     * when {@link CommandPowerSystem} drains the queue on the next tick.
     */
    public void requestActivation(String powerId, int cellX, int cellY) {
        pending.add(new PendingActivation(powerId, cellX, cellY));
    }

    // ---- mutations driven by CommandPowerSystem ----

    List<PendingActivation> drainPending() {
        if (pending.isEmpty()) return Collections.emptyList();
        List<PendingActivation> drained = new ArrayList<>(pending);
        pending.clear();
        return drained;
    }

    /** Debit the cost and start the cooldown. Caller must have checked
     *  {@link #canActivate} first. */
    void commit(CommandPower power) {
        commandPoints -= power.cpCost;
        cooldowns.put(power.id, power.cooldownSeconds);
    }

    /** Register a transient reveal — called from {@link ReconPing#resolve}. */
    public void addActivePing(int cellX, int cellY, int radius, float seconds) {
        activePings.add(new ActivePing(cellX, cellY, radius, seconds));
    }

    void regenCommandPoints(float dt) {
        commandPoints = Math.min(MAX_COMMAND_POINTS, commandPoints + REGEN_PER_SECOND * dt);
    }

    void tickCooldowns(float dt) {
        for (Map.Entry<String, Float> e : cooldowns.entrySet()) {
            float remaining = e.getValue() - dt;
            e.setValue(remaining > 0f ? remaining : 0f);
        }
    }

    void tickActivePings(float dt) {
        for (int i = activePings.size() - 1; i >= 0; i--) {
            ActivePing p = activePings.get(i);
            p.remainingSeconds -= dt;
            if (p.remainingSeconds <= 0f) {
                activePings.remove(i);
            }
        }
    }
}
