package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.ship.BayAperture;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The company ship's own hangar, with her boats standing in it.
 *
 * <p>The room rather than a diagram of it: the deck scene draws the bay, and
 * the boats on screen are the aircraft the ship actually keeps in her berths,
 * turned round by her own hands. What this adds is the one thing a render
 * cannot say — which berth the player has selected — so the marks are drawn
 * over the floor and the boats are drawn over the marks, the way
 * {@link BarracksCanvas} marks a squad.
 *
 * <p><b>The campaign deck and the generated bay are counted separately and may
 * disagree.</b> The berth count comes off the hull's room program and the
 * berths on screen come off a laid deck, and the two are measured equal rather
 * than guaranteed equal. So a berth with no boat behind it simply carries no
 * mark, and a boat with no berth on screen is a card the player can still open
 * — never an exception, because the picture failing is not a reason the room
 * cannot be used.
 */
public final class BoatDeckCanvas implements CanvasProducer {

    private static final Color BACKGROUND = new Color(0x08, 0x0E, 0x15);
    private static final Color MARK = new Color(0xFF, 0xD4, 0x64, 226);
    private static final Color VACANT = new Color(0xFF, 0xD4, 0x64, 102);
    private static final Color HOVER = new Color(0x6D, 0xD5, 0xF2, 170);
    /** How far along each side of the berth a selection corner runs, in cells. */
    private static final float MARK_CORNER = 0.9f;
    /** Dash and gap of a vacant berth's outline, in cells. */
    private static final float DASH = 0.5f;
    /** Hull kept around the bay, so its door is not clipped into a gap. */
    private static final int SURROUND_CELLS = 2;
    private static final int OUTBOARD_CELLS = 8;
    private static final Color SPACE = new Color(0x03, 0x07, 0x10);
    private static final Color STAR = new Color(0x86, 0xA2, 0xBC, 155);
    private static final Color THRESHOLD = new Color(0x6A, 0xD5, 0xE0, 180);

    private static final EnumSet<RenderLayer> BACKDROP_LAYERS = EnumSet.of(
            RenderLayer.GROUND, RenderLayer.DOODADS);
    private static final EnumSet<RenderLayer> ACTOR_LAYERS = EnumSet.of(
            RenderLayer.UNITS, RenderLayer.SHUTTLES, RenderLayer.SHOTS);

    private final CompanyDeck ship;
    private final Supplier<DeckGraph.Compartment> bay;
    private final IntSupplier selectedBerth;
    private final IntSupplier berthOffset;
    private final IntPredicate berthHeld;
    private List<BerthTarget> targets = List.of();
    private int hovered = -1;

    /**
     * @param selectedBerth the campaign deck's berth index, which counts every
     *     hangar's berths in deck order rather than restarting in each bay
     * @param berthOffset how many boat berths lie in the hangars before this
     *     one, which is what turns a berth on screen into that index
     * @param berthHeld whether the company still has a boat for a campaign
     *     berth index; the ones it does not are drawn as the holes they are
     */
    public BoatDeckCanvas(CompanyDeck ship, Supplier<DeckGraph.Compartment> bay,
                          IntSupplier selectedBerth, IntSupplier berthOffset,
                          IntPredicate berthHeld) {
        if (ship == null || bay == null || selectedBerth == null || berthOffset == null
                || berthHeld == null) {
            throw new IllegalArgumentException(
                    "a ship, a bay, a selected berth, its offset and what is held"
                            + " are required");
        }
        this.ship = ship;
        this.bay = bay;
        this.selectedBerth = selectedBerth;
        this.berthOffset = berthOffset;
        this.berthHeld = berthHeld;
    }

    /**
     * The boat berths standing in one hangar, in generation order.
     *
     * <p>Filtered rather than taken whole: a deck keeps one berth list for
     * machines and boats alike, so a bay that also held a machine berth would
     * shift every boat's index by one and silently mark the wrong berth.
     */
    public static List<Gantry> boatBerthsIn(ShipDeckBattleScene scene,
                                            DeckGraph.Compartment room) {
        if (scene == null || room == null) return List.of();
        List<Gantry> boats = new ArrayList<>();
        for (Gantry berth : scene.berthsIn(room)) {
            if (berth.holds == Gantry.Holds.BOAT) boats.add(berth);
        }
        return List.copyOf(boats);
    }

    @Override
    public void draw(CanvasContext context) {
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        ShipDeckBattleScene aboard = ship.live() ? ship.scene() : null;
        DeckGraph.Compartment hangar = aboard != null ? bay.get() : null;
        if (hangar == null) {
            targets = List.of();
            context.fillRect(0f, 0f, width, height, BACKGROUND);
            return;
        }
        BayAperture aperture = aboard.bayDoor(hangar);
        ShipDeckBattleScene.RoomView view = hangarView(hangar, aperture);

        CanvasHostViewport[] host = new CanvasHostViewport[1];
        BattleSceneHostPass backdrop = aboard.pass(view, BACKDROP_LAYERS);
        boolean rendered = context.hostPass(new BattleSceneHostPass() {
            @Override
            public BattleSceneFrame prepare(CanvasHostViewport viewport, float alphaMult) {
                host[0] = viewport;
                return backdrop.prepare(viewport, alphaMult);
            }

            @Override
            public void draw(CanvasHostViewport viewport, float alphaMult) {
                host[0] = viewport;
                backdrop.draw(viewport, alphaMult);
            }
        });
        if (!rendered || host[0] == null) {
            targets = List.of();
            context.fillRect(0f, 0f, width, height, BACKGROUND);
            return;
        }

        Projection projection = Projection.forHost(aboard, view, host[0]);
        space(context, projection, aboard.simulation().getTopology());
        if (aperture != null) threshold(context, projection, aperture);
        markBerths(context, projection, boatBerthsIn(aboard, hangar));
        context.hostPass(aboard.pass(view, ACTOR_LAYERS));
    }

    /** Keep the exit and the space beyond it in view along with every berth. */
    private static ShipDeckBattleScene.RoomView hangarView(
            DeckGraph.Compartment room, BayAperture door) {
        int left = room.left() - SURROUND_CELLS;
        int top = room.top() - SURROUND_CELLS;
        int across = room.width() + 2 * SURROUND_CELLS;
        int down = room.depth() + 2 * SURROUND_CELLS;
        if (door != null) {
            if (door.outDx() < 0) left -= OUTBOARD_CELLS;
            if (door.outDy() < 0) top -= OUTBOARD_CELLS;
            across += Math.abs(door.outDx()) * OUTBOARD_CELLS;
            down += Math.abs(door.outDy()) * OUTBOARD_CELLS;
        }
        return new ShipDeckBattleScene.RoomView(left, top, across, down,
                left + across * 0.5f, top + down * 0.5f, 0f);
    }

    /** Decorative stars only where the generated topology actually has no deck. */
    private static void space(CanvasContext context, Projection p, CellTopology topology) {
        BattleCamera camera = p.camera();
        int left = (int) Math.floor(camera.screenToCellX(camera.vpX()));
        int right = (int) Math.ceil(camera.screenToCellX(camera.vpX() + camera.vpW()));
        int bottom = (int) Math.floor(camera.screenToCellY(camera.vpY()));
        int top = (int) Math.ceil(camera.screenToCellY(camera.vpY() + camera.vpH()));
        for (int y = bottom; y < top; y++) {
            for (int x = left; x < right; x++) {
                if (topology.inBounds(x, y)
                        && topology.getGroundKind(x, y) != CellTopology.GroundKind.VOID) continue;
                float px = (float) Math.floor(p.x(x));
                float py = (float) Math.floor(p.y(y + 1f));
                context.fillRect(px, py, (float) Math.ceil(p.x(x + 1f)) - px,
                        (float) Math.ceil(p.y(y)) - py, SPACE);
                int hash = (x * 73856093) ^ (y * 19349663);
                hash ^= hash >>> 13;
                if ((hash & 7) != 0) continue;
                float sx = p.x(x + 0.15f + ((hash >>> 4) & 255) / 365f);
                float sy = p.y(y + 0.15f + ((hash >>> 12) & 255) / 365f);
                float size = (hash & 256) == 0 ? 1f : 2f;
                context.fillRect(sx, sy, size, size, STAR);
            }
        }
    }

    /** Navigation lights mark the real opening; the blue line is its pressure screen. */
    private static void threshold(CanvasContext context, Projection p, BayAperture door) {
        float tangentX = -door.outDy();
        float tangentY = door.outDx();
        float half = door.widthCells() * 0.5f;
        float x = door.centerX();
        float y = door.centerY();
        context.line(p.x(x - tangentX * half), p.y(y - tangentY * half),
                p.x(x + tangentX * half), p.y(y + tangentY * half), THRESHOLD, 2f);
        for (int side : new int[]{-1, 1}) {
            float jambX = x + tangentX * (half + 0.15f) * side;
            float jambY = y + tangentY * (half + 0.15f) * side;
            context.line(p.x(jambX - door.outDx() * 0.8f),
                    p.y(jambY - door.outDy() * 0.8f),
                    p.x(jambX + door.outDx() * 0.8f),
                    p.y(jambY + door.outDy() * 0.8f), MARK, 4f);
        }
        for (int step = 2; step <= 4; step += 2) {
            float tipX = x - door.outDx() * step;
            float tipY = y - door.outDy() * step;
            for (int side : new int[]{-1, 1}) {
                context.line(p.x(tipX - door.outDx() * 0.6f + tangentX * side * 0.6f),
                        p.y(tipY - door.outDy() * 0.6f + tangentY * side * 0.6f),
                        p.x(tipX), p.y(tipY), THRESHOLD, 2f);
            }
        }
    }

    /** Records hover over the berths drawn during the last frame. */
    public void pointAt(float canvasX, float canvasY) {
        hovered = localBerthAt(canvasX, canvasY);
    }

    /**
     * The campaign deck's index for the berth at this canvas-local point, or -1
     * outside every berth. Projected from the last frame's framing, which is
     * the one the player clicked on.
     */
    public int berthAt(float canvasX, float canvasY) {
        int local = localBerthAt(canvasX, canvasY);
        return local < 0 ? -1 : berthOffset.getAsInt() + local;
    }

    private int localBerthAt(float canvasX, float canvasY) {
        if (!Float.isFinite(canvasX) || !Float.isFinite(canvasY)) return -1;
        for (BerthTarget target : targets) {
            if (target.contains(canvasX, canvasY)) return target.index();
        }
        return -1;
    }

    private void markBerths(CanvasContext context, Projection projection,
                            List<Gantry> berths) {
        int offset = berthOffset.getAsInt();
        int selected = selectedBerth.getAsInt();
        List<BerthTarget> found = new ArrayList<>(berths.size());
        for (int index = 0; index < berths.size(); index++) {
            Gantry berth = berths.get(index);
            float left = projection.x(berth.left());
            float right = projection.x(berth.right() + 1f);
            float top = projection.y(berth.top() + 1f);
            float bottom = projection.y(berth.bottom());
            BerthTarget target = new BerthTarget(index,
                    Math.min(left, right), Math.min(top, bottom),
                    Math.abs(right - left), Math.abs(bottom - top));
            found.add(target);
            // The hole first, so a vacant berth the player has opened still
            // reads as a berth under its selection bracket.
            if (!berthHeld.test(offset + index)) {
                dashedOutline(context, projection, berth);
            }
            if (offset + index == selected) {
                bracket(context, projection, berth);
            } else if (index == hovered) {
                context.strokeRect(target.x(), target.y(),
                        Math.max(1f, target.width()), Math.max(1f, target.height()),
                        HOVER, 1f);
            }
        }
        targets = List.copyOf(found);
    }

    /** Corner marks on the berth's own footprint, so the boat stays legible. */
    private static void bracket(CanvasContext context, Projection projection, Gantry berth) {
        float left = projection.x(berth.left());
        float right = projection.x(berth.right() + 1f);
        float top = projection.y(berth.top() + 1f);
        float bottom = projection.y(berth.bottom());
        float run = Math.abs(projection.x(MARK_CORNER) - projection.x(0f));
        for (float[] corner : new float[][]{
                {left, top, 1f, 1f}, {right, top, -1f, 1f},
                {left, bottom, 1f, -1f}, {right, bottom, -1f, -1f}}) {
            float x = corner[0];
            float y = corner[1];
            context.line(x, y, x + run * corner[2], y, MARK, 2f);
            context.line(x, y, x, y + run * corner[3], MARK, 2f);
        }
    }

    /**
     * A berth with nothing standing in it: its whole footprint, broken.
     *
     * <p>Dashed rather than drawn solid because a boat's own berth marks are
     * corners and a continuous rectangle would read as a stronger mark than the
     * selection it sits under. There is no dash primitive, so the run is walked
     * in cell-sized steps.
     */
    private static void dashedOutline(CanvasContext context, Projection projection,
                                      Gantry berth) {
        float left = projection.x(berth.left());
        float right = projection.x(berth.right() + 1f);
        float top = projection.y(berth.top() + 1f);
        float bottom = projection.y(berth.bottom());
        float dash = Math.abs(projection.x(DASH) - projection.x(0f));
        dashedLine(context, left, top, right, top, dash);
        dashedLine(context, left, bottom, right, bottom, dash);
        dashedLine(context, left, top, left, bottom, dash);
        dashedLine(context, right, top, right, bottom, dash);
    }

    /** @see #dashedOutline */
    private static void dashedLine(CanvasContext context, float fromX, float fromY,
                                   float toX, float toY, float dash) {
        float runX = toX - fromX;
        float runY = toY - fromY;
        float length = (float) Math.sqrt(runX * runX + runY * runY);
        if (!(length > 0f) || !(dash > 0f)) return;
        float stepX = runX / length;
        float stepY = runY / length;
        for (float along = 0f; along < length; along += dash * 2f) {
            float end = Math.min(length, along + dash);
            context.line(fromX + stepX * along, fromY + stepY * along,
                    fromX + stepX * end, fromY + stepY * end, VACANT, 2f);
        }
    }

    private record BerthTarget(int index, float x, float y, float width, float height) {
        boolean contains(float pointX, float pointY) {
            return pointX >= x && pointX <= x + width
                    && pointY >= y && pointY <= y + height;
        }
    }
}
