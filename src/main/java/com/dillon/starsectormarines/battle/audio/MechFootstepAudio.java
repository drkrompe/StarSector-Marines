package com.dillon.starsectormarines.battle.audio;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.mech.MechGaitState;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Presentation-only footsteps shared by the two audio hosts; never uses simulation RNG. */
public final class MechFootstepAudio {
    private static final float RANGE_CELLS = 28f;
    private static final int MAX_STEPS_PER_FRAME = 4;
    private final Map<MechGaitState, Long> observed = new IdentityHashMap<>();
    private final Random random = new Random();
    private int lastVariant = -1;

    public record Cue(String soundId, float pitch, float volume, float cellX, float cellY) {}

    public void reset() { observed.clear(); }

    /** Consume every contact, including hidden/distant ones, so none queue up for later. */
    public List<Cue> advance(BattleSimulation sim, float listenerX, float listenerY) {
        List<Cue> cues = new ArrayList<>();
        Set<MechGaitState> live = Collections.newSetFromMap(new IdentityHashMap<>());
        BattleComponents components = sim.getRoster().components();
        EntityWorld world = sim.getRoster().entityWorld();
        FogOfWarService fog = sim.getFogOfWar();
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (!sim.identity().type(unit).isMech()) continue;
            if (!world.has(unit, components.MECH_GAIT_STATE)) continue;
            MechGaitState gait = (MechGaitState) world.getObject(
                    unit, components.MECH_GAIT_STATE, BattleComponents.MECH_GAIT_STATE_STATE);
            if (gait == null) continue;
            live.add(gait);
            boolean visible = sim.identity().faction(unit) == Faction.MARINE
                    || fog.isInitialized() && fog.getUnitVisibility(i) == FogOfWarService.VIS_VISIBLE;
            Cue cue = observe(gait, sim.identity().mechVariant(unit),
                    visible && !sim.isComplete(), listenerX, listenerY);
            if (cue != null) cues.add(cue);
        }
        observed.keySet().retainAll(live);
        cues.sort(Comparator.comparingDouble(cue -> distanceSquared(
                cue.cellX, cue.cellY, listenerX, listenerY)));
        return cues.size() <= MAX_STEPS_PER_FRAME ? cues : cues.subList(0, MAX_STEPS_PER_FRAME);
    }

    /** Sampling twice without a new plant is silent; missed ticks collapse to one fresh contact. */
    Cue observe(MechGaitState gait, MechVariant chassis, boolean audible,
                float listenerX, float listenerY) {
        MechGaitState.Touchdown touchdown = gait.lastTouchdown();
        long sequence = touchdown != null ? touchdown.sequence() : 0;
        Long previous = observed.put(gait, sequence);
        // First sight of an existing gait establishes a cursor, never replays its history.
        if (previous == null || touchdown == null || sequence == previous || !audible) return null;
        float distance = (float) Math.sqrt(distanceSquared(
                touchdown.x(), touchdown.y(), listenerX, listenerY));
        if (distance >= RANGE_CELLS) return null;
        int variant = random.nextInt(lastVariant < 0 ? 3 : 2);
        if (lastVariant >= 0 && variant >= lastVariant) variant++;
        lastVariant = variant;
        if (chassis == null) chassis = MechVariant.BULWARK;
        float pitch = chassis == MechVariant.BULWARK ? 0.92f
                : chassis == MechVariant.HOUND ? 1.08f : 1f;
        pitch += (random.nextFloat() - 0.5f) * 0.06f;
        float volume = chassis == MechVariant.BULWARK ? 0.35f : 0.25f;
        return new Cue("marines_mech_step_0" + (variant + 1), pitch,
                volume * (1f - distance / RANGE_CELLS), touchdown.x(), touchdown.y());
    }

    private static float distanceSquared(float x, float y, float listenerX, float listenerY) {
        float dx = x - listenerX;
        float dy = y - listenerY;
        return dx * dx + dy * dy;
    }
}
