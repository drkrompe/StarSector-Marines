package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipHitFx;
import com.dillon.starsectormarines.battle.warship.WarshipHitFx.Character;
import com.dillon.starsectormarines.battle.warship.WarshipHitFx.Kind;
import com.dillon.starsectormarines.battle.warship.WarshipHitFx.Mark;
import com.dillon.starsectormarines.battle.warship.WarshipHitFx.Puff;
import com.dillon.starsectormarines.battle.warship.WarshipPlating;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.awt.Color;

/**
 * Draws a hull fight's hits ({@link WarshipHitFx}) so a round that lands on a shield and one that lands on metal can
 * never be mistaken for each other.
 *
 * <ul>
 *   <li><b>On a shield</b> the hit is light on the bubble: a cold white flare at the point on the arc, a ripple
 *       running both ways along the rim and a ring spreading over the surface from the point, and sparks skipping off
 *       it at the angle of reflection. Kinetic (doubled on shields) flares hardest and skips the most; high explosive
 *       is a dull warm splash that hardly troubles it; energy ripples in the weapon's own colour; fragmentation is a
 *       spray of fine sparks.</li>
 *   <li><b>On metal</b> it is fire and matter: a hot flash, sparks thrown back off the plate, chips of armour plate
 *       tumbling away, a lick of smoke and a scorch that glows and dies. High explosive is a fireball; kinetic a hard
 *       white flash and long sparks; energy a small searing flash that leaves the plate molten; fragmentation a wide
 *       spray. A round that holes the hull through adds a second blast, a shockwave and a shower of plate; a mount
 *       lost with its plate goes up in its own blast.</li>
 *   <li><b>A shield under strain</b> shows it: it shifts from cold blue toward violet as the pool fills, its rim
 *       shivers and crackles, and when it goes it goes visibly — shattering outward on an overload, guttering out when
 *       an automatic shield locks itself down. An overloaded ship crawls with arcs until it recovers.</li>
 *   <li><b>An open breach</b> vents: grey vapour and tongues of fire jet out of the rim of the hole, and a ship under
 *       way trails smoke behind it.</li>
 * </ul>
 *
 * <p>Sizes are the game's own hit glow at the hull's drawn scale, so a hit is as large against the ship as in the
 * base game, and readable from a duel camera's distance. Every frame is a pure function of the marks, the ships and
 * the warships' clock, which advances in review frames as in the game.
 */
final class WarshipHitFxRenderer {

    private static final String GLOW = "graphics/fx/particlealpha64linear.png";
    private static final String HIT_GLOW = "graphics/fx/hit_glow.png";
    private static final String RING = "graphics/fx/explosion_ring0.png";
    private static final String SMOKE = "graphics/fx/smoke32.png";
    private static final String[] FIREBALLS = {"graphics/fx/explosion0.png", "graphics/fx/explosion1.png",
            "graphics/fx/explosion2.png", "graphics/fx/explosion3.png", "graphics/fx/explosion4.png"};
    private static final Color SHIELD_TINT = new Color(140, 215, 255);
    private static final Color SHIELD_STRAIN = new Color(205, 150, 255);
    private static final Color SHIELD_COLLAPSE = new Color(255, 190, 120);

    private WarshipHitFxRenderer() {}

    /** Every mark on {@code ship}'s hull (not its shield): drawn over the hull and its mounts, under the shield. */
    static void hullMarks(DrawList out, BattleCamera cam, BattleSprites sprites, WarshipHitFx fx, Warship ship,
                          float alpha, float now, boolean detail) {
        for (Mark m : fx.marks()) {
            if (m.ship() != ship) continue;
            switch (m.kind()) {
                case HULL, BREACH, MOUNT_LOST -> hullHit(out, cam, sprites, m, alpha, now, detail);
                default -> { }
            }
        }
    }

    /** Every mark on {@code ship}'s shield, and its collapses: drawn over the bubble. */
    static void shieldMarks(DrawList out, BattleCamera cam, BattleSprites sprites, WarshipHitFx fx, Warship ship,
                            float alpha, float now, boolean detail) {
        for (Mark m : fx.marks()) {
            if (m.ship() != ship) continue;
            switch (m.kind()) {
                case SHIELD -> shieldHit(out, cam, sprites, fx, m, alpha, now, detail);
                case SHIELD_COLLAPSE -> collapse(out, cam, sprites, fx, m, alpha, now);
                case SHIELD_LOCKOUT -> lockout(out, cam, sprites, fx, m, alpha, now);
                default -> { }
            }
        }
    }

    // ---- shield ----------------------------------------------------------------------------------------------------

    /** The shield's colour at {@code strain} (0 at rest, 1 at its drop level). */
    static float[] shieldColour(float strain) {
        float k = Math.max(0f, Math.min(1f, strain));
        return new float[]{
                lerp(SHIELD_TINT.getRed(), SHIELD_STRAIN.getRed(), k) / 255f,
                lerp(SHIELD_TINT.getGreen(), SHIELD_STRAIN.getGreen(), k) / 255f,
                lerp(SHIELD_TINT.getBlue(), SHIELD_STRAIN.getBlue(), k) / 255f};
    }

    /** How strained a shield is at a flux share: nothing below half, all of it at the automatic drop level. */
    static float strain(float fluxFraction) {
        float t = Math.max(0f, Math.min(1f, (fluxFraction - 0.5f) / 0.4f));
        return t * t * (3f - 2f * t);
    }

    private static void shieldHit(DrawList out, BattleCamera cam, BattleSprites sprites, WarshipHitFx fx, Mark m,
                                  float alpha,
                                  float now, boolean detail) {
        Warship ship = m.ship();
        float t = now - m.bornAt();
        float life = m.lifeSeconds();
        float u = t / life;
        if (u < 0f || u >= 1f) return;
        float cellPx = cam.cellPxSize();
        float[] p = world(ship, m.localX(), m.localY());
        float cx = ship.shieldCentreX();
        float cy = ship.shieldCentreY();
        float radius = ship.shieldRadiusCells();
        float nx = p[0] - cx;
        float ny = p[1] - cy;
        float nl = (float) Math.hypot(nx, ny);
        if (nl < 1e-4f) return;
        nx /= nl;
        ny /= nl;
        // The arc's own angle convention: a point at heading h lies at centre + R * (-sin h, cos h).
        float theta = (float) Math.toDegrees(Math.atan2(-nx, ny));

        Character ch = m.character();
        float size = m.sizeCells() * cellPx * (ch == Character.KINETIC ? 1.25f
                : ch == Character.HIGH_EXPLOSIVE ? 0.85f : ch == Character.FRAGMENTATION ? 0.7f : 1f)
                * (m.beam() ? 0.7f : 1f);
        float[] base = shieldColour(strain(m.flux()));
        float[] hit = base;
        if (ch == Character.HIGH_EXPLOSIVE) hit = mix(base, new float[]{1f, 0.68f, 0.38f}, 0.55f);
        else if (ch == Character.ENERGY && m.colour() != null) hit = mix(base, rgb(m.colour()), 0.5f);
        float sx = cam.cellToScreenX(p[0]);
        float sy = cam.cellToScreenY(p[1]);
        ShuttleSpriteCache glow = sprites.spriteByPath(GLOW);
        ShuttleSpriteCache hitGlow = sprites.spriteByPath(HIT_GLOW);
        float fade = 1f - u;

        // The ripple along the rim: two fronts leaving the point both ways, and a bright patch where it struck.
        float arc = Math.min(360f, ship.shieldArcDegrees());
        float facing = shieldFacing(ship, fx);
        float spread = (ch == Character.KINETIC ? 75f : ch == Character.HIGH_EXPLOSIVE ? 35f : 55f)
                * (float) Math.sqrt(u) * (m.beam() ? 0.5f : 1f);
        float rimWidth = Math.max(2f, 0.28f * cellPx);
        rimArc(out, cam, ship, cx, cy, radius, theta - 12f, theta + 12f, arc, facing, rimWidth * 2.2f,
                hit, 0.85f * fade * fade * alpha);
        for (int side = -1; side <= 1; side += 2) {
            float front = theta + side * spread;
            rimArc(out, cam, ship, cx, cy, radius, front - 7f, front + 7f, arc, facing, rimWidth * 1.4f,
                    hit, 0.6f * fade * alpha);
        }
        // A ring spreading over the surface from the point.
        float ringR = m.sizeCells() * (0.4f + 1.3f * (float) Math.sqrt(u)) * (m.beam() ? 0.6f : 1f);
        circle(out, cam, p[0], p[1], ringR, 18, Math.max(1.5f, 0.12f * cellPx), hit, 0.35f * fade * alpha);

        // The flare: a cold white core on a broad glow in the shield's colour.
        float flash = Math.max(0f, 1f - t / (life * 0.45f));
        if (glow != null) {
            out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite, sx, sy, size * 2.5f, size * 2.5f, 0f,
                    hit[0], hit[1], hit[2], 0.8f * fade * fade * alpha);
            out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite, sx, sy, size * 1.1f, size * 1.1f, 0f,
                    1f, 1f, 1f, 0.95f * flash * alpha);
        }
        if (hitGlow != null && ch != Character.HIGH_EXPLOSIVE) {
            out.addAdditiveSprite(RenderLayer.SHUTTLES, hitGlow.sprite, sx, sy, size * 2f, size * 2f,
                    (m.seed() & 0xFF) * 1.4f, hit[0], hit[1], hit[2], 0.7f * flash * alpha);
        }
        if (!detail) return;

        // Sparks skipping off the bubble at the angle of reflection.
        float[] out2 = world(ship, m.outX(), m.outY(), true);
        float dx = -out2[0];
        float dy = -out2[1];
        float dot = dx * nx + dy * ny;
        float rx = dx - 2f * dot * nx;
        float ry = dy - 2f * dot * ny;
        if (dot > 0f) {
            rx = nx;
            ry = ny;
        }
        int sparks = m.beam() ? 3 : switch (ch) {
            case KINETIC -> 11;
            case HIGH_EXPLOSIVE -> 4;
            case ENERGY -> 6;
            case FRAGMENTATION -> 14;
            case OTHER -> 6;
        };
        float reach = m.sizeCells() * (ch == Character.FRAGMENTATION ? 2.2f : ch == Character.KINETIC ? 4.5f : 3f);
        float[] sparkColour = ch == Character.HIGH_EXPLOSIVE ? new float[]{1f, 0.75f, 0.45f}
                : mix(hit, new float[]{1f, 1f, 1f}, 0.55f);
        sparks(out, cam, glow, m.seed(), sparks, p[0], p[1], rx, ry, 45f, reach, u,
                Math.max(1.5f, (ch == Character.FRAGMENTATION ? 0.12f : 0.2f) * cellPx), sparkColour,
                0.95f * alpha);
    }

    /** The shield gives under an overload: the bubble flashes and breaks into shards flung outward. */
    private static void collapse(DrawList out, BattleCamera cam, BattleSprites sprites, WarshipHitFx fx, Mark m,
                                 float alpha,
                                 float now) {
        Warship ship = m.ship();
        float u = (now - m.bornAt()) / m.lifeSeconds();
        if (u < 0f || u >= 1f) return;
        float cellPx = cam.cellPxSize();
        float cx = ship.shieldCentreX();
        float cy = ship.shieldCentreY();
        float radius = ship.shieldRadiusCells();
        float arc = Math.min(360f, ship.shieldArcDegrees());
        float start = eventFacing(m, fx) - arc * 0.5f;
        float fade = 1f - u;
        float[] c = rgb(SHIELD_COLLAPSE);
        float[] white = {1f, 0.97f, 0.92f};
        int shards = Math.max(8, Math.round(arc / 14f));
        float width = Math.max(2f, 0.3f * cellPx);
        for (int i = 0; i < shards; i++) {
            int h = WarshipHitFx.mix(m.seed() + i * 977);
            float a0 = start + arc * (i + 0.12f) / shards;
            float a1 = start + arc * (i + 0.88f) / shards;
            float mid = (a0 + a1) * 0.5f;
            // Each shard flies out along its own radius and turns a little as it goes.
            float fly = radius * (0.08f + 0.4f * WarshipHitFx.unit(h)) * (float) Math.sqrt(u);
            float twist = (WarshipHitFx.unit(h >>> 5) - 0.5f) * 50f * u;
            float r = radius + fly;
            float[] q0 = onArc(cx, cy, r, a0 + twist);
            float[] q1 = onArc(cx, cy, r, a1 + twist);
            float[] col = u < 0.2f ? white : c;
            out.addLine(RenderLayer.SHUTTLES, cam.cellToScreenX(q0[0]), cam.cellToScreenY(q0[1]),
                    cam.cellToScreenX(q1[0]), cam.cellToScreenY(q1[1]), width * (1.6f - u),
                    col[0], col[1], col[2], 0.9f * fade * alpha);
            float[] q = onArc(cx, cy, r, mid + twist);
            ShuttleSpriteCache glow = sprites.spriteByPath(GLOW);
            if (glow != null) {
                float s = radius * 0.22f * cellPx * fade;
                out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite, cam.cellToScreenX(q[0]),
                        cam.cellToScreenY(q[1]), s, s, 0f, c[0], c[1], c[2], 0.5f * fade * alpha);
            }
        }
        // The flash of the bubble going, and the emitter's own burst at its heart.
        rimArc(out, cam, ship, cx, cy, radius * (1f + 0.15f * u), start, start + arc, arc,
                eventFacing(m, fx), width * 3f, white, 0.7f * (1f - Math.min(1f, u * 3f)) * alpha);
        ShuttleSpriteCache glow = sprites.spriteByPath(GLOW);
        if (glow != null) {
            float s = radius * 1.4f * cellPx * (0.6f + 0.6f * u);
            out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite, cam.cellToScreenX(cx), cam.cellToScreenY(cy),
                    s, s, 0f, c[0], c[1], c[2], 0.45f * fade * fade * alpha);
        }
    }

    /** An automatic shield shutting itself down at its drop level: the rim stutters, dims and sheds sparks. */
    private static void lockout(DrawList out, BattleCamera cam, BattleSprites sprites, WarshipHitFx fx, Mark m,
                                float alpha,
                                float now) {
        Warship ship = m.ship();
        float t = now - m.bornAt();
        float u = t / m.lifeSeconds();
        if (u < 0f || u >= 1f) return;
        float cellPx = cam.cellPxSize();
        // On and off at a hashed stutter, dimmer as it goes.
        int step = (int) Math.floor(t * 22f);
        boolean on = (WarshipHitFx.mix(m.seed() + step) & 3) != 0;
        float fade = 1f - u;
        float cx = ship.shieldCentreX();
        float cy = ship.shieldCentreY();
        float radius = ship.shieldRadiusCells() * (1f - 0.12f * u);
        float arc = Math.min(360f, ship.shieldArcDegrees());
        float start = eventFacing(m, fx) - arc * 0.5f;
        float[] c = shieldColour(1f);
        if (on) {
            rimArc(out, cam, ship, cx, cy, radius, start, start + arc, arc, eventFacing(m, fx),
                    Math.max(2f, 0.3f * cellPx), c, 0.75f * fade * alpha);
        }
        ShuttleSpriteCache glow = sprites.spriteByPath(GLOW);
        for (int i = 0; i < 6; i++) {
            int h = WarshipHitFx.mix(m.seed() * 31 + i);
            float a = start + arc * WarshipHitFx.unit(h);
            float[] q = onArc(cx, cy, radius, a);
            float[] n = onArc(0f, 0f, 1f, a);
            sparks(out, cam, glow, h, 2, q[0], q[1], n[0], n[1], 30f, radius * 0.25f, u,
                    Math.max(1.5f, 0.16f * cellPx), new float[]{0.9f, 0.85f, 1f}, 0.8f * alpha);
        }
    }

    /**
     * What a raised shield shows of its strain at every frame, over the bubble the caller has drawn: crackling arcs
     * walking its rim once the pool is past half, more of them the fuller it is.
     */
    static void strainCrackle(DrawList out, BattleCamera cam, WarshipHitFx fx, Warship ship, float strain,
                              float alpha, float now) {
        if (strain <= 0.15f || ship.shieldLevel() <= 0f) return;
        float cellPx = cam.cellPxSize();
        float cx = ship.shieldCentreX();
        float cy = ship.shieldCentreY();
        float radius = ship.shieldRadiusCells() * (0.8f + 0.2f * ship.shieldLevel());
        float arc = Math.min(360f, ship.shieldArcDegrees());
        float start = shieldFacing(ship, fx) - arc * 0.5f;
        int count = Math.round(strain * 3f);
        int step = (int) Math.floor(now * 12f);
        float width = Math.max(1.5f, 0.14f * cellPx);
        for (int i = 0; i < count; i++) {
            int h = WarshipHitFx.mix(step * 7919 + i * 104729 ^ salt(ship));
            float a0 = start + arc * WarshipHitFx.unit(h);
            float len = 8f + 10f * WarshipHitFx.unit(h >>> 3);
            float lastX = Float.NaN;
            float lastY = Float.NaN;
            for (int k = 0; k <= 4; k++) {
                float a = a0 + len * k / 4f;
                float jitter = k == 0 || k == 4 ? 0f
                        : (WarshipHitFx.unit(h + k * 13) - 0.5f) * radius * 0.035f;
                float[] q = onArc(cx, cy, radius + jitter, a);
                float px = cam.cellToScreenX(q[0]);
                float py = cam.cellToScreenY(q[1]);
                if (k > 0) {
                    out.addLine(RenderLayer.SHUTTLES, lastX, lastY, px, py, width * 2.4f,
                            0.75f, 0.6f, 1f, 0.3f * strain * alpha);
                    out.addLine(RenderLayer.SHUTTLES, lastX, lastY, px, py, width,
                            0.97f, 0.94f, 1f, 0.85f * strain * alpha);
                }
                lastX = px;
                lastY = py;
            }
        }
    }

    /** An overloaded ship crawls with arcs across its hull, as long as the overload holds. */
    static void overloadArcs(DrawList out, BattleCamera cam, BattleSprites sprites, Warship ship, float alpha,
                             float now) {
        if (!ship.overloaded()) return;
        float cellPx = cam.cellPxSize();
        int step = (int) Math.floor(now * 14f);
        float halfL = ship.lengthCells * 0.42f;
        float halfW = ship.widthCells * 0.38f;
        float width = Math.max(1.5f, 0.15f * cellPx);
        ShuttleSpriteCache glow = sprites.spriteByPath(GLOW);
        int arcs = 3 + Math.round(ship.lengthCells / 30f);
        for (int i = 0; i < arcs; i++) {
            int h = WarshipHitFx.mix(step * 6151 + i * 3571 ^ salt(ship));
            float[] a = hullPoint(h, halfW, halfL);
            float[] b = hullPoint(WarshipHitFx.mix(h + 17), halfW, halfL);
            float lastX = Float.NaN;
            float lastY = Float.NaN;
            for (int k = 0; k <= 5; k++) {
                float f = k / 5f;
                float lx = a[0] + (b[0] - a[0]) * f;
                float ly = a[1] + (b[1] - a[1]) * f;
                if (k > 0 && k < 5) {
                    lx += (WarshipHitFx.unit(h + k * 41) - 0.5f) * halfW * 0.5f;
                    ly += (WarshipHitFx.unit(h + k * 43) - 0.5f) * halfW * 0.5f;
                }
                float[] w = world(ship, lx, ly);
                float px = cam.cellToScreenX(w[0]);
                float py = cam.cellToScreenY(w[1]);
                if (k > 0) {
                    out.addLine(RenderLayer.SHUTTLES, lastX, lastY, px, py, width * 2.6f,
                            0.45f, 0.65f, 1f, 0.35f * alpha);
                    out.addLine(RenderLayer.SHUTTLES, lastX, lastY, px, py, width,
                            0.92f, 0.97f, 1f, 0.95f * alpha);
                }
                lastX = px;
                lastY = py;
            }
            if (glow != null) {
                float s = Math.max(2f, ship.widthCells * 0.12f) * cellPx;
                out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite, lastX, lastY, s, s, 0f,
                        0.55f, 0.75f, 1f, 0.6f * alpha);
            }
        }
    }

    /**
     * Where a shield's arc is drawn facing. A front shield faces the bow. An omni shield covers every bearing in the
     * model; one narrower than all round is drawn turned on what it is fighting (its hull target, the base game's
     * omni shield tracking the threat), else off the bow. Presentation only: what it absorbs is the model's.
     */
    static float shieldFacing(Warship ship, WarshipHitFx fx) {
        float facing = ship.shieldFacingDegrees();
        if (!"omni".equals(ship.shieldType()) || ship.shieldArcDegrees() >= 359.5f) return facing;
        Warship target = ship.hullTarget();
        float dx;
        float dy;
        if (target != null) {
            dx = target.centerX() - ship.shieldCentreX();
            dy = target.centerY() - ship.shieldCentreY();
        } else {
            // No target (an overloaded ship holds none): the side its last absorbed hit came in on.
            Mark last = fx.lastShieldHit(ship);
            if (last == null) return facing;
            float[] p = world(ship, last.localX(), last.localY());
            dx = p[0] - ship.shieldCentreX();
            dy = p[1] - ship.shieldCentreY();
        }
        if (Math.abs(dx) + Math.abs(dy) < 1e-3f) return facing;
        return (float) Math.toDegrees(Math.atan2(-dx, dy));
    }

    /** Where a collapse or lockout mark faces: the side its shield held when it went, else the arc's own facing. */
    private static float eventFacing(Mark m, WarshipHitFx fx) {
        Warship ship = m.ship();
        if (!"omni".equals(ship.shieldType()) || ship.shieldArcDegrees() >= 359.5f) return shieldFacing(ship, fx);
        float[] d = world(ship, m.outX(), m.outY(), true);
        if (Math.abs(d[0]) + Math.abs(d[1]) < 1e-3f) return shieldFacing(ship, fx);
        return (float) Math.toDegrees(Math.atan2(-d[0], d[1]));
    }

    /** A per-ship salt for continuous jitter, so two hulls of one design do not flicker in step. */
    private static int salt(Warship ship) {
        return ship.hullId.hashCode() * 31 + ship.faction.ordinal() * 7 + Math.round(ship.lengthCells * 13f);
    }

    private static float[] hullPoint(int h, float halfW, float halfL) {
        double a = WarshipHitFx.unit(h) * Math.PI * 2.0;
        float r = (float) Math.sqrt(WarshipHitFx.unit(h >>> 9));
        return new float[]{(float) Math.cos(a) * halfW * r, (float) Math.sin(a) * halfL * r};
    }

    // ---- hull ------------------------------------------------------------------------------------------------------

    private static void hullHit(DrawList out, BattleCamera cam, BattleSprites sprites, Mark m, float alpha,
                                float now, boolean detail) {
        Warship ship = m.ship();
        float t = now - m.bornAt();
        float life = m.lifeSeconds();
        float u = t / life;
        if (u < 0f || u >= 1f) return;
        float cellPx = cam.cellPxSize();
        Character ch = m.character();
        Kind kind = m.kind();
        boolean big = kind != Kind.HULL;
        float[] p = world(ship, m.localX(), m.localY());
        float sx = cam.cellToScreenX(p[0]);
        float sy = cam.cellToScreenY(p[1]);
        float size = m.sizeCells() * cellPx * (m.beam() ? 0.6f : 1f) * (big ? 1.35f : 1f);
        float fade = 1f - u;
        ShuttleSpriteCache glow = sprites.spriteByPath(GLOW);
        float[] tint = m.colour() != null ? rgb(m.colour()) : new float[]{1f, 0.6f, 0.25f};
        float[] metal = WarshipHullDamageRenderer.palette(ship.techStyle());

        // The scorch: a dull glow that cools from orange to a dark red, longest and brightest where energy melted it.
        float ember = ch == Character.ENERGY ? 1f : 0.55f;
        if (glow != null) {
            float cool = (float) Math.sqrt(u);
            out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite, sx, sy, size * 1.1f, size * 1.1f, 0f,
                    1f, 0.55f - 0.35f * cool, 0.15f - 0.1f * cool, ember * 0.7f * fade * alpha);
        }

        // Smoke from the blast, under the fire.
        if (detail && (ch == Character.HIGH_EXPLOSIVE || big || ch == Character.OTHER) && !m.beam()) {
            ShuttleSpriteCache smoke = sprites.spriteByPath(SMOKE);
            if (smoke == null) smoke = glow;
            if (smoke != null) {
                int n = big ? 4 : 2;
                float[] o = world(ship, m.outX(), m.outY(), true);
                for (int i = 0; i < n; i++) {
                    int h = WarshipHitFx.mix(m.seed() + 31 * i);
                    float drift = m.sizeCells() * (0.4f + 1.6f * WarshipHitFx.unit(h)) * (float) Math.sqrt(u);
                    float ang = (WarshipHitFx.unit(h >>> 4) - 0.5f) * 2.2f;
                    float[] d = turn(o[0], o[1], ang);
                    float px = cam.cellToScreenX(p[0] + d[0] * drift);
                    float py = cam.cellToScreenY(p[1] + d[1] * drift);
                    float s = size * (1f + 1.8f * u) * (0.8f + 0.4f * WarshipHitFx.unit(h >>> 8));
                    float g = 0.16f + 0.18f * u;
                    out.addSprite(RenderLayer.SHUTTLES, smoke.sprite, px, py, s, s, (h & 0x1FF) * 0.7f,
                            g, g * 0.97f, g * 0.94f, 0.5f * fade * Math.min(1f, t * 8f) * alpha);
                }
            }
        }

        // The fireball: high explosive, a breach, a mount going up.
        if (ch == Character.HIGH_EXPLOSIVE || big || (ch == Character.OTHER && !m.beam())) {
            float fireLife = big ? 1.1f : 0.75f;
            float f = t / fireLife;
            ShuttleSpriteCache ball = sprites.spriteByPath(FIREBALLS[(m.seed() >>> 3) % FIREBALLS.length]);
            if (f < 1f && ball != null) {
                float s = size * (1.6f + 1.4f * (float) Math.sqrt(f)) * (ch == Character.HIGH_EXPLOSIVE ? 1.15f : 1f);
                out.addAdditiveSprite(RenderLayer.SHUTTLES, ball.sprite, sx, sy, s, s, (m.seed() & 0x1FF) * 0.7f,
                        lerp(1f, tint[0], 0.4f), lerp(0.62f, tint[1], 0.4f), lerp(0.3f, tint[2], 0.4f),
                        0.95f * (1f - f) * (1f - f) * alpha);
            }
        }
        // A round that holes the hull through: a second blast a beat later, off the first, and a shockwave.
        if (big) {
            float second = t - 0.18f;
            if (second > 0f && second < 0.7f && glow != null) {
                float f = second / 0.7f;
                float ox = (WarshipHitFx.unit(m.seed() >>> 2) - 0.5f) * m.sizeCells();
                float oy = (WarshipHitFx.unit(m.seed() >>> 6) - 0.5f) * m.sizeCells();
                float[] q = world(ship, m.localX() + ox, m.localY() + oy);
                float s = size * (1.4f + 1.2f * f);
                out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite, cam.cellToScreenX(q[0]),
                        cam.cellToScreenY(q[1]), s, s, 0f, 1f, 0.6f, 0.25f, 0.85f * (1f - f) * alpha);
            }
            ShuttleSpriteCache ring = sprites.spriteByPath(RING);
            float rf = t / 0.6f;
            if (ring != null && rf < 1f) {
                float s = size * (1.2f + 5f * (float) Math.sqrt(rf));
                out.addAdditiveSprite(RenderLayer.SHUTTLES, ring.sprite, sx, sy, s, s, 0f, 1f, 0.85f, 0.6f,
                        0.6f * (1f - rf) * alpha);
            }
        }

        // The flash: hot white for kinetic, the weapon's own colour searing white for energy.
        float flashLife = ch == Character.ENERGY ? 0.22f : 0.3f;
        float flash = Math.max(0f, 1f - t / flashLife);
        if (flash > 0f && glow != null) {
            float[] c = switch (ch) {
                case KINETIC -> new float[]{1f, 0.95f, 0.8f};
                case ENERGY -> mix(tint, new float[]{1f, 1f, 1f}, 0.45f);
                case FRAGMENTATION -> new float[]{1f, 0.85f, 0.55f};
                default -> new float[]{1f, 0.8f, 0.5f};
            };
            float s = size * (ch == Character.FRAGMENTATION ? 1.4f : ch == Character.ENERGY ? 1.3f : 1.8f);
            out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite, sx, sy, s, s, 0f, c[0], c[1], c[2],
                    flash * alpha);
            out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite, sx, sy, s * 0.45f, s * 0.45f, 0f,
                    1f, 1f, 1f, flash * alpha);
        }
        if (!detail) return;

        float[] o = world(ship, m.outX(), m.outY(), true);
        // Sparks thrown back off the plate toward where the round came from.
        int sparks = m.beam() ? 3 : switch (ch) {
            case KINETIC -> 12;
            case HIGH_EXPLOSIVE -> 7;
            case ENERGY -> 5;
            case FRAGMENTATION -> 16;
            case OTHER -> 7;
        } + (big ? 8 : 0);
        float reach = m.sizeCells() * (ch == Character.KINETIC ? 4f : ch == Character.ENERGY ? 1.6f
                : ch == Character.FRAGMENTATION ? 2.6f : 3f) * (big ? 1.4f : 1f);
        float[] sparkColour = ch == Character.ENERGY ? new float[]{1f, 0.55f, 0.2f} : new float[]{1f, 0.82f, 0.45f};
        sparks(out, cam, glow, m.seed(), sparks, p[0], p[1], o[0], o[1],
                ch == Character.FRAGMENTATION ? 85f : 65f, reach, Math.min(1f, u * 1.6f),
                Math.max(1.5f, (ch == Character.FRAGMENTATION ? 0.13f : 0.2f) * cellPx), sparkColour, alpha);

        // Chips of armour plate tumbling away, the hull's own metal, darkening as they cool.
        int chips = m.beam() ? 0 : switch (ch) {
            case KINETIC -> 5;
            case HIGH_EXPLOSIVE -> 5;
            case ENERGY -> 1;
            case FRAGMENTATION -> 2;
            case OTHER -> 3;
        } + (big ? 7 : 0);
        for (int i = 0; i < chips; i++) {
            int h = WarshipHitFx.mix(m.seed() * 13 + i * 7717);
            float ang = (WarshipHitFx.unit(h) - 0.5f) * 2.6f;
            float[] d = turn(o[0], o[1], ang);
            float dist = m.sizeCells() * (1.2f + 3.5f * WarshipHitFx.unit(h >>> 5)) * (big ? 1.5f : 1f)
                    * (1f - (1f - u) * (1f - u));
            float cxw = p[0] + d[0] * dist;
            float cyw = p[1] + d[1] * dist;
            float spin = (WarshipHitFx.unit(h >>> 11) - 0.5f) * 14f * t + WarshipHitFx.unit(h >>> 3) * 6.3f;
            float len = m.sizeCells() * (0.07f + 0.1f * WarshipHitFx.unit(h >>> 15)) * (big ? 1.4f : 1f);
            float ex = (float) Math.cos(spin) * len * 0.5f;
            float ey = (float) Math.sin(spin) * len * 0.5f;
            float hot = Math.max(0f, 1f - t * 4f);
            float shade = 0.35f + 0.25f * WarshipHitFx.unit(h >>> 19);
            out.addLine(RenderLayer.SHUTTLES, cam.cellToScreenX(cxw - ex), cam.cellToScreenY(cyw - ey),
                    cam.cellToScreenX(cxw + ex), cam.cellToScreenY(cyw + ey),
                    Math.max(1.5f, len * 0.5f * cellPx),
                    lerp(metal[0] * shade, 1f, hot), lerp(metal[1] * shade, 0.55f, hot),
                    lerp(metal[2] * shade, 0.2f, hot), 0.95f * Math.min(1f, fade * 2f) * alpha);
        }
    }

    /**
     * What an open breach shows every frame: grey vapour and tongues of fire jetting out of the rim of each hole the
     * ship smokes from, pointing away from the hull's middle. Drawn over the hull.
     */
    static void breachVents(DrawList out, BattleCamera cam, BattleSprites sprites, Warship ship, float alpha,
                            float now) {
        WarshipPlating p = ship.plating();
        if (p == null || p.breachCount() == 0) return;
        ShuttleSpriteCache glow = sprites.spriteByPath(GLOW);
        ShuttleSpriteCache smoke = sprites.spriteByPath(SMOKE);
        if (glow == null) return;
        if (smoke == null) smoke = glow;
        float cellPx = cam.cellPxSize();
        float step = Math.max(1.5f, ship.widthCells * 0.1f);
        for (int cell : WarshipHitFx.smokeSources(p)) {
            float x = ship.cellX(cell);
            float y = ship.cellY(cell);
            float ox = x - ship.centerX();
            float oy = y - ship.centerY();
            float len = (float) Math.hypot(ox, oy);
            if (len < 1e-3f) continue;
            ox /= len;
            oy /= len;
            int h = WarshipHitFx.mix(cell * 2654435 ^ salt(ship));
            boolean fire = (h & 1) == 0;
            float phase = WarshipHitFx.unit(h);
            for (int k = 0; k < 6; k++) {
                float along = (k + frac(now * 2.2f + phase)) * step;
                float sway = (float) Math.sin(now * 5f + k + phase * 6f) * 0.25f;
                float[] d = turn(ox, oy, sway * 0.4f);
                float px = cam.cellToScreenX(x + d[0] * along);
                float py = cam.cellToScreenY(y + d[1] * along);
                float k01 = (k + frac(now * 2.2f + phase)) / 6f;
                if (fire && k < 3) {
                    float s = step * (1.6f - 0.3f * k) * cellPx;
                    float flicker = 0.75f + 0.25f * (float) Math.sin(now * 19f + k * 2.1f + phase * 9f);
                    out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite, px, py, s, s, 0f,
                            1f, 0.5f + 0.15f * k, 0.15f, 0.8f * (1f - k01) * flicker * alpha);
                } else {
                    float s = step * (1.4f + 2.2f * k01) * cellPx;
                    float g = fire ? 0.3f : 0.85f;
                    out.addSprite(RenderLayer.SHUTTLES, smoke.sprite, px, py, s, s, (h & 0xFF) + k * 40f,
                            g, g * 1.01f, g * 1.04f, 0.7f * (1f - k01) * alpha);
                }
            }
        }
    }

    /** The smoke a holed ship trails: puffs left in the world, swelling, paling and thinning as they hang. */
    static void trailSmoke(DrawList out, BattleCamera cam, BattleSprites sprites, WarshipHitFx fx, float alpha,
                           float now) {
        if (fx.puffs().isEmpty()) return;
        ShuttleSpriteCache smoke = sprites.spriteByPath(SMOKE);
        ShuttleSpriteCache glow = sprites.spriteByPath(GLOW);
        if (smoke == null) smoke = glow;
        if (smoke == null) return;
        float cellPx = cam.cellPxSize();
        for (Puff p : fx.puffs()) {
            float age = now - p.bornAt();
            float u = age / Puff.LIFE_SECONDS;
            if (u < 0f || u >= 1f) continue;
            float x = p.x() + p.driftX() * age;
            float y = p.y() + p.driftY() * age;
            float s = p.sizeCells() * (1f + 3f * (float) Math.sqrt(u)) * cellPx;
            float sx = cam.cellToScreenX(x);
            float sy = cam.cellToScreenY(y);
            // Black where it leaves the fire, paling to grey as it hangs.
            float g = 0.22f + 0.38f * (float) Math.sqrt(u);
            float in = Math.min(1f, age / 0.25f);
            out.addSprite(RenderLayer.SHUTTLES, smoke.sprite, sx, sy, s, s, (p.seed() & 0x1FF) * 0.7f + age * 12f,
                    g, g * 0.97f, g * 0.95f, 0.65f * (float) Math.pow(1f - u, 1.2f) * in * alpha);
            if (p.fire() && age < 0.5f && glow != null) {
                float f = 1f - age / 0.5f;
                out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite, sx, sy, s * 0.9f, s * 0.9f, 0f,
                        1f, 0.5f, 0.15f, 0.7f * f * alpha);
            }
        }
    }

    // ---- primitives ------------------------------------------------------------------------------------------------

    /** {@code count} sparks from {@code (x, y)} along {@code (dx, dy)} within {@code spreadDeg} either side. */
    private static void sparks(DrawList out, BattleCamera cam, ShuttleSpriteCache glow, int seed, int count, float x,
                               float y, float dx, float dy, float spreadDeg, float reach, float u, float widthPx,
                               float[] colour, float alpha) {
        if (glow == null || count <= 0 || u >= 1f) return;
        float fade = 1f - u;
        float travel = 1f - (1f - u) * (1f - u);
        for (int i = 0; i < count; i++) {
            int h = WarshipHitFx.mix(seed + i * 40503);
            float ang = (float) Math.toRadians((WarshipHitFx.unit(h) - 0.5f) * 2f * spreadDeg);
            float[] d = turn(dx, dy, ang);
            float dist = reach * (0.35f + 0.65f * WarshipHitFx.unit(h >>> 6)) * travel;
            float tail = reach * 0.28f * fade * (0.6f + 0.4f * WarshipHitFx.unit(h >>> 12));
            float hx = x + d[0] * dist;
            float hy = y + d[1] * dist;
            float px0 = cam.cellToScreenX(hx - d[0] * tail);
            float py0 = cam.cellToScreenY(hy - d[1] * tail);
            float px1 = cam.cellToScreenX(hx);
            float py1 = cam.cellToScreenY(hy);
            float sdx = px1 - px0;
            float sdy = py1 - py0;
            float lenPx = (float) Math.hypot(sdx, sdy);
            if (lenPx < 0.5f) continue;
            float heading = (float) Math.toDegrees(Math.atan2(-sdx, sdy));
            float a = alpha * fade * (0.6f + 0.4f * WarshipHitFx.unit(h >>> 18));
            out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite, (px0 + px1) * 0.5f, (py0 + py1) * 0.5f,
                    widthPx * 2.2f, lenPx + widthPx * 2f, heading, colour[0], colour[1], colour[2], a);
            out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite, px1, py1, widthPx * 1.4f, widthPx * 1.4f, 0f,
                    1f, 1f, 1f, a);
        }
    }

    /**
     * Part of the shield rim from {@code from} to {@code to} degrees (the arc's own convention), clipped to the arc a
     * front shield covers, as a soft glowing line.
     */
    private static void rimArc(DrawList out, BattleCamera cam, Warship ship, float cx, float cy, float radius,
                               float from, float to, float arc, float facing, float width, float[] c, float a) {
        if (a <= 0f) return;
        if (arc < 359.5f) {
            float lo = facing - arc * 0.5f;
            float hi = facing + arc * 0.5f;
            // Bring the span near the arc before clipping it.
            float mid = (from + to) * 0.5f;
            float shift = 360f * Math.round((facing - mid) / 360f);
            from += shift;
            to += shift;
            from = Math.max(from, lo);
            to = Math.min(to, hi);
            if (to <= from) return;
        }
        int segments = Math.max(2, (int) Math.ceil((to - from) / 4f));
        float lastX = 0f;
        float lastY = 0f;
        for (int i = 0; i <= segments; i++) {
            float[] q = onArc(cx, cy, radius, from + (to - from) * i / segments);
            float px = cam.cellToScreenX(q[0]);
            float py = cam.cellToScreenY(q[1]);
            if (i > 0) {
                out.addLine(RenderLayer.SHUTTLES, lastX, lastY, px, py, width * 2.2f, c[0], c[1], c[2], 0.3f * a);
                out.addLine(RenderLayer.SHUTTLES, lastX, lastY, px, py, width, c[0], c[1], c[2], a);
                out.addLine(RenderLayer.SHUTTLES, lastX, lastY, px, py, width * 0.4f, 1f, 1f, 1f, a);
            }
            lastX = px;
            lastY = py;
        }
    }

    private static void circle(DrawList out, BattleCamera cam, float x, float y, float r, int segments, float width,
                               float[] c, float a) {
        if (a <= 0f || r <= 0f) return;
        float lastX = 0f;
        float lastY = 0f;
        for (int i = 0; i <= segments; i++) {
            double ang = 2.0 * Math.PI * i / segments;
            float px = cam.cellToScreenX(x + (float) Math.cos(ang) * r);
            float py = cam.cellToScreenY(y + (float) Math.sin(ang) * r);
            if (i > 0) out.addLine(RenderLayer.SHUTTLES, lastX, lastY, px, py, width, c[0], c[1], c[2], a);
            lastX = px;
            lastY = py;
        }
    }

    /** A point on a circle at {@code degrees} in the shield's convention: {@code centre + r * (-sin, cos)}. */
    private static float[] onArc(float cx, float cy, float r, float degrees) {
        double rad = Math.toRadians(degrees);
        return new float[]{cx - (float) Math.sin(rad) * r, cy + (float) Math.cos(rad) * r};
    }

    /** A point in {@code ship}'s frame (about its sprite centre) in the world. */
    private static float[] world(Warship ship, float lx, float ly) {
        return world(ship, lx, ly, false);
    }

    /** {@link #world(Warship, float, float)}, or just turned (a direction) when {@code direction}. */
    private static float[] world(Warship ship, float lx, float ly, boolean direction) {
        double r = Math.toRadians(ship.facingDegrees);
        float cos = (float) Math.cos(r);
        float sin = (float) Math.sin(r);
        float wx = lx * cos - ly * sin;
        float wy = lx * sin + ly * cos;
        if (direction) return new float[]{wx, wy};
        return new float[]{ship.centerX() + wx, ship.centerY() + wy};
    }

    private static float[] turn(float dx, float dy, float radians) {
        float c = (float) Math.cos(radians);
        float s = (float) Math.sin(radians);
        return new float[]{dx * c - dy * s, dx * s + dy * c};
    }

    private static float[] rgb(Color c) {
        return new float[]{c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f};
    }

    private static float[] mix(float[] a, float[] b, float t) {
        return new float[]{lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t)};
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }
}
