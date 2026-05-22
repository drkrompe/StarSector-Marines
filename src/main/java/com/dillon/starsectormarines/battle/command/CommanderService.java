package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.Faction;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Per-faction strategic commanders. A faction with no entry here has no
 * commander tier active — its squads run on ambient ENGAGEMENT goals with
 * {@code Squad.assignedObjective} left null. Missions that want
 * commander-driven coordination (Conquest spreads marine squads across
 * charge sites via {@link ConquestCommand}) install one via
 * {@link #setCommander(Faction, MissionCommand)} during {@code BattleSetup}.
 *
 * <p>Owned by {@link com.dillon.starsectormarines.battle.BattleSimulation};
 * sibling slice to {@link com.dillon.starsectormarines.battle.fx.EffectsService},
 * {@link com.dillon.starsectormarines.battle.vision.VisionService}, and
 * {@link com.dillon.starsectormarines.battle.shots.ShotService}.
 *
 * <p>{@link #tick(float, Consumer)} owns the COMMANDER_TICK_PERIOD cadence;
 * the per-commander dispatch goes through the supplied {@code Consumer} so
 * this class doesn't import {@code BattleSimulation} (the same callback
 * shape {@code ShotService} uses for projectile arrivals).
 */
public final class CommanderService {

    /**
     * Sim-seconds between commander-tier slow ticks. The squad-GOAP replan
     * loop runs every {@code GoapInfantryBehavior.REPLAN_PERIOD} (2s today);
     * the commander runs at a slower cadence so strategic assignments don't
     * thrash. Set so each commander tick is roughly bracketed by one full
     * GOAP replan cycle — gives squads a chance to act on a fresh assignment
     * before the commander considers reassigning. Tune in playtest.
     */
    public static final float COMMANDER_TICK_PERIOD = 2.5f;

    private final Map<Faction, MissionCommand> commanders = new EnumMap<>(Faction.class);

    /**
     * Sim-seconds accumulated since the last commander slow-tick. When this
     * crosses {@link #COMMANDER_TICK_PERIOD}, every registered commander is
     * dispatched via the {@code tickHandler} supplied to {@link #tick}.
     */
    private float accumulator = 0f;

    /**
     * Install (or replace) the strategic commander for one faction. Pass
     * {@code null} to clear an existing commander — the faction's squads
     * fall back to ambient ENGAGEMENT goals. Typically called once during
     * {@code BattleSetup} per faction that wants the layer.
     */
    public void setCommander(Faction faction, MissionCommand commander) {
        if (commander == null) {
            commanders.remove(faction);
        } else {
            commanders.put(faction, commander);
        }
    }

    /** The commander for {@code faction}, or {@code null} if none is wired. */
    public MissionCommand getCommander(Faction faction) {
        return commanders.get(faction);
    }

    public boolean isEmpty() { return commanders.isEmpty(); }

    /**
     * Accumulates {@code dt} into the cadence timer and, when it crosses
     * {@link #COMMANDER_TICK_PERIOD}, dispatches every registered commander
     * through {@code tickHandler}. Skipped entirely when no commanders are
     * registered (the common case for non-Conquest / non-Assault missions).
     * Per-faction order is enum-declaration order via the EnumMap —
     * deterministic across runs.
     */
    public void tick(float dt, Consumer<MissionCommand> tickHandler) {
        if (commanders.isEmpty()) return;
        accumulator += dt;
        if (accumulator < COMMANDER_TICK_PERIOD) return;
        accumulator -= COMMANDER_TICK_PERIOD;
        for (MissionCommand cmd : commanders.values()) {
            tickHandler.accept(cmd);
        }
    }
}
