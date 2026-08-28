package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.combat.MitigationService;
import com.dillon.starsectormarines.battle.infantry.IntegralSystemService;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.marine.IntegralSystemDef;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongLists;

/**
 * Authors the {@code SYSTEM_FX} appearance component for every actor whose
 * armour pattern carries an integral system: while one runs, what the wearer
 * looks like; the moment it expires, nothing ({@code progression-nouns.md}).
 *
 * <p>A <b>presentation system</b>, the {@link FacingSystem} sibling. It reads
 * live simulation state and writes appearance data; it never writes simulation
 * state and nothing in the simulation reads what it wrote. An integral system
 * is spent on its authored policy, and no part of that decision may reach this
 * class.
 *
 * <p><b>Tick placement is load-bearing, for the same reason {@link FacingSystem}'s
 * is.</b> This runs at the tail of the tick, after the sweep that spends a
 * system and after the one that drains and aims the screen, so it authors the
 * <em>post-tick</em> state a render read this frame will see. That is what
 * makes the story's acceptance literal rather than approximate: the treatment
 * is present on the tick of activation and gone on the tick the effect expires,
 * because both are read after they have already happened.
 *
 * <p><b>The arc is copied, never derived.</b> The drawn screen's facing, width,
 * and how much of its pool is left come straight off {@link MitigationService} —
 * the same columns the damage path resolves a hit against. There is deliberately
 * no second arc calculation here to drift out of step with the first, because
 * the entire point of drawing the screen is that the player can trust which way
 * it faces.
 *
 * <p><b>A screen ends two ways, and they look different.</b> A window that ran
 * out simply stops; a pool beaten to nothing shatters, and the mark that says so
 * is authored here from the durability side's own record of the absorb that
 * emptied it. That mark is the one part of the treatment that is drawn after the
 * system has stopped running.
 *
 * <p>Activations are also collected for the frame, so the audio tier can play
 * one positional cue per spend without inventing its own edge detection over
 * render state. The list follows the {@code getDeathsThisFrame} lifecycle: it
 * accumulates across every tick inside one {@code advance} call and is cleared
 * at the start of the next.
 */
public final class SystemFxSystem {

    /** How long one shimmer cycle takes, in sim-seconds. Slow enough to read as a sheen rather than a strobe. */
    private static final float SHIMMER_PERIOD_SECONDS = 1.1f;

    private final UnitRosterService rosterService;
    private final LongList activationsThisFrame = new LongArrayList();

    public SystemFxSystem(UnitRosterService rosterService) {
        this.rosterService = rosterService;
    }

    /**
     * Drops the previous frame's activations. Called unconditionally at the top
     * of {@code BattleSimulation.advance} so a paused caller does not keep
     * replaying the last frame's cues.
     */
    public void beginFrame() {
        activationsThisFrame.clear();
    }

    /**
     * Actors whose system was spent during the last {@code advance} call, in
     * roster order. Presentation-only: a consumer plays a cue or spawns an
     * effect from it and nothing more.
     */
    public LongList activationsThisFrame() {
        return LongLists.unmodifiable(activationsThisFrame);
    }

    /** Authors every carrier's treatment from this tick's settled state. */
    public void tick() {
        IntegralSystemService systems = rosterService.integralSystems();
        MitigationService screens = rosterService.mitigations();
        SystemFxService fx = rosterService.systemFx();
        long[] live = rosterService.denseArray();
        int count = rosterService.liveCount();
        for (int i = 0; i < count; i++) {
            long id = live[i];
            if (!fx.has(id)) continue;
            // Authored first and unconditionally: a screen that shattered is
            // already gone by the time this runs, and its going is the frame
            // worth drawing.
            fx.writeBreakFlash(id, screens.breakFlashRemaining(id)
                    / MitigationService.BREAK_FLASH_SECONDS);
            boolean wasRunning = fx.isRunning(id);
            float remaining = systems.activeRemaining(id);
            if (remaining <= 0f) {
                fx.clear(id);
                continue;
            }
            IntegralSystemDef def = systems.spec(id);
            float duration = def != null ? def.durationSeconds() : 0f;
            // A window with no length is not a window; treat it as fully open
            // rather than dividing by zero into an invisible treatment.
            float intensity = duration > 0f ? remaining / duration : 1f;
            fx.write(id, intensity, screens.facingDegrees(id),
                    screens.arcDegrees(id), screens.soakFraction(id),
                    shimmerPhase(duration - remaining));
            if (!wasRunning) activationsThisFrame.add(id);
        }
    }

    /**
     * Where the shimmer stands, from how long this activation has been running.
     * Derived from simulation time rather than sampled from a wall clock so the
     * same battle state draws the same frame — deterministic visual evidence
     * depends on it, and a render-time clock would make two runs of an
     * unchanged scene differ.
     */
    private static float shimmerPhase(float elapsedSeconds) {
        float cycles = elapsedSeconds / SHIMMER_PERIOD_SECONDS;
        return cycles - (float) Math.floor(cycles);
    }
}
