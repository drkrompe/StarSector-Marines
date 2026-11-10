package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.ruins.RuinsWorks;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;
import com.dillon.starsectormarines.battle.world.gen.ship.RoomDoor;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.util.ArrayList;
import java.util.List;

/**
 * The ruins' sliding leaf doors ({@code ruins-expedition.md}, The ruins' doors are leaf doors): a hall door in every
 * plant hall's join and a cargo door in every way in from open ground, drawn as a ship's compartment doors are
 * ({@link RoomDoor}, {@link LeafDoorArt}).
 *
 * <p>A door that still <b>answers</b> parts for whoever stands in line with it and closes behind them, as a ship's
 * does. A <b>dead</b> door stands part-open where its power died, its leaves in its end cells (shut at generation). A
 * <b>forced</b> door was torn open long ago: one leaf lies on the floor beside the way, the other jammed in its track.
 * Presentation only: passage and sight are the grid's.
 */
final class RuinsLeafDoorRenderSystem implements RenderSystem {

    private static final String HALL_LEAF = "graphics/doors/ruins-leaf.png",
            CARGO_LEAF = "graphics/doors/ruins-cargo-leaf.png", TORN_LEAF = "graphics/doors/ruins-leaf-torn.png";
    /**
     * A hall leaf's art length and thickness in cells, a cargo leaf's, and a torn leaf's thickness: each a single
     * leaf at 160 px a cell ({@code art-source/ruins-themes}, the {@code doors} family).
     */
    static final float HALL_ART_CELLS = 1.5f, HALL_DEPTH = .55f, CARGO_ART_CELLS = 1f, CARGO_DEPTH = .7f,
            TORN_DEPTH = .8f;
    /** How far a forced door's surviving leaf stands open in its track. */
    static final float FORCED_TRAVEL = .3f;

    private final BattleSprites sprites;
    private final LongBucket near = new LongBucket();
    private BattleSimulation builtFor;
    private List<RuinsDelve.LeafDoor> doors = List.of();
    private List<RoomDoor> moving = List.of();

    RuinsLeafDoorRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
    }

    @Override public RenderLayer layer() { return RenderLayer.DOODADS; }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        if (sprites == null || ctx.sim == null) return;
        if (builtFor != ctx.sim) build(ctx.sim);
        if (doors.isEmpty()) return;
        ShuttleSpriteCache hall = sprites.spriteByPath(HALL_LEAF), cargo = sprites.spriteByPath(CARGO_LEAF);
        if (hall == null || cargo == null) return;
        ShuttleSpriteCache torn = sprites.spriteByPath(TORN_LEAF), jamb = sprites.spriteByPath(LeafDoorArt.RUINS_JAMB);
        ShuttleSpriteCache rubble = sprites.spriteByPath(LeafDoorArt.RUINS_RUBBLE);
        advance(ctx.sim, ctx.realDt);
        BattleCamera cam = ctx.camera;
        float margin = 4f;
        float left = cam.screenToCellX(cam.vpX()) - margin, right = cam.screenToCellX(cam.vpX() + cam.vpW()) + margin;
        float low = Math.min(cam.screenToCellY(cam.vpY()), cam.screenToCellY(cam.vpY() + cam.vpH())) - margin;
        float high = Math.max(cam.screenToCellY(cam.vpY()), cam.screenToCellY(cam.vpY() + cam.vpH())) + margin;
        for (int i = 0; i < doors.size(); i++) {
            RoomDoor m = moving.get(i);
            if (m.centerX() < left || m.centerX() > right || m.centerY() < low || m.centerY() > high) continue;
            RuinsDelve.LeafDoor door = doors.get(i);
            LeafDoorArt.Span span = new LeafDoorArt.Span(m.spanX(), m.spanMin(), m.spanMax(), m.across());
            ShuttleSpriteCache art = door.cargo() ? cargo : hall;
            boolean pair = false;
            float artCells = door.cargo() ? CARGO_ART_CELLS : HALL_ART_CELLS;
            float depth = door.cargo() ? CARGO_DEPTH : HALL_DEPTH;
            int perSide = door.cargo() ? 2 : 1;
            switch (door.stance()) {
                case ANSWERS -> {
                    LeafDoorArt.jambs(cam, out, jamb, span, LeafDoorArt.LAMP_OPEN, ctx.alphaMult);
                    LeafDoorArt.leaves(cam, out, art, pair, artCells, span, depth, m.leafTravel(), perSide, 0, 1f,
                            ctx.alphaMult);
                }
                // Its leaves stand in its end cells: each reaches one cell in from its jamb.
                case DEAD -> {
                    LeafDoorArt.jambs(cam, out, jamb, span, LeafDoorArt.LAMP_DEAD, ctx.alphaMult);
                    LeafDoorArt.leaves(cam, out, art, pair, artCells, span, depth, 1f - 1f / span.half(), perSide, 0,
                            .7f, ctx.alphaMult);
                }
                case FORCED -> {
                    int floor = floorSide(ctx.sim, door);
                    LeafDoorArt.jambs(cam, out, jamb, span, LeafDoorArt.LAMP_DEAD, ctx.alphaMult);
                    LeafDoorArt.rubble(cam, out, rubble, span, -1, floor, ctx.alphaMult);
                    LeafDoorArt.torn(cam, out, torn != null ? torn : art, pair, span,
                            torn != null ? TORN_DEPTH : depth, -1, floor, ctx.alphaMult);
                    LeafDoorArt.leaves(cam, out, art, pair, artCells, span, depth, FORCED_TRAVEL, perSide, -1, .8f,
                            ctx.alphaMult);
                }
            }
        }
    }

    private void build(BattleSimulation sim) {
        builtFor = sim;
        RuinsWorks works = sim.getRuinsWorks();
        RuinsDelve delve = works == null ? null : works.delve();
        doors = delve == null || delve.interior() == null ? List.of() : delve.interior().doors();
        List<RoomDoor> m = new ArrayList<>(doors.size());
        for (RuinsDelve.LeafDoor door : doors) {
            RuinsDelve.Area a = door.span();
            m.add(door.alongX() ? new RoomDoor(true, a.left(), a.right() + 1f, a.bottom() + .5f, false)
                    : new RoomDoor(false, a.bottom(), a.top() + 1f, a.left() + .5f, false));
        }
        moving = List.copyOf(m);
    }

    /** Opens each answering door for whoever is in line with it and closes it behind them. */
    private void advance(BattleSimulation sim, float seconds) {
        var index = sim.getUnitIndex();
        var world = sim.world();
        for (int i = 0; i < doors.size(); i++) {
            if (doors.get(i).stance() != RuinsDelve.DoorStance.ANSWERS) continue;
            RoomDoor door = moving.get(i);
            boolean called = false;
            if (index != null) {
                index.gather(door.centerX(), door.centerY(), door.callRadius(), near);
                for (int k = 0; k < near.size && !called; k++) {
                    long id = near.ids[k];
                    called = world.hasPosition(id) && door.calledBy(world.x(id), world.y(id));
                }
            }
            door.advance(seconds, called);
        }
    }

    /** The side of a forced door's line its torn leaf lies on: the side with open floor beside the door's middle. */
    private static int floorSide(BattleSimulation sim, RuinsDelve.LeafDoor door) {
        RuinsDelve.Area a = door.span();
        int x = door.alongX() ? a.centreX() : a.left(), y = door.alongX() ? a.bottom() : a.centreY();
        int dx = door.alongX() ? 0 : 2, dy = door.alongX() ? 2 : 0;
        return sim.getGrid().inBounds(x + dx, y + dy) && sim.getGrid().isWalkable(x + dx, y + dy) ? 1 : -1;
    }
}
