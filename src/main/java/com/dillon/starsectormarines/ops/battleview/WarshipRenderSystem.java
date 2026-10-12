package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.engine.EngineStylePalette;
import com.dillon.starsectormarines.battle.warship.MissileFlightSpec;
import com.dillon.starsectormarines.battle.warship.MissilePath;
import com.dillon.starsectormarines.battle.warship.VanillaBeamLook;
import com.dillon.starsectormarines.battle.warship.VanillaWeapon;
import com.dillon.starsectormarines.battle.warship.VanillaWeaponLooks;
import com.dillon.starsectormarines.battle.warship.WarheadStrike;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipEngine;
import com.dillon.starsectormarines.battle.warship.WarshipPointDefence;
import com.dillon.starsectormarines.battle.warship.WarshipScale;
import com.dillon.starsectormarines.battle.warship.WarshipShell;
import com.dillon.starsectormarines.battle.warship.WarshipThrust;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.awt.Color;
import java.util.List;

/**
 * Draws a warship hanging over the battle: the game's own hull art at the scale
 * every hull is drawn at, and each fitted weapon's own turret sprite on the slot
 * the hull's data places it, pointing where the barrel points.
 *
 * <p>Nothing here is authored by the mod. The hull and turret art are the
 * install's, named by the hull spec and the weapon spec, so a player who has
 * flown the ship sees the ship they know.
 *
 * <p>Rounds in flight are drawn from the weapon's own projectile art: a shell or
 * missile as its sprite, an energy bolt as a streak in the projectile's fringe
 * and core colours, a missile with a glowing tail behind it. A muzzle flash
 * lights the barrel for the instant after it fires, in the weapon's own flash
 * colour. They share the hull's layer so they read as coming from it, and so the
 * headless review frames, which draw that layer, show them.
 *
 * <p>Reads the simulation's warships and nothing else; what they fire, and what
 * it does, is the simulation's.
 */
public final class WarshipRenderSystem implements RenderSystem {

    /** Presentation scale on a vanilla round's size: at ground scale the game's own proportions read as boulders. */
    private static final float ROUND_VISUAL_SCALE = 0.6f;
    private static final float MUZZLE_FLASH_SECONDS = 0.15f;
    /** Diameter of the glow under a slug's sprite, in cells. */
    private static final float BOLT_GLOW_CELLS = 1.4f;
    private static final int TAIL_PUFFS = 10;
    /** The longest a missile's smoke is drawn behind it, in seconds, however long its spec lets a contrail hang. */
    private static final float TRAIL_MAX_SECONDS = 1.2f;
    private static final float SPLIT_FLASH_SECONDS = 0.35f;
    private static final float STRIKE_STREAK_CELLS = 3f;
    private static final String GLOW = "graphics/fx/particlealpha64linear.png";
    private static final Color DEFAULT_FLASH = new Color(255, 225, 165);
    private static final Color PD_FRINGE = new Color(255, 200, 90);
    private static final Color MISSILE_FLAME = new Color(255, 140, 40);
    private static final Color MISSILE_SMOKE = new Color(150, 150, 150);
    /**
     * The shield's pale cyan: the game's default shield tint. The stock hulls name no colour of
     * their own in their spec, so this is the one every hull gets.
     */
    private static final Color SHIELD_TINT = new Color(140, 215, 255);
    private static final Color SHIELD_HIT = new Color(235, 250, 255);
    private static final Color SHIELD_COLLAPSE = new Color(255, 190, 120);
    private static final String SHIELD_FILL = "graphics/fx/shields256.png";
    private static final String SHIELD_RING = "graphics/fx/shields256ring.png";
    /** Longest an arc segment of the rim is, in degrees. */
    private static final float SHIELD_SEGMENT_DEGREES = 6f;

    /**
     * Flame length at nozzle level 0 and the extra it grows to by level 1, as shares of the slot's
     * own length. Wider than an aircraft's range (0.4 to 1.0) because a capital's authored plume is
     * small against its hull at ground scale and the difference between hovering and flying has to read.
     */
    private static final float ENGINE_LEN_FLOOR = 0.35f;
    private static final float ENGINE_LEN_RANGE = 1.45f;
    private static final float ENGINE_WIDTH_FLOOR = 0.7f;
    private static final float ENGINE_WIDTH_RANGE = 0.5f;
    private static final float ENGINE_ALPHA_FLOOR = 0.45f;
    /** The glow is a bloom a little wider than the column. */
    private static final float ENGINE_GLOW_WIDTH_MULT = 2.2f;

    private final BattleSprites sprites;
    /** Seconds the flicker has run, fed from the host clock or accumulated frame time. */
    private float engineClockSeconds;

    public WarshipRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
    }

    @Override
    public RenderLayer layer() {
        return RenderLayer.SHUTTLES;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        BattleCamera cam = ctx.camera;
        float cellPx = cam.cellPxSize();
        engineClockSeconds = Float.isFinite(ctx.animationTimeSeconds)
                ? ctx.animationTimeSeconds
                : engineClockSeconds + Math.max(0f, ctx.realDt);
        sprites.ensureEngineFxSprites();
        for (Warship ship : ctx.sim.warships().ships()) {
            // Under the hull: the nozzles sit inside its outline and the flame trails out of it.
            drawEngines(out, cam, cellPx, ctx.alphaMult, ship);
            drawHull(out, cam, cellPx, ctx.alphaMult, ship);
            for (Warship.Mount m : ship.mounts()) {
                drawMount(out, cam, cellPx, ctx.alphaMult, ship, m);
                drawMuzzleFlash(out, cam, cellPx, ctx.alphaMult, ship, m);
            }
            drawShield(out, cam, cellPx, ctx.alphaMult, ship);
        }
        for (WarshipShell shell : ctx.sim.warships().shells()) {
            drawShell(out, cam, cellPx, ctx.alphaMult, shell);
        }
        for (WarheadStrike strike : ctx.sim.warships().strikes()) {
            drawStrike(out, cam, cellPx, ctx.alphaMult, strike);
        }
        for (WarshipPointDefence.Mark mark : ctx.sim.warships().pointDefence().marks()) {
            drawEngagement(out, cam, cellPx, ctx.alphaMult, mark);
        }
    }

    /**
     * Every nozzle the hull's data lists, burning in the game's engine colour for
     * its style: a soft glow at the nozzle and a flame column streaming out along
     * the slot's angle on the turned hull. How hard it burns is the ship's actual
     * motion ({@link WarshipThrust}) — a low glow hovering, long and bright
     * accelerating, dimmed braking — with a little flicker so it is not a decal.
     * Additive, so it brightens the hull and ground beneath rather than covering them.
     */
    private void drawEngines(DrawList out, BattleCamera cam, float cellPx, float alpha, Warship ship) {
        List<WarshipEngine> engines = WarshipEngine.forHull(ship.hullId);
        if (engines.isEmpty()) return;
        SpriteAPI flame = sprites.engineFlameSprite();
        SpriteAPI glow = sprites.engineGlowSprite();
        if (flame == null && glow == null) return;
        ShuttleSpriteCache soft = sprites.spriteByPath(GLOW);
        WarshipThrust.Demand demand = WarshipThrust.of(ship);
        double rad = Math.toRadians(ship.facingDegrees);
        float c = (float) Math.cos(rad);
        float s = (float) Math.sin(rad);
        for (int i = 0; i < engines.size(); i++) {
            WarshipEngine e = engines.get(i);
            float heading = ship.facingDegrees + e.angleDegrees();
            float level = demand.level() * WarshipThrust.slotShare(demand.pushX(), demand.pushY(), heading);
            level = Math.max(0f, Math.min(1f, level * flicker(i)));
            Color col = EngineStylePalette.flameColor(e.style());
            float r = col.getRed() / 255f;
            float g = col.getGreen() / 255f;
            float b = col.getBlue() / 255f;

            float wx = ship.x + e.localX() * c - e.localY() * s;
            float wy = ship.y + e.localX() * s + e.localY() * c;
            float sx = cam.cellToScreenX(wx);
            float sy = cam.cellToScreenY(wy);
            double hr = Math.toRadians(heading);
            float dirX = (float) -Math.sin(hr);
            float dirY = (float) Math.cos(hr);

            float lenPx = e.lengthCells() * cellPx * (ENGINE_LEN_FLOOR + ENGINE_LEN_RANGE * level);
            float widthPx = e.widthCells() * cellPx * (ENGINE_WIDTH_FLOOR + ENGINE_WIDTH_RANGE * level);
            float lit = alpha * (ENGINE_ALPHA_FLOOR + (1f - ENGINE_ALPHA_FLOOR) * level);
            float glowPx = Math.max(widthPx * ENGINE_GLOW_WIDTH_MULT, cellPx * 0.4f);

            // The glow first, so the column reads as coming out of it.
            if (glow != null) {
                out.addAdditiveSprite(RenderLayer.SHUTTLES, glow, sx, sy, glowPx, glowPx, 0f, r, g, b, lit);
            } else if (soft != null) {
                out.addAdditiveSprite(RenderLayer.SHUTTLES, soft.sprite, sx, sy, glowPx, glowPx, 0f, r, g, b, lit);
            }
            if (flame != null) {
                // The sprite is centred and bright at its base, so it is laid half a length down the flame.
                out.addAdditiveSprite(RenderLayer.SHUTTLES, flame,
                        sx + dirX * lenPx * 0.5f, sy + dirY * lenPx * 0.5f, widthPx, lenPx,
                        heading, r, g, b, lit);
            } else if (soft != null) {
                out.addAdditiveSprite(RenderLayer.SHUTTLES, soft.sprite,
                        sx + dirX * lenPx * 0.3f, sy + dirY * lenPx * 0.3f, widthPx, lenPx * 0.8f,
                        heading, r, g, b, lit);
            }
        }
    }

    /** A little life in each nozzle: two incommensurate sines, a different phase for every slot. */
    private float flicker(int slot) {
        float t = engineClockSeconds;
        return 1f + 0.07f * (float) Math.sin(t * 31f + slot * 1.7f)
                + 0.05f * (float) Math.sin(t * 53f + slot * 2.9f);
    }

    private void drawHull(DrawList out, BattleCamera cam, float cellPx, float alpha, Warship ship) {
        ShuttleSpriteCache hull = sprites.spriteByPath(ship.spritePath);
        if (hull == null) return;
        float rad = (float) Math.toRadians(ship.facingDegrees);
        float c = (float) Math.cos(rad);
        float s = (float) Math.sin(rad);
        // The sprite's centre sits at the authored pivot from the centre of
        // gravity, in the hull frame; the hull rotates about its CoG.
        float hx = ship.x + (ship.pivotX * c - ship.pivotY * s);
        float hy = ship.y + (ship.pivotX * s + ship.pivotY * c);
        float pxH = ship.lengthCells * cellPx;
        // A battered hull darkens toward charcoal as its hit points go.
        float tint = 0.4f + 0.6f * ship.hullFraction();
        out.addSprite(RenderLayer.SHUTTLES, hull.sprite,
                cam.cellToScreenX(hx), cam.cellToScreenY(hy),
                pxH * hull.aspect, pxH, ship.facingDegrees,
                tint, tint, tint, alpha);
    }

    private void drawMount(DrawList out, BattleCamera cam, float cellPx, float alpha,
                           Warship ship, Warship.Mount m) {
        String path = m.slot.hardpoint() ? m.weapon.hardpointSprite() : m.weapon.turretSprite();
        ShuttleSpriteCache art = sprites.spriteByPath(path);
        if (art == null) return;
        float w = WarshipScale.cells(art.pxW) * cellPx;
        float h = WarshipScale.cells(art.pxH) * cellPx;
        out.addSprite(RenderLayer.SHUTTLES, art.sprite,
                cam.cellToScreenX(ship.mountX(m)), cam.cellToScreenY(ship.mountY(m)),
                w, h, heading(ship, m), 1f, 1f, 1f, alpha);
    }

    /**
     * The shield bubble: the arc of a circle of the hull's own shield radius about
     * its shield centre, a rim and a soft fill, rising with the shield, brighter
     * on an absorbed hit and flaring orange when it collapses. A full bubble
     * (an omni shield) uses the game's own shield art; an arc is drawn as a rim of
     * lines, since a texture cannot be clipped to a part of a circle here.
     * Nothing is drawn for a hull with no shield or a ship not on flux.
     */
    private void drawShield(DrawList out, BattleCamera cam, float cellPx, float alpha, Warship ship) {
        if (!ship.hasShield()) return;
        float level = ship.shieldLevel();
        float hit = ship.shieldHitFlash();
        float drop = ship.shieldDropFlash();
        if (level <= 0f && drop <= 0f) return;
        float radius = ship.shieldRadiusCells() * (0.8f + 0.2f * level);
        float arc = Math.min(360f, ship.shieldArcDegrees());
        float cx = ship.shieldCentreX();
        float cy = ship.shieldCentreY();
        float strength = Math.max(level, drop);
        Color base = drop > 0.01f && level < 1f ? SHIELD_COLLAPSE : SHIELD_TINT;
        float mix = Math.min(1f, hit);
        float r = lerp(base.getRed(), SHIELD_HIT.getRed(), mix) / 255f;
        float g = lerp(base.getGreen(), SHIELD_HIT.getGreen(), mix) / 255f;
        float b = lerp(base.getBlue(), SHIELD_HIT.getBlue(), mix) / 255f;
        float a = alpha * strength;
        float diameterPx = radius * 2f * cellPx;
        float sx = cam.cellToScreenX(cx);
        float sy = cam.cellToScreenY(cy);
        ShuttleSpriteCache fill = arc >= 359.5f ? sprites.spriteByPath(SHIELD_FILL) : null;
        ShuttleSpriteCache ring = arc >= 359.5f ? sprites.spriteByPath(SHIELD_RING) : null;
        if (fill != null) {
            out.addAdditiveSprite(RenderLayer.SHUTTLES, fill.sprite, sx, sy, diameterPx, diameterPx, 0f,
                    r, g, b, (0.18f + 0.4f * hit) * a);
        }
        if (ring != null) {
            out.addAdditiveSprite(RenderLayer.SHUTTLES, ring.sprite, sx, sy, diameterPx, diameterPx, 0f,
                    r, g, b, (0.5f + 0.5f * hit) * a);
        }
        float lineWidth = Math.max(2f, 0.22f * cellPx);
        int segments = Math.max(8, (int) Math.ceil(arc / SHIELD_SEGMENT_DEGREES));
        float start = ship.shieldFacingDegrees() - arc * 0.5f;
        float step = arc / segments;
        float lastX = 0f;
        float lastY = 0f;
        for (int i = 0; i <= segments; i++) {
            double rad = Math.toRadians(start + step * i);
            float px = cam.cellToScreenX(cx - (float) Math.sin(rad) * radius);
            float py = cam.cellToScreenY(cy + (float) Math.cos(rad) * radius);
            if (i > 0 && ring == null) {
                out.addLine(RenderLayer.SHUTTLES, lastX, lastY, px, py, lineWidth * 3.5f,
                        r, g, b, (0.07f + 0.2f * hit) * a);
                out.addLine(RenderLayer.SHUTTLES, lastX, lastY, px, py, lineWidth * 1.8f,
                        r, g, b, (0.14f + 0.25f * hit) * a);
                out.addLine(RenderLayer.SHUTTLES, lastX, lastY, px, py, lineWidth,
                        r, g, b, (0.45f + 0.5f * hit) * a);
            }
            lastX = px;
            lastY = py;
        }
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    /** A hardpoint is fixed to the hull; a turret points where its barrel does. */
    private static float heading(Warship ship, Warship.Mount m) {
        return m.slot.hardpoint() ? ship.facingDegrees + m.slot.angleDegrees() : m.headingDegrees;
    }

    private void drawMuzzleFlash(DrawList out, BattleCamera cam, float cellPx, float alpha,
                                 Warship ship, Warship.Mount m) {
        if (m.secondsSinceFired >= MUZZLE_FLASH_SECONDS) return;
        ShuttleSpriteCache glow = sprites.spriteByPath(GLOW);
        if (glow == null) return;
        float fade = 1f - m.secondsSinceFired / MUZZLE_FLASH_SECONDS;
        Color c = m.weapon.muzzleColor() != null ? m.weapon.muzzleColor() : DEFAULT_FLASH;
        double rad = Math.toRadians(heading(ship, m));
        // The flash sits a barrel's length out from the pivot, along the heading.
        float barrel = 1.2f;
        float fx = ship.mountX(m) - (float) Math.sin(rad) * barrel;
        float fy = ship.mountY(m) + (float) Math.cos(rad) * barrel;
        float size = Math.max(1.5f, WarshipScale.cells(m.weapon.muzzleLengthPx()) * 2f) * cellPx;
        out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite,
                cam.cellToScreenX(fx), cam.cellToScreenY(fy), size, size, 0f,
                c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, 0.9f * fade * alpha);
    }

    /**
     * A point-defence tracer in the weapon's own colours, and, where the round
     * died, a flash of its blast colour at the kill; a miss is the tracer alone.
     */
    private void drawEngagement(DrawList out, BattleCamera cam, float cellPx, float alpha,
                                WarshipPointDefence.Mark mark) {
        VanillaWeapon w = mark.weapon();
        VanillaBeamLook beam = w.beam() ? VanillaWeaponLooks.beam(w.id()) : null;
        Color fringe = beam != null && beam.fringe() != null ? beam.fringe()
                : w.fringe() != null ? w.fringe() : PD_FRINGE;
        Color core = beam != null && beam.core() != null ? beam.core()
                : w.core() != null ? w.core() : Color.WHITE;
        float f = mark.freshness();
        float x0 = cam.cellToScreenX(mark.fromX());
        float y0 = cam.cellToScreenY(mark.fromY());
        float x1 = cam.cellToScreenX(mark.toX());
        float y1 = cam.cellToScreenY(mark.toY());
        float width = Math.max(2f, 0.18f * cellPx);
        out.addLine(RenderLayer.SHUTTLES, x0, y0, x1, y1, width,
                fringe.getRed() / 255f, fringe.getGreen() / 255f, fringe.getBlue() / 255f, 0.7f * f * alpha);
        out.addLine(RenderLayer.SHUTTLES, x0, y0, x1, y1, width * 0.4f,
                core.getRed() / 255f, core.getGreen() / 255f, core.getBlue() / 255f, f * alpha);
        if (!mark.stopped()) return;
        ShuttleSpriteCache glow = sprites.spriteByPath(GLOW);
        if (glow == null) return;
        Color c = w.explosionColor() != null ? w.explosionColor() : DEFAULT_FLASH;
        float size = Math.max(1.5f, 2.2f * (1.2f - f)) * cellPx;
        out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite, x1, y1, size, size, 0f,
                c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, 0.9f * f * alpha);
    }

    private void drawShell(DrawList out, BattleCamera cam, float cellPx, float alpha, WarshipShell shell) {
        VanillaWeapon w = shell.weapon;
        // The round is where its flight function says it is now, nose along the path's tangent.
        float x = shell.x();
        float y = shell.y();
        float heading = shell.headingDegrees();
        ShuttleSpriteCache art = sprites.spriteByPath(w.projectileSprite());
        if (art != null) {
            float wPx = WarshipScale.cells(art.pxW) * cellPx * ROUND_VISUAL_SCALE;
            float hPx = WarshipScale.cells(art.pxH) * cellPx * ROUND_VISUAL_SCALE;
            if (w.missile()) drawMissileTail(out, cam, cellPx, alpha, shell, x, y);
            else drawBoltGlow(out, cam, cellPx, alpha, w, x, y);
            out.addSprite(RenderLayer.SHUTTLES, art.sprite,
                    cam.cellToScreenX(x), cam.cellToScreenY(y), wPx, hPx, heading,
                    1f, 1f, 1f, alpha);
            return;
        }
        // No sprite: an energy or kinetic bolt, a streak in the projectile's own colours.
        float lengthCells = Math.max(0.6f, WarshipScale.cells(w.projectileLengthPx())) * ROUND_VISUAL_SCALE;
        float widthPx = Math.max(2f, WarshipScale.cells(w.projectileWidthPx()) * cellPx * ROUND_VISUAL_SCALE);
        double rad = Math.toRadians(heading);
        float tx = x + (float) Math.sin(rad) * lengthCells;
        float ty = y - (float) Math.cos(rad) * lengthCells;
        Color fringe = w.fringe() != null ? w.fringe() : Color.ORANGE;
        Color core = w.core() != null ? w.core() : Color.WHITE;
        out.addLine(RenderLayer.SHUTTLES, cam.cellToScreenX(x), cam.cellToScreenY(y),
                cam.cellToScreenX(tx), cam.cellToScreenY(ty), widthPx,
                fringe.getRed() / 255f, fringe.getGreen() / 255f, fringe.getBlue() / 255f, 0.8f * alpha);
        out.addLine(RenderLayer.SHUTTLES, cam.cellToScreenX(x), cam.cellToScreenY(y),
                cam.cellToScreenX(tx), cam.cellToScreenY(ty), widthPx * 0.4f,
                core.getRed() / 255f, core.getGreen() / 255f, core.getBlue() / 255f, alpha);
    }

    /**
     * A soft glow in the round's own fringe colour under a slug's sprite. At
     * ground scale a gun's shell sprite is a few pixels across — a Devastator
     * round is 5 by 8 pixels close in and nothing at whole-map scale — so the
     * glow is what keeps a heavy gun's fall of shot visible between the muzzle
     * and the landing.
     */
    private void drawBoltGlow(DrawList out, BattleCamera cam, float cellPx, float alpha,
                              VanillaWeapon w, float x, float y) {
        ShuttleSpriteCache glow = sprites.spriteByPath(GLOW);
        if (glow == null) return;
        Color c = w.fringe() != null ? w.fringe() : DEFAULT_FLASH;
        float size = BOLT_GLOW_CELLS * cellPx;
        out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite,
                cam.cellToScreenX(x), cam.cellToScreenY(y), size, size, 0f,
                c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, 0.7f * alpha);
    }

    /**
     * A missile's flame at its tail and the smoke behind it. The smoke is the same
     * flight function sampled at earlier times, so it hangs along the weave the
     * missile actually flew and costs no state; its colours, lifetime and width
     * are the projectile's own contrail spec, and its flame the engine slot's.
     */
    private void drawMissileTail(DrawList out, BattleCamera cam, float cellPx, float alpha,
                                 WarshipShell shell, float x, float y) {
        ShuttleSpriteCache glow = sprites.spriteByPath(GLOW);
        if (glow == null) return;
        MissileFlightSpec flight = shell.weapon.flight();
        Color flame = flight != null && flight.engineColor() != null ? flight.engineColor() : MISSILE_FLAME;
        Color smoke = flight != null && flight.contrailColor() != null ? flight.contrailColor() : MISSILE_SMOKE;
        float smokeAlpha = smoke.getAlpha() / 255f;
        float lifetime = Math.max(0.3f, Math.min(TRAIL_MAX_SECONDS, flight == null ? 0.6f : flight.contrailSeconds()));
        float widthMult = flight == null ? 1f : Math.max(0.5f, Math.min(2.5f, flight.contrailWidthMult()));
        MissilePath path = shell.path();
        float now = shell.elapsed();
        float span = Math.min(now, lifetime);
        for (int i = 0; i < TAIL_PUFFS; i++) {
            float back = span * i / TAIL_PUFFS;
            float fade = 1f - (float) i / TAIL_PUFFS;
            float size = (0.6f + 0.1f * i) * (float) Math.sqrt(widthMult) * cellPx * ROUND_VISUAL_SCALE * 2f;
            Color c = i < 2 ? flame : smoke;
            float a = i < 2 ? 0.6f : 0.4f * Math.max(0.35f, smokeAlpha);
            out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite,
                    cam.cellToScreenX(path.x(now - back)), cam.cellToScreenY(path.y(now - back)),
                    size, size, 0f,
                    c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, a * fade * alpha);
        }
        SpriteAPI flameSprite = sprites.engineFlameSprite();
        if (flameSprite == null || flight == null) return;
        // The nozzle burns while the motor does; the flame points back down the path.
        if (now > flight.burnSeconds() && flight.burnSeconds() > 0f) return;
        float heading = path.headingDegrees(now) + 180f;
        double hr = Math.toRadians(heading);
        float lenPx = Math.max(0.6f, WarshipScale.cells(flight.flameLengthPx())) * cellPx * 1.6f;
        float widthPx = Math.max(0.3f, WarshipScale.cells(flight.flameWidthPx())) * cellPx * 1.4f;
        float sx = cam.cellToScreenX(x);
        float sy = cam.cellToScreenY(y);
        float flicker = 0.85f + 0.15f * (float) Math.sin(engineClockSeconds * 47f + x * 3.1f);
        out.addAdditiveSprite(RenderLayer.SHUTTLES, flameSprite,
                sx + (float) -Math.sin(hr) * lenPx * 0.5f, sy + (float) Math.cos(hr) * lenPx * 0.5f,
                widthPx, lenPx * flicker, heading,
                flame.getRed() / 255f, flame.getGreen() / 255f, flame.getBlue() / 255f, 0.9f * alpha);
    }

    /**
     * A warhead a carrier has let go, crossing from the split point to where it will
     * burst: a bright dart with a short streak behind it, and, for the first instants, the
     * flash and smoke of the split itself. Presentation of a landing already decided.
     */
    private void drawStrike(DrawList out, BattleCamera cam, float cellPx, float alpha, WarheadStrike strike) {
        ShuttleSpriteCache glow = sprites.spriteByPath(GLOW);
        if (glow == null) return;
        Color c = strike.weapon.explosionColor() != null ? strike.weapon.explosionColor() : DEFAULT_FLASH;
        float r = c.getRed() / 255f;
        float g = c.getGreen() / 255f;
        float b = c.getBlue() / 255f;
        if (strike.age() < SPLIT_FLASH_SECONDS) {
            float fade = 1f - strike.age() / SPLIT_FLASH_SECONDS;
            float size = (2f + 6f * (1f - fade)) * cellPx;
            out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite,
                    cam.cellToScreenX(strike.releaseX), cam.cellToScreenY(strike.releaseY), size, size, 0f,
                    1f, 0.9f, 0.7f, 0.8f * fade * alpha);
        }
        float x = strike.x();
        float y = strike.y();
        float dx = strike.aimX - strike.releaseX;
        float dy = strike.aimY - strike.releaseY;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        float ux = len > 0.001f ? dx / len : 0f;
        float uy = len > 0.001f ? dy / len : 0f;
        float tail = Math.min(STRIKE_STREAK_CELLS, strike.progress() * len);
        float width = Math.max(2f, 0.2f * cellPx);
        float hx = cam.cellToScreenX(x);
        float hy = cam.cellToScreenY(y);
        float tx = cam.cellToScreenX(x - ux * tail);
        float ty = cam.cellToScreenY(y - uy * tail);
        out.addLine(RenderLayer.SHUTTLES, hx, hy, tx, ty, width, r, g, b, 0.75f * alpha);
        out.addLine(RenderLayer.SHUTTLES, hx, hy, tx, ty, width * 0.4f, 1f, 1f, 1f, alpha);
        float head = 1.3f * cellPx;
        out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite, hx, hy, head, head, 0f, r, g, b, 0.85f * alpha);
    }
}
