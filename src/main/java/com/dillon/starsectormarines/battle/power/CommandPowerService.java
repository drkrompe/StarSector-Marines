package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.warship.Warship;

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
 * <p>Command points are a single <em>player</em> scalar (powers are player-only);
 * generalize to a per-faction pool if an enemy commander ever gets powers. The
 * roster starts empty and is injected at battle setup from the detachment's
 * committed, deck-filtered fleet and employer capabilities.
 *
 * <p>Threading: {@link #requestActivation} is called from the input pass
 * ({@code BattleScreen.processInput}) and the queue is drained from the sim tick
 * ({@code BattleScreen.advance -> sim.advance}). Both run on the game's main
 * thread, in order, every frame — no concurrent access, so a plain list is
 * safe. (The sim's parallelism is internal to {@code sim.advance} and joins
 * before it returns.)
 */
public final class CommandPowerService {

    /** A UI-requested activation awaiting resolution on the next tick drain. */
    static final class PendingActivation {
        final String powerId;
        final int cellX;
        final int cellY;
        /** The player's pick for a power with a picker; {@code null} for its default. */
        final List<Integer> selection;
        /** Whether this is a drawn line from the cell to {@link #endX}, {@link #endY}. */
        final boolean line;
        final int endX;
        final int endY;

        PendingActivation(String powerId, int cellX, int cellY, List<Integer> selection) {
            this(powerId, cellX, cellY, selection, false, cellX, cellY);
        }

        private PendingActivation(String powerId, int cellX, int cellY, List<Integer> selection,
                                  boolean line, int endX, int endY) {
            this.powerId = powerId;
            this.cellX = cellX;
            this.cellY = cellY;
            this.selection = selection == null ? null : List.copyOf(selection);
            this.line = line;
            this.endX = endX;
            this.endY = endY;
        }

        static PendingActivation line(String powerId, int ax, int ay, int bx, int by) {
            return new PendingActivation(powerId, ax, ay, null, true, bx, by);
        }
    }

    // ---- Placeholder tuning (persistent capacity scaling remains open) ----
    private static final float STARTING_COMMAND_POINTS = 4f;
    private static final float MAX_COMMAND_POINTS = 10f;
    private static final float REGEN_PER_SECOND = 0.5f;

    private float commandPoints = STARTING_COMMAND_POINTS;

    /** Available powers, keyed by id. Insertion-ordered for a stable UI layout. */
    private final Map<String, CommandPower> powers = new LinkedHashMap<>();

    /** Per-power cooldown remaining (sim-seconds); absent / {@code <= 0} = ready. */
    private final Map<String, Float> cooldowns = new LinkedHashMap<>();
    /** Remaining per-battle activations for limited powers; absent = unlimited. */
    private final Map<String, Integer> charges = new LinkedHashMap<>();
    /** The hull each power has over the battle now, by power id; a power with one aloft is not called again until it is gone. */
    private final Map<String, Warship> aloft = new LinkedHashMap<>();

    private final List<PendingActivation> pending = new ArrayList<>();
    /** Queued armor drops the UI asked to cancel, by lift-queue job id. */
    private final List<Long> pendingCancels = new ArrayList<>();
    private final List<PendingDetonation> activeFireMissions = new ArrayList<>();
    private CommandPowerResources resources = CommandPowerResources.FREE;

    public CommandPowerService() {
        // Roster starts empty; the battle setup injects the resolved powers via
        // BattleSimulation#setCommandPowers -> setPowers (the fleet -> powers
        // resolver, ops.detachment). An empty roster hides the power UI.
    }

    public void register(CommandPower power) {
        powers.put(power.id, power);
        if (power.maxCharges > 0) charges.put(power.id, power.maxCharges);
        else charges.remove(power.id);
    }

    /**
     * Replace the available-power roster wholesale — called once at battle setup
     * (before the first tick) with the detachment-resolved powers. Clears any
     * prior roster + cooldown state so a re-setup can't leave stale timers.
     */
    public void setPowers(List<CommandPower> resolved) {
        powers.clear();
        cooldowns.clear();
        charges.clear();
        aloft.clear();
        if (resolved != null) {
            for (CommandPower power : resolved) {
                register(power);
            }
        }
    }

    // ---- reads (UI + view layer) ----

    public float getCommandPoints() { return commandPoints; }

    public float getMaxCommandPoints() { return MAX_COMMAND_POINTS; }

    /** Supplies visible to command powers at this instant. */
    public int getAvailableSupplies() { return resources.availableSupplies(); }

    public CommandPower getPower(String id) { return powers.get(id); }

    public void setResources(CommandPowerResources resources) {
        this.resources = resources != null ? resources : CommandPowerResources.FREE;
    }

    public CommandPowerResources getResources() {
        return resources;
    }

    public List<CommandPower> getAvailablePowers() {
        return new ArrayList<>(powers.values());
    }

    /** Cooldown remaining for a power in sim-seconds; {@code 0} when ready. */
    public float getCooldownRemaining(String powerId) {
        Float v = cooldowns.get(powerId);
        return v == null ? 0f : Math.max(0f, v);
    }

    /** Remaining uses, or {@code -1} when the power is unlimited. */
    public int getChargesRemaining(String powerId) {
        CommandPower power = powers.get(powerId);
        if (power != null && power.availableCalls() >= 0) return power.availableCalls();
        Integer remaining = charges.get(powerId);
        return remaining == null ? -1 : Math.max(0, remaining);
    }

    /** True if the power exists, is off cooldown, and the pool can pay for it. */
    public boolean canActivate(CommandPower power) {
        return power != null
                && getCooldownRemaining(power.id) <= 0f
                && getChargesRemaining(power.id) != 0
                && !aloft.containsKey(power.id)
                && commandPoints >= power.cpCost
                && resources.availableSupplies() >= power.supplyCost;
    }

    /** Records {@code ship} as the hull {@code powerId} has over the battle: the power waits for it to be gone. */
    public void aloft(String powerId, Warship ship) {
        if (ship != null) aloft.put(powerId, ship);
    }

    /** The hull {@code powerId} has over the battle, or {@code null}. */
    public Warship aloftFor(String powerId) {
        return aloft.get(powerId);
    }

    /** The power's hull is gone, home or lost; it may be called again once its cooldown and charges allow. */
    public void landed(String powerId) {
        aloft.remove(powerId);
    }

    /** Adds {@code seconds} to what the power's cooldown has left: a hull brought home hurt is a while in repair. */
    public void lengthenCooldown(String powerId, float seconds) {
        if (seconds > 0f) cooldowns.put(powerId, getCooldownRemaining(powerId) + seconds);
    }

    /** Takes the power's remaining calls away for the rest of the battle: the hull it called is lost. */
    public void retire(String powerId) {
        charges.put(powerId, 0);
    }

    /** Telegraphs for orbital fire missions awaiting impact. */
    public List<PendingDetonation> getActiveFireMissions() {
        return Collections.unmodifiableList(activeFireMissions);
    }

    // ---- activation request (input pass) ----

    /**
     * Queue a power activation at the targeted cell. Called from the input pass;
     * the actual commit (cost + cooldown + {@link CommandPower#resolve}) happens
     * when {@link CommandPowerSystem} drains the queue on the next tick.
     */
    public void requestActivation(String powerId, int cellX, int cellY) {
        requestActivation(powerId, cellX, cellY, null);
    }

    /**
     * As above, with the player's pick for a power that offers one (an armor drop's one
     * machine). The pick is copied now: what the player chose is what the tick commits.
     */
    public void requestActivation(String powerId, int cellX, int cellY, List<Integer> selection) {
        pending.add(new PendingActivation(powerId, cellX, cellY, selection));
    }

    /** Queue a line power's activation along the drawn line from {@code (ax, ay)} to {@code (bx, by)}. */
    public void requestLineActivation(String powerId, int ax, int ay, int bx, int by) {
        pending.add(PendingActivation.line(powerId, ax, ay, bx, by));
    }

    /**
     * An armor drop the player has placed that the sim has not committed yet (the battle is
     * paused while the picker is open, so placements wait here until it runs).
     */
    public record PendingDrop(String powerId, int cellX, int cellY, int manifestIndex) {}

    /** Placed armor drops still waiting for the next tick, oldest first. */
    public List<PendingDrop> pendingDrops() {
        List<PendingDrop> drops = new ArrayList<>();
        for (PendingActivation request : pending) {
            Integer pick = ArmorDropPower.single(request.selection);
            if (pick != null && powers.get(request.powerId) instanceof ArmorDropPower) {
                drops.add(new PendingDrop(request.powerId, request.cellX, request.cellY, pick));
            }
        }
        return drops;
    }

    /**
     * Withdraws a placed armor drop the sim has not committed yet. Nothing was spent on it, so
     * withdrawing it is only taking the request back.
     */
    public boolean withdrawPendingDrop(String powerId, int manifestIndex) {
        for (int i = 0; i < pending.size(); i++) {
            PendingActivation request = pending.get(i);
            Integer pick = ArmorDropPower.single(request.selection);
            if (request.powerId.equals(powerId) && pick != null && pick == manifestIndex) {
                pending.remove(i);
                return true;
            }
        }
        return false;
    }

    /**
     * Asks to cancel a queued armor drop by its lift-queue job id. Revalidated on the next
     * tick: a drop a craft has already taken stays in the air.
     */
    public void requestDropCancel(long jobId) {
        pendingCancels.add(jobId);
    }

    // ---- mutations driven by CommandPowerSystem ----

    List<Long> drainCancels() {
        if (pendingCancels.isEmpty()) return Collections.emptyList();
        List<Long> drained = new ArrayList<>(pendingCancels);
        pendingCancels.clear();
        return drained;
    }

    List<PendingActivation> drainPending() {
        if (pending.isEmpty()) return Collections.emptyList();
        List<PendingActivation> drained = new ArrayList<>(pending);
        pending.clear();
        return drained;
    }

    /** Debit the cost and start the cooldown. Caller must have checked
     *  {@link #canActivate} first. */
    boolean commit(CommandPower power) {
        if (!resources.spendSupplies(power.supplyCost)) return false;
        commandPoints -= power.cpCost;
        cooldowns.put(power.id, power.cooldownSeconds);
        Integer remaining = charges.get(power.id);
        if (remaining != null) charges.put(power.id, Math.max(0, remaining - 1));
        return true;
    }

    public void addFireMission(PendingDetonation detonation) {
        if (detonation != null) activeFireMissions.add(detonation);
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

    void tickFireMissions(float dt, BattleControl battle) {
        for (int i = activeFireMissions.size() - 1; i >= 0; i--) {
            PendingDetonation mission = activeFireMissions.get(i);
            mission.remainingTime -= dt;
            if (mission.remainingTime <= 0f) {
                battle.detonateNow(mission);
                battle.spawnHeavyImpact(mission.endpointX, mission.endpointY, mission.aoeRadius);
                activeFireMissions.remove(i);
            }
        }
    }
}
