package com.dillon.starsectormarines.tools.roomauthoring;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.FurnishableRoom;
import com.dillon.starsectormarines.battle.world.gen.fit.Hookup;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFloor;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.AuthoredFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.RoomLayout;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.model.WallMasks;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The room being edited, drawn as it will look.
 *
 * <p>The editing grid used to be coloured rectangles: deck, lane, fixture. That
 * is fine for saying which cell is reserved and useless for choosing between a
 * vent plate and hazard striping, which is a decision about how something
 * <em>looks</em> and can only be made by looking.
 *
 * <p>Still the battle renderer, per law 17 — the room is stood up as a small map
 * of its own and drawn by the same renderer that draws it in the hull. That is a
 * different framing from the comparison screen and not a different painter: this
 * answers "what am I making" while a deck is being edited, and the comparison
 * answers "what will the ship do with it".
 *
 * <p>Rendered at exactly one cell per cell with no surround, so the authoring
 * overlay drawn on top lines up without arithmetic.
 */
public final class RoomPreview implements AutoCloseable {

    private final Path modRoot;
    private HeadlessUiRenderer drain;

    public RoomPreview(Path projectRoot) {
        this.modRoot = projectRoot.resolve("mod");
    }

    /**
     * Draw this layout on its own.
     *
     * @param cellPx pixels per cell; the image is exactly the room's size times
     *     this, so cell {@code (x, y)} starts at {@code (x * cellPx, y * cellPx)}
     */
    public BufferedImage render(RoomLayout layout, int cellPx) {
        RoomShape shape = layout.shape();
        int margin = 2;
        int width = shape.width() + margin * 2;
        int height = shape.height() + margin * 2;

        NavigationGrid grid = new NavigationGrid(width, height);
        CellTopology topology = new CellTopology(width, height);

        // The room's own floor, and a bulkhead round it. Without the ring the
        // room has no edge and the walls this page can author have nothing to
        // draw on; without the ground kind every cell reads as off-map, since
        // an uncarved topology defaults to VOID.
        for (int[] cell : shapeCells(shape)) {
            int x = margin + cell[0];
            int y = margin + cell[1];
            grid.setWalkableFloor(x, y);
            topology.setGroundKind(x, y, GroundKind.INDOOR);
        }
        for (int[] cell : shape.wall()) {
            int x = margin + cell[0];
            int y = margin + cell[1];
            // A tag, not a ground kind — there is no WALL kind, and the wall
            // pass keys on the tag.
            topology.setWall(x, y, true);
        }

        GenContext ctx = new GenContext(grid, topology, new Random(0L), width, height, 0L);
        RoomFloor floor = new RoomFloor(ctx, new PreviewRoom(shape, margin, margin,
                RoomPose.CANONICAL, layout.purpose(), doors(layout, margin)), layout.fit());
        new AuthoredFitting(layout).fit(floor);
        floor.seal();
        // After the fill, because a bulkhead the room authored is stamped during
        // it and a face derived before would be derived from the wrong walls.
        WallMasks.stampUnclaimed(topology);

        MapResult map = new MapResult(grid, topology, 0, 0, 0, 0,
                new ArrayList<>(), new ArrayList<>(ctx.doodads));
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(map, 0L)) {
            return renderer().renderHostPass(
                    scene.pass(ShipDeckBattleScene.DeckView.over(
                            margin, margin, shape.width(), shape.height(), cellPx)),
                    shape.width() * cellPx, shape.height() * cellPx);
        }
    }

    private static List<int[]> shapeCells(RoomShape shape) {
        List<int[]> cells = new ArrayList<>();
        for (int x = 0; x < shape.width(); x++) {
            for (int y = 0; y < shape.height(); y++) {
                if (shape.contains(x, y)) cells.add(new int[]{ x, y });
            }
        }
        return cells;
    }

    private static List<Doorway> doors(RoomLayout layout, int margin) {
        List<Doorway> doors = new ArrayList<>();
        for (Hookup hookup : layout.hookups()) {
            for (Hookup.DoorSlot slot : hookup.slots()) {
                int[] cell = slot.cells().get(0);
                doors.add(new Doorway(margin + cell[0], margin + cell[1]));
            }
            break;
        }
        if (doors.isEmpty()) {
            doors.add(new Doorway(margin - 1, margin + layout.shape().height() / 2));
        }
        return doors;
    }

    private HeadlessUiRenderer renderer() {
        if (drain == null) {
            drain = new HeadlessUiRenderer(new HeadlessBattleSceneRenderer(modRoot), modRoot);
        }
        return drain;
    }

    private record PreviewRoom(RoomShape shape, int originX, int originY, RoomPose pose,
                               RoomPurpose purpose, List<Doorway> doors)
            implements FurnishableRoom { }

    @Override
    public void close() {
        drain = null;
    }
}
