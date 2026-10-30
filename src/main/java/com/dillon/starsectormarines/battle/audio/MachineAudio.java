package com.dillon.starsectormarines.battle.audio;

import com.dillon.starsectormarines.battle.mech.MechFlux;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.GroundTurret;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The machinery a battle sounds like, shared by the two audio hosts as {@link MechFootstepAudio} is: each ground
 * vehicle's engine as a loop whose pitch and volume follow its speed, and the flux vent of a mech or a flux-fed turret
 * (a laser tank's) as the vanilla vent cue when it starts and the vanilla flux hiss while it lasts, and a hull running
 * into something that stops it as a heavy impact, louder the faster it hit and deeper the heavier the hull. Presentation only;
 * it reads simulation state and never writes it. A host plays each {@link Loop} with {@code playLoop} every frame
 * (its {@code key} is the per-voice identity) and each {@link Cue} once, converting cells to its own world units.
 *
 * <p>Only what the player could see is heard: their own side's machines, and another side's on a revealed cell. Engines
 * are kept to the nearest few within reach, so a column of hulls is a column, not a wall of noise.
 */
public final class MachineAudio {

    /** Cells beyond which an engine or a vent is not heard at all. */
    static final float RANGE_CELLS = 40f;
    /** Engine loops held at once, nearest first. */
    static final int MAX_ENGINES = 6;

    /** Vanilla: a ship venting flux, once, as the vent opens. */
    static final String VENT_CUE = "vent_flux";
    /** Vanilla's flux flush loop a venting ship holds, under our own id: vanilla {@code flux_loop} also lists the
     *  Dweller's lightning loop. */
    static final String VENT_LOOP = "marines_vent_loop";

    /** A hull running into something that stops it: vanilla's ship-on-rock collision, under our own id. */
    public static final String HULL_BUMP = "marines_hull_bump";

    public static final String ENGINE_TRACKED = "marines_engine_tracked";
    public static final String ENGINE_TURBINE = "marines_engine_turbine";
    public static final String ENGINE_WHEELED = "marines_engine_wheeled";
    public static final String ENGINE_HOVER = "marines_engine_hover";

    /** A loop to hold this frame. Velocity is in cells a second, for Doppler. */
    public record Loop(String soundId, Object key, float pitch, float volume,
                       float cellX, float cellY, float vxCells, float vyCells) {}

    /** A sound to play once. */
    public record Cue(String soundId, float pitch, float volume, float cellX, float cellY) {}

    /** One frame of machinery: loops to hold and cues to fire. */
    public record Frame(List<Loop> loops, List<Cue> cues) {}

    /** The engine voice a hull speaks with: its loop, and pitch and volume at a standstill and at top speed. */
    record EngineVoice(String soundId, float idlePitch, float fullPitch, float idleVolume, float fullVolume) {
        float pitch(float throttle) { return idlePitch + (fullPitch - idlePitch) * throttle; }
        float volume(float throttle) { return idleVolume + (fullVolume - idleVolume) * throttle; }
    }

    private static final EngineVoice TRACKED = new EngineVoice(ENGINE_TRACKED, .78f, 1.08f, .40f, .85f);
    private static final EngineVoice TURBINE = new EngineVoice(ENGINE_TURBINE, .82f, 1.12f, .40f, .85f);
    private static final EngineVoice WHEELED = new EngineVoice(ENGINE_WHEELED, .85f, 1.18f, .28f, .65f);
    private static final EngineVoice HOVER = new EngineVoice(ENGINE_HOVER, .90f, 1.15f, .35f, .70f);

    /** Which vents were open last frame; a vent first seen open opens silently rather than replaying. */
    private final Map<Object, Boolean> venting = new IdentityHashMap<>();
    /** Each hull's bump count as last heard; a hull first seen does not replay the bumps it already had. */
    private final Map<Object, Integer> bumped = new IdentityHashMap<>();

    public void reset() {
        venting.clear();
        bumped.clear();
    }

    /** The voice a hull's engine speaks with: a gas turbine for the high-tech laser hull, a diesel for the other tank and
     *  APC lines, a fan for hover, wheels otherwise. */
    static EngineVoice engineVoice(VehicleType type) {
        if (type.isHover()) return HOVER;
        if (type == VehicleType.LASER_TANK) return TURBINE;
        if (type.hasAnimatedTreads()) return TRACKED;
        return WHEELED;
    }

    /** Throttle from speed: a standstill is zero, top speed (either way) is one. */
    static float throttle(float speed, float maxSpeed) {
        if (!(maxSpeed > 0f)) return 0f;
        return Math.min(1f, Math.abs(speed) / maxSpeed);
    }

    /** A per-hull pitch offset of up to ±3%, stable frame to frame, so two hulls on one clip do not phase-lock. */
    static float pitchJitter(long id) {
        return (((id >> 8) & 0xffL) / 255f * 2f - 1f) * .03f;
    }

    public Frame advance(BattleSimulation sim, float listenerX, float listenerY) {
        List<Loop> loops = new ArrayList<>();
        List<Cue> cues = new ArrayList<>();
        Set<Object> live = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean over = sim.isComplete();
        engines(sim, listenerX, listenerY, over, loops);
        bumps(sim, listenerX, listenerY, over, cues);
        turretVents(sim, listenerX, listenerY, over, loops, cues, live);
        mechVents(sim, listenerX, listenerY, over, loops, cues, live);
        venting.keySet().retainAll(live);
        return new Frame(loops, cues);
    }

    private void engines(BattleSimulation sim, float lx, float ly, boolean over, List<Loop> out) {
        if (over) return;
        ConvoyService convoy = sim.convoy();
        FogOfWarService fog = sim.getFogOfWar();
        List<Loop> heard = new ArrayList<>();
        for (long id : convoy.entityIds()) {
            VehicleMission mission = convoy.mission(id);
            if (mission == null || !mission.isVisible() || mission.state == VehicleState.WRECKED) continue;
            if (!convoy.isPresent(id) || convoy.structure(id) <= 0f) continue;
            GroundBody body = convoy.body(id);
            if (body == null || !audible(convoy.faction(id), body.x, body.y, fog, lx, ly)) continue;
            VehicleType type = convoy.vehicleType(id);
            EngineVoice voice = engineVoice(type);
            // A hull with its drive shot away still has its engine running; it idles.
            float throttle = throttle(body.speed, type.maxSpeed);
            double heading = Math.toRadians(body.facingDegrees + 90f);
            heard.add(new Loop(voice.soundId, body, voice.pitch(throttle) + pitchJitter(id), voice.volume(throttle),
                    body.x, body.y, (float) Math.cos(heading) * body.speed, (float) Math.sin(heading) * body.speed));
        }
        heard.sort(Comparator.comparingDouble(loop -> distanceSquared(loop.cellX, loop.cellY, lx, ly)));
        out.addAll(heard.subList(0, Math.min(MAX_ENGINES, heard.size())));
    }

    private void bumps(BattleSimulation sim, float lx, float ly, boolean over, List<Cue> out) {
        ConvoyService convoy = sim.convoy();
        FogOfWarService fog = sim.getFogOfWar();
        Set<Object> live = Collections.newSetFromMap(new IdentityHashMap<>());
        for (long id : convoy.entityIds()) {
            GroundBody body = convoy.body(id);
            if (body == null || !convoy.isPresent(id)) continue;
            live.add(body);
            VehicleType type = convoy.vehicleType(id);
            boolean heard = !over && audible(convoy.faction(id), body.x, body.y, fog, lx, ly);
            bump(body, body.bumps, heard, type, body.lastBumpSpeed, body.x, body.y, out);
        }
        bumped.keySet().retainAll(live);
    }

    /**
     * One hull's bumps: a cue when its count has risen since last frame, at a volume that follows how fast it hit
     * against its top speed and a pitch that falls with its weight. A hull first seen cues nothing.
     */
    void bump(Object key, int count, boolean heard, VehicleType type, float impactSpeed, float x, float y,
              List<Cue> cues) {
        Integer was = bumped.put(key, count);
        if (was == null || count <= was || !heard) return;
        float hit = throttle(impactSpeed, type.maxSpeed);
        float pitch = switch (type.weight()) {
            case HEAVY -> .8f;
            case MEDIUM -> .95f;
            case LIGHT -> 1.15f;
        };
        cues.add(new Cue(HULL_BUMP, pitch, .3f + .6f * hit, x, y));
    }

    private void turretVents(BattleSimulation sim, float lx, float ly, boolean over,
                             List<Loop> loops, List<Cue> cues, Set<Object> live) {
        ConvoyService convoy = sim.convoy();
        FogOfWarService fog = sim.getFogOfWar();
        for (long id : convoy.entityIds()) {
            if (!convoy.isPresent(id)) continue;
            VehicleType type = convoy.vehicleType(id);
            if (!type.hasTurretWeapon() || convoy.turretStructure(id).mount.flux == null) continue;
            GroundTurret turret = convoy.turret(id);
            GroundBody body = convoy.body(id);
            if (turret == null || body == null) continue;
            live.add(turret);
            boolean heard = !over && audible(convoy.faction(id), body.x, body.y, fog, lx, ly);
            vent(turret, turret.fluxVenting() && !turret.destroyed(), heard, body.x, body.y, loops, cues);
        }
    }

    private void mechVents(BattleSimulation sim, float lx, float ly, boolean over,
                           List<Loop> loops, List<Cue> cues, Set<Object> live) {
        FogOfWarService fog = sim.getFogOfWar();
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (!sim.identity().type(unit).isMech() || !sim.world().hasMechLoadout(unit)) continue;
            MechFlux flux = sim.world().mechLoadout(unit).flux;
            live.add(flux);
            float x = sim.world().x(unit);
            float y = sim.world().y(unit);
            boolean seen = sim.identity().faction(unit) == Faction.MARINE
                    || fog.isInitialized() && fog.getUnitVisibility(i) == FogOfWarService.VIS_VISIBLE;
            boolean heard = !over && seen && distanceSquared(x, y, lx, ly) < RANGE_CELLS * RANGE_CELLS;
            vent(flux, flux.venting(), heard, x, y, loops, cues);
        }
    }

    /**
     * One vent: the cue as it opens, the loop while it stays open. A vent first seen already open (the camera
     * arriving on it, a load) holds its loop without the opening cue.
     */
    void vent(Object key, boolean open, boolean heard, float x, float y, List<Loop> loops, List<Cue> cues) {
        Boolean was = venting.put(key, open);
        if (!open || !heard) return;
        if (was != null && !was) cues.add(new Cue(VENT_CUE, 1f, .55f, x, y));
        loops.add(new Loop(VENT_LOOP, key, 1f, .45f, x, y, 0f, 0f));
    }

    private static boolean audible(Faction side, float x, float y, FogOfWarService fog, float lx, float ly) {
        if (distanceSquared(x, y, lx, ly) >= RANGE_CELLS * RANGE_CELLS) return false;
        if (side == Faction.MARINE) return true;
        return fog.isInitialized() && fog.isCellRevealed((int) Math.floor(x), (int) Math.floor(y));
    }

    private static float distanceSquared(float x, float y, float lx, float ly) {
        float dx = x - lx;
        float dy = y - ly;
        return dx * dx + dy * dy;
    }
}
