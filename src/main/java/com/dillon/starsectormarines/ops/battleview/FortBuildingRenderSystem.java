package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.battery.FortressBattery;
import com.dillon.starsectormarines.battle.fort.Fort;
import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.battle.fort.FortBuildingArt;
import com.dillon.starsectormarines.battle.fort.FortFrame;
import com.dillon.starsectormarines.battle.fort.FortGatehouseArt;
import com.dillon.starsectormarines.battle.turret.FortWorks;
import com.dillon.starsectormarines.battle.fort.FortService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortPlan;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.util.List;

/**
 * Draws what a fort adds over its buildings and gate, in the fort's local frame turned with the fort.
 *
 * <p>A fort building is an ordinary kit building (`fort-works.md`): its walls and doors are the ground pass's, in the
 * style of the fort's lot, and its roof is the ordinary roof, lifting as any building's does. This system adds only
 * what the kit has no piece for: the holder's banner over each building (tinted by who holds it, larger once taken) and
 * the magazine's blast door, damaged with the building ({@link FortBuildingArt#stateOf}). At the gate it draws the
 * leaves over the passage and over each open sally port; the towers either side are rooms, drawn like any building.
 */
public final class FortBuildingRenderSystem implements RenderSystem {

    /**
     * Sprite angle is the on-screen heading of the local +x axis. The battle is drawn with cell +y up the screen and
     * sprite angles run counter-clockwise on that screen, so the heading is the plain {@code atan2} of the axis.
     */
    private static final float ANGLE_SIGN = 1f;
    /** A banner stays legible with the roof faded away; this is the least alpha it draws at. */
    private static final float STANDING_PART_FLOOR = 0.55f;
    private static final float TAKEN_BANNER_SCALE = 1.35f;

    private final BattleSprites sprites;

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
        ViewCull view = ViewCull.of(ctx.camera);
        for (Fort fort : forts.forts()) {
            FortFrame frame = fort.frame();
            if (frame == null) continue;
            for (Fort.Place place : fort.places()) {
                drawBuilding(ctx, out, view, fort, frame, place);
            }
            drawGatehouse(ctx, out, view, fort, frame);
        }
    }

    // ------------------------------------------------------------------------------------------- buildings

    private void drawBuilding(RenderContext ctx, DrawList out, ViewCull view, Fort fort, FortFrame frame,
                              Fort.Place place) {
        FortressBattery battery = fort.battery();
        if (place.structureId() == null) return;
        StructureDef def = TurretCatalogRegistry.installed().getStructure(place.structureId());
        if (def == null || def.buildingArt == null) return;
        FortBuildingArt art = def.buildingArt;
        FortPlan plan = frame.plan();
        int[] floor = plan.floor(place.aspect());
        boolean flipped = art.needsHalfTurn(plan.door(place.aspect())[1] < floor[1]);
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
        boolean gunBound = place.aspect() == FortAspect.FIRE_CONTROL && battery != null;
        Faction holder = gunBound ? battery.owner() : place.control().holder();
        boolean taken = !holder.friendlyTo(gunBound ? battery.originalOwner() : place.originalHolder());

        drawBlastDoor(ctx, out, frame, place, art, state, flipped);
        if (state == FortBuildingArt.State.WRECKED) return;

        FortBuildingArt.Banner banner = art.banner();
        ShuttleSpriteCache flag = banner == null ? null : sprites.spriteByPath(BANNER_PATH);
        if (flag != null) {
            BattleCamera cam = ctx.camera;
            float standing = Math.max(roofAlpha(ctx, bounds), STANDING_PART_FLOOR) * ctx.alphaMult;
            float[] tint = ShieldDomeRenderSystem.tintFor(holder);
            float size = banner.cells() * (taken ? TAKEN_BANNER_SCALE : 1f);
            float[] reg = FortBuildingArt.registered(flipped, floor[2], floor[3], banner.x(), banner.y());
            float[] at = localPoint(frame, floor, reg[0], reg[1]);
            float h = size * cam.cellPxSize();
            out.addSprite(RenderLayer.ROOFS, flag.sprite, cam.cellToScreenX(at[0]), cam.cellToScreenY(at[1]),
                    h * flag.aspect, h, 0f, tint[0], tint[1], tint[2], standing);
        }
    }

    /**
     * The heavy door over the building's main door, if its art has one: three cells along the wall, centred on the door
     * cell, low over the floor so units pass under it. A building that is hurt or wrecked shows the damaged picture.
     */
    private void drawBlastDoor(RenderContext ctx, DrawList out, FortFrame frame, Fort.Place place,
                               FortBuildingArt art, FortBuildingArt.State state, boolean flipped) {
        FortBuildingArt.BlastDoor door = art.blastDoor();
        if (door == null) return;
        ShuttleSpriteCache sprite = sprites.spriteByPath(
                state == FortBuildingArt.State.INTACT ? door.sprite() : door.damaged());
        if (sprite == null) return;
        int[] at = frame.plan().door(place.aspect());
        int[] cell = frame.cell(at[0], at[1]);
        BattleCamera cam = ctx.camera;
        float cellPx = cam.cellPxSize();
        out.addSprite(RenderLayer.DOODADS, sprite.sprite, cam.cellToScreenX(cell[0] + 0.5f),
                cam.cellToScreenY(cell[1] + 0.5f), BLAST_DOOR_CELLS * cellPx, cellPx,
                frameAngle(frame) + (flipped ? 180f : 0f), 1f, 1f, 1f, ctx.alphaMult);
    }

    /** How many cells of wall a blast door piece spans. */
    private static final float BLAST_DOOR_CELLS = 3f;

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

    private void drawGatehouse(RenderContext ctx, DrawList out, ViewCull view, Fort fort, FortFrame frame) {
        FortPlan plan = frame.plan();
        if (plan.gatehouse().isEmpty()) return;
        StructureDef platform = TurretCatalogRegistry.installed().getStructure(plan.centrepieceStructureId());
        if (platform == null || platform.gatehouseArt == null) return;
        BattleCamera cam = ctx.camera;
        CellTopology topology = ctx.sim.getTopology();
        // The gate is the lot's works: drawn in the style its fort was built in (`fort-works.md`).
        float[] centre = frame.point(frame.plan().width() * 0.5f, frame.plan().height() * 0.5f);
        FortGatehouseArt art = platform.gatehouseArt.inStyle(
                FortWorks.styleAt(topology, (int) Math.floor(centre[0]), (int) Math.floor(centre[1])));
        float cellPx = cam.cellPxSize();
        for (FortPlan.Piece piece : plan.gatehouse()) {
            int[] b = frame.bounds(piece.x(), piece.y(), piece.width(), piece.height());
            float cx = (b[0] + b[2] + 1) * 0.5f;
            float cy = (b[1] + b[3] + 1) * 0.5f;
            if (!view.visible(cx, cy, 4f)) continue;
            String path = art.gate();
            RenderLayer layer = RenderLayer.DOODADS;
            ShuttleSpriteCache sprite = sprites.spriteByPath(path);
            if (sprite == null) continue;
            // Authored upright, then turned by the piece's own quarter turns: a sideways piece swaps its extents.
            boolean sideways = piece.turns() % 2 == 1;
            float w = (sideways ? piece.height() : piece.width()) * cellPx;
            float h = (sideways ? piece.width() : piece.height()) * cellPx;
            out.addSprite(layer, sprite.sprite, cam.cellToScreenX(cx), cam.cellToScreenY(cy), w, h,
                    frameAngle(frame) + piece.turns() * 90f, 1f, 1f, 1f, ctx.alphaMult);
        }
        // An unbarred sally port carries the gate's leaves, folded back over its passage; a barred one is the wall.
        ShuttleSpriteCache leaves = sprites.spriteByPath(art.gate());
        List<FortPlan.SallyPort> ports = plan.sallyPorts();
        for (int i = 0; i < ports.size() && leaves != null; i++) {
            if (!fort.sallyPortOpen(i)) continue;
            FortPlan.SallyPort port = ports.get(i);
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
            for (int[] c : port.cells()) {
                minX = Math.min(minX, c[0]);
                minY = Math.min(minY, c[1]);
                maxX = Math.max(maxX, c[0]);
                maxY = Math.max(maxY, c[1]);
            }
            int[] b = frame.bounds(minX, minY, maxX - minX + 1, maxY - minY + 1);
            float cx = (b[0] + b[2] + 1) * 0.5f;
            float cy = (b[1] + b[3] + 1) * 0.5f;
            if (!view.visible(cx, cy, 4f)) continue;
            // The leaves are authored for a west-facing passage; a port facing north, east or south turns one, two or three quarters.
            int turns = port.outX() < 0 ? 0 : port.outY() < 0 ? 1 : port.outX() > 0 ? 2 : 3;
            float across = (maxX - minX + 1) * cellPx;
            float along = (maxY - minY + 1) * cellPx;
            boolean sideways = turns % 2 == 1;
            out.addSprite(RenderLayer.DOODADS, leaves.sprite, cam.cellToScreenX(cx), cam.cellToScreenY(cy),
                    sideways ? along : across, sideways ? across : along, frameAngle(frame) + turns * 90f,
                    1f, 1f, 1f, ctx.alphaMult);
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
        return frame.point(floor[0] + px, floor[1] + py);
    }
}
