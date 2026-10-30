package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.GroundTurret;
import com.dillon.starsectormarines.battle.vehicle.MountedGun;
import com.dillon.starsectormarines.battle.vehicle.TurretMountStyle;
import com.dillon.starsectormarines.battle.vehicle.HullLocations;
import com.dillon.starsectormarines.battle.vehicle.VehicleGun;
import com.dillon.starsectormarines.battle.vehicle.VehicleKit;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.util.EnumSet;
import java.util.Set;

/**
 * Emits the {@link RenderLayer#CONVOY} layer — ground convoy vehicles (supply
 * trucks and their optional turret), just under the shuttle layer. Each chassis
 * is one rotated {@code SHEET_QUAD} at the vehicle body center; vehicles that
 * mount a turret emit a second rotated quad at the mount/pivot-resolved position.
 *
 * <p>Rotated sheet sub-rects: unlike VEHICLES (axis-aligned parked vehicles),
 * convoy trucks face along their heading, so this uses the rotated
 * {@code addSheetQuad} overload (drain → {@code QuadBatch.appendRotated}). The
 * chassis/turret kinematics are a faithful port of the former inline
 * {@code BattleRenderer.renderConvoyVehicles}; the batch path drops that pass's
 * per-frame mutation of the shared sheet sprite (sub-rect, color, angle) and its
 * end-of-pass {@code setAngle(0)} reset.
 *
 * <p>A vehicle is a unit with a hull: it carries the same structure and armor
 * capacities as anything else that can be shot, so it wears the same ownership-coded
 * {@link DurabilityBarDecor} gauge, emitted last in the layer so bars paint over
 * every chassis. A wreck is exempt — its bar would read empty forever, and the
 * registered wreck art already says what happened to it.
 *
 * <p>The debug overlays the old method dispatched (Reeds-Shepp docking paths,
 * selected-vehicle debug) are own-GL line passes and stay inline in
 * {@code BattleRenderer.renderWorld} after this layer drains.
 */
public final class ConvoyRenderSystem implements RenderSystem {

    /** How far a hover hull's shadow lies from it, down and to the right of a light from the upper left, in cells. */
    static final float HOVER_SHADOW_CELLS = .35f;

    private final BattleSprites sprites;

    public ConvoyRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
    }

    @Override
    public RenderLayer layer() {
        return RenderLayer.CONVOY;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        long[] ids = ctx.sim.getConvoyVehicleIds();
        if (ids.length == 0) return;
        ConvoyService convoy = ctx.sim.convoy();

        BattleCamera cam = ctx.camera;
        float cellPx = cam.cellPxSize();
        float alphaMult = ctx.alphaMult;

        for (long id : ids) {
            VehicleMission v = convoy.mission(id);
            if (v == null || !v.isVisible()) continue;
            VehicleType type = convoy.vehicleType(id);
            GroundBody body = convoy.body(id);
            boolean wrecked = v.state == VehicleState.WRECKED;
            UnitSpriteCache cache = sprites.convoySprites().get(type);
            if (cache == null || cache.sheet == null || cache.frames == null) continue;
            if (type.spriteFrame < 0 || type.spriteFrame >= cache.frames.frames.length) continue;
            SpriteSheetFrames frames = cache.frames;
            SpriteSheetFrames.Frame f = frames.frames[wrecked ? type.wreckHullFrame() : type.spriteFrame];

            float frameAspect = (float) f.w / (float) f.h;
            float drawLong = type.drawnLengthCells * cellPx;
            float drawShort = drawLong / frameAspect;
            float chassisFacingDeg = body.facingDegrees + type.spriteFacingOffsetDeg;
            float cx = cam.cellToScreenX(body.x);
            float cy = cam.cellToScreenY(body.y);
            if (type.isHover() && !wrecked) {
                // A hover hull rides clear of the ground, so its shadow lies apart from it, away from the light.
                out.addSheetQuad(RenderLayer.CONVOY, cache.sheet,
                        f.x, f.y, f.w, f.h,
                        cam.cellToScreenX(body.x + HOVER_SHADOW_CELLS), cam.cellToScreenY(body.y - HOVER_SHADOW_CELLS),
                        drawLong * 1.02f, drawShort * 1.02f, chassisFacingDeg,
                        0f, 0f, 0f, alphaMult * .38f);
            }
            float[] tint = type.hullTint();
            out.addSheetQuad(RenderLayer.CONVOY, cache.sheet,
                    f.x, f.y, f.w, f.h,
                    cx, cy, drawLong, drawShort, chassisFacingDeg,
                    tint[0], tint[1], tint[2], alphaMult);

            if (type.hasAnimatedTreads() && !wrecked) VehicleTreadRender.collect(type, cache, body, cam, alphaMult, out);
            HullLocations locations = wrecked ? null : convoy.locations(id);
            if (locations != null) collectHullScuffs(type, cache, f, locations, id, body, chassisFacingDeg, cam, alphaMult, out);
            boolean activeProtection = !wrecked && convoy.kits(id).contains(VehicleKit.ACTIVE_PROTECTION);
            if (activeProtection && !APS_ON_TURRET.contains(type)) {
                // The kit's launchers and sensor panels are bolted to the hull, drawn in its live frame's registration.
                LayeredSpriteCache aps = sprites.vehicleKitActiveProtection();
                if (aps != null) {
                    out.addSprite(RenderLayer.CONVOY, aps.sprite, cx, cy, drawLong, drawShort, chassisFacingDeg,
                            tint[0], tint[1], tint[2], alphaMult);
                }
            }
            if (!wrecked && type.fluxVentAftCells() > 0f) {
                collectFluxHaze(ctx, type, convoy.turret(id), body, cam, cellPx, alphaMult, out);
            }

            // A hull's second guns stand under its main turret, each drawn from its own catalog mount.
            GroundTurret mainTurret = convoy.turret(id);
            if (mainTurret != null) {
                for (MountedGun gun : mainTurret.secondaries) {
                    collectMountTurret(gun.mount().structure(), gun.turret().facingDeg, gun.mount().mountX(),
                            gun.mount().mountY(), body, wrecked, cam, cellPx, alphaMult, out);
                }
            }
            if (type.turretFrame >= 0 && type.turretFrame < frames.frames.length) {
                // Gated on turretFrame (broader than hasTurretWeapon), so a decorative
                // turret variant may have no GROUND_TURRET state — draw it facing 0.
                GroundTurret turret = convoy.turret(id);
                int turretFrame = wrecked ? type.wreckTurretFrame() : type.turretFrame;
                if (!wrecked && type.turretFiringFrame() >= 0 && turret != null && engaged(turret)) {
                    turretFrame = type.turretFiringFrame();
                }
                if (turret != null) turretFrame = type.headFrame(turret.weapon, turretFrame);
                SpriteSheetFrames.Frame tf = frames.frames[turretFrame];
                float turretAspect = (float) tf.w / (float) tf.h;
                float tDrawLong = type.turretVisualCells * cellPx;
                float tDrawShort = tDrawLong / turretAspect;

                float turretStateFacing = (turret != null) ? turret.facingDeg : 0f;
                float turretFacingDeg = turretStateFacing + type.turretSpriteFacingOffsetDeg;
                float cRad = (float) Math.toRadians(body.facingDegrees);
                float cc = (float) Math.cos(cRad);
                float cs = (float) Math.sin(cRad);
                float mountWorldX = type.turretMountX * cc - type.turretMountY * cs;
                float mountWorldY = type.turretMountX * cs + type.turretMountY * cc;
                float tRad = (float) Math.toRadians(turretFacingDeg);
                float tc = (float) Math.cos(tRad);
                float ts = (float) Math.sin(tRad);
                float pivotWorldX = type.turretPivotX * tc - type.turretPivotY * ts;
                float pivotWorldY = type.turretPivotX * ts + type.turretPivotY * tc;
                float drawCellX = body.x + mountWorldX - pivotWorldX;
                float drawCellY = body.y + mountWorldY - pivotWorldY;

                LayeredSpriteCache gun = turret == null ? null
                        : VehicleGunArt.sprite(sprites, type, turret.weapon);
                float recoilCells = gun != null ? VehicleGun.recoilCells(type, turret.weapon) : type.turretRecoilCells();
                float recoil = 0f;
                if (!wrecked && recoilCells > 0f) {
                    for (var shot : ctx.sim.getActiveShots()) if (shot.shooterId == id) {
                        float age = shot.lifetimeMax - shot.lifetime;
                        recoil = Math.max(recoil, recoilCells * Math.max(0f, 1f - age / .22f));
                    }
                    float bearing = (float) Math.toRadians(turretStateFacing);
                    drawCellX += (float) Math.sin(bearing) * recoil;
                    drawCellY -= (float) Math.cos(bearing) * recoil;
                }
                float drawFacingDeg = turretFacingDeg;
                boolean raised = !wrecked && turret != null && turret.raises(type) && turret.elevation > 0f;
                // Standing up to fire, pitched to the angle its rounds leave at. The battle is drawn with
                // height as screen-up, as the missiles' arcs are, so a pitched rack's nose rises up the screen
                // as well as running toward the target: it draws along the arc's first tangent, turned off the
                // true bearing and longer pointing north than south. It stays centred on its turntable, never
                // moved off it; the raise is only that turn and foreshortening, a nose a little wider for coming
                // up toward the eye, and a full-length shadow left on the deck down and right of it, like the
                // sheets' baked shadows.
                float pitchAxisX = 0f, pitchAxisY = 0f, pitchCos = 1f;
                if (raised) {
                    float bearing = (float) Math.toRadians(turretStateFacing);
                    float ux = -(float) Math.sin(bearing);
                    float uy = (float) Math.cos(bearing);
                    WeaponDef weapon = turret.structureFor(type).mount.weapon;
                    float pitch = Math.min(launchPitch(weapon.arcHeight,
                            Float.isFinite(turret.raiseRangeCells) ? turret.raiseRangeCells : weapon.range),
                            readablePitch(uy)) * turret.elevation;
                    pitchCos = (float) Math.cos(pitch);
                    pitchAxisX = ux * pitchCos;
                    pitchAxisY = uy * pitchCos + (float) Math.sin(pitch);
                }
                boolean modular = type.hasModularTurret();
                if (modular) {
                    // The ring is bare machinery; the collar in it turns with the head, so the player sees the head driven.
                    float ringShade = wrecked ? WRECK_SHADE : 1f;
                    float ringX = cam.cellToScreenX(body.x + mountWorldX);
                    float ringY = cam.cellToScreenY(body.y + mountWorldY);
                    float ringPx = VehicleGun.mountCells(type) * cellPx;
                    LayeredSpriteCache ring = sprites.vehicleGunMount(type.mountStyle());
                    if (ring != null) {
                        out.addSprite(RenderLayer.CONVOY, ring.sprite, ringX, ringY, ringPx, ringPx, 0f,
                                ringShade, ringShade, ringShade, alphaMult);
                    }
                    // Only the white ring has a collar of its own; an army head's housing is the plate that turns on it.
                    LayeredSpriteCache collar = type.mountStyle() == TurretMountStyle.WHITE
                            ? sprites.vehicleTurretCollar() : null;
                    if (collar != null) {
                        float collarPx = ringPx * COLLAR_SHARE_OF_RING;
                        out.addSprite(RenderLayer.CONVOY, collar.sprite, ringX, ringY, collarPx, collarPx,
                                turretStateFacing, ringShade, ringShade, ringShade, alphaMult);
                    }
                }
                if (modular && (turret == null || turret.removed)) {
                    // No part in the gun card: the ring stands bare, and the hull has no turret and no gun.
                } else if (gun == null) {
                    if (raised) {
                        float lift = RAISE_SHADOW_CELLS * turret.elevation;
                        out.addSheetQuad(RenderLayer.CONVOY, cache.sheet,
                                tf.x, tf.y, tf.w, tf.h,
                                cam.cellToScreenX(drawCellX + lift), cam.cellToScreenY(drawCellY + lift),
                                tDrawLong, tDrawShort, turretFacingDeg,
                                0f, 0f, 0f, alphaMult * RAISE_SHADOW_ALPHA * turret.elevation);
                        drawFacingDeg = (float) Math.toDegrees(Math.atan2(-pitchAxisX, pitchAxisY))
                                + type.turretSpriteFacingOffsetDeg;
                        tDrawLong *= (float) Math.hypot(pitchAxisX, pitchAxisY);
                        tDrawShort *= 1f + RAISE_WIDEN * (1f - pitchCos);
                    }
                    out.addSheetQuad(RenderLayer.CONVOY, cache.sheet,
                            tf.x, tf.y, tf.w, tf.h,
                            cam.cellToScreenX(drawCellX), cam.cellToScreenY(drawCellY),
                            tDrawLong, tDrawShort, drawFacingDeg,
                            1f, 1f, 1f, alphaMult);
                    if (activeProtection && APS_ON_TURRET.contains(type)) {
                        // The launchers ride the turret housing's rear flanks and the panels its cheeks.
                        LayeredSpriteCache aps = sprites.vehicleKitActiveProtectionTurret();
                        if (aps != null) {
                            out.addSprite(RenderLayer.CONVOY, aps.sprite, cam.cellToScreenX(drawCellX),
                                    cam.cellToScreenY(drawCellY), tDrawLong, tDrawShort, drawFacingDeg,
                                    1f, 1f, 1f, alphaMult);
                        }
                    }
                } else {
                    // The fitted weapon is the turret: its own drawing, turning on the mount ring, with the
                    // weapon's own recoil and raise, at the length it fires from ({@link VehicleGun}). A wreck keeps
                    // it where it last pointed, charred and knocked a little off its bearing.
                    float shade = wrecked ? WRECK_SHADE : 1f;
                    float wreckSkewDeg = wrecked ? Math.floorMod(id ^ TURRET_SEED, 31L) - 15f : 0f;
                    float bearing = (float) Math.toRadians(turretStateFacing);
                    float ringX = body.x + mountWorldX - (float) Math.sin(bearing) * recoil;
                    float ringY = body.y + mountWorldY + (float) Math.cos(bearing) * recoil;
                    float length = VehicleGun.lengthCells(type, turret.weapon);
                    float centreAhead = (.5f - VehicleGun.RING_FROM_REAR) * length;
                    float gunX = ringX - (float) Math.sin(bearing) * centreAhead;
                    float gunY = ringY + (float) Math.cos(bearing) * centreAhead;
                    float mountPx = VehicleGun.mountCells(type) * cellPx;
                    LayeredSpriteCache mount = modular ? null : sprites.vehicleGunMount(type.mountStyle());
                    if (modular && type.mountStyle() == TurretMountStyle.WHITE) {
                        // A laser stands in a round base on the collar, its muzzle just showing past the ring.
                        boolean light = turret.weapon.footprintRows < 2;
                        LayeredSpriteCache base = sprites.vehicleTurretBase(light);
                        if (base != null) {
                            float basePx = VehicleGun.baseCells(turret.weapon) * cellPx;
                            out.addSprite(RenderLayer.CONVOY, base.sprite, cam.cellToScreenX(body.x + mountWorldX),
                                    cam.cellToScreenY(body.y + mountWorldY), basePx, basePx, turretStateFacing + wreckSkewDeg,
                                    shade, shade, shade, alphaMult);
                        }
                    }
                    if (mount != null) {
                        out.addSprite(RenderLayer.CONVOY, mount.sprite, cam.cellToScreenX(body.x + mountWorldX),
                                cam.cellToScreenY(body.y + mountWorldY), mountPx, mountPx, 0f, shade, shade, shade, alphaMult);
                    }
                    // A light machine gun hull carries its weapon on a gunner's shield that turns in the ring with it, and an
                    // army gun tank a weapon with no head of its own on the same plate ({@link VehicleGun#plateCells}).
                    float plateCells = VehicleGun.plateCells(type, turret.weapon);
                    LayeredSpriteCache plate = plateCells > 0f ? sprites.vehicleGunPlate() : null;
                    if (plate != null) {
                        float platePx = plateCells * cellPx;
                        out.addSprite(RenderLayer.CONVOY, plate.sprite, cam.cellToScreenX(body.x + mountWorldX),
                                cam.cellToScreenY(body.y + mountWorldY), platePx, platePx, turretStateFacing + wreckSkewDeg,
                                shade, shade, shade, alphaMult);
                    }
                    float gunFacing = turretStateFacing + wreckSkewDeg;
                    float lengthScale = 1f;
                    float widthScale = 1f;
                    if (raised) {
                        float lift = RAISE_SHADOW_CELLS * turret.elevation;
                        VehicleGunArt.draw(out, gun, cam.cellToScreenX(gunX + lift), cam.cellToScreenY(gunY + lift),
                                length * cellPx, turretStateFacing, 1f, 1f, 0f,
                                alphaMult * RAISE_SHADOW_ALPHA * turret.elevation);
                        gunFacing = (float) Math.toDegrees(Math.atan2(-pitchAxisX, pitchAxisY));
                        lengthScale = (float) Math.hypot(pitchAxisX, pitchAxisY);
                        widthScale = 1f + RAISE_WIDEN * (1f - pitchCos);
                    }
                    VehicleGunArt.draw(out, gun, cam.cellToScreenX(gunX), cam.cellToScreenY(gunY),
                            length * cellPx, gunFacing, lengthScale, widthScale, shade, alphaMult);
                }
                if (locations != null && locations.turret() != null
                        && wornPlateOnTurretFrame(modular, turret != null && turret.removed, gun != null)) {
                    VehicleScuffs.Mask mask = VehicleScuffs.turretMask(sprites, type, frames);
                    float wear = VehicleScuffs.wear(locations.turret().armor(), locations.turret().maxArmor());
                    if (mask != null && wear > 0f) {
                        beginScuffs(out, cam, tf, drawCellX, drawCellY, turretFacingDeg,
                                type.turretVisualCells / tf.w, mask, alphaMult);
                        VehicleScuffs.place(mask, 0, wear, id ^ TURRET_SEED, scuffMark);
                        scuffMark.out = null;
                    }
                }
            }

        }

        if (ctx.hostProfile.unitDecorationsVisible()) {
            sweepDurabilityBars(ids, convoy, cam, cellPx, alphaMult, out);
        }
    }

    /**
     * Whether a worn turret's scuffs are drawn: only over a turret drawn from the sheet (a hull's own turret or a head),
     * whose footprint the mask is cut from. A bare ring has no turret to mark, and a weapon drawn as its own drawing does
     * not fill that footprint, so marks there would land on the gun and the bare deck.
     */
    static boolean wornPlateOnTurretFrame(boolean modular, boolean bareRing, boolean weaponDrawing) {
        return !(modular && bareRing) && !weaponDrawing;
    }

    /**
     * The hulls whose turret is the Onager's angular housing, on which the active-protection kit rides with the turret
     * where the Parapet wore it; any other hull wears the kit on its deck.
     */
    private static final Set<VehicleType> APS_ON_TURRET = EnumSet.of(VehicleType.BREAKTHROUGH_TANK);

    /** How wide the turning collar is drawn against the turret ring it rides in. */
    private static final float COLLAR_SHARE_OF_RING = .86f;

    /** How dark a wrecked gun is drawn: charred, not black. */
    private static final float WRECK_SHADE = .35f;

    /** Mixed into a turret's seed, so it does not repeat its hull's front plate. */
    private static final long TURRET_SEED = 0x5DEECE66DL;

    private final ScuffMark scuffMark = new ScuffMark();

    /**
     * Worn plate over the hull and its treads, under the turret ({@code combat-durability-nouns.md}): each plate's
     * marks from {@link VehicleScuffs}, laid in the hull's own frame and turned with it.
     */
    private void collectHullScuffs(VehicleType type, UnitSpriteCache cache, SpriteSheetFrames.Frame f,
                                   HullLocations locations, long id, GroundBody body, float angleDeg,
                                   BattleCamera cam, float alphaMult, DrawList out) {
        VehicleScuffs.Mask mask = VehicleScuffs.hullMask(sprites, type, cache.frames);
        if (mask == null) return;
        beginScuffs(out, cam, f, body.x, body.y, angleDeg, type.drawnLengthCells / f.w, mask, alphaMult);
        for (HullLocations.Plate plate : HullLocations.Plate.values()) {
            float wear = VehicleScuffs.wear(locations.armor(plate), locations.maxArmor(plate));
            if (wear > 0f) VehicleScuffs.place(mask, plate.ordinal(), wear, id, scuffMark);
        }
        scuffMark.out = null;
    }

    /** Aims {@link #scuffMark} at frame {@code f} drawn centred on cell {@code (x, y)}, turned {@code angleDeg}. */
    private void beginScuffs(DrawList out, BattleCamera cam, SpriteSheetFrames.Frame f, float x, float y,
                             float angleDeg, float cellsPerPixel, VehicleScuffs.Mask mask, float alpha) {
        float px = cellsPerPixel * cam.cellPxSize();
        scuffMark.begin(out, RenderLayer.CONVOY, sprites, cam.cellToScreenX(x), cam.cellToScreenY(y), f.w, f.h,
                px, px, angleDeg, mask.decalPx * px, alpha);
    }

    /**
     * The hull's flux made visible ({@link FluxHaze}): a purple haze over the hull's vents that thickens as its
     * reservoir fills ({@code GroundTurret#reservoir}), and a bright lavender plume while it vents.
     */
    private void collectFluxHaze(RenderContext ctx, VehicleType type, GroundTurret turret, GroundBody body,
                                 BattleCamera cam, float cellPx, float alphaMult, DrawList out) {
        if (turret == null || turret.reservoir == null) return;
        StructureDef structure = turret.structureFor(type);
        // Every hull gun builds flux; only a hull whose art authors vents shows it rising off them.
        if (structure == null || structure.mount.flux == null || type.fluxVentAftCells() <= 0f) return;
        float level = Math.min(1f, turret.fluxValue() / turret.fluxCapacity(structure.mount));
        if (FluxHaze.strength(level, turret.fluxVenting()) <= 0f) return;
        sprites.ensureEngineFxSprites();
        // Hull-local +Y is the nose (the turret mount's own transform); aft is -Y.
        float rad = (float) Math.toRadians(body.facingDegrees);
        float ventX = body.x + (float) Math.sin(rad) * type.fluxVentAftCells();
        float ventY = body.y - (float) Math.cos(rad) * type.fluxVentAftCells();
        FluxHaze.collect(out, RenderLayer.CONVOY, sprites.engineGlowSprite(), cam, cellPx, ventX, ventY,
                body.facingDegrees, type.fluxVentHalfSpanCells(), 1f, level, turret.fluxVenting(),
                ctx.sim.getSimTickIndex() * BattleSimulation.TICK_DT, alphaMult);
    }

    /** How much wider a raised rack draws per unit it shortens: its nose has come up toward the eye. */
    private static final float RAISE_WIDEN = .3f;
    /** How far a fully raised rack stands off its own shadow, in cells, and how dark that shadow is. */
    private static final float RAISE_SHADOW_CELLS = .14f;
    private static final float RAISE_SHADOW_ALPHA = .45f;
    /** The steepest a rack is drawn pitched, for a target so near its rounds would leave almost straight up. */
    private static final float MAX_LAUNCH_PITCH = (float) Math.toRadians(70);
    /** The least a raised rack draws along its own bearing, as a share of its length ({@link #readablePitch}). */
    static final float MIN_RAISED_ALONG = .7f;

    /**
     * The angle an arcing round leaves the ground at, in radians: the slope of the drawn arc at its start. The arc
     * rises {@code 4 * arcHeight} over its whole run ({@code ShotRenderService.spriteFlight}), so the slope is that
     * over the range, and the same arc height lobbed at a nearer target leaves steeper.
     */
    static float launchPitch(float arcHeight, float rangeCells) {
        if (arcHeight <= 0f) return 0f;
        return Math.min(MAX_LAUNCH_PITCH, (float) Math.atan2(4f * arcHeight, Math.max(1f, rangeCells)));
    }

    /**
     * The steepest a rack firing on this bearing can be drawn and still read as pointing at its target: its drawn
     * length along the bearing, {@code cos + uy * sin} of the pitch, never under {@link #MIN_RAISED_ALONG}. Firing
     * north or across the screen it never binds. Firing south, toward the bottom of the screen, a pitched nose
     * comes back up the screen over its own tail, and the rack would fold to nothing and then point north, the
     * way a lobbed round's drawn arc does leave; it stands only as tall as stays readable instead.
     */
    static float readablePitch(float uy) {
        double reach = Math.hypot(1.0, uy);
        return (float) (Math.atan2(uy, 1.0) + Math.acos(Math.min(1.0, MIN_RAISED_ALONG / reach)));
    }

    /** A gun with a target, ground or air, or a burst under way: what a firing frame is drawn for. */
    private static boolean engaged(GroundTurret turret) {
        return turret.targetId != 0L || turret.airTargetId != 0L || turret.burstRemaining > 0;
    }

    /**
     * Durability bars for every live convoy vehicle, run <b>last</b> in the layer
     * so a bar is never painted over by a neighbouring chassis — the same
     * per-stratum sweep the UNITS layer uses rather than per-entity decoration.
     *
     * <p>Vehicles are always authored with both capacities, so this always emits the
     * armored two-row bar. Placement follows the shared rule: the gauge spans the
     * body it belongs to and sits that body's half-extent above its center. A hull
     * is measured by its longest side so the bar clears the sprite at every
     * heading instead of sinking into it broadside-on.
     */
    private static void sweepDurabilityBars(long[] ids, ConvoyService convoy,
                                            BattleCamera cam, float cellPx,
                                            float alphaMult, DrawList out) {
        for (long id : ids) {
            VehicleMission v = convoy.mission(id);
            if (v == null || !v.isVisible() || v.state == VehicleState.WRECKED) continue;
            VehicleType type = convoy.vehicleType(id);
            GroundBody body = convoy.body(id);
            float bodyPx = Math.max(type.drawnLengthCells, type.visualWidthCells) * cellPx;
            float cx = cam.cellToScreenX(body.x);
            float barY = cam.cellToScreenY(body.y) + bodyPx / 2f + BattleRenderer.HP_BAR_GAP;
            DurabilityBarDecor.emit(out, RenderLayer.CONVOY,
                    Allegiance.of(convoy.faction(id)), cx, barY, bodyPx,
                    convoy.structure(id), convoy.maxStructure(id),
                    convoy.armor(id), convoy.maxArmor(id), alphaMult);
        }
    }

    /** A catalog mount's sprite standing {@code localX, localY} cells from the hull's centre, facing {@code facing}. */
    private void collectMountTurret(StructureDef structure, float facing, float localX, float localY, GroundBody body,
                                    boolean wrecked, BattleCamera cam, float cellPx, float alphaMult, DrawList out) {
        if (structure == null) return;
        ShuttleSpriteCache base = sprites.turretSprites().get(structure.id);
        if (base == null) return;
        ShuttleSpriteCache barrel = sprites.turretRecoilSprites().get(structure.id);
        float cRad = (float) Math.toRadians(body.facingDegrees);
        float cc = (float) Math.cos(cRad);
        float cs = (float) Math.sin(cRad);
        float mountX = body.x + localX * cc - localY * cs;
        float mountY = body.y + localX * cs + localY * cc;
        float heightPx = structure.mount.visualCells * cellPx;
        float shade = wrecked ? WRECK_SHADE : 1f;
        float sx = cam.cellToScreenX(mountX);
        float sy = cam.cellToScreenY(mountY);
        if (barrel != null) {
            out.addSprite(RenderLayer.CONVOY, barrel.sprite, sx, sy, heightPx * barrel.aspect, heightPx,
                    facing, shade, shade, shade, alphaMult);
        }
        out.addSprite(RenderLayer.CONVOY, base.sprite, sx, sy, heightPx * base.aspect, heightPx,
                facing, shade, shade, shade, alphaMult);
    }
}
