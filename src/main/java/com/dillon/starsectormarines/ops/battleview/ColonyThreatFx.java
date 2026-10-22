package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.evacuation.GroundColonies;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.TurretStateService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.turret.StructureArt;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What makes the Rav Bloom's two colonies read as armed ({@code colony-breach-nouns.md}, Spore colony and Creep: Ground
 * colony). Observation only: it reads the simulation and draws, and changes nothing.
 *
 * <ul>
 *   <li><b>Spore colony.</b> Spores lift off its sacs whether or not anything is overhead: amber motes rising and
 *   drifting off the body. Its reach is not drawn (owner).</li>
 *   <li><b>Ground colony.</b> Its spines are a shot that travels under the ground. The run is drawn as a ripple
 *   heaving through the creep from the colony to the strike, opening a crack lit by an amber seam behind it; at the
 *   strike the ground heaves and a crown of bone spines bursts up, holds and sinks back, and the crack and the heave
 *   fade after. Its wind-up before the shot is the body's own (`StructureArt.Layer.windUp`).</li>
 * </ul>
 *
 * Everything is timed on the simulation clock, so a review frame shows what the battle shows. A strike is remembered
 * from the shot that carries it, keyed by its shooter and firing tick. {@code -Dbattle.render.colonyThreat=false} is
 * the control: none of it is drawn, and no wind-up.
 */
public final class ColonyThreatFx {

    /** Control-run switch; drawn unless it reads {@code false}. */
    public static final String PROPERTY = "battle.render.colonyThreat";
    static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty(PROPERTY));

    static final String SPORE_COLONY_PREFIX = "structure.spore-colony";

    /** Seconds the crack stays after the strike, fading. */
    static final float CRACK_FADE_SECONDS = 1.4f;
    /** Seconds the heave round the strike stays, fading. */
    static final float HEAVE_SECONDS = 1.6f;
    /** Spines: grown by this long after the strike, held until {@link #SPINE_HOLD}, sunk back by {@link #SPINE_GONE}. */
    static final float SPINE_GROW = 0.07f;
    static final float SPINE_HOLD = 0.45f;
    static final float SPINE_GONE = 0.85f;
    /** The amber flash where the spines break out. */
    static final float FLASH_SECONDS = 0.18f;

    private static final int SPINES = 8;
    private static final float CRACK_JITTER = 0.2f;
    private static final float CRACK_WIDTH = 0.16f;
    private static final float SEAM_WIDTH = 0.055f;
    private static final float MIN_MOTE_CELL_PX = 6f;

    // Rav Bloom palette: dusky violet and plum carapace, magenta-crimson sinew, ivory bone, amber nodes.
    private static final float[] CRACK = {0.17f, 0.07f, 0.13f};
    private static final float[] SEAM = {1.0f, 0.66f, 0.26f};
    private static final float[] HEAVE = {0.20f, 0.10f, 0.18f};
    private static final float[] CREST = {0.42f, 0.27f, 0.42f};
    private static final float[] BONE = {0.93f, 0.88f, 0.78f};
    private static final float[] SINEW = {0.54f, 0.12f, 0.24f};
    private static final float[] AMBER = {1.0f, 0.70f, 0.28f};
    private static final float[] HALO = {0.64f, 0.52f, 0.74f};

    /** One spine strike, remembered from its shot. */
    record Strike(float fromX, float fromY, float toX, float toY, float firedAt, float flight, long seed) {

        float length() {
            return (float) Math.hypot(toX - fromX, toY - fromY);
        }

        /** Seconds after which nothing of it is drawn. */
        float endsAt() {
            return firedAt + flight + Math.max(Math.max(CRACK_FADE_SECONDS, HEAVE_SECONDS), SPINE_GONE);
        }
    }

    private final BattleSprites sprites;
    private final Map<Long, Strike> strikes = new LinkedHashMap<>();
    private final PolyMesh ground = new PolyMesh(128);
    private final PolyMesh spines = new PolyMesh(128);
    private int seenTick = Integer.MIN_VALUE;

    public ColonyThreatFx(BattleSprites sprites) {
        this.sprites = sprites;
    }

    /** The ground half, drawn on the creep under the bodies: cracks, ripples and heaves. */
    public RenderSystem ground() {
        return RenderSystem.of(RenderLayer.DECALS, this::collectGround);
    }

    /** The half over the bodies: spines, flashes and spores. */
    public RenderSystem over() {
        return RenderSystem.of(RenderLayer.HAZARDS, this::collectOver);
    }

    /** The simulation clock, in seconds. */
    static float simSeconds(BattleSimulation sim) {
        return sim.getSimTickIndex() * BattleSimulation.TICK_DT;
    }

    // ---- strikes ---------------------------------------------------------------------------------------------------

    /** Remembers every ground-colony shot in flight and forgets strikes that have finished. Once per simulation tick. */
    private void observe(BattleSimulation sim) {
        int tick = sim.getSimTickIndex();
        if (tick == seenTick) return;
        seenTick = tick;
        float now = simSeconds(sim);
        for (ShotEvent shot : sim.getActiveShots()) {
            StructureDef structure = shot.turretStructureDef;
            if (structure == null || !GroundColonies.STRUCTURE.equals(structure.id)) continue;
            float flown = Math.max(0f, shot.lifetimeMax - shot.lifetime);
            int firedTick = Math.round((now - flown) / BattleSimulation.TICK_DT);
            long key = shot.shooterId * 1_000_003L + firedTick;
            if (!strikes.containsKey(key)) {
                strikes.put(key, new Strike(shot.fromX, shot.fromY, shot.toX, shot.toY,
                        firedTick * BattleSimulation.TICK_DT, Math.max(BattleSimulation.TICK_DT, shot.lifetimeMax),
                        mix(key)));
            }
        }
        for (Iterator<Strike> it = strikes.values().iterator(); it.hasNext(); ) {
            Strike strike = it.next();
            if (now > strike.endsAt() || now < strike.firedAt) it.remove();
        }
    }

    private void collectGround(RenderContext ctx, DrawList out) {
        if (!ENABLED || ctx.sim == null || ctx.camera == null) return;
        BattleSimulation sim = ctx.sim;
        observe(sim);
        BattleCamera camera = ctx.camera;
        ViewCull view = ViewCull.of(camera);
        float now = simSeconds(sim);
        float cellPx = camera.cellPxSize();
        ground.reset();
        sprites.ensureShadowSprite();
        SpriteAPI disc = sprites.shadowBlobSprite();
        for (Strike strike : strikes.values()) {
            float minX = Math.min(strike.fromX, strike.toX) - 1.5f, maxX = Math.max(strike.fromX, strike.toX) + 1.5f;
            float minY = Math.min(strike.fromY, strike.toY) - 1.5f, maxY = Math.max(strike.fromY, strike.toY) + 1.5f;
            if (!view.visibleSpan(minX, minY, maxX, maxY)) continue;
            float t = now - strike.firedAt;
            crack(camera, strike, t, ctx.alphaMult);
            if (disc != null) ripple(out, camera, disc, strike, t, cellPx, ctx.alphaMult);
        }
        if (!ground.isEmpty()) out.addPoly(RenderLayer.DECALS, ground);
    }

    private void collectOver(RenderContext ctx, DrawList out) {
        if (!ENABLED || ctx.sim == null || ctx.camera == null) return;
        BattleSimulation sim = ctx.sim;
        observe(sim);
        BattleCamera camera = ctx.camera;
        ViewCull view = ViewCull.of(camera);
        float now = simSeconds(sim);
        float cellPx = camera.cellPxSize();
        spines.reset();
        sprites.ensureEngineFxSprites();
        sprites.ensureShadowSprite();
        SpriteAPI glow = sprites.engineGlowSprite();
        for (Strike strike : strikes.values()) {
            if (!view.visible(strike.toX, strike.toY, 2f)) continue;
            float t = now - strike.firedAt - strike.flight;
            if (t < 0f) continue;
            spineCrown(camera, strike, t, ctx.alphaMult);
            if (glow != null && t < FLASH_SECONDS) {
                float k = 1f - t / FLASH_SECONDS;
                float size = 1.5f * cellPx * (0.7f + 0.3f * k);
                out.addAdditiveSprite(RenderLayer.HAZARDS, glow, camera.cellToScreenX(strike.toX),
                        camera.cellToScreenY(strike.toY), size, size, 0f,
                        AMBER[0], AMBER[1], AMBER[2], 0.85f * k * ctx.alphaMult);
            }
        }
        if (!spines.isEmpty()) out.addPoly(RenderLayer.HAZARDS, spines);
        if (cellPx >= MIN_MOTE_CELL_PX) spores(ctx, out, view, now);
    }

    /**
     * The crack the run opens: a jagged line from the colony to the head of the run, a dark split in the creep with an
     * amber seam lit along it. The seam is brightest at the head and dies first; the split fades after the strike.
     */
    private void crack(BattleCamera camera, Strike strike, float t, float alphaMult) {
        float length = strike.length();
        if (length < 0.05f) return;
        float reach = Math.min(1f, t / strike.flight);
        float after = Math.max(0f, t - strike.flight);
        float splitAlpha = 0.8f * Math.max(0f, 1f - after / CRACK_FADE_SECONDS) * alphaMult;
        float seamAlpha = 0.95f * Math.max(0f, 1f - after / (CRACK_FADE_SECONDS * 0.45f)) * alphaMult;
        if (splitAlpha <= 0f) return;
        int segments = Math.max(4, Math.round(length * 2.2f));
        float dx = (strike.toX - strike.fromX) / length, dy = (strike.toY - strike.fromY) / length;
        float px = -dy, py = dx;
        float headAt = reach * segments;
        float prevX = strike.fromX, prevY = strike.fromY;
        for (int i = 1; i <= segments; i++) {
            if (i - 1 >= headAt) break;
            float u = Math.min(i, headAt) / segments;
            float offset = (i == segments || u >= 1f) ? 0f : CRACK_JITTER * signed(strike.seed, i);
            float x = strike.fromX + dx * length * u + px * offset;
            float y = strike.fromY + dy * length * u + py * offset;
            // Narrow at the colony and at the head, widest along the middle of the run.
            float taper = 0.55f + 0.45f * (float) Math.sin(Math.PI * Math.min(1f, u));
            segment(ground, camera, prevX, prevY, x, y, CRACK_WIDTH * taper, CRACK, splitAlpha);
            float behind = headAt - i;
            float lit = seamAlpha * Math.max(0.25f, 1f - Math.max(0f, behind) / (segments * 0.8f));
            segment(ground, camera, prevX, prevY, x, y, SEAM_WIDTH * taper, SEAM, lit);
            prevX = x;
            prevY = y;
        }
        // Splits radiating from the strike, opened as the spines break out.
        if (after > 0f) {
            float grown = Math.min(1f, after / SPINE_GROW);
            for (int k = 0; k < 5; k++) {
                double a = 2.0 * Math.PI * (k + 0.5f * unit(strike.seed, 40 + k)) / 5.0;
                float len = (0.5f + 0.45f * unit(strike.seed, 50 + k)) * grown;
                float ex = strike.toX + (float) Math.cos(a) * len, ey = strike.toY + (float) Math.sin(a) * len;
                segment(ground, camera, strike.toX, strike.toY, ex, ey, CRACK_WIDTH * 0.75f, CRACK, splitAlpha);
            }
        }
    }

    /** The heave of the run under the creep while it travels, and the torn ground round the strike after. */
    private void ripple(DrawList out, BattleCamera camera, SpriteAPI disc, Strike strike, float t, float cellPx,
                        float alphaMult) {
        if (t < strike.flight) {
            float u = t / strike.flight;
            float x = strike.fromX + (strike.toX - strike.fromX) * u;
            float y = strike.fromY + (strike.toY - strike.fromY) * u;
            float sx = camera.cellToScreenX(x), sy = camera.cellToScreenY(y);
            float size = 1.0f * cellPx;
            out.addSprite(RenderLayer.DECALS, disc, sx, sy, size, size, 0f,
                    HEAVE[0], HEAVE[1], HEAVE[2], 0.75f * alphaMult);
            float crest = 0.55f * cellPx;
            out.addSprite(RenderLayer.DECALS, disc, sx - 0.08f * cellPx, sy + 0.08f * cellPx, crest, crest, 0f,
                    CREST[0], CREST[1], CREST[2], 0.8f * alphaMult);
            return;
        }
        float after = t - strike.flight;
        if (after > HEAVE_SECONDS) return;
        float fade = 1f - after / HEAVE_SECONDS;
        float size = (1.5f + 0.3f * Math.min(1f, after / SPINE_GROW)) * cellPx;
        out.addSprite(RenderLayer.DECALS, disc, camera.cellToScreenX(strike.toX), camera.cellToScreenY(strike.toY),
                size, size, 0f, HEAVE[0], HEAVE[1], HEAVE[2], 0.8f * fade * alphaMult);
    }

    /**
     * The crown of spines that bursts up at the strike: bone blades leaning out from the centre, crimson at the root,
     * each with its shadow, grown at once, held, then sunk back into the ground.
     */
    private void spineCrown(BattleCamera camera, Strike strike, float t, float alphaMult) {
        float grown;
        if (t < SPINE_GROW) grown = t / SPINE_GROW;
        else if (t < SPINE_HOLD) grown = 1f;
        else if (t < SPINE_GONE) grown = 1f - (t - SPINE_HOLD) / (SPINE_GONE - SPINE_HOLD);
        else return;
        grown = Math.max(0f, grown);
        float alpha = alphaMult * Math.min(1f, 0.35f + grown);
        for (int pass = 0; pass < 2; pass++) {
            boolean shadow = pass == 0;
            for (int k = 0; k <= SPINES; k++) {
                // The last is the tallest, standing up out of the centre (foreshortened toward the top of the screen).
                boolean centre = k == SPINES;
                double a = centre ? Math.PI / 2.0 + 0.25 * signed(strike.seed, 90)
                        : 2.0 * Math.PI * (k + 0.6f * unit(strike.seed, 60 + k)) / SPINES;
                float ax = (float) Math.cos(a), ay = (float) Math.sin(a);
                float root = centre ? 0f : 0.08f + 0.16f * unit(strike.seed, 70 + k);
                float len = (centre ? 1.7f : 1.0f + 0.6f * unit(strike.seed, 80 + k)) * grown;
                float half = centre ? 0.15f : 0.1f;
                float bx = strike.toX + ax * root, by = strike.toY + ay * root;
                float tx = bx + ax * len, ty = by + ay * len;
                if (shadow) {
                    // Cast down and to the right, the battle's default sun, flat on the ground.
                    float s = 0.18f * grown;
                    triangle(spines, camera, bx + s * 0.3f, by - s * 0.3f, tx + s, ty - s, half,
                            0f, 0f, 0f, 0.3f * alpha);
                    continue;
                }
                triangle(spines, camera, bx, by, tx, ty, half, BONE[0], BONE[1], BONE[2], alpha);
                float mx = bx + (tx - bx) * 0.42f, my = by + (ty - by) * 0.42f;
                trapezoid(spines, camera, bx, by, mx, my, half, half * 0.62f, SINEW[0], SINEW[1], SINEW[2], alpha);
            }
        }
    }

    // ---- spore colonies --------------------------------------------------------------------------------------------

    /** Spores lifting off every living spore colony in view. */
    private void spores(RenderContext ctx, DrawList out, ViewCull view, float now) {
        BattleSimulation sim = ctx.sim;
        BattleCamera camera = ctx.camera;
        World world = sim.world();
        TurretStateService turrets = sim.turretState();
        SpriteAPI glow = sprites.engineGlowSprite();
        SpriteAPI disc = sprites.shadowBlobSprite();
        if (glow == null || disc == null) return;
        float cellPx = camera.cellPxSize();
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            if (!turrets.isTurret(id) || !world.isAlive(id)) continue;
            StructureDef structure = turrets.structure(id);
            if (!structure.id.startsWith(SPORE_COLONY_PREFIX)) continue;
            float body = bodyCells(structure);
            float x = world.renderX(id), y = world.renderY(id);
            if (!view.visible(x, y, body * 1.5f)) continue;
            long seed = mix(id);
            motes(out, camera, glow, disc, x, y, body, seed, now, cellPx, ctx.alphaMult);
        }
    }

    /**
     * Spore motes lifting off the sacs: each an amber spark in a lilac halo, swelling and drifting up the screen as it
     * rises toward the camera, swaying as it goes, and fading out; one every half second or so per grade of body.
     */
    private static void motes(DrawList out, BattleCamera camera, SpriteAPI glow, SpriteAPI disc, float x, float y,
                              float body, long seed, float now, float cellPx, float alphaMult) {
        int count = Math.round(body * 2.5f);
        for (int k = 0; k < count; k++) {
            long s = mix(seed + k * 0x9E3779B97F4A7C15L);
            float period = 2.6f + 1.0f * unit(s, 1);
            float u = frac(now / period + unit(s, 2));
            double a = 2.0 * Math.PI * unit(s, 3);
            float start = body * 0.22f * (float) Math.sqrt(unit(s, 4));
            float rise = (1.0f + 0.6f * unit(s, 5)) * u;
            float sway = 0.18f * (float) Math.sin(2.0 * Math.PI * (u * 1.3f + unit(s, 6)));
            float mx = x + (float) Math.cos(a) * start + sway + 0.25f * u * signed(s, 7);
            float my = y + (float) Math.sin(a) * start * 0.6f + rise;
            float fade = (float) Math.pow(Math.sin(Math.PI * u), 0.8);
            float size = (0.10f + 0.06f * unit(s, 8)) * (1f + 0.6f * u) * cellPx;
            float sx = camera.cellToScreenX(mx), sy = camera.cellToScreenY(my);
            out.addSprite(RenderLayer.HAZARDS, disc, sx, sy, size * 3.2f, size * 3.2f, 0f,
                    HALO[0], HALO[1], HALO[2], 0.28f * fade * alphaMult);
            out.addAdditiveSprite(RenderLayer.HAZARDS, glow, sx, sy, size * 2.4f, size * 2.4f, 0f,
                    AMBER[0], AMBER[1], AMBER[2], 0.9f * fade * alphaMult);
        }
    }

    // ---- geometry --------------------------------------------------------------------------------------------------

    private static float bodyCells(StructureDef structure) {
        float cells = 1f;
        if (structure.art != null) {
            for (StructureArt.Layer layer : structure.art.layers()) cells = Math.max(cells, layer.cells());
        }
        return cells;
    }

    /** A strip of {@code width} cells from one cell point to another. */
    private static void segment(PolyMesh mesh, BattleCamera camera, float x0, float y0, float x1, float y1,
                                float width, float[] rgb, float alpha) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-4f || alpha <= 0f) return;
        float nx = -dy / len * width * 0.5f, ny = dx / len * width * 0.5f;
        mesh.appendQuad(
                camera.cellToScreenX(x0 + nx), camera.cellToScreenY(y0 + ny),
                camera.cellToScreenX(x1 + nx), camera.cellToScreenY(y1 + ny),
                camera.cellToScreenX(x1 - nx), camera.cellToScreenY(y1 - ny),
                camera.cellToScreenX(x0 - nx), camera.cellToScreenY(y0 - ny),
                rgb[0], rgb[1], rgb[2], alpha);
    }

    /** A blade from a base {@code half} cells either side of {@code (bx, by)} to a point at {@code (tx, ty)}. */
    private static void triangle(PolyMesh mesh, BattleCamera camera, float bx, float by, float tx, float ty,
                                 float half, float r, float g, float b, float alpha) {
        float dx = tx - bx, dy = ty - by;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-4f || alpha <= 0f) return;
        float nx = -dy / len * half, ny = dx / len * half;
        float sx = camera.cellToScreenX(tx), sy = camera.cellToScreenY(ty);
        mesh.appendQuad(
                camera.cellToScreenX(bx + nx), camera.cellToScreenY(by + ny), sx, sy, sx, sy,
                camera.cellToScreenX(bx - nx), camera.cellToScreenY(by - ny), r, g, b, alpha);
    }

    /** The root of a blade: {@code half0} either side at the base narrowing to {@code half1} at {@code (mx, my)}. */
    private static void trapezoid(PolyMesh mesh, BattleCamera camera, float bx, float by, float mx, float my,
                                  float half0, float half1, float r, float g, float b, float alpha) {
        float dx = mx - bx, dy = my - by;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-4f || alpha <= 0f) return;
        float ux = -dy / len, uy = dx / len;
        mesh.appendQuad(
                camera.cellToScreenX(bx + ux * half0), camera.cellToScreenY(by + uy * half0),
                camera.cellToScreenX(mx + ux * half1), camera.cellToScreenY(my + uy * half1),
                camera.cellToScreenX(mx - ux * half1), camera.cellToScreenY(my - uy * half1),
                camera.cellToScreenX(bx - ux * half0), camera.cellToScreenY(by - uy * half0), r, g, b, alpha);
    }

    // ---- seeded numbers --------------------------------------------------------------------------------------------

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    /** A number in [0, 1) fixed by {@code seed} and {@code salt}. */
    static float unit(long seed, int salt) {
        return (mix(seed + salt * 0xD1B54A32D192ED03L) >>> 40) / (float) (1L << 24);
    }

    /** A number in [-1, 1) fixed by {@code seed} and {@code salt}. */
    static float signed(long seed, int salt) {
        return unit(seed, salt) * 2f - 1f;
    }

    private static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
