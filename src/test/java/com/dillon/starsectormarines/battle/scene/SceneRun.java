package com.dillon.starsectormarines.battle.scene;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * The loop every scene shares: orders in, instruments read, frame recorded,
 * tick advanced.
 *
 * <p><b>The order within a tick is the contract.</b> The scripted player issues
 * that tick's orders first, so they are in the mailbox the command phase is
 * about to drain. Observers run next, which means what they see is the state
 * the tick will act on rather than the state it left behind — a trace sampled
 * after {@code advance} attributes every reading to the tick after the one that
 * caused it, and off-by-one is exactly the error a verdict like "handed back
 * within a tick" cannot survive. The frame is recorded from that same state, so
 * the picture and the verdict come from one play rather than two. Only then
 * does the world move.
 */
public final class SceneRun {

    private final SceneWorld world;
    private final List<TickObserver> observers = new ArrayList<>();
    private ScriptedPlayer player;
    private FrameSink frames = FrameSink.NONE;
    private String loopId = "main";
    private int everyTicks;
    private IntFunction<String> caption;
    private int tick;

    private SceneRun(SceneWorld world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    public static SceneRun of(SceneWorld world) {
        return new SceneRun(world);
    }

    public SceneRun player(ScriptedPlayer player) {
        this.player = player;
        return this;
    }

    /** Attaches instruments, called in registration order every tick. */
    public SceneRun observe(TickObserver... observers) {
        Collections.addAll(this.observers, observers);
        return this;
    }

    /** Records a frame every {@code everyTicks} ticks, captioned per tick. */
    public SceneRun frames(FrameSink sink, String loopId, int everyTicks,
                           IntFunction<String> caption) {
        if (everyTicks <= 0) {
            throw new IllegalArgumentException("Frame cadence must be positive: " + everyTicks);
        }
        this.frames = Objects.requireNonNull(sink, "sink");
        this.loopId = Objects.requireNonNull(loopId, "loopId");
        this.everyTicks = everyTicks;
        this.caption = caption;
        return this;
    }

    /** The tick index the next {@link #step()} will play. */
    public int tick() {
        return tick;
    }

    public SceneWorld world() {
        return world;
    }

    /** Plays {@code ticks} ticks from wherever the run currently stands. */
    public void run(int ticks) {
        for (int i = 0; i < ticks; i++) step();
    }

    /** One tick: orders, observers, frame, advance. */
    public void step() {
        BattleSimulation sim = world.sim();
        if (player != null) player.tick(sim, tick);
        for (TickObserver observer : observers) observer.observe(sim, tick);
        if (everyTicks > 0 && tick % everyTicks == 0) {
            frames.frame(loopId, sim, tick, caption == null ? "" : caption.apply(tick));
        }
        sim.advance(BattleSimulation.TICK_DT);
        tick++;
    }
}
