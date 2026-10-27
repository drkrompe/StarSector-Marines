package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.fx.EffectsService;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

/**
 * The dust a moving hull raises, queued as presentation events for whatever draws the battle.
 *
 * <p>A wheeled or tracked hull moving over loose ground throws a soft puff off each trailing wheel or track end
 * ({@link RunningGear}), back down that track's line and barely outward, harder the faster it goes; on pavement it
 * raises nothing. A hover hull blows a low
 * ring of haze out from under its skirt whether or not it is moving, because the lift fans never stop, and over
 * water that haze is spray. Runs in the serial part of the tick and draws nothing from the simulation's random
 * stream: puff placement is a hash of the hull and the tick, so a battle replays identically whoever watches it.
 */
public final class VehicleDustEmitter {

    /** Ticks between puffs off a wheeled or tracked hull's corners. */
    static final int WHEEL_EVERY_TICKS = 3;
    /** Ticks between rings of haze round a hover skirt. */
    static final int SKIRT_EVERY_TICKS = 5;
    /** Slowest speed, in cells per second, at which a wheel throws dust. */
    static final float MIN_WHEEL_SPEED = .6f;
    /** Puffs in one ring round a skirt. */
    static final int SKIRT_PUFFS = 5;
    /** How fast a wheel's puff drifts outward from its track, cells per second. */
    static final float SPLAY = .08f;

    public void tick(int tickIndex, ConvoyService convoy, CellTopology topology, EffectsService fx) {
        for (int i = 0, n = convoy.vehicleCount(); i < n; i++) {
            long id = convoy.vehicleAt(i);
            VehicleType type = convoy.vehicleType(id);
            VehicleMission mission = convoy.mission(id);
            if (type == null || mission == null || !mission.isVisible() || mission.state == VehicleState.WRECKED
                    || !convoy.isPresent(id)) continue;
            GroundBody body = convoy.body(id);
            if (body == null) continue;
            int phase = (int) Math.floorMod(id, 7L);
            if (type.isHover()) {
                if ((tickIndex + phase) % SKIRT_EVERY_TICKS == 0) skirt(id, tickIndex, type, body, topology, fx);
            } else if (Math.abs(body.speed) >= MIN_WHEEL_SPEED && (tickIndex + phase) % WHEEL_EVERY_TICKS == 0) {
                wheels(id, tickIndex, type, body, topology, fx);
            }
        }
    }

    /** Whether a wheel or track raises dust from this ground: earth, sand, snow and rubble, never paving or floor. */
    static boolean loose(GroundKind kind) {
        return kind == GroundKind.DIRT || kind == GroundKind.GRASS || kind == GroundKind.SAND
                || kind == GroundKind.SNOW || kind == GroundKind.RUBBLE;
    }

    /** What a hover skirt blows off this ground: spray off water, haze off anything open, nothing indoors. */
    static int skirtKind(GroundKind kind) {
        if (kind == GroundKind.WATER) return EffectsService.DUST_SPRAY;
        if (kind == GroundKind.INDOOR || kind == GroundKind.VOID) return -1;
        return EffectsService.DUST_SKIRT;
    }

    private static void wheels(long id, int tick, VehicleType type, GroundBody body, CellTopology topology,
                               EffectsService fx) {
        double a = Math.toRadians(body.facingDegrees);
        float fx0 = (float) -Math.sin(a), fy0 = (float) Math.cos(a);
        float rx = fy0, ry = -fx0;
        // The trailing contact is the rear wheels or track ends going forward and the front ones in reverse.
        RunningGear gear = type.runningGear();
        float sign = body.speed >= 0f ? 1f : -1f;
        float speed = Math.abs(body.speed);
        float back = (sign > 0f ? gear.rearCells() : gear.frontCells()) * sign;
        float side = gear.halfGaugeCells();
        float radius = .26f + .05f * Math.min(speed, 5f);
        for (int s = -1; s <= 1; s += 2) {
            float x = body.x - fx0 * back + rx * side * s;
            float y = body.y - fy0 * back + ry * side * s;
            if (!topology.inBounds((int) Math.floor(x), (int) Math.floor(y))
                    || !loose(topology.getGroundKind((int) Math.floor(x), (int) Math.floor(y)))) continue;
            float j = jitter(id, tick, s);
            // Thrown back down the track's own line, splaying only a little: the trail stays in the track's wake.
            float vx = -fx0 * sign * speed * .22f + rx * s * SPLAY * (1f + .3f * j);
            float vy = -fy0 * sign * speed * .22f + ry * s * SPLAY * (1f + .3f * j);
            fx.emitGroundDust(x + .08f * j, y - .08f * j, vx, vy, radius, EffectsService.DUST_WHEEL);
        }
    }

    private static void skirt(long id, int tick, VehicleType type, GroundBody body, CellTopology topology,
                              EffectsService fx) {
        double a = Math.toRadians(body.facingDegrees);
        float fx0 = (float) -Math.sin(a), fy0 = (float) Math.cos(a);
        float rx = fy0, ry = -fx0;
        float halfL = type.visualLengthCells * .5f, halfW = type.visualWidthCells * .5f;
        float turn = (tick * 37) % 360;
        for (int k = 0; k < SKIRT_PUFFS; k++) {
            double t = Math.toRadians(turn + 360f * k / SKIRT_PUFFS);
            float along = (float) Math.cos(t), across = (float) Math.sin(t);
            float x = body.x + fx0 * along * halfL + rx * across * halfW;
            float y = body.y + fy0 * along * halfL + ry * across * halfW;
            int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
            if (!topology.inBounds(cx, cy)) continue;
            int kind = skirtKind(topology.getGroundKind(cx, cy));
            if (kind < 0) continue;
            float out = 1.3f + .3f * jitter(id, tick, k);
            float ox = fx0 * along + rx * across, oy = fy0 * along + ry * across;
            float len = (float) Math.hypot(ox, oy);
            float vx = ox / len * out - fx0 * body.speed * .15f;
            float vy = oy / len * out - fy0 * body.speed * .15f;
            fx.emitGroundDust(x, y, vx, vy, kind == EffectsService.DUST_SPRAY ? .32f : .4f, kind);
        }
    }

    /** A replayable value in [-1, 1] for one puff. */
    static float jitter(long id, int tick, int k) {
        long h = id * 0x9E3779B97F4A7C15L + tick * 0xC2B2AE3D27D4EB4FL + k * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return ((h & 0xFFFF) / 32767.5f) - 1f;
    }
}
