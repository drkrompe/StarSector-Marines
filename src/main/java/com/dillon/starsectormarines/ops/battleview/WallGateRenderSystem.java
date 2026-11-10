package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.door.WallGate;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.world.gen.ship.RoomDoor;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.util.ArrayList;
import java.util.List;

/**
 * The gates in walled places' walls ({@code precincts.md}, Gates are leaf doors), drawn as leaf doors
 * ({@link LeafDoorArt}): a gatepost from the fort's blast-door frame on the wall either side, and between them a pair
 * of hazard-banded leaves (the forcible door's art, as the ruins' cargo doors) that part for whoever comes through and
 * close behind them, as a ship's compartment door does ({@link RoomDoor}). A gate whose jamb a breach blew has lost a
 * leaf: it lies on the ground inside, the other jammed part-open in its track, and the gateposts are the damaged
 * frame's. Presentation only: the way through is the grid's.
 */
final class WallGateRenderSystem implements RenderSystem {

    private static final String LEAVES = "graphics/doors/forcible-4.png", FRAME = "graphics/fort/blast-door.png",
            FRAME_DAMAGED = "graphics/fort/blast-door-damaged.png";
    /** One leaf's art length (half the pair) and a leaf's thickness through the wall, in cells. */
    static final float ART_CELLS = 2f, DEPTH = .7f;
    /** How far either side of the wall line a body (a vehicle's nose included) calls the gate open, in cells. */
    static final float CALL_CELLS = 3.5f;
    /** How far past either end of the gate, along the wall, a body still counts as in line with it. */
    static final float CALL_SLACK_CELLS = .35f;
    /** Gates this wide or wider hang two telescoping leaves each way. */
    static final int WIDE = 5;
    /** How far a forced gate's surviving leaf stands open in its track. */
    static final float FORCED_TRAVEL = .3f;

    private final BattleSprites sprites;
    private BattleSimulation builtFor;
    private List<WallGate> gates = List.of();
    private List<RoomDoor> moving = List.of();
    private boolean[] forced = new boolean[0];

    WallGateRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
    }

    @Override public RenderLayer layer() { return RenderLayer.DOODADS; }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        if (sprites == null || ctx.sim == null) return;
        if (builtFor != ctx.sim) build(ctx.sim);
        if (gates.isEmpty()) return;
        ShuttleSpriteCache leaves = sprites.spriteByPath(LEAVES);
        ShuttleSpriteCache frame = sprites.spriteByPath(FRAME), damaged = sprites.spriteByPath(FRAME_DAMAGED);
        if (leaves == null) return;
        advance(ctx.sim, ctx.realDt);
        BattleCamera cam = ctx.camera;
        float margin = 6f;
        float left = cam.screenToCellX(cam.vpX()) - margin, right = cam.screenToCellX(cam.vpX() + cam.vpW()) + margin;
        float low = Math.min(cam.screenToCellY(cam.vpY()), cam.screenToCellY(cam.vpY() + cam.vpH())) - margin;
        float high = Math.max(cam.screenToCellY(cam.vpY()), cam.screenToCellY(cam.vpY() + cam.vpH())) + margin;
        for (int i = 0; i < gates.size(); i++) {
            RoomDoor m = moving.get(i);
            if (m.centerX() < left || m.centerX() > right || m.centerY() < low || m.centerY() > high) continue;
            WallGate gate = gates.get(i);
            LeafDoorArt.Span span = new LeafDoorArt.Span(m.spanX(), m.spanMin(), m.spanMax(), m.across());
            int perSide = gate.width() >= WIDE ? 2 : 1;
            if (forced[i]) {
                int floor = -(gate.spanX() ? gate.outY() : gate.outX());   // it falls inside, the way it was driven
                LeafDoorArt.torn(cam, out, leaves, true, span, DEPTH, -1, floor, ctx.alphaMult);
                LeafDoorArt.leaves(cam, out, leaves, true, ART_CELLS, span, DEPTH, FORCED_TRAVEL, perSide, -1, .8f,
                        ctx.alphaMult);
            } else {
                LeafDoorArt.leaves(cam, out, leaves, true, ART_CELLS, span, DEPTH, m.leafTravel(), perSide, 0, 1f,
                        ctx.alphaMult);
            }
            ShuttleSpriteCache posts = forced[i] && damaged != null ? damaged : frame;
            if (posts != null) gateposts(cam, out, posts, gate, ctx.alphaMult);
        }
    }

    /** The frame's end blocks, one cell each, on the jamb cells either side of the gate: its left end low, right high. */
    private static void gateposts(BattleCamera cam, DrawList out, ShuttleSpriteCache art, WallGate gate, float alpha) {
        int third = art.pxW / 3;
        float px = cam.cellPxSize();
        int[][] jambs = gate.jambs();
        for (int end = 0; end < 2; end++) {
            int[] j = jambs[end];
            out.addImageRegion(RenderLayer.DOODADS, art.sprite, art.pxW, art.pxH, end == 0 ? 0 : art.pxW - third, 0,
                    third, art.pxH, cam.cellToScreenX(j[0] + .5f), cam.cellToScreenY(j[1] + .5f), px, px,
                    gate.spanX() ? 0f : 90f, 1f, 1f, 1f, alpha);
        }
    }

    private void build(BattleSimulation sim) {
        builtFor = sim;
        gates = sim.wallGates();
        List<RoomDoor> m = new ArrayList<>(gates.size());
        for (WallGate g : gates) m.add(new RoomDoor(g.spanX(), g.min(), g.max() + 1f, g.across() + .5f, false));
        moving = List.copyOf(m);
        forced = new boolean[gates.size()];
        // A gate starts where its callers leave it, not sliding from shut on the first frame.
        for (int i = 0; i < gates.size(); i++) moving.get(i).advance(RoomDoor.TRAVEL_SECONDS, called(sim, i));
    }

    /** Opens each gate for whoever is in line with it and closes it behind them; latches a gate a breach forced. */
    private void advance(BattleSimulation sim, float seconds) {
        for (int i = 0; i < gates.size(); i++) {
            WallGate gate = gates.get(i);
            if (!forced[i]) {
                forced[i] = gate.forced((x, y) -> sim.getGrid().inBounds(x, y) && sim.getGrid().isWalkable(x, y));
            }
            // A still frame (no time passed) shows the gate where its callers would leave it.
            if (!forced[i]) moving.get(i).advance(seconds > 0f ? seconds : RoomDoor.TRAVEL_SECONDS, called(sim, i));
        }
    }

    /** Whether any body stands in line with gate {@code i} and near enough the wall to call it. */
    private boolean called(BattleSimulation sim, int i) {
        RoomDoor door = moving.get(i);
        World world = sim.world();
        var roster = sim.getRoster();
        for (int k = 0; k < roster.liveCount(); k++) {
            long id = roster.get(k);
            if (!world.hasPosition(id)) continue;
            float along = door.spanX() ? world.x(id) : world.y(id);
            float off = Math.abs((door.spanX() ? world.y(id) : world.x(id)) - door.across());
            if (along >= door.spanMin() - CALL_SLACK_CELLS && along <= door.spanMax() + CALL_SLACK_CELLS
                    && off <= CALL_CELLS) return true;
        }
        return false;
    }
}
