package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.engine.EngineStylePalette;
import com.dillon.starsectormarines.battle.warship.BloomPath;
import com.dillon.starsectormarines.battle.warship.MissileFlightSpec;
import com.dillon.starsectormarines.battle.warship.MissilePath;
import com.dillon.starsectormarines.battle.warship.VanillaBeamLook;
import com.dillon.starsectormarines.battle.warship.VanillaMountLook;
import com.dillon.starsectormarines.battle.warship.VanillaRoundLook;
import com.dillon.starsectormarines.battle.warship.VanillaWeapon;
import com.dillon.starsectormarines.battle.warship.SplitEvent;
import com.dillon.starsectormarines.battle.warship.VanillaWeaponLooks;
import com.dillon.starsectormarines.battle.warship.WarheadStrike;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipAtmosphere;
import com.dillon.starsectormarines.battle.warship.WarshipEngine;
import com.dillon.starsectormarines.battle.warship.WarshipHitFx;
import com.dillon.starsectormarines.battle.warship.WarshipPointDefence;
import com.dillon.starsectormarines.battle.warship.WarshipScale;
import com.dillon.starsectormarines.battle.warship.WarshipShell;
import com.dillon.starsectormarines.battle.warship.WarshipWreck;
import com.dillon.starsectormarines.battle.warship.WarshipThrust;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.ContrailStyle;
import com.dillon.starsectormarines.render2d.ContrailTrail;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a warship hanging over the battle: the game's own hull art at the scale
 * every hull is drawn at, and each fitted weapon on the slot the hull's data
 * places it, pointing where the barrel points, with every sprite layer its spec
 * names (under-plate, base, recoiling barrel, loaded missiles, glow; see
 * {@link VanillaMountLook}).
 *
 * <p>Nothing here is authored by the mod. The hull and turret art are the
 * install's, named by the hull spec and the weapon spec, so a player who has
 * flown the ship sees the ship they know.
 *
 * <p>Rounds in flight are drawn as the weapon's projectile spec says the game draws
 * them ({@link VanillaRoundLook}): a shell as its bullet sprite stretched to the
 * spec's length and width, a {@code BALLISTIC_AS_BEAM} bolt as a tapered streak
 * in its fringe and core colours, a missile as its sprite with its flame and a
 * continuous contrail ribbon. Between hulls they are drawn at the hulls' own
 * scale, so a round stands to the ship it hits as in the base game. A muzzle flash
 * lights the barrel for the instant after it fires, in the weapon's own flash
 * colour. They share the hull's layer so they read as coming from it, and so the
 * headless review frames, which draw that layer, show them.
 *
 * <p>Reads the simulation's warships and nothing else; what they fire, and what
 * it does, is the simulation's.
 */
public final class WarshipRenderSystem implements RenderSystem {

    private static final float MUZZLE_FLASH_SECONDS = 0.15f;
    /** Samples along a missile's contrail ribbon close in, in a crowded sky, and at a far framing. */
    private static final int TRAIL_SAMPLES = 20, TRAIL_SAMPLES_DENSE = 8, TRAIL_SAMPLES_FAR = 5;
    /** How much wider than the spec's width a streak's soft fringe is laid, since the glow texture fades to its rim. */
    private static final float STREAK_FRINGE_SPREAD = 1.7f;
    /** A streak's core against its fringe, across. */
    private static final float STREAK_CORE_SHARE = 0.35f;
    /** A missile's flame against its engine slot's authored length (the base game's full-thrust plume). */
    private static final float MISSILE_FLAME_SHARE = 0.8f;
    /** More bodies than this in the air and each lays a coarser contrail, so a full swarm stays affordable. */
    private static final int DENSE_BODIES = 96;
    private static final int STRIKE_TRAIL_SAMPLES = 6;
    private static final float STRIKE_TRAIL_SECONDS = 0.4f;
    private static final int SPLIT_SMOKE_PUFFS = 7;
    private static final int FOOTPRINT_SEGMENTS = 56;
    /** The furthest a body's drawn trail reaches from it, in cells, for culling. */
    private static final float BODY_CULL_EXTENT_CELLS = 14f;
    /**
     * The same between hulls, where a round is drawn at the hulls' scale and a missile's contrail can lie a few hull
     * lengths behind it.
     */
    private static final float HULL_BODY_CULL_EXTENT_CELLS = 220f;
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
     * Flame length at nozzle level 0 and the extra it grows to by level 1, as shares of the slot's authored
     * length <em>at the shared density</em>. In atmosphere a warship's exhaust is the heat of a heavy hull holding
     * itself up and pushing slowly, not a vacuum burn: short against the hull, and drawn at the nozzle's authored
     * size rather than {@link WarshipScale#HULL_PRESENCE} times it, so growing the body does not grow the torch.
     */
    private static final float ENGINE_LEN_FLOOR = 0.4f;
    private static final float ENGINE_LEN_RANGE = 0.9f;
    /** The longest a flame may be against the hull it pushes, whatever the slot's authored length. */
    private static final float ENGINE_LEN_MAX_HULL_SHARE = 0.35f;
    /** The column is narrower than the nozzle (which is drawn at the hull's size, being part of the body). */
    private static final float ENGINE_WIDTH_FLOOR = 0.55f;
    private static final float ENGINE_WIDTH_RANGE = 0.3f;
    /** A dim hover, a brighter working burn; never the full-strength additive of open space. */
    private static final float ENGINE_ALPHA_FLOOR = 0.45f;
    private static final float ENGINE_ALPHA_CEILING = 0.8f;
    /** The glow is a soft bloom a little wider than the column. */
    private static final float ENGINE_GLOW_WIDTH_MULT = 1.8f;
    /** How much of the flame colour goes to a warm heat white: an atmospheric exhaust is hot air, not a plasma jet. */
    private static final float ENGINE_HEAT_MIX = 0.35f;
    private static final Color ENGINE_HEAT = new Color(235, 220, 205);
    /**
     * The heat haze astern of a working nozzle: a faint, broad smear of warm air, longer than the flame and as wide
     * as a few nozzles. It is what reads as the air being worked rather than a beam being fired.
     */
    private static final float HAZE_LENGTH_MULT = 1.8f;
    private static final float HAZE_WIDTH_MULT = 2.6f;
    private static final float HAZE_ALPHA = 0.2f;

    private final BattleSprites sprites;
    /** Seconds the flicker has run, fed from the host clock or accumulated frame time. */
    private float engineClockSeconds;
    /** The warships' own clock this frame, which a hull's plating dates its breaches on. */
    private float battleSeconds;
    /** Whether this frame's framing is close enough for a swarm body's flame and contrail to read. */
    private boolean swarmDetail = true;
    /** How many samples a missile's contrail ribbon takes this frame. */
    private int tailSamples = TRAIL_SAMPLES;
    /** Contrail ribbons rebuilt every frame from the missiles' flights, reused frame to frame. */
    private final List<ContrailTrail> trailPool = new ArrayList<>();
    private int trailsUsed;
    /** Each missile weapon's contrail, per scale it is drawn at. */
    private final Map<String, ContrailStyle[]> contrailStyles = new HashMap<>();

    private final ShipAtmosphereFx atmosphereFx;
    /** The battle's hit marks, read from the simulation each frame. */
    private WarshipHitFx hitFx;
    /** What the world's surface tints the cloud, the re-entry glow and the dust; read from the battle each frame. */
    private WarshipAtmosphere.Tint tint = WarshipAtmosphere.Tint.forPalette(null);

    /** How much larger a falling wreck is drawn at the height it burst at, read as the aircraft altitude cue. */
    private static final float WRECK_HEIGHT_SCALE = 0.15f;

    public WarshipRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
        this.atmosphereFx = new ShipAtmosphereFx(sprites);
    }

    /** The tint this system last drew with; the battle's own surface, set from the simulation each frame. */
    WarshipAtmosphere.Tint tint() {
        return tint;
    }

    @Override
    public RenderLayer layer() {
        return RenderLayer.SHUTTLES;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        BattleCamera cam = ctx.camera;
        float cellPx = cam.cellPxSize();
        tint = WarshipAtmosphere.Tint.forPalette(ctx.sim.surface());
        engineClockSeconds = Float.isFinite(ctx.animationTimeSeconds)
                ? ctx.animationTimeSeconds
                : engineClockSeconds + Math.max(0f, ctx.realDt);
        sprites.ensureEngineFxSprites();
        battleSeconds = ctx.sim.warships().clock();
        hitFx = ctx.sim.warships().hitFx();
        boolean hitDetail = ZoomDetail.swarmDetailVisible(cam);
        for (Warship ship : ctx.sim.warships().ships()) {
            // The dust a ship's wash kicks and the ring its arrival sends out lie on the ground, under it.
            atmosphereFx.ground(out, cam, cellPx, ctx.alphaMult, ship, tint);
            // A ship in the cloud deck is seen through it.
            float seen = ctx.alphaMult * WarshipAtmosphere.hullVisibility(
                    ship.entrySeconds(), ship.climbSeconds(), ship.departing());
            // Under the hull: the nozzles sit inside its outline and the flame trails out of it. A berthed hull is cold.
            if (!ship.berthed()) drawEngines(out, cam, cellPx, seen, ship);
            drawHull(out, cam, cellPx, seen, ship);
            // A docked hull's guns are stowed; they run out as it lifts.
            float armed = seen * ship.liftProgress();
            for (Warship.Mount m : ship.mounts()) {
                // A mount lost with the plating under it is gone: the breach beneath is what shows.
                if (m.knockedOut() || armed <= 0f) continue;
                drawMount(out, cam, cellPx, armed, ship, m);
                drawMuzzleFlash(out, cam, cellPx, armed, ship, m);
            }
            // What the hull has taken: blasts, sparks and chips over the plate, and the open holes venting.
            WarshipHitFxRenderer.breachVents(out, cam, sprites, ship, seen, battleSeconds);
            WarshipHitFxRenderer.hullMarks(out, cam, sprites, hitFx, ship, seen, battleSeconds, hitDetail);
            if (!ship.berthed()) {
                drawShield(out, cam, cellPx, seen, ship);
                WarshipHitFxRenderer.shieldMarks(out, cam, sprites, hitFx, ship, seen, battleSeconds, hitDetail);
                WarshipHitFxRenderer.overloadArcs(out, cam, sprites, ship, seen, battleSeconds);
            }
            // Cloud and heat over the hull: the deck it is dropping through, the burn of coming down.
            atmosphereFx.sky(out, cam, cellPx, ctx.alphaMult, ship, tint);
        }
        // The smoke a holed ship trails hangs where it was let go, over every hull.
        WarshipHitFxRenderer.trailSmoke(out, cam, sprites, hitFx, ctx.alphaMult, battleSeconds);
        for (WarshipWreck wreck : ctx.sim.warships().wrecks()) drawWreck(out, cam, cellPx, ctx.alphaMult, wreck);
        ViewCull cull = ViewCull.of(cam);
        List<WarshipShell> shells = ctx.sim.warships().shells();
        List<WarheadStrike> strikes = ctx.sim.warships().strikes();
        swarmDetail = ZoomDetail.swarmDetailVisible(cam);
        tailSamples = !swarmDetail ? TRAIL_SAMPLES_FAR
                : shells.size() + strikes.size() > DENSE_BODIES ? TRAIL_SAMPLES_DENSE : TRAIL_SAMPLES;
        trailsUsed = 0;
        List<WarshipShell.Footprint> ringed = new ArrayList<>(4);
        for (WarshipShell shell : shells) {
            WarshipShell.Footprint footprint = shell.footprint();
            if (footprint != null && !ringed.contains(footprint)) {
                ringed.add(footprint);
                drawFootprint(out, cam, cellPx, ctx.alphaMult, shell);
            }
            float extent = shell.betweenHulls() ? HULL_BODY_CULL_EXTENT_CELLS : BODY_CULL_EXTENT_CELLS;
            if (!cull.visible(shell.x(), shell.y(), extent)) continue;
            drawShell(out, cam, cellPx, ctx.alphaMult, shell);
        }
        for (WarheadStrike strike : strikes) {
            if (!cull.visible(strike.x(), strike.y(), BODY_CULL_EXTENT_CELLS)) continue;
            drawStrike(out, cam, cellPx, ctx.alphaMult, strike);
        }
        for (SplitEvent split : ctx.sim.warships().splits()) {
            drawSplit(out, cam, cellPx, ctx.alphaMult, split);
        }
        for (WarshipPointDefence.Mark mark : ctx.sim.warships().pointDefence().marks()) {
            drawEngagement(out, cam, cellPx, ctx.alphaMult, mark);
        }
    }

    /**
     * Every nozzle the hull's data lists, burning in a muted, heat-whitened take on the game's engine colour for its
     * style: a soft glow at the nozzle, a short exhaust column along the slot's angle on the turned hull, and a faint
     * haze of worked air behind it. How hard it burns is the ship's actual motion ({@link WarshipThrust}): a low glow
     * hovering and barely more cruising, a modest flare accelerating or coming about (the nozzles on the outside of
     * the turn hardest), dimmed braking, with a little flicker so it is not a decal. Additive, so it brightens the
     * hull and ground beneath rather than covering them. The flame is sized to the nozzle's authored length at the
     * shared density and capped against the hull's length, so a ship of the line never trails a torch.
     */
    private void drawEngines(DrawList out, BattleCamera cam, float cellPx, float alpha, Warship ship) {
        List<WarshipEngine> engines = WarshipEngine.forHull(ship.hullId);
        if (engines.isEmpty()) return;
        SpriteAPI flame = sprites.warshipEngineFlameSprite();
        SpriteAPI glow = sprites.warshipEngineGlowSprite();
        if (flame == null && glow == null) return;
        ShuttleSpriteCache soft = sprites.spriteByPath(GLOW);
        WarshipThrust.Demand demand = WarshipThrust.of(ship);
        float body = ship.bodyScale();
        // The nozzles are read once per hull at the standard presence; a hull fitted at its own carries them with it.
        float nozzle = body * ship.presenceShare();
        double rad = Math.toRadians(ship.facingDegrees);
        float c = (float) Math.cos(rad);
        float s = (float) Math.sin(rad);
        float halfBeam = Math.max(0.5f, ship.widthCells * 0.5f);
        float maxLen = ship.lengthCells * ENGINE_LEN_MAX_HULL_SHARE;
        for (int i = 0; i < engines.size(); i++) {
            WarshipEngine e = engines.get(i);
            float heading = ship.facingDegrees + e.angleDegrees();
            float level = demand.level() * WarshipThrust.slotShare(demand.pushX(), demand.pushY(), heading)
                    * WarshipThrust.turnShare(demand.turn(), e.localX() * ship.presenceShare() / halfBeam);
            level = Math.max(0f, Math.min(1f, level * flicker(i)));
            float[] col = engineColour(e.style());
            float r = col[0];
            float g = col[1];
            float b = col[2];

            float wx = ship.x + nozzle * (e.localX() * c - e.localY() * s);
            float wy = ship.y + nozzle * (e.localX() * s + e.localY() * c);
            float sx = cam.cellToScreenX(wx);
            float sy = cam.cellToScreenY(wy);
            double hr = Math.toRadians(heading);
            float dirX = (float) -Math.sin(hr);
            float dirY = (float) Math.cos(hr);

            float lenPx = body * Math.min(maxLen, engineFlameCells(e.lengthCells(), level)) * cellPx;
            float widthPx = nozzle * e.widthCells() * cellPx * (ENGINE_WIDTH_FLOOR + ENGINE_WIDTH_RANGE * level);
            float lit = alpha * (ENGINE_ALPHA_FLOOR + (ENGINE_ALPHA_CEILING - ENGINE_ALPHA_FLOOR) * level);
            float glowPx = Math.max(widthPx * ENGINE_GLOW_WIDTH_MULT, cellPx * 0.4f);

            // The haze first and broadest, under the flame: warm air being worked, strongest when the nozzle is.
            if (soft != null) {
                float hazeLen = lenPx * HAZE_LENGTH_MULT;
                out.addAdditiveSprite(RenderLayer.SHUTTLES, soft.sprite,
                        sx + dirX * hazeLen * 0.45f, sy + dirY * hazeLen * 0.45f,
                        widthPx * HAZE_WIDTH_MULT, hazeLen, heading, r, g, b, alpha * HAZE_ALPHA * (0.3f + level));
            }
            // The glow next, so the column reads as coming out of it: a round soft bloom. The game's engine glow is
            // a bar (opaque edge to edge across, fading only along its length), so laid as a square it drew a hard
            // block at every nozzle; it is kept only as a bar across the nozzle when the soft disc is missing.
            if (soft != null) {
                out.addAdditiveSprite(RenderLayer.SHUTTLES, soft.sprite, sx, sy, glowPx, glowPx, 0f, r, g, b, lit);
            } else if (glow != null) {
                out.addAdditiveSprite(RenderLayer.SHUTTLES, glow, sx, sy, widthPx, glowPx * 0.5f, heading,
                        r, g, b, lit);
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

    /**
     * How long one nozzle's flame is, in cells, at {@code level}: the slot's authored length brought back to the
     * shared density (the hull's presence grows the body, not the burn), scaled between a short hover glow and a
     * modest working flare. The caller also caps it against the hull's length.
     */
    static float engineFlameCells(float slotLengthCells, float level) {
        float l = Math.max(0f, Math.min(1f, level));
        return slotLengthCells / WarshipScale.HULL_PRESENCE * (ENGINE_LEN_FLOOR + ENGINE_LEN_RANGE * l);
    }

    /** The game's engine colour for {@code style}, part-way to a warm heat white, as 0-1 channels. */
    static float[] engineColour(String style) {
        Color col = EngineStylePalette.flameColor(style);
        float k = ENGINE_HEAT_MIX;
        return new float[]{
                (col.getRed() + (ENGINE_HEAT.getRed() - col.getRed()) * k) / 255f,
                (col.getGreen() + (ENGINE_HEAT.getGreen() - col.getGreen()) * k) / 255f,
                (col.getBlue() + (ENGINE_HEAT.getBlue() - col.getBlue()) * k) / 255f};
    }

    /** A little life in each nozzle: two incommensurate sines, a different phase for every slot. */
    private float flicker(int slot) {
        float t = engineClockSeconds;
        return 1f + 0.07f * (float) Math.sin(t * 31f + slot * 1.7f)
                + 0.05f * (float) Math.sin(t * 53f + slot * 2.9f);
    }

    private void drawHull(DrawList out, BattleCamera cam, float cellPx, float alpha, Warship ship) {
        ShuttleSpriteCache hull = ship.lowHoverWidth() > 0f
                ? sprites.spriteByPath("graphics/tilesets/shepherd-recovery.png") : GroundHullArt.sprite(sprites, ship);
        if (hull == null) return;
        float rad = (float) Math.toRadians(ship.facingDegrees);
        float c = (float) Math.cos(rad);
        float s = (float) Math.sin(rad);
        // A berthed hull is drawn at true landed scale, easing to its flight size as it lifts; the body grows
        // about the centre of gravity, so the pivot, slots and nozzles grow with it.
        float body = ship.bodyScale();
        // The sprite's centre sits at the authored pivot from the centre of
        // gravity, in the hull frame; the hull rotates about its CoG.
        float hx = ship.x + body * (ship.pivotX * c - ship.pivotY * s);
        float hy = ship.y + body * (ship.pivotX * s + ship.pivotY * c);
        if (ship.lowHoverWidth() > 0f) { hx = ship.x; hy = ship.y; }
        float pxH = (ship.lowHoverWidth() > 0f ? ship.lowHoverWidth() : ship.lengthCells * body) * cellPx;
        float pxW = ship.lowHoverWidth() > 0f ? ship.lowHoverHeight() * cellPx : pxH * hull.aspect;
        // A landed hull is a ground object among the vehicles; it takes to the air layer as it lifts.
        RenderLayer layer = ship.berthed() ? RenderLayer.VEHICLES : RenderLayer.SHUTTLES;
        // A hull with an armour grid shows its damage where it took it: stripped, scorched, cut away.
        if (ship.lowHoverWidth() <= 0f && WarshipHullDamageRenderer.draw(out, layer, cam, sprites, hull, ship, hx, hy,
                pxW, pxH, alpha, 1f, battleSeconds, engineClockSeconds)) {
            return;
        }
        // A hull with no grid (the control) darkens toward charcoal as its hit points go; an untouched grid is clean.
        float tint = ship.plating() != null ? 1f : 0.4f + 0.6f * ship.hullFraction();
        out.addSprite(layer, hull.sprite,
                cam.cellToScreenX(hx), cam.cellToScreenY(hy),
                pxW, pxH, ship.facingDegrees,
                tint, tint, tint, alpha);
    }

    /**
     * A hull taken to nothing: charred, its body coming back to true landed scale as it falls
     * ({@link WarshipWreck#bodyScale}) with the aircraft altitude cue on top (a little larger while it is still up),
     * and once it has struck, lying at true scale among the parked vehicles where it came to rest. The hull-damage
     * cuts are drawn at the same size.
     */
    private void drawWreck(DrawList out, BattleCamera cam, float cellPx, float alpha, WarshipWreck wreck) {
        Warship ship = wreck.ship;
        ShuttleSpriteCache hull = GroundHullArt.sprite(sprites, ship);
        if (hull == null) return;
        float rad = (float) Math.toRadians(ship.facingDegrees);
        float c = (float) Math.cos(rad);
        float s = (float) Math.sin(rad);
        float body = wreck.bodyScale();
        float hx = ship.x + body * (ship.pivotX * c - ship.pivotY * s);
        float hy = ship.y + body * (ship.pivotX * s + ship.pivotY * c);
        float scale = body * (1f + WRECK_HEIGHT_SCALE * wreck.lift());
        float pxH = ship.lengthCells * cellPx * scale;
        float pxW = pxH * hull.aspect;
        boolean down = wreck.phase() != WarshipWreck.Phase.FALLING;
        float tint = down ? 0.22f : 0.3f;
        // A hull that was holed falls and lies holed: the same cut, burnt.
        if (WarshipHullDamageRenderer.draw(out, down ? RenderLayer.VEHICLES : RenderLayer.SHUTTLES, cam, sprites, hull,
                ship, hx, hy, pxW, pxH, alpha, tint * 2.4f, battleSeconds, engineClockSeconds)) {
            return;
        }
        out.addSprite(down ? RenderLayer.VEHICLES : RenderLayer.SHUTTLES, hull.sprite,
                cam.cellToScreenX(hx), cam.cellToScreenY(hy), pxW, pxH, ship.facingDegrees,
                tint, tint * 0.9f, tint * 0.85f, alpha);
    }

    /**
     * A fitted weapon, drawn as the game draws it: every layer its spec names
     * ({@link VanillaMountLook#layers}), bottom to top: the under-plate, the barrel
     * and the base (the barrel beneath only for {@code RENDER_BARREL_BELOW}), any
     * missiles loaded in an open rack, and the glow lit in its own colour as it
     * fires. A turret's sprites centre on the mount; a hardpoint's sit a quarter of
     * their height forward of it, where the game pivots one. The barrel slides back
     * by the spec's {@code visualRecoil} on each shot and runs out again. A weapon
     * whose spec cannot be read draws its base alone.
     */
    private void drawMount(DrawList out, BattleCamera cam, float cellPx, float alpha,
                           Warship ship, Warship.Mount m) {
        boolean hp = m.slot.hardpoint();
        // A mount is part of the hull's body, so it is drawn at the hull's size, berthed or flying, and at its presence.
        float body = ship.bodyScale() * ship.presenceShare();
        RenderLayer layer = ship.berthed() ? RenderLayer.VEHICLES : RenderLayer.SHUTTLES;
        float heading = heading(ship, m);
        float px = bodyX(ship, ship.mountX(m));
        float py = bodyY(ship, ship.mountY(m));
        VanillaMountLook look = VanillaWeaponLooks.mount(m.weapon.id());
        if (look == null) {
            ShuttleSpriteCache art = sprites.spriteByPath(hp ? m.weapon.hardpointSprite() : m.weapon.turretSprite());
            if (art != null) drawLayer(out, cam, cellPx, layer, art, false, px, py, heading, hp, 0f, body,
                    1f, 1f, 1f, alpha);
            return;
        }
        float cooldown = m.weapon.cooldownSeconds();
        float recoil = WarshipScale.hullCells(look.recoilPx(m.secondsSinceFired, cooldown)) * body;
        // An animated weapon (a rotary cannon's barrels) turns its frames while it fires.
        int frame = look.frameAt(battleSeconds, m.secondsSinceFired, cooldown);
        for (VanillaMountLook.Layer l : look.layers(hp)) {
            if (l.kind() == VanillaMountLook.Kind.LOADED_MISSILES) {
                drawLoadedMissiles(out, cam, cellPx, layer, alpha, ship, m, look, px, py, heading);
                continue;
            }
            ShuttleSpriteCache art = sprites.spriteByPath(look.frameSprite(l.sprite(), frame));
            if (art == null) art = sprites.spriteByPath(l.sprite());
            if (art == null) continue;
            switch (l.kind()) {
                case BARREL -> drawLayer(out, cam, cellPx, layer, art, false, px, py, heading, hp, -recoil, body,
                        1f, 1f, 1f, alpha);
                case GLOW -> {
                    float lit = look.glowLevel(m.secondsSinceFired, cooldown);
                    if (lit > 0.01f) {
                        Color g = look.glowColor();
                        drawLayer(out, cam, cellPx, layer, art, true, px, py, heading, hp, 0f, body,
                                g.getRed() / 255f, g.getGreen() / 255f, g.getBlue() / 255f,
                                alpha * lit * g.getAlpha() / 255f);
                    }
                }
                case BASE -> drawLayer(out, cam, cellPx, layer, art, look.additive(), px, py, heading, hp, 0f, body,
                        1f, 1f, 1f, alpha);
                default -> drawLayer(out, cam, cellPx, layer, art, false, px, py, heading, hp, 0f, body,
                        1f, 1f, 1f, alpha);
            }
        }
    }

    /**
     * One weapon sprite on its mount at {@code (px, py)}, pointing {@code heading}, moved {@code along} cells along
     * the barrel (negative is recoil). A hardpoint sprite is pivoted a quarter of its height from its breech.
     */
    private static void drawLayer(DrawList out, BattleCamera cam, float cellPx, RenderLayer layer,
                                  ShuttleSpriteCache art, boolean additive, float px, float py, float heading,
                                  boolean hardpoint, float along, float body, float r, float g, float b, float a) {
        float wCells = WarshipScale.hullCells(art.pxW) * body;
        float hCells = WarshipScale.hullCells(art.pxH) * body;
        float ahead = along + (hardpoint ? hCells * 0.25f : 0f);
        double rad = Math.toRadians(heading);
        float cx = px - (float) Math.sin(rad) * ahead;
        float cy = py + (float) Math.cos(rad) * ahead;
        float sx = cam.cellToScreenX(cx);
        float sy = cam.cellToScreenY(cy);
        if (additive) {
            out.addAdditiveSprite(layer, art.sprite, sx, sy, wCells * cellPx, hCells * cellPx, heading, r, g, b, a);
        } else {
            out.addSprite(layer, art.sprite, sx, sy, wCells * cellPx, hCells * cellPx, heading, r, g, b, a);
        }
    }

    /**
     * The missiles an open rack shows ({@code RENDER_LOADED_MISSILES}): the round's own sprite on every barrel it
     * leaves from, nose along the barrel. A barrel that has just fired stands empty until the launcher has cycled,
     * and a rack whose magazine is spent shows none.
     */
    private void drawLoadedMissiles(DrawList out, BattleCamera cam, float cellPx, RenderLayer layer, float alpha,
                                    Warship ship, Warship.Mount m, VanillaMountLook look,
                                    float px, float py, float heading) {
        if (m.ammoRemaining() == 0) return;
        ShuttleSpriteCache art = sprites.spriteByPath(look.missileSprite());
        if (art == null) return;
        boolean hp = m.slot.hardpoint();
        float body = ship.bodyScale() * ship.presenceShare();
        boolean cycling = m.secondsSinceFired < Math.max(0.3f, m.weapon.cooldownSeconds());
        boolean[] empty = cycling ? look.firedBarrels(hp, m.shotsFired, m.weapon.burstSize()) : null;
        float wPx = look.missileWidthPx() > 0f ? look.missileWidthPx() : art.pxW;
        float hPx = look.missileHeightPx() > 0f ? look.missileHeightPx() : art.pxH;
        float wCells = WarshipScale.hullCells(wPx) * body;
        float hCells = WarshipScale.hullCells(hPx) * body;
        float ahead = WarshipScale.hullCells(look.missileCentreForwardPx()) * body;
        List<VanillaMountLook.Barrel> barrels = look.barrels(hp);
        for (int i = 0; i < barrels.size(); i++) {
            if (empty != null && empty[i]) continue;
            VanillaMountLook.Barrel b = barrels.get(i);
            float[] at = barrelPoint(px, py, heading, b, body);
            double rad = Math.toRadians(heading + b.angleDegrees());
            float cx = at[0] - (float) Math.sin(rad) * ahead;
            float cy = at[1] + (float) Math.cos(rad) * ahead;
            out.addSprite(layer, art.sprite, cam.cellToScreenX(cx), cam.cellToScreenY(cy),
                    wCells * cellPx, hCells * cellPx, heading + b.angleDegrees(), 1f, 1f, 1f, alpha);
        }
    }

    /**
     * Where a barrel's round leaves, in cells: the spec's offset (forward along the weapon, left across it) from the
     * mount at {@code (px, py)}, turned to {@code heading} and grown with the hull's body.
     */
    static float[] barrelPoint(float px, float py, float heading, VanillaMountLook.Barrel b, float body) {
        double rad = Math.toRadians(heading);
        float s = (float) Math.sin(rad);
        float c = (float) Math.cos(rad);
        float fwd = WarshipScale.hullCells(b.forwardPx()) * body;
        float left = WarshipScale.hullCells(b.leftPx()) * body;
        // Forward is (-sin, cos); left of it is (-cos, -sin).
        return new float[]{px - s * fwd - c * left, py + c * fwd - s * left};
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
        // The fuller the pool, the more the bubble strains: it warms toward violet, shivers and flickers.
        float strain = WarshipHitFxRenderer.strain(ship.fluxFraction());
        float[] tint = WarshipHitFxRenderer.shieldColour(strain);
        Color base = drop > 0.01f && level < 1f ? SHIELD_COLLAPSE
                : new Color(tint[0], tint[1], tint[2]);
        // The whole bubble only brightens a little on a hit: the flare at the point struck carries it.
        float mix = Math.min(1f, hit) * 0.5f;
        float r = lerp(base.getRed(), SHIELD_HIT.getRed(), mix) / 255f;
        float g = lerp(base.getGreen(), SHIELD_HIT.getGreen(), mix) / 255f;
        float b = lerp(base.getBlue(), SHIELD_HIT.getBlue(), mix) / 255f;
        float shiver = 1f - strain * (0.3f + 0.3f * (float) Math.sin(battleSeconds * 37f)
                * (float) Math.sin(battleSeconds * 11f + ship.lengthCells));
        float a = alpha * strength * shiver;
        hit *= 0.5f;
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
        float start = WarshipHitFxRenderer.shieldFacing(ship, hitFx) - arc * 0.5f;
        float step = arc / segments;
        float lastX = 0f;
        float lastY = 0f;
        for (int i = 0; i <= segments; i++) {
            double rad = Math.toRadians(start + step * i);
            // A strained rim shivers: a ripple running round it, quicker and deeper the fuller the pool.
            double around = Math.toRadians(step * i);
            float rr = radius * (1f + strain * 0.012f * (float) Math.sin(around * 9.0 - battleSeconds * 14f)
                    + strain * 0.006f * (float) Math.sin(around * 23.0 + battleSeconds * 31f));
            float px = cam.cellToScreenX(cx - (float) Math.sin(rad) * rr);
            float py = cam.cellToScreenY(cy + (float) Math.cos(rad) * rr);
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
        WarshipHitFxRenderer.strainCrackle(out, cam, hitFx, ship, strain, alpha, battleSeconds);
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    /** A world point on the hull, moved out from the centre of gravity by the body's landed scale. */
    private static float bodyX(Warship ship, float worldX) {
        return ship.x + (worldX - ship.x) * ship.bodyScale();
    }

    /** See {@link #bodyX}. */
    private static float bodyY(Warship ship, float worldY) {
        return ship.y + (worldY - ship.y) * ship.bodyScale();
    }

    /** A hardpoint is fixed to the hull; a turret points where its barrel does. */
    private static float heading(Warship ship, Warship.Mount m) {
        return m.slot.hardpoint() ? ship.facingDegrees + m.slot.angleDegrees() : m.headingDegrees;
    }

    /**
     * The flash at the muzzle of the barrel that just fired (every barrel, for a linked weapon), at the offset the
     * weapon's spec gives its rounds, in the weapon's own flash colour. A weapon whose spec cannot be read flashes a
     * fixed barrel's length out.
     */
    private void drawMuzzleFlash(DrawList out, BattleCamera cam, float cellPx, float alpha,
                                 Warship ship, Warship.Mount m) {
        if (m.secondsSinceFired >= MUZZLE_FLASH_SECONDS) return;
        ShuttleSpriteCache glow = sprites.spriteByPath(GLOW);
        if (glow == null) return;
        float fade = 1f - m.secondsSinceFired / MUZZLE_FLASH_SECONDS;
        Color c = m.weapon.muzzleColor() != null ? m.weapon.muzzleColor() : DEFAULT_FLASH;
        float heading = heading(ship, m);
        float px = bodyX(ship, ship.mountX(m));
        float py = bodyY(ship, ship.mountY(m));
        float size = Math.max(1.5f, WarshipScale.cells(m.weapon.muzzleLengthPx()) * 2f) * cellPx;
        float r = c.getRed() / 255f;
        float g = c.getGreen() / 255f;
        float b = c.getBlue() / 255f;
        VanillaMountLook look = VanillaWeaponLooks.mount(m.weapon.id());
        if (look == null) {
            double rad = Math.toRadians(heading);
            float barrel = 1.2f * ship.presence();
            out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite,
                    cam.cellToScreenX(px - (float) Math.sin(rad) * barrel),
                    cam.cellToScreenY(py + (float) Math.cos(rad) * barrel), size, size, 0f,
                    r, g, b, 0.9f * fade * alpha);
            return;
        }
        boolean hp = m.slot.hardpoint();
        List<VanillaMountLook.Barrel> barrels = look.barrels(hp);
        boolean[] fired = look.firedBarrels(hp, m.shotsFired, 1);
        for (int i = 0; i < barrels.size(); i++) {
            if (!fired[i]) continue;
            float[] at = barrelPoint(px, py, heading, barrels.get(i), ship.bodyScale() * ship.presenceShare());
            out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite,
                    cam.cellToScreenX(at[0]), cam.cellToScreenY(at[1]), size, size, 0f,
                    r, g, b, 0.9f * fade * alpha);
        }
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

    /**
     * A round in flight, as its projectile spec says the game draws it ({@link VanillaRoundLook}): a shell is its
     * bullet sprite stretched to the spec's length and width along its flight, a {@code BALLISTIC_AS_BEAM} bolt a
     * tapered streak of light, a missile its sprite with its flame and contrail. A round between hulls is drawn at the
     * hulls' own scale, so it stands to the ship it hits as in the base game; a round at the ground or the air at the
     * shared density. The glow under it is the spec's own, never larger than the round when the spec names none.
     */
    private void drawShell(DrawList out, BattleCamera cam, float cellPx, float alpha, WarshipShell shell) {
        VanillaWeapon w = shell.weapon;
        if (shell.hasBeam()) drawStandoffBeam(out, cam, cellPx, alpha, shell);
        VanillaRoundLook look = VanillaWeaponLooks.round(w.id());
        boolean hull = shell.betweenHulls();
        float scale = VanillaRoundLook.cellsPerPx(hull);
        // The round is where its flight function says it is now, nose along the path's tangent.
        float x = shell.x();
        float y = shell.y();
        float heading = shell.headingDegrees();
        if (w.missile()) {
            drawMissile(out, cam, cellPx, alpha, shell, look, scale, hull, x, y, heading);
            return;
        }
        float lengthPx = look != null ? look.lengthPx() : w.projectileLengthPx();
        float widthPx = look != null ? look.widthPx() : w.projectileWidthPx();
        // The round's tip is its position; it trails back along its flight, never further than it has flown.
        float flown = (float) Math.hypot(x - shell.fromX, y - shell.fromY);
        float length = Math.min(Math.max(1f, lengthPx) * scale, Math.max(flown, 0.2f * lengthPx * scale));
        float width = Math.max(1f, widthPx) * scale;
        ShuttleSpriteCache art = look == null || look.kind() == VanillaRoundLook.Kind.SHELL
                ? sprites.spriteByPath(w.projectileSprite()) : null;
        Color fringe = w.fringe() != null ? w.fringe() : look == null || look.fringe() == null
                ? DEFAULT_FLASH : look.fringe();
        if (art != null) {
            drawRoundGlow(out, cam, cellPx, alpha, look, fringe, scale, x, y, heading, length, width, 0.5f);
            float[] at = along(x, y, heading, -0.5f * length);
            out.addSprite(RenderLayer.SHUTTLES, art.sprite, cam.cellToScreenX(at[0]), cam.cellToScreenY(at[1]),
                    Math.max(1.5f, width * cellPx), Math.max(2f, length * cellPx), heading, 1f, 1f, 1f, alpha);
            return;
        }
        drawStreak(out, cam, cellPx, alpha, look, fringe, w.core() != null ? w.core() : Color.WHITE, scale,
                x, y, heading, length, width);
    }

    /** The point {@code d} cells along {@code heading} from {@code (x, y)}; negative is astern. */
    private static float[] along(float x, float y, float heading, float d) {
        double rad = Math.toRadians(heading);
        return new float[]{x - (float) Math.sin(rad) * d, y + (float) Math.cos(rad) * d};
    }

    /**
     * The glow under a round. The spec's own ({@code glowColor} at {@code glowRadius}, at the round's scale) where it
     * names one; else a soft halo in the round's fringe colour hugging the round, no longer than it and at most three
     * of its widths across. Centred {@code centreBack} of the round's length astern of its tip.
     */
    private void drawRoundGlow(DrawList out, BattleCamera cam, float cellPx, float alpha, VanillaRoundLook look,
                               Color fringe, float scale, float x, float y, float heading, float length, float width,
                               float centreBack) {
        ShuttleSpriteCache glow = sprites.spriteByPath(GLOW);
        if (glow == null) return;
        float[] at = along(x, y, heading, -centreBack * length);
        if (look != null && look.glowColor() != null && look.glowRadiusPx() > 0f) {
            Color c = look.glowColor();
            float d = 2f * look.glowRadiusPx() * scale * cellPx;
            out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite, cam.cellToScreenX(at[0]),
                    cam.cellToScreenY(at[1]), d, d, 0f, c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f,
                    c.getAlpha() / 255f * alpha);
            return;
        }
        float across = Math.min(length, 3f * width);
        out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite, cam.cellToScreenX(at[0]), cam.cellToScreenY(at[1]),
                Math.max(2f, across * cellPx), Math.max(2f, length * cellPx), heading,
                fringe.getRed() / 255f, fringe.getGreen() / 255f, fringe.getBlue() / 255f,
                0.45f * fringe.getAlpha() / 255f * alpha);
    }

    /**
     * A {@code BALLISTIC_AS_BEAM} bolt (or a projectile with no sprite): a streak of light {@code length} by
     * {@code width} trailing back from its tip, in its fringe colour with a narrower core, brightest toward the head
     * and fading soft at both ends, over the spec's own glow. Additive, as the game's is.
     */
    private void drawStreak(DrawList out, BattleCamera cam, float cellPx, float alpha, VanillaRoundLook look,
                            Color fringe, Color core, float scale, float x, float y, float heading,
                            float length, float width) {
        ShuttleSpriteCache glow = sprites.spriteByPath(GLOW);
        if (glow == null) return;
        if (look != null && look.glowColor() != null && look.glowRadiusPx() > 0f) {
            drawRoundGlow(out, cam, cellPx, alpha, look, fringe, scale, x, y, heading, length, width, 0.3f);
        }
        float fr = fringe.getRed() / 255f, fg = fringe.getGreen() / 255f, fb = fringe.getBlue() / 255f;
        float fa = fringe.getAlpha() / 255f;
        // The glow texture falls off to nothing at its rim, so it is laid a little wider than the streak.
        float wide = Math.max(2f, STREAK_FRINGE_SPREAD * width * cellPx);
        float[] body = along(x, y, heading, -0.5f * length);
        out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite, cam.cellToScreenX(body[0]), cam.cellToScreenY(body[1]),
                wide, Math.max(2f, length * cellPx), heading, fr, fg, fb, 0.75f * fa * alpha);
        float[] head = along(x, y, heading, -0.22f * length);
        out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite, cam.cellToScreenX(head[0]), cam.cellToScreenY(head[1]),
                wide, Math.max(2f, 0.45f * length * cellPx), heading, fr, fg, fb, 0.6f * fa * alpha);
        float[] mid = along(x, y, heading, -0.36f * length);
        out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite, cam.cellToScreenX(mid[0]), cam.cellToScreenY(mid[1]),
                Math.max(1.5f, STREAK_CORE_SHARE * STREAK_FRINGE_SPREAD * width * cellPx),
                Math.max(2f, 0.68f * length * cellPx), heading,
                core.getRed() / 255f, core.getGreen() / 255f, core.getBlue() / 255f, core.getAlpha() / 255f * alpha);
    }

    /**
     * What a standoff missile does once it has stopped: a thin pulsing paint line to the ground while it
     * paints, then the payload's own beam, in the colours and width of its weapon, fading as it burns.
     * Presentation of a landing already committed; nothing here decides damage.
     */
    private void drawStandoffBeam(DrawList out, BattleCamera cam, float cellPx, float alpha, WarshipShell shell) {
        WarshipShell.BeamPhase phase = shell.phase();
        if (phase == WarshipShell.BeamPhase.FLIGHT) return;
        VanillaBeamLook look = VanillaWeaponLooks.beam(shell.beam().payloadWeaponId());
        Color fringe = look != null && look.fringe() != null ? look.fringe() : DEFAULT_FLASH;
        Color core = look != null && look.core() != null ? look.core() : Color.WHITE;
        float x0 = cam.cellToScreenX(shell.x());
        float y0 = cam.cellToScreenY(shell.y());
        float x1 = cam.cellToScreenX(shell.toX);
        float y1 = cam.cellToScreenY(shell.toY);
        float progress = shell.phaseProgress();
        ShuttleSpriteCache glow = sprites.spriteByPath(GLOW);
        if (phase == WarshipShell.BeamPhase.PAINT) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(engineClockSeconds * 14f);
            float a = (0.25f + 0.35f * progress) * (0.6f + 0.4f * pulse) * alpha;
            out.addLine(RenderLayer.SHUTTLES, x0, y0, x1, y1, Math.max(1.5f, 0.07f * cellPx),
                    fringe.getRed() / 255f, fringe.getGreen() / 255f, fringe.getBlue() / 255f, a);
            if (glow != null) {
                float size = (0.8f + 1.6f * progress) * cellPx;
                out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite, x1, y1, size, size, 0f,
                        fringe.getRed() / 255f, fringe.getGreen() / 255f, fringe.getBlue() / 255f, a);
            }
            return;
        }
        float f = 1f - 0.5f * progress;
        float widthPx = Math.max(3f, WarshipScale.cells(look != null ? look.widthPx() : 40f) * cellPx * 0.5f);
        out.addLine(RenderLayer.SHUTTLES, x0, y0, x1, y1, widthPx,
                fringe.getRed() / 255f, fringe.getGreen() / 255f, fringe.getBlue() / 255f, 0.75f * f * alpha);
        out.addLine(RenderLayer.SHUTTLES, x0, y0, x1, y1, widthPx * 0.4f,
                core.getRed() / 255f, core.getGreen() / 255f, core.getBlue() / 255f, f * alpha);
        if (glow != null) {
            float size = 5f * cellPx * f;
            out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite, x1, y1, size, size, 0f,
                    fringe.getRed() / 255f, fringe.getGreen() / 255f, fringe.getBlue() / 255f, 0.9f * f * alpha);
        }
    }

    /**
     * A missile in flight: its body at its spec's size and scale, held at its spec's centre; the flame at its nozzle
     * while the motor burns, with the engine's glow; and its contrail laid as one continuous ribbon along the path it
     * actually flew (the same flight function sampled at earlier times, so it hangs along the weave and costs no
     * state), the spec's own width widening to its spread at the end and fading out over the spec's duration on the
     * fight's clock. Without a readable spec it is the weapon's sprite and a grey trail.
     */
    private void drawMissile(DrawList out, BattleCamera cam, float cellPx, float alpha, WarshipShell shell,
                             VanillaRoundLook look, float scale, boolean hull, float x, float y, float heading) {
        MissileFlightSpec flight = shell.weapon.flight();
        Color flame = look != null && look.engineColor() != null ? look.engineColor()
                : flight != null && flight.engineColor() != null ? flight.engineColor() : MISSILE_FLAME;
        drawContrail(out, alpha, shell, look, flight, scale, hull);
        float now = shell.elapsed();
        boolean burning = flight == null || flight.burnSeconds() <= 0f || now <= flight.burnSeconds();
        ShuttleSpriteCache glow = sprites.spriteByPath(GLOW);
        float behind = (look != null ? look.nozzleBehindPx() : 10f) * scale;
        float[] nozzle = along(x, y, heading, -behind);
        float flameWidthPx = look != null ? look.flameWidthPx() : flight != null ? flight.flameWidthPx() : 4f;
        float flameLengthPx = look != null ? look.flameLengthPx() : flight != null ? flight.flameLengthPx() : 20f;
        if (burning && glow != null) {
            float glowMult = look != null ? Math.max(1f, look.engineGlowMult()) : 1f;
            float d = Math.max(2f, 2f * flameWidthPx * scale * glowMult * cellPx);
            out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite, cam.cellToScreenX(nozzle[0]),
                    cam.cellToScreenY(nozzle[1]), d, d, 0f,
                    flame.getRed() / 255f, flame.getGreen() / 255f, flame.getBlue() / 255f, 0.55f * alpha);
        }
        SpriteAPI flameSprite = sprites.warshipEngineFlameSprite();
        if (burning && flameSprite != null && swarmDetail) {
            // The nozzle burns while the motor does; the flame points back down the path from the nozzle.
            float flicker = 0.85f + 0.15f * (float) Math.sin(engineClockSeconds * 47f + x * 3.1f);
            float len = flameLengthPx * scale * MISSILE_FLAME_SHARE * flicker;
            float[] at = along(nozzle[0], nozzle[1], heading, -0.5f * len);
            out.addAdditiveSprite(RenderLayer.SHUTTLES, flameSprite, cam.cellToScreenX(at[0]), cam.cellToScreenY(at[1]),
                    Math.max(1.5f, flameWidthPx * scale * cellPx), Math.max(2f, len * cellPx), heading + 180f,
                    flame.getRed() / 255f, flame.getGreen() / 255f, flame.getBlue() / 255f, 0.9f * alpha);
        }
        String path = look != null && look.sprite() != null ? look.sprite() : shell.weapon.projectileSprite();
        ShuttleSpriteCache art = sprites.spriteByPath(path);
        if (art == null) return;
        float bodyLength = (look != null && look.lengthPx() > 0f ? look.lengthPx() : art.pxH) * scale;
        float bodyWidth = (look != null && look.widthPx() > 0f ? look.widthPx() : art.pxW) * scale;
        float[] mid = along(x, y, heading, look != null ? look.spriteAheadPx() * scale : 0f);
        out.addSprite(RenderLayer.SHUTTLES, art.sprite, cam.cellToScreenX(mid[0]), cam.cellToScreenY(mid[1]),
                Math.max(1.5f, bodyWidth * cellPx), Math.max(2f, bodyLength * cellPx), heading, 1f, 1f, 1f, alpha);
    }

    /**
     * A missile's contrail as one ribbon ({@link ContrailTrail}, drained through the same {@code RibbonBatch} as every
     * other trail, in game and in review frames): samples along the flight function back to the spec's duration,
     * each placed at the nozzle as it was then and aged by how long ago that was. The ribbons are pooled for the
     * frame, since the trail is rebuilt from the flight every frame rather than recorded.
     */
    private void drawContrail(DrawList out, float alpha, WarshipShell shell, VanillaRoundLook look,
                              MissileFlightSpec flight, float scale, boolean hull) {
        ContrailStyle[] styles = contrailStyles(shell.weapon, look, flight, scale, hull);
        MissilePath path = shell.path();
        float now = shell.elapsed();
        // Smoke is laid while the motor burns: after flameout the trail comes away from the missile and fades.
        float burn = flight == null || flight.burnSeconds() <= 0f ? Float.MAX_VALUE : flight.burnSeconds();
        float newest = Math.min(now, burn);
        float oldest = Math.max(0f, now - styles[0].durationSec);
        if (newest - oldest <= 1e-3f) return;
        float behind = (look != null ? look.nozzleBehindPx() : 10f) * scale;
        // Smoke let go keeps a share of the missile's speed: a puff laid at te, aged a, has drifted on to where the
        // missile was at te + drift * a.
        float drift = look != null ? look.contrailDrift() : 0f;
        ContrailTrail outer = nextTrail(styles[0]);
        ContrailTrail inner = nextTrail(styles[1]);
        int n = tailSamples;
        for (int i = 0; i <= n; i++) {
            float laid = oldest + (newest - oldest) * i / n;
            float age = now - laid;
            float t = Math.min(now, laid + drift * age);
            float[] nozzle = along(path.x(t), path.y(t), path.headingDegrees(t), -behind);
            outer.pushSample(nozzle[0], nozzle[1]);
            outer.get(outer.size() - 1).age = age;
            inner.pushSample(nozzle[0], nozzle[1]);
            inner.get(inner.size() - 1).age = age;
        }
        out.addRibbon(RenderLayer.SHUTTLES, outer, alpha);
        out.addRibbon(RenderLayer.SHUTTLES, inner, alpha);
    }

    /**
     * The contrail a weapon's missile lays at a scale, from its spec, cached per weapon and scale: an outer ribbon the
     * spec's full width and a denser inner one half as wide, so the smoke is thickest down its middle and soft at its
     * edges (a ribbon is a flat fill) while the middle carries the spec's own opacity.
     */
    private ContrailStyle[] contrailStyles(VanillaWeapon w, VanillaRoundLook look, MissileFlightSpec flight,
                                           float scale, boolean hull) {
        String key = w.id() + (hull ? "@hull" : "@ground");
        ContrailStyle[] cached = contrailStyles.get(key);
        if (cached != null) return cached;
        Color c = look != null && look.contrailColor() != null ? look.contrailColor()
                : flight != null && flight.contrailColor() != null ? flight.contrailColor() : MISSILE_SMOKE;
        float seconds = Math.max(0.05f, look != null && look.contrailSeconds() > 0f ? look.contrailLifeSeconds(hull)
                : flight != null ? flight.contrailSeconds() : 0.6f);
        float widthPx = look != null && look.contrailWidthPx() > 0f ? look.contrailWidthPx()
                : flight != null ? 4f * flight.contrailWidthMult() : 4f;
        float growth = 1f + Math.max(-0.9f, look != null ? look.contrailGrowthAtEnd() : 1f);
        float half = 0.5f * widthPx * scale;
        float r = c.getRed() / 255f, g = c.getGreen() / 255f, b = c.getBlue() / 255f;
        // A vanilla contrail's alpha is how thick the smoke is. A glow-type contrail is added in the base game; a
        // ribbon run lays it instead, which over the ground reads much the same at these alphas.
        float a = Math.min(1f, Math.max(0.12f, c.getAlpha() / 255f));
        // Two layers whose overlap in the middle comes to the spec's opacity: 1 - (1 - a/2)(1 - a') = a.
        float edge = 0.5f * a;
        float middle = 1f - (1f - a) / (1f - edge);
        ContrailStyle[] styles = {
                new ContrailStyle(seconds, half, half * growth, r, g, b, edge, r, g, b, 0f, 1e-4f, false),
                new ContrailStyle(seconds, 0.5f * half, 0.5f * half * growth, r, g, b, middle, r, g, b, 0f, 1e-4f,
                        false)};
        contrailStyles.put(key, styles);
        return styles;
    }

    /** The next pooled ribbon for this frame, emptied and set to {@code style}. */
    private ContrailTrail nextTrail(ContrailStyle style) {
        if (trailsUsed == trailPool.size()) trailPool.add(null);
        ContrailTrail trail = trailPool.get(trailsUsed);
        if (trail == null || trail.style != style) {
            trail = new ContrailTrail(style, TRAIL_SAMPLES + 2);
            trailPool.set(trailsUsed, trail);
        }
        trail.clear();
        trailsUsed++;
        return trail;
    }

    /**
     * A warhead a carrier has let go, crossing from the split point to where it will burst: its
     * own sprite nose-first along the curve it flies, an engine glow, and a trail that is the same
     * curve sampled a little earlier, so it hangs along the weave and costs no state. At far zoom
     * only the glow is drawn. Presentation of a landing already decided.
     */
    private void drawStrike(DrawList out, BattleCamera cam, float cellPx, float alpha, WarheadStrike strike) {
        ShuttleSpriteCache glow = sprites.spriteByPath(GLOW);
        if (glow == null) return;
        VanillaWeapon w = strike.weapon;
        Color c = w.explosionColor() != null ? w.explosionColor() : DEFAULT_FLASH;
        MissileFlightSpec flight = w.flight();
        Color flame = flight != null && flight.engineColor() != null ? flight.engineColor() : MISSILE_FLAME;
        float r = c.getRed() / 255f;
        float g = c.getGreen() / 255f;
        float b = c.getBlue() / 255f;
        float x = strike.x();
        float y = strike.y();
        float hx = cam.cellToScreenX(x);
        float hy = cam.cellToScreenY(y);
        float age = strike.age();
        if (swarmDetail) {
            BloomPath path = strike.path();
            for (int i = STRIKE_TRAIL_SAMPLES - 1; i >= 1; i--) {
                float back = i * STRIKE_TRAIL_SECONDS / STRIKE_TRAIL_SAMPLES;
                if (age - back < 0f) continue;
                float fade = 1f - (float) i / STRIKE_TRAIL_SAMPLES;
                float size = (0.5f + 0.7f * fade) * cellPx;
                Color tint = i < 2 ? flame : c;
                out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite,
                        cam.cellToScreenX(path.x(age - back)), cam.cellToScreenY(path.y(age - back)),
                        size, size, 0f, tint.getRed() / 255f, tint.getGreen() / 255f, tint.getBlue() / 255f,
                        0.55f * fade * alpha);
            }
            String spritePath = strike.spriteId() != null ? strike.spriteId() : w.projectileSprite();
            ShuttleSpriteCache art = sprites.spriteByPath(spritePath);
            if (art != null) {
                float wPx = WarshipScale.cells(art.pxW) * cellPx * VanillaRoundLook.GROUND_ROUND_SCALE;
                float hPx = WarshipScale.cells(art.pxH) * cellPx * VanillaRoundLook.GROUND_ROUND_SCALE;
                out.addSprite(RenderLayer.SHUTTLES, art.sprite, hx, hy, wPx, hPx, strike.headingDegrees(),
                        1f, 1f, 1f, alpha);
            }
        }
        float head = (swarmDetail ? 1.5f : 1.1f) * cellPx;
        out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite, hx, hy, head, head, 0f, r, g, b, 0.85f * alpha);
        out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite, hx, hy, head * 0.4f, head * 0.4f, 0f,
                1f, 1f, 1f, 0.9f * alpha);
    }

    /**
     * The split: one hot flash, a shockwave ring and a puff of smoke at the point the carrier let
     * its warheads go, drawn once for the whole sheaf and fading over {@link SplitEvent#SECONDS}.
     */
    private void drawSplit(DrawList out, BattleCamera cam, float cellPx, float alpha, SplitEvent split) {
        ShuttleSpriteCache glow = sprites.spriteByPath(GLOW);
        if (glow == null) return;
        float fresh = split.freshness();
        float grown = 1f - fresh;
        float sx = cam.cellToScreenX(split.x);
        float sy = cam.cellToScreenY(split.y);
        float flash = (3f + 9f * grown) * split.scale * cellPx;
        out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite, sx, sy, flash, flash, 0f, 1f, 0.9f, 0.7f,
                0.9f * fresh * fresh * alpha);
        float core = (1.5f + 3f * grown) * split.scale * cellPx;
        out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite, sx, sy, core, core, 0f, 1f, 1f, 1f,
                fresh * alpha);
        ShuttleSpriteCache ring = sprites.spriteByPath(SHIELD_RING);
        if (ring != null) {
            float d = (2f + 16f * (float) Math.sqrt(grown)) * split.scale * cellPx;
            out.addAdditiveSprite(RenderLayer.SHUTTLES, ring.sprite, sx, sy, d, d, 0f, 1f, 0.8f, 0.5f,
                    0.7f * fresh * alpha);
        }
        // Smoke: the spec's own colour (else grey), puffs thrown outward and slowed as they go.
        Color smoke = split.smoke != null ? split.smoke : MISSILE_SMOKE;
        float smokeAlpha = Math.max(0.25f, smoke.getAlpha() / 255f);
        for (int i = 0; i < SPLIT_SMOKE_PUFFS; i++) {
            double a = i * 2.399963 + split.x * 1.7;
            float reach = (1f + 4f * (float) Math.sqrt(grown)) * split.scale * (0.6f + 0.4f * ((i * 37) % 10) / 10f);
            float size = (1.6f + 2.4f * grown) * split.scale * cellPx;
            out.addSprite(RenderLayer.SHUTTLES, glow.sprite,
                    sx + (float) Math.cos(a) * reach * cellPx, sy + (float) Math.sin(a) * reach * cellPx,
                    size, size, 0f, smoke.getRed() / 255f, smoke.getGreen() / 255f, smoke.getBlue() / 255f,
                    0.5f * smokeAlpha * fresh * alpha);
        }
    }

    /**
     * The ground a volley or a sheaf will come down on, while it is still in the air: a faint dashed
     * ring that turns slowly and pulses faster as the carrier or the head of the volley nears it.
     * In the style of a lance's telegraph, and much quieter, because there are many bodies and the
     * ring is a warning rather than a line of fire.
     */
    private void drawFootprint(DrawList out, BattleCamera cam, float cellPx, float alpha,
                               WarshipShell shell) {
        WarshipShell.Footprint f = shell.footprint();
        float rate = 2f + 6f * shell.progress();
        float pulse = 0.5f + 0.5f * (float) Math.sin(engineClockSeconds * rate * Math.PI);
        float a = (0.2f + 0.3f * pulse) * alpha;
        float widthPx = Math.max(1.5f, 0.16f * cellPx);
        Color c = shell.weapon.explosionColor() != null ? shell.weapon.explosionColor() : DEFAULT_FLASH;
        float r = Math.min(1f, 0.5f + c.getRed() / 510f);
        float g = Math.min(1f, 0.25f + c.getGreen() / 600f);
        float b = Math.min(1f, 0.15f + c.getBlue() / 700f);
        float spin = engineClockSeconds * 0.25f;
        float px = 0f;
        float py = 0f;
        for (int i = 0; i <= FOOTPRINT_SEGMENTS; i++) {
            double ang = spin + 2.0 * Math.PI * i / FOOTPRINT_SEGMENTS;
            float x = cam.cellToScreenX(f.x() + (float) Math.cos(ang) * f.radius());
            float y = cam.cellToScreenY(f.y() + (float) Math.sin(ang) * f.radius());
            if (i > 0 && (i & 1) == 0) out.addLine(RenderLayer.SHUTTLES, px, py, x, y, widthPx, r, g, b, a);
            px = x;
            py = y;
        }
    }
}
