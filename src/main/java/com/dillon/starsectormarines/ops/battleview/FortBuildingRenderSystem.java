package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.battery.FortressBattery;
import com.dillon.starsectormarines.battle.fort.Fort;
import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.battle.fort.FortBuildingArt;
import com.dillon.starsectormarines.battle.fort.FortFrame;
import com.dillon.starsectormarines.battle.fort.FortGatehouseArt;
import com.dillon.starsectormarines.battle.fort.FortService;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.fortress.BatteryFort;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws the fort's buildings and gatehouse from their authored art, in the fort's local frame turned with the fort.
 *
 * <p>A fort building's roof is this system's, not the ordinary brick roof's: the ordinary roof skips these cells
 * ({@link FortService#roofArtCovers}) and the roof art here fades with the same per-building reveal, so a building with
 * a squad inside still opens to show its interior and the capture rules are untouched. What stands on the roof (the
 * turning radar, the turbine fans, the lights, the holder's banner) is drawn above it; a wrecked building draws only its
 * wreck, low over the floor and under the units, because its roof is gone and the interior is open.
 *
 * <p>A building's state comes from the core structure's durability and the aspect's wrecked flag
 * ({@link FortBuildingArt#stateOf}); its machinery runs only while the building is whole and its power is its holder's.
 * Tint follows the existing allegiance convention: a taken building flies the new holder's banner and takes a wash of
 * its colour over the roof.
 */
public final class FortBuildingRenderSystem implements RenderSystem {

    /** Sprite angle is the on-screen heading of the local +x axis; flip if the draw convention turns the other way. */
    private static final float ANGLE_SIGN = -1f;
    /** How much of a holder's colour washes over the roof of a building taken from its original owner. */
    private static final float TAKEN_WASH = 0.4f;
    /** A radar and a banner stay legible with the roof faded away; this is the least alpha they draw at. */
    private static final float STANDING_PART_FLOOR = 0.55f;
    private static final float TAKEN_BANNER_SCALE = 1.35f;

    private final BattleSprites sprites;
    private final Map<Long, Float> rotorAngles = new HashMap<>();
    private float clock;

    public FortBuildingRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
    }

    @Override
    public RenderLayer layer() {
        return RenderLayer.ROOFS;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        FortService forts = ctx.sim.forts();
        if (forts.isEmpty()) return;
        float now = Float.isFinite(ctx.animationTimeSeconds) ? ctx.animationTimeSeconds : clock + Math.max(0f, ctx.realDt);
        float dt = Math.max(0f, Math.min(0.25f, now - clock));
        clock = now;
        ViewCull view = ViewCull.of(ctx.camera);
        for (Fort fort : forts.forts()) {
            FortFrame frame = fort.frame();
            FortressBattery battery = fort.battery();
            if (frame == null || battery == null) continue;
            for (Fort.Place place : fort.places()) {
                drawBuilding(ctx, out, view, fort, battery, frame, place, dt);
            }
            drawGatehouse(ctx, out, view, battery, frame);
        }
    }

    // ------------------------------------------------------------------------------------------- buildings

    private void drawBuilding(RenderContext ctx, DrawList out, ViewCull view, Fort fort, FortressBattery battery,
                              FortFrame frame, Fort.Place place, float dt) {
        if (place.structureId() == null) return;
        StructureDef def = TurretCatalogRegistry.installed().getStructure(place.structureId());
        if (def == null || def.buildingArt == null) return;
        FortBuildingArt art = def.buildingArt;
        int[] floor = BatteryFort.floor(place.aspect());
        int[] bounds = place.floorBounds(frame);
        float centreX = (bounds[0] + bounds[2] + 1) * 0.5f;
        float centreY = (bounds[1] + bounds[3] + 1) * 0.5f;
        if (!view.visible(centreX, centreY, Math.max(floor[2], floor[3]) * 1.5f)) return;

        World world = ctx.sim.world();
        boolean wrecked = place.wrecked();
        float durability = 0f;
        if (!wrecked) {
            float full = def.maxStructure + def.armorCapacity;
            durability = Math.min(1f, (world.hp(place.coreId()) + world.armor(place.coreId())) / full);
        }
        FortBuildingArt.State state = art.stateOf(wrecked, durability);
        boolean fireControl = place.aspect() == FortAspect.FIRE_CONTROL;
        Faction holder = fireControl ? battery.owner() : place.control().holder();
        boolean powered = place.aspect() == FortAspect.POWER
                ? !place.control().contested() && !battery.destroyed()
                : fort.powered(battery.owner());
        float activity = art.activity(state, powered);
        boolean taken = !holder.friendlyTo(battery.originalOwner());

        BattleCamera cam = ctx.camera;
        float cellPx = cam.cellPxSize();
        float roofAlpha = roofAlpha(ctx, bounds);
        float alpha = ctx.alphaMult;
        float angle = frameAngle(frame);

        if (state == FortBuildingArt.State.WRECKED) {
            ShuttleSpriteCache wreck = sprites.spriteByPath(art.wreckPath());
            if (wreck != null) {
                put(out, RenderLayer.DOODADS, cam, frame, floor, wreck, floor[2], floor[3], floor[2] * 0.5f,
                        floor[3] * 0.5f, angle, 1f, 1f, 1f, alpha, false);
            }
            return;
        }

        ShuttleSpriteCache roof = sprites.spriteByPath(art.roofFor(state));
        if (roof != null && roofAlpha > 0.01f) {
            float r = 1f, g = 1f, b = 1f;
            if (taken) {
                float[] tint = ShieldDomeRenderSystem.tintFor(holder);
                r = 1f + (tint[0] - 1f) * TAKEN_WASH;
                g = 1f + (tint[1] - 1f) * TAKEN_WASH;
                b = 1f + (tint[2] - 1f) * TAKEN_WASH;
            }
            put(out, RenderLayer.ROOFS, cam, frame, floor, roof, floor[2], floor[3], floor[2] * 0.5f, floor[3] * 0.5f,
                    angle, r, g, b, roofAlpha * alpha, false);
            FortBuildingArt.Lights lights = art.lights();
            ShuttleSpriteCache glow = lights == null ? null : sprites.spriteByPath(lights.spritePath());
            if (glow != null && activity > 0f) {
                double wave = 0.5 + 0.5 * Math.sin(2.0 * Math.PI * lights.pulseHz() * clock);
                float level = activity * (1f - lights.pulseDepth() * (float) wave);
                put(out, RenderLayer.ROOFS, cam, frame, floor, glow, floor[2], floor[3], floor[2] * 0.5f,
                        floor[3] * 0.5f, angle, level, level, level, roofAlpha * alpha, true);
            }
        }

        float standing = Math.max(roofAlpha, STANDING_PART_FLOOR) * alpha;
        long key = (long) fort.id() * 64L + place.aspect().ordinal() * 8L;
        for (int i = 0; i < art.rotors().size(); i++) {
            FortBuildingArt.Rotor rotor = art.rotors().get(i);
            ShuttleSpriteCache sprite = sprites.spriteByPath(rotor.spritePath());
            if (sprite == null) continue;
            float scale = state == FortBuildingArt.State.DAMAGED ? rotor.damagedSpin() : 1f;
            float spin = rotor.spinDegPerSec() * scale * (powered ? 1f : 0f) * dt;
            float turn = rotorAngles.getOrDefault(key + i, 0f) + spin;
            turn %= 360f;
            rotorAngles.put(key + i, turn);
            float shade = powered ? 1f : 0.6f;
            put(out, RenderLayer.ROOFS, cam, frame, floor, sprite, rotor.cells(), rotor.cells(), rotor.x(), rotor.y(),
                    angle + turn, shade, shade, shade, standing, false);
        }

        FortBuildingArt.Banner banner = art.banner();
        ShuttleSpriteCache flag = banner == null ? null : sprites.spriteByPath(BANNER_PATH);
        if (flag != null) {
            float[] tint = ShieldDomeRenderSystem.tintFor(holder);
            float size = banner.cells() * (taken ? TAKEN_BANNER_SCALE : 1f);
            float[] at = localPoint(frame, floor, banner.x(), banner.y());
            float h = size * cellPx;
            out.addSprite(RenderLayer.ROOFS, flag.sprite, cam.cellToScreenX(at[0]), cam.cellToScreenY(at[1]),
                    h * flag.aspect, h, 0f, tint[0], tint[1], tint[2], standing);
        }
    }

    /** The banner is one shared neutral sprite for every fort building, tinted by whoever holds it. */
    static final String BANNER_PATH = "graphics/fort/fort-banner.png";

    private static float roofAlpha(RenderContext ctx, int[] bounds) {
        Buildings buildings = ctx.sim.getBuildings();
        if (buildings == null) return 1f;
        CellTopology topology = ctx.sim.getTopology();
        int id = topology.getBuildingId((bounds[0] + bounds[2]) / 2, (bounds[1] + bounds[3]) / 2);
        Building building = id == 0 ? null : buildings.get(id);
        return building == null ? 1f : building.currentAlpha;
    }

    // ------------------------------------------------------------------------------------------- gatehouse

    private void drawGatehouse(RenderContext ctx, DrawList out, ViewCull view, FortressBattery battery,
                               FortFrame frame) {
        StructureDef platform = battery.structure();
        if (platform == null || platform.gatehouseArt == null) return;
        FortGatehouseArt art = platform.gatehouseArt;
        BattleCamera cam = ctx.camera;
        NavigationGrid grid = ctx.sim.getGrid();
        CellTopology topology = ctx.sim.getTopology();
        float cellPx = cam.cellPxSize();
        for (BatteryFort.Piece piece : BatteryFort.GATEHOUSE) {
            int[] b = frame.bounds(piece.x(), piece.y(), piece.width(), piece.height());
            float cx = (b[0] + b[2] + 1) * 0.5f;
            float cy = (b[1] + b[3] + 1) * 0.5f;
            if (!view.visible(cx, cy, 4f)) continue;
            String path;
            RenderLayer layer;
            if (piece.kind() == BatteryFort.PieceKind.TOWER) {
                float fraction = 1f;
                boolean standing = true;
                for (int y = b[1]; y <= b[3] && standing; y++) {
                    for (int x = b[0]; x <= b[2]; x++) {
                        if (!topology.isWall(x, y) || grid.getWallHp(x, y) <= 0) {
                            standing = false;
                            break;
                        }
                        fraction = Math.min(fraction, grid.getWallHp(x, y) / (float) BatteryFort.WALL_HP);
                    }
                }
                if (!standing) continue;
                path = art.towerFor(fraction);
                layer = RenderLayer.ROOFS;
            } else {
                path = art.gate();
                layer = RenderLayer.DOODADS;
            }
            ShuttleSpriteCache sprite = sprites.spriteByPath(path);
            if (sprite == null) continue;
            // Authored two cells across and three along the wall, then turned by the piece's own quarter turns.
            float w = 2f * cellPx;
            float h = 3f * cellPx;
            out.addSprite(layer, sprite.sprite, cam.cellToScreenX(cx), cam.cellToScreenY(cy), w, h,
                    frameAngle(frame) + piece.turns() * 90f, 1f, 1f, 1f, ctx.alphaMult);
        }
    }

    // --------------------------------------------------------------------------------------------- geometry

    /** On-screen heading, in degrees, of the fort's local +x axis. */
    static float frameAngle(FortFrame frame) {
        int[] ex = frame.across();
        return ANGLE_SIGN * (float) Math.toDegrees(Math.atan2(ex[1], ex[0]));
    }

    /** The world point, in cells, of a point given in cells from the top-left of the local rectangle {@code floor}. */
    private static float[] localPoint(FortFrame frame, int[] floor, float px, float py) {
        int[] a = frame.cell(0, 0);
        int[] ex = frame.across();
        int[] ey = frame.downward();
        float u = floor[0] + px - 0.5f;
        float v = floor[1] + py - 0.5f;
        return new float[]{a[0] + 0.5f + ex[0] * u + ey[0] * v, a[1] + 0.5f + ex[1] * u + ey[1] * v};
    }

    /** Draws a sprite authored in the local frame, centred at ({@code px}, {@code py}) cells into the rectangle. */
    private void put(DrawList out, RenderLayer layer, BattleCamera cam, FortFrame frame, int[] floor,
                     ShuttleSpriteCache sprite, float cellsW, float cellsH, float px, float py, float angle,
                     float r, float g, float b, float a, boolean additive) {
        float[] at = localPoint(frame, floor, px, py);
        float cellPx = cam.cellPxSize();
        float sx = cam.cellToScreenX(at[0]);
        float sy = cam.cellToScreenY(at[1]);
        if (additive) {
            out.addAdditiveSprite(layer, sprite.sprite, sx, sy, cellsW * cellPx, cellsH * cellPx, angle, r, g, b, a);
        } else {
            out.addSprite(layer, sprite.sprite, sx, sy, cellsW * cellPx, cellsH * cellPx, angle, r, g, b, a);
        }
    }
}
