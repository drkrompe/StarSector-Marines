package com.dillon.starsectormarines.battle.audio;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.mech.MechGaitState;
import com.dillon.starsectormarines.battle.mech.MechGaitState.FootEvent;
import com.dillon.starsectormarines.battle.mech.MechGaitState.FootPhase;
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
    private static final int MAX_CUES_PER_PHASE = 4;
    private final Map<MechGaitState, Long> observed = new IdentityHashMap<>();
    private final Random random = new Random();
    private int lastImpact = -1;
    private int lastPneumatic = -1;

    public record Cue(String soundId, FootPhase phase, int foot,
                      float pitch, float volume, float cellX, float cellY) {}

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
            cues.addAll(observe(gait, sim.identity().mechVariant(unit),
                    visible && !sim.isComplete(), listenerX, listenerY));
        }
        observed.keySet().retainAll(live);
        cues.sort(Comparator.comparingDouble(cue -> distanceSquared(
                cue.cellX, cue.cellY, listenerX, listenerY)));
        int lifts = 0;
        int landings = 0;
        List<Cue> admitted = new ArrayList<>();
        for (Cue cue : cues) {
            // Machinery cannot spend the landing budget or defer impacts into later frames.
            if (cue.phase == FootPhase.LAND ? landings++ < MAX_CUES_PER_PHASE
                    : lifts++ < MAX_CUES_PER_PHASE) admitted.add(cue);
        }
        return admitted;
    }

    /** Missed ticks retain only the newest landing and any lift still in progress. */
    List<Cue> observe(MechGaitState gait, MechVariant chassis, boolean audible,
                float listenerX, float listenerY) {
        FootEvent landing = gait.lastTouchdown();
        FootEvent lift = gait.lastLiftOff();
        long sequence = Math.max(landing != null ? landing.sequence() : 0,
                lift != null ? lift.sequence() : 0);
        Long previous = observed.put(gait, sequence);
        // First sight of an existing gait establishes a cursor, never replays its history.
        if (previous == null || sequence == previous || !audible) return List.of();
        List<Cue> cues = new ArrayList<>(2);
        if (landing != null && landing.sequence() > previous) {
            addCue(cues, landing, chassis, listenerX, listenerY);
        }
        if (lift != null && lift.sequence() > previous
                && (landing == null || lift.sequence() > landing.sequence())) {
            addCue(cues, lift, chassis, listenerX, listenerY);
        }
        return cues;
    }

    private void addCue(List<Cue> cues, FootEvent event, MechVariant chassis,
                        float listenerX, float listenerY) {
        float distance = (float) Math.sqrt(distanceSquared(
                event.x(), event.y(), listenerX, listenerY));
        if (distance >= RANGE_CELLS) return;
        if (chassis == null) chassis = MechVariant.BULWARK;
        boolean impact = event.phase() == FootPhase.LAND;
        int variant = nextVariant(impact ? lastImpact : lastPneumatic, impact ? 3 : 2);
        if (impact) lastImpact = variant;
        else lastPneumatic = variant;
        float pitch = chassis == MechVariant.BULWARK ? 0.86f
                : chassis == MechVariant.HOUND ? 0.96f : 0.92f;
        if (!impact) pitch += 0.12f;
        pitch += (random.nextFloat() - 0.5f) * 0.04f;
        float volume = impact ? (chassis == MechVariant.BULWARK ? 0.65f : 0.50f) : 0.20f;
        cues.add(new Cue((impact ? "marines_mech_land_0" : "marines_mech_lift_0")
                + (variant + 1), event.phase(), event.foot(), pitch,
                volume * (1f - distance / RANGE_CELLS), event.x(), event.y()));
    }

    private int nextVariant(int previous, int count) {
        int variant = random.nextInt(previous < 0 ? count : count - 1);
        return previous >= 0 && variant >= previous ? variant + 1 : variant;
    }

    private static float distanceSquared(float x, float y, float listenerX, float listenerY) {
        float dx = x - listenerX;
        float dy = y - listenerY;
        return dx * dx + dy * dy;
    }
}
