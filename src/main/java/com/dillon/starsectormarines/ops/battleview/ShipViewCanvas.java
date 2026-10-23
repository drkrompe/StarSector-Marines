package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph.Compartment;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;
import com.dillon.starsectormarines.ui.retained.CanvasMetrics;
import com.dillon.starsectormarines.ui.retained.Rect;

import java.awt.Color;

/**
 * The whole ship, running, with the camera in the player's hands.
 *
 * <p>Every other view of the company ship is pointed at one compartment,
 * because it is there to show the player a room. This one is pointed at the
 * vessel: the watch walking between the mess and the range, machines standing
 * in their berths, the length of her. A ship you can only ever see one room of
 * is a set of rooms, and the whole point of generating a deck rather than
 * authoring rooms is that she is one place.
 *
 * <p><b>Nothing is commanded here.</b> The player looks; the ship carries on.
 * That is what makes it worth having a camera at all — there is something to
 * find by flying around it rather than a diagram to read.
 *
 * <p>The camera is an ordinary {@link BattleCamera}, framed so that fully
 * zoomed out is the whole hull. Its own zoom floor then means the player cannot
 * pull back into empty space around a ship, and its pan clamp means they cannot
 * lose her off the edge of the view.
 */
public final class ShipViewCanvas implements CanvasProducer {

    private static final Color BACKGROUND = new Color(0x08, 0x0E, 0x15);

    /**
     * What the hull is darkened to behind the deck.
     *
     * <p>A tint multiplies, so this cannot flatten the art into one colour —
     * bright plating stays faintly brighter than the panel lines. That is the
     * point rather than a limitation: the plating is most of what makes a hull
     * recognisable, and a flat cutout of a Valkyrie is a flat cutout.
     */
    private static final Color HULL = new Color(0x2C, 0x39, 0x4C, 0xFF);

    /**
     * A vanilla hull sprite is drawn bow-up and a deck runs bow to stern along
     * +x — the bow at low x, which is the left of the picture — so the art is
     * turned a quarter turn counter-clockwise to lie along her.
     *
     * <p>Positive is counter-clockwise in both backends: Starsector turns a
     * sprite that way, and the raster backend negates the angle to compensate
     * for its own Y-down axis. This was negative, which pointed her bow at the
     * stern of the deck drawn on top of her.
     */
    private static final float BOW_LEFT = 90f;

    /**
     * How much larger the hull is drawn than the deck laid inside her.
     *
     * <p>A deck is a slice through a ship, not her outline: the plating, the
     * spaces below and the structure the compartments are hung on all sit
     * outside it. Drawing the art to the deck's own bounds would put her
     * skin exactly on the walls of her rooms, which is the one place it
     * certainly is not.
     */
    private static final float HULL_OVERHANG = 1.06f;

    private final CompanyDeck ship;
    private BattleCamera camera;
    private RoomPurpose focus;
    private Compartment focusedRoom, selectedRoom;
    public void focus(Compartment room) { focusedRoom = room; selectedRoom = room; flying = false; }

    /** Where the camera stands: zoom and the cell at the centre of the view. */
    public record View(float zoom, float x, float y) {}

    private boolean flying, flightStarted, flightWhole;
    private Compartment flightRoom;
    private View flightView;
    private float flightTime, flightSeconds, fromZoom, fromX, fromY;

    /**
     * Flies the camera to {@code room}, or out to the whole ship when it is null, over
     * {@code seconds}, eased at both ends. Lands on the same framing {@link #focus(Compartment)}
     * gives, so a deck laid out again under the same room can pick up exactly where the flight
     * ended.
     */
    public void flyTo(Compartment room, float seconds) {
        flightRoom = room;
        flightView = null;
        flightWhole = room == null;
        flightSeconds = Math.max(0.01f, seconds);
        flightTime = 0f;
        flightStarted = false;
        flying = true;
        selectedRoom = room;
    }

    /** Flies back to a view taken earlier with {@link #view()}. */
    public void flyTo(View view, float seconds) {
        flyTo((Compartment) null, seconds);
        flightView = view;
        flightWhole = false;
        selectedRoom = null;
    }

    /** The camera's current view, or null before the first frame has sized it. */
    public View view() {
        return camera == null ? null : new View(camera.zoom(), camera.panCellX(), camera.panCellY());
    }

    /** Advances a flight in progress; the screen calls it once per frame. */
    public void advance(float dt) { if (flying) flightTime += dt; }

    public boolean flying() { return flying; }
    /** Inspection does not fly the camera away from the other room markers. */
    public void select(Compartment room) { selectedRoom = room; }

    /** A small glyph straddles the upper-right wall, leaving the room interior clear. */
    public Rect markerBounds(Compartment room, CanvasMetrics metrics) {
        Rect bounds = roomBounds(room, metrics);
        float size = Math.min(22f, Math.min(bounds.width(), bounds.height()) * 0.45f);
        return new Rect(bounds.right() - size / 2f, bounds.y() - size / 2f, size, size);
    }

    /** Screen-space footprint lets inspection cards open beside either edge of a room. */
    public Rect roomBounds(Compartment room, CanvasMetrics metrics) {
        if (camera == null || host == null) return Rect.EMPTY;
        float left = metrics.toDocumentX(host.canvasXForScreen(camera.cellToScreenX(room.left())));
        float right = metrics.toDocumentX(host.canvasXForScreen(camera.cellToScreenX(room.right() + 1)));
        float top = metrics.toDocumentY(host.canvasYForScreen(camera.cellToScreenY(room.bottom() + 1)));
        float bottom = metrics.toDocumentY(host.canvasYForScreen(camera.cellToScreenY(room.top())));
        return new Rect(left, top, right - left, bottom - top);
    }
    public Compartment roomAt(float x, float y) {
        if (camera == null || !camera.containsScreen(x, y) || !ship.ready()) return null;
        int cx = (int) Math.floor(camera.screenToCellX(x)), cy = (int) Math.floor(camera.screenToCellY(y));
        return ship.rooms().compartments().stream().filter(r -> r.contains(cx, cy)).findFirst().orElse(null);
    }

    /** Applied on draw, when the generated deck and viewport are both ready. */
    public void focus(RoomPurpose purpose) { focus = purpose; }
    /**
     * The host rectangle the deck was last drawn into, or null before the first
     * frame. Held so the screen flying the camera can put a pointer into the
     * same space the camera is stated in.
     */
    private CanvasHostViewport host;

    /**
     * <p>The hull's art is read off the ship rather than passed in beside her.
     * Taking it as a second argument made drawing the vessel something a caller
     * had to remember, and the caller that mattered did not: the screen built
     * this canvas from the deck alone, so the game drew a plan floating in
     * empty space while the headless evidence — which did pass it — showed the
     * ship. A backdrop is a fact about which vessel this is, and the deck
     * already knows which vessel this is.
     */
    public ShipViewCanvas(CompanyDeck ship) {
        if (ship == null) throw new IllegalArgumentException("a ship is required");
        this.ship = ship;
    }

    /**
     * The camera being flown, or null before the first frame has sized it.
     *
     * <p>Built on the first draw rather than in the constructor because it is
     * framed on the rectangle the deck is drawn into, and where on the host that
     * rectangle falls is not known until the document has laid out.
     */
    public BattleCamera camera() {
        return camera;
    }

    /**
     * Puts a point in this canvas's own surface coordinates into the camera's
     * space, which is the host's — Y-up pixels on the screen the deck is drawn
     * to.
     *
     * <p>The camera is stated there because that is where the deck is projected,
     * and its cells are square there and nowhere else: the canvas surface is a
     * fixed 16:9 stretched onto whatever shape the panel is, by a different
     * factor on each axis, with its own Y running the other way. A pointer
     * handed over in surface coordinates has the camera compare two spaces and
     * conclude the cursor is somewhere it is not, which is felt as a zoom that
     * drifts off the cursor and a drag that runs at the wrong speed and pulls
     * the wrong way up.
     */
    public float toCameraX(float canvasX) {
        return host == null ? canvasX : host.screenXForCanvas(canvasX);
    }

    /** @see #toCameraX */
    public float toCameraY(float canvasY) {
        return host == null ? canvasY : host.screenYForCanvas(canvasY);
    }

    @Override
    public void draw(CanvasContext context) {
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        ShipDeckBattleScene aboard = ship.drawable();
        // The deck is drawn by a host pass, so a backend that runs none has no
        // ship to show and no rectangle in which to frame her.
        CanvasHostViewport rect = context.hostViewport();
        host = rect;
        if (aboard == null || rect == null || width <= 0f || height <= 0f
                || !(rect.scaleX() > 0f) || !(rect.scaleY() > 0f)) {
            context.fillRect(0f, 0f, width, height, BACKGROUND);
            return;
        }

        MapResult deck = ship.map();
        int cellsAcross = Math.max(1, deck.grid.getWidth());
        int cellsDown = Math.max(1, deck.grid.getHeight());
        // Fully zoomed out is the whole hull, so the camera's own zoom floor
        // becomes "you cannot pull back past the ship" for free. Fitted to the
        // host rect rather than to the canvas surface: the surface is a fixed
        // aspect that the panel stretches, so a cell fitted there is not the
        // cell the deck is about to be drawn with.
        float baseCell = Math.min(rect.width() / cellsAcross, rect.height() / cellsDown);
        if (camera == null) {
            camera = new BattleCamera(cellsAcross, cellsDown);
            camera.centerOn(cellsAcross * 0.5f, cellsDown * 0.5f);
        }
        // Re-stated every frame so a resized dialog re-fits rather than keeping
        // a scale that was right for a window that is gone. Stated on the host
        // rect itself, which makes this the same camera the deck's own pass
        // builds from the framing below rather than one that has to be kept in
        // step with it.
        camera.setViewport(rect.screenX(), rect.screenY(),
                rect.width(), rect.height(), baseCell);

        if (flying) fly(rect, baseCell, cellsAcross, cellsDown);
        if (focus != null || focusedRoom != null) {
            var room = focusedRoom != null ? focusedRoom : ship.room(focus);
            selectedRoom = room;
            focusedRoom = null;
            if (room != null) {
                float target = Math.min(rect.width() / (room.width() + 8f), rect.height() / (room.depth() + 8f));
                camera.zoomAt((float) (Math.log(target / camera.cellPxSize()) / Math.log(1.2)),
                        rect.screenX() + rect.width() / 2f, rect.screenY() + rect.height() / 2f);
                camera.centerOn(room.originX() + room.width() / 2f, room.originY() + room.depth() / 2f);
            }
            focus = null;
        }
        drawHull(context, rect, cellsAcross, cellsDown);

        ShipDeckBattleScene.DeckView view = new ShipDeckBattleScene.DeckView(
                camera.panCellX(), camera.panCellY(), camera.cellPxSize());
        if (!context.hostPass(aboard.pass(view, ShipDeckBattleScene.DECK_LAYERS))) {
            context.fillRect(0f, 0f, width, height, BACKGROUND);
        }
        drawSelection(context, rect);
    }

    private void fly(CanvasHostViewport rect, float baseCell, int cellsAcross, int cellsDown) {
        if (!flightStarted) {
            fromZoom = camera.zoom(); fromX = camera.panCellX(); fromY = camera.panCellY();
            flightStarted = true;
        }
        float toZoom = BattleCamera.MIN_ZOOM, toX = cellsAcross * 0.5f, toY = cellsDown * 0.5f;
        if (flightView != null) {
            toZoom = flightView.zoom(); toX = flightView.x(); toY = flightView.y();
        } else if (!flightWhole) {
            float cell = Math.min(rect.width() / (flightRoom.width() + 8f), rect.height() / (flightRoom.depth() + 8f));
            toZoom = Math.max(BattleCamera.MIN_ZOOM, Math.min(BattleCamera.MAX_ZOOM, cell / baseCell));
            toX = flightRoom.originX() + flightRoom.width() / 2f;
            toY = flightRoom.originY() + flightRoom.depth() / 2f;
        }
        float t = Math.min(1f, flightTime / flightSeconds);
        float eased = t * t * (3f - 2f * t);
        float zoom = (float) Math.exp(Math.log(fromZoom) + (Math.log(toZoom) - Math.log(fromZoom)) * eased);
        float centerX = rect.screenX() + rect.width() / 2f, centerY = rect.screenY() + rect.height() / 2f;
        camera.zoomAt((float) (Math.log(zoom / camera.zoom()) / Math.log(1.2)), centerX, centerY);
        camera.centerOn(fromX + (toX - fromX) * eased, fromY + (toY - fromY) * eased);
        if (t >= 1f) flying = false;
    }

    /**
     * The hull herself, darkened, under the deck.
     *
     * <p>Drawn first so the compartments sit on top of her: what shows is the
     * art around and between the rooms, which is where a hull's shape actually
     * lives. A backdrop over the deck would be a picture of a ship with a plan
     * hidden behind it.
     *
     * <p>Scaled to the deck's own extent, and turned to match. The sprite's
     * long axis is her length and the deck's long axis is the same length, so
     * the two agree by construction rather than by being nudged into place.
     *
     * <p><b>Placed through the camera, then converted back out of it.</b> The
     * deck is projected in host pixels with Y running up; this is drawn with a
     * canvas primitive, in surface units with Y running down. Reading the
     * camera's answer straight into a canvas call — which is what this did —
     * lands the hull correctly only while the camera sits exactly centered, and
     * thereafter slides her against the deck at a rate set by the stretch, and
     * the wrong way entirely in the vertical.
     */
    private void drawSelection(CanvasContext context, CanvasHostViewport rect) {
        if (selectedRoom == null) return;
        var room = selectedRoom;
        float x = rect.canvasXForScreen(camera.cellToScreenX(room.left()));
        float y = rect.canvasYForScreen(camera.cellToScreenY(room.bottom() + 1));
        context.strokeRect(x, y, room.width() * camera.cellPxSize() / rect.scaleX(),
                room.depth() * camera.cellPxSize() / rect.scaleY(), new Color(0xC8, 0xB4, 0x73), 2f);
    }

    private void drawHull(CanvasContext context, CanvasHostViewport rect,
                          int cellsAcross, int cellsDown) {
        String hullArt = ship.ship() == null ? null : ship.ship().art();
        if (hullArt == null || hullArt.isBlank()) return;
        float cell = camera.cellPxSize();
        // The extents are divided by the stretch a backend applies to the
        // sprite's own axes, which is before it is turned — so her beam, which
        // ends up vertical on screen, is the one carrying the horizontal one.
        context.sprite(hullArt, null,
                rect.canvasXForScreen(camera.cellToScreenX(cellsAcross * 0.5f)),
                rect.canvasYForScreen(camera.cellToScreenY(cellsDown * 0.5f)),
                cellsDown * cell * HULL_OVERHANG / rect.scaleX(),
                cellsAcross * cell * HULL_OVERHANG / rect.scaleY(),
                BOW_LEFT, HULL);
    }
}
