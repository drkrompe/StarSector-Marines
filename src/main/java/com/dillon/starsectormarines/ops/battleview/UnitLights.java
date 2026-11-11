package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;

import java.util.Arrays;

/**
 * The company's own lights ({@code battle-render-nouns.md}, night lighting): a helmet lamp on every marine, a flood on
 * every mech and machine frame, and a pair of headlamps' throw ahead of every vehicle, each a cone of cool light in
 * its facing. They light the dark (a sealed ruin's plant, a night map), never the day, and like every lamp they change
 * no sight: a cone stops at what the player cannot see and at its own building's walls. Presentation only.
 *
 * <p>{@link #gather} takes this frame's lit company bodies in view; {@link #pool} is a cone's light at a point, the one
 * definition both shade paths sample.
 */
final class UnitLights {

    /** The kinds of light the company carries: reach (cells), half-angle (degrees), strength, and how far ahead of the
     * body's centre the lamp sits (cells). */
    enum Kind {
        HELMET(5.5f, 20f, 0.5f, 0.3f),
        FLOOD(9f, 28f, 0.7f, 1.1f),
        HEADLAMP(10f, 24f, 0.7f, 1.3f);

        final float reach, cosHalf, strength, ahead;

        Kind(float reach, float halfAngleDegrees, float strength, float ahead) {
            this.reach = reach;
            this.cosHalf = (float) Math.cos(Math.toRadians(halfAngleDegrees));
            this.strength = strength;
            this.ahead = ahead;
        }
    }

    /** Cool white: LED and arc lamps, apart from the plant's warm strips and fire's orange. */
    static final float RED = 0.82f, GREEN = 0.9f, BLUE = 1f;
    /** A cone lights only ground at least this dark; in the day it would only stain the ground. */
    static final float MIN_DARKNESS = 0.3f;
    /** Zoomed out past this many screen pixels a cell, a cone is a speck not worth its upload, and none is drawn. */
    static final float MIN_CELL_PX = 8f;
    /** The most cones one frame fills: the nearest to the view's middle when more are in view. */
    static final int MAX_CONES = 96;
    /** The glow round the lamp itself, so a body's own feet read in the dark: this far, at this share. */
    static final float SPILL_CELLS = 1.4f, SPILL_SHARE = 0.35f;
    /** The cone's soft edge, as a share of the cosine between its edge and its axis. */
    static final float EDGE = 0.5f;

    private float[] x = new float[MAX_CONES], y = new float[MAX_CONES];
    private float[] dirX = new float[MAX_CONES], dirY = new float[MAX_CONES];
    private Kind[] kinds = new Kind[MAX_CONES];
    private float[] order = new float[MAX_CONES];
    private int count;

    int count() { return count; }
    float x(int i) { return x[i]; }
    float y(int i) { return y[i]; }
    float dirX(int i) { return dirX[i]; }
    float dirY(int i) { return dirY[i]; }
    Kind kind(int i) { return kinds[i]; }

    /**
     * The light of a cone of {@code kind} at {@code (dx, dy)} from its lamp, pointing along unit {@code (dirX, dirY)}:
     * full on its axis near the lamp, falling to nothing at its reach and past its edge, plus a little glow all round
     * the lamp itself.
     */
    static float pool(Kind kind, float dx, float dy, float dirX, float dirY) {
        float d2 = dx * dx + dy * dy;
        float reach2 = kind.reach * kind.reach;
        if (d2 >= reach2) return 0f;
        float d = (float) Math.sqrt(d2);
        float spill = d < SPILL_CELLS ? SPILL_SHARE * (1f - d / SPILL_CELLS) * (1f - d / SPILL_CELLS) : 0f;
        if (d < 1e-4f) return kind.strength * Math.max(spill, 1f);
        float cos = (dx * dirX + dy * dirY) / d;
        float edge = (cos - kind.cosHalf) / (EDGE * (1f - kind.cosHalf) + 1e-4f);
        float angular = edge <= 0f ? 0f : edge >= 1f ? 1f : edge * edge * (3f - 2f * edge);
        float radial = 1f - d2 / reach2;
        return kind.strength * Math.max(spill, angular * radial * radial);
    }

    /** The way a body facing {@code degrees} looks: the battle's bearings run anticlockwise from north, so 0 looks up
     * the map and 90 to the west. */
    static float forwardX(float degrees) { return (float) -Math.sin(Math.toRadians(degrees)); }

    static float forwardY(float degrees) { return (float) Math.cos(Math.toRadians(degrees)); }

    /**
     * This frame's company lights whose cones reach the visible cells {@code minX..maxX, minY..maxY}: marines, mechs and
     * machine frames standing alive, and vehicles not wrecked, of the player's side.
     */
    void gather(BattleSimulation sim, int minX, int minY, int maxX, int maxY) {
        count = 0;
        float midX = (minX + maxX + 1) * 0.5f, midY = (minY + maxY + 1) * 0.5f;
        BattleComponents c = sim.getBattleComponents();
        for (ArchetypeTable t : sim.getEntityWorld().matched(c.liveSprites)) {
            boolean mechTable = t.has(c.MECH_LAYERED_ANIMATION);
            if (!mechTable && !t.has(c.LAYERED_ANIMATION)) continue;
            float[] hp = t.floats(c.HEALTH, BattleComponents.HEALTH_HP).array();
            float[] rx = t.floats(c.POSITION, BattleComponents.POSITION_X).array();
            float[] ry = t.floats(c.POSITION, BattleComponents.POSITION_Y).array();
            Object[] types = t.objects(c.IDENTITY, BattleComponents.IDENTITY_TYPE).array();
            Object[] factions = t.objects(c.IDENTITY, BattleComponents.IDENTITY_FACTION).array();
            float[] facings = mechTable
                    ? t.floats(c.MECH_LAYERED_ANIMATION, BattleComponents.MECH_LAYERED_FACING_DEGREES).array()
                    : t.floats(c.LAYERED_ANIMATION, BattleComponents.LAYERED_FACING_DEGREES).array();
            for (int r = 0, n = t.rowCount(); r < n; r++) {
                if (hp[r] <= 0f || factions[r] != Faction.MARINE) continue;
                UnitType type = (UnitType) types[r];
                Kind kind = type.isMech() || type.isMachineFrame() ? Kind.FLOOD : Kind.HELMET;
                add(kind, rx[r], ry[r], facings[r], minX, minY, maxX, maxY, midX, midY);
            }
        }
        ConvoyService convoy = sim.convoy();
        long[] vehicles = sim.getConvoyVehicleIds();
        for (long id : vehicles) {
            VehicleMission mission = convoy.mission(id);
            if (mission == null || !mission.isVisible() || mission.state == VehicleState.WRECKED) continue;
            if (convoy.faction(id) != Faction.MARINE) continue;
            GroundBody body = convoy.body(id);
            if (body == null) continue;
            add(Kind.HEADLAMP, body.x, body.y, body.facingDegrees, minX, minY, maxX, maxY, midX, midY);
        }
    }

    private void add(Kind kind, float bodyX, float bodyY, float facingDegrees, int minX, int minY, int maxX, int maxY,
                     float midX, float midY) {
        float fx = forwardX(facingDegrees), fy = forwardY(facingDegrees);
        float lx = bodyX + fx * kind.ahead, ly = bodyY + fy * kind.ahead;
        float r = kind.reach;
        if (lx + r < minX || lx - r > maxX + 1 || ly + r < minY || ly - r > maxY + 1) return;
        float far = (lx - midX) * (lx - midX) + (ly - midY) * (ly - midY);
        int at = count;
        if (count == MAX_CONES) {
            // Full: this cone takes the place of the farthest from the view's middle, if it is nearer.
            int worst = 0;
            for (int i = 1; i < count; i++) if (order[i] > order[worst]) worst = i;
            if (order[worst] <= far) return;
            at = worst;
        } else {
            count++;
        }
        x[at] = lx;
        y[at] = ly;
        dirX[at] = fx;
        dirY[at] = fy;
        kinds[at] = kind;
        order[at] = far;
    }

    @Override public String toString() { return "UnitLights[" + count + " " + Arrays.toString(Arrays.copyOf(kinds, count)) + "]"; }
}
