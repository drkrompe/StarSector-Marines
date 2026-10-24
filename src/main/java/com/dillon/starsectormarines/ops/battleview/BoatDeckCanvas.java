package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.AirAppearance;
import com.dillon.starsectormarines.battle.air.BayDoor;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.ship.BayAperture;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;
import com.dillon.starsectormarines.ui.retained.UiElement;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.function.IntPredicate;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The company ship's own hangar, with her boats standing in it.
 *
 * <p>The room rather than a diagram of it: the deck scene draws the bay, and
 * the boats on screen are the aircraft the ship actually keeps in her berths,
 * turned round by her own hands. What this adds is the one thing a render
 * cannot say — which berth the player has selected — so the marks are drawn
 * over the floor and the boats are drawn over the marks.
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
    private static final Color LEAF = new Color(0x3A, 0x44, 0x4C);
    private static final Color LEAF_SEAM = new Color(0x22, 0x29, 0x2F);
    private static final Color LEAF_RIB = new Color(0x5C, 0x69, 0x72);
    private static final Color HAZARD = new Color(0xE8, 0xB4, 0x3A);
    private static final Color HAZARD_DARK = new Color(0x1A, 0x1A, 0x16);
    private static final Color MOVING = new Color(0xFF, 0x6A, 0x3A, 230);
    private static final Color TURRET = new Color(0xE2, 0x8F, 0x4A, 235);
    /** How far a turret's traverse wedge is drawn out from its mount, in cells. */
    private static final float TRAVERSE_CELLS = 2.2f;

    /**
     * A mount's traverse as a wedge in deck cells. The hull's nose is its
     * local {@code +Y}, which the parked facing turns into the world, and a
     * spec angle swings toward port, so the wedge's middle is the nose bearing
     * turned by the mount's own angle.
     */
    private static void traverse(CanvasContext context, Projection p, float worldX, float worldY,
                                 float noseDegrees, float arcDeg, float reach, Color tone, boolean lit) {
        float half = Math.min(180f, Math.max(4f, arcDeg * 0.5f));
        int steps = Math.max(6, Math.round(half / 6f));
        Color fill = BoatDollPanel.withAlpha(tone, lit ? 60 : 34);
        Color edge = BoatDollPanel.withAlpha(tone, lit ? 220 : 140);
        float cx = p.x(worldX), cy = p.y(worldY);
        float[] previous = null;
        for (int i = -steps; i <= steps; i++) {
            // Nose bearing in the world: the local +Y axis turned by the parked facing.
            double bearing = Math.toRadians(noseDegrees + half * i / steps);
            float ex = p.x(worldX - (float) Math.sin(bearing) * reach);
            float ey = p.y(worldY + (float) Math.cos(bearing) * reach);
            if (previous != null) {
                context.fillQuad(cx, cy, previous[0], previous[1], ex, ey, cx, cy, fill);
                context.line(previous[0], previous[1], ex, ey, edge, 1.2f);
            }
            if (i == -steps || i == steps) context.line(cx, cy, ex, ey, edge, 1f);
            previous = new float[]{ex, ey};
        }
    }
    /** How thick a leaf is through the hull, in cells. */
    private static final float LEAF_DEPTH = 1.0f;
    /** Width of the striped band on each leaf's meeting edge, in cells. */
    private static final float HAZARD_BAND = 0.45f;

    private static final EnumSet<RenderLayer> BACKDROP_LAYERS = EnumSet.of(
            RenderLayer.GROUND, RenderLayer.DOODADS);
    private static final EnumSet<RenderLayer> ACTOR_LAYERS = EnumSet.of(
            RenderLayer.UNITS, RenderLayer.SHUTTLES, RenderLayer.AIRCRAFT_LIGHTS, RenderLayer.SHOTS);

    private final CompanyDeck ship;
    private final Supplier<DeckGraph.Compartment> bay;
    private final IntSupplier selectedBerth;
    private final IntSupplier berthOffset;
    private final IntPredicate berthHeld;
    private List<BerthTarget> targets = List.of();
    private List<BerthTarget> recoveryTargets = List.of();
    private IntFunction<RecoveryMarker> recovery = index -> null;

    /** Campaign condition, independent of the decorative aircraft's tactical health. */
    public record RecoveryMarker(float condition, float daysRemaining) { }

    public BoatDeckCanvas recovery(IntFunction<RecoveryMarker> recovery) {
        this.recovery = Objects.requireNonNull(recovery);
        return this;
    }
    private int hovered = -1;
    private Supplier<BoatDoll> doll = () -> null;
    private DoubleSupplier seconds = () -> 0d;
    private List<BoatDollPanel.CardTarget> cards = List.of();
    private String hoveredCard;

    /**
     * Where the camera is and where it is going, eased the way the Mech Lab's
     * is. {@code focus} runs from the wide shot of the bay at 0 to one berth
     * filling the right of the panel at 1; the look-at point travels with it.
     */
    private float lookX = Float.NaN, lookY = Float.NaN, focus;
    private float fromX, fromY, fromFocus, toX, toY, toFocus;
    private float transition = TRANSITION_SECONDS;
    static final float TRANSITION_SECONDS = 0.72f;
    /** How much of the right-hand view a focused berth fills. */
    private static final float FOCUS_FILL = 0.82f;
    /** Cells of deck kept round a focused berth so the boat is not cropped at its wingtips. */
    private static final float FOCUS_SURROUND = 3f;

    /**
     * The selected boat as the doll draws her, or null while the room is not
     * opened on one. While it answers, the camera closes on her berth and the
     * board stands over the left of the panel.
     */
    public BoatDeckCanvas doll(Supplier<BoatDoll> doll, DoubleSupplier seconds) {
        this.doll = Objects.requireNonNull(doll);
        this.seconds = Objects.requireNonNull(seconds);
        return this;
    }

    /** Puts the camera where it is going without easing, for a room opened on a boat. */
    public void snap() {
        retarget();
        lookX = toX;
        lookY = toY;
        focus = toFocus;
        transition = TRANSITION_SECONDS;
    }

    /** Eases the camera toward the selected berth, or back out to the bay. Returns whether it moved. */
    public boolean advance(float dt) {
        float wasX = lookX, wasY = lookY, wasFocus = focus;
        if (!retarget()) return false;
        if (Float.isNaN(lookX)) {
            snap();
            return true;
        }
        transition = Math.min(TRANSITION_SECONDS, transition + Math.max(0f, Float.isFinite(dt) ? dt : 0f));
        float linear = transition / TRANSITION_SECONDS;
        float eased = linear * linear * (3f - 2f * linear);
        lookX = fromX + (toX - fromX) * eased;
        lookY = fromY + (toY - fromY) * eased;
        focus = fromFocus + (toFocus - fromFocus) * eased;
        return lookX != wasX || lookY != wasY || focus != wasFocus;
    }

    /** Sets where the camera is going; false when there is no bay to look at. */
    private boolean retarget() {
        ShipDeckBattleScene aboard = ship.drawable();
        DeckGraph.Compartment hangar = aboard != null ? bay.get() : null;
        if (hangar == null) return false;
        ShipDeckBattleScene.RoomView wide = hangarView(hangar, aboard.bayDoor(hangar));
        Gantry focused = focusedBerth(aboard, hangar);
        float x = focused != null ? focused.worldCenterX() : wide.centerCellX();
        float y = focused != null ? focused.worldCenterY() : wide.centerCellY();
        float f = focused != null ? 1f : 0f;
        if (x != toX || y != toY || f != toFocus || Float.isNaN(lookX)) {
            fromX = Float.isNaN(lookX) ? x : lookX;
            fromY = Float.isNaN(lookY) ? y : lookY;
            fromFocus = focus;
            toX = x;
            toY = y;
            toFocus = f;
            transition = 0f;
        }
        return true;
    }

    /** The berth on screen the doll is about, or null while the room shows the whole bay. */
    private Gantry focusedBerth(ShipDeckBattleScene aboard, DeckGraph.Compartment hangar) {
        if (doll.get() == null) return null;
        List<Gantry> berths = boatBerthsIn(aboard, hangar);
        int local = selectedBerth.getAsInt() - berthOffset.getAsInt();
        return local >= 0 && local < berths.size() ? berths.get(local) : null;
    }

    /** The doll card at this canvas-local point, by its slot key, or null. */
    public String slotAt(float canvasX, float canvasY) {
        if (!Float.isFinite(canvasX) || !Float.isFinite(canvasY)) return null;
        for (BoatDollPanel.CardTarget card : cards) {
            if (card.contains(canvasX, canvasY)) return card.key();
        }
        return null;
    }

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

    /** Keep annotations in document pixels rather than stretching their fonts with the camera. */
    public static void fitSurface(UiElement canvas) {
        if (canvas.box() == null) return;
        int width = Math.max(1, Math.round(canvas.box().contentBox().width()));
        int height = Math.max(1, Math.round(canvas.box().contentBox().height()));
        if (width != canvas.canvasWidth() || height != canvas.canvasHeight()) canvas.canvasSize(width, height);
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
        ShipDeckBattleScene aboard = ship.drawable();
        DeckGraph.Compartment hangar = aboard != null ? bay.get() : null;
        if (hangar == null) {
            targets = List.of();
            recoveryTargets = List.of();
            context.fillRect(0f, 0f, width, height, BACKGROUND);
            return;
        }
        BayAperture aperture = aboard.bayDoor(hangar);
        ShipDeckBattleScene.RoomView wide = hangarView(hangar, aperture);
        if (Float.isNaN(lookX)) snap();
        Gantry focused = focusedBerth(aboard, hangar);
        ShipDeckBattleScene.RoomView view = framing(aboard, wide, focused, width, height);

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
            recoveryTargets = List.of();
            context.fillRect(0f, 0f, width, height, BACKGROUND);
            return;
        }

        Projection projection = Projection.forHost(aboard, view, host[0]);
        space(context, projection, aboard.simulation().getTopology());
        if (aperture != null) threshold(context, projection, aperture, aboard.bayDoorLeaves(hangar));
        markBerths(context, projection, boatBerthsIn(aboard, hangar));
        context.hostPass(aboard.pass(view, ACTOR_LAYERS));
        BoatDoll shown = doll.get();
        if (shown != null && focused != null && focus > 0.5f) {
            markTurrets(context, projection, focused, shown);
            cards = BoatDollPanel.draw(context, shown, width, height, hoveredCard, seconds.getAsDouble());
            recoveryTargets = List.of();
        } else {
            cards = List.of();
            markRecovery(context);
        }
    }

    /**
     * The bay as the camera sees it this frame: the whole hangar, eased in
     * toward the focused berth and slid right so the boat stands clear of the
     * board, by however far {@link #focus} has got.
     */
    private ShipDeckBattleScene.RoomView framing(ShipDeckBattleScene aboard, ShipDeckBattleScene.RoomView wide,
                                                 Gantry focused, float width, float height) {
        if (!(focus > 0f) || !(width > 0f) || !(height > 0f)) return wide;
        float notches = focus * focusNotches(wide, focused, width, height);
        ShipDeckBattleScene.RoomView closer = wide.lookingAt(lookX, lookY, notches);
        float cellPx = aboard.cameraFor(closer, 0f, 0f, width, height).cellPxSize();
        if (!(cellPx > 0f)) return closer;
        float shift = focus * ((1f + BoatDollPanel.SHARE) * 0.5f - 0.5f);
        return wide.lookingAt(lookX - shift * width / cellPx, lookY, notches);
    }

    /** Wheel notches in from the bay's fitted scale at which one berth fills the view beside the board. */
    private static float focusNotches(ShipDeckBattleScene.RoomView wide, Gantry berth, float width, float height) {
        if (berth == null) return 0f;
        float fitted = wide.fittedCellPx(width, height);
        float room = Math.min(width * (1f - BoatDollPanel.SHARE), height) * FOCUS_FILL;
        float across = Math.max(berth.width(), berth.height()) + FOCUS_SURROUND * 2f;
        float wanted = room / across;
        if (!(fitted > 0f) || !(wanted > fitted)) return 0f;
        return (float) (Math.log(wanted / fitted) / Math.log(1.2));
    }

    /**
     * Each hardpoint on the boat standing in the focused berth: where its gun
     * sits on the hull and which way it can turn. Placed the way a parked
     * airframe is drawn, at the berth's centre and facing and at ground scale,
     * so the mark lands on the hull's own painted mount, and its traverse is a
     * wedge about the mount's facing — port positive off the nose, as the spec
     * writes it.
     */
    private static void markTurrets(CanvasContext context, Projection p, Gantry berth, BoatDoll doll) {
        float facing = (float) Math.toRadians(berth.facing.degrees());
        float cos = (float) Math.cos(facing);
        float sin = (float) Math.sin(facing);
        float originX = berth.centerX + 0.5f;
        float originY = berth.centerY + 0.5f;
        for (BoatDoll.Slot slot : doll.slots()) {
            if (slot.kind() != BoatDoll.Kind.HARDPOINT || !slot.placed()) continue;
            float lx = slot.localX() * AirAppearance.GROUND_SCALE;
            float ly = slot.localY() * AirAppearance.GROUND_SCALE;
            float worldX = originX + lx * cos - ly * sin;
            float worldY = originY + lx * sin + ly * cos;
            float x = p.x(worldX);
            float y = p.y(worldY);
            Color tone = slot.selected() ? MARK : TURRET;
            traverse(context, p, worldX, worldY, berth.facing.degrees() + slot.facingDeg(), slot.arcDeg(),
                    TRAVERSE_CELLS, tone, slot.selected());
            BoatDollPanel.ring(context, x, y, slot.selected() ? 9f : 7f, new Color(0, 0, 0, 190), 4f);
            BoatDollPanel.ring(context, x, y, slot.selected() ? 9f : 7f, tone, 2f);
            String label = Strings.format("boatDeckHardpointShort", BoatDoll.hardpointOf(slot.key()) + 1);
            context.fillRect(x + 11f, y - 9f, Fonts.ORBITRON_10.measureWidth(label) + 8f, 16f, new Color(8, 12, 14, 220));
            context.text(Fonts.ORBITRON_10, label, x + 15f, y - 7f, tone);
        }
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
                        && (topology.isWall(x, y)
                        || topology.getGroundKind(x, y) != CellTopology.GroundKind.VOID)) continue;
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

    /**
     * Navigation lights mark the real opening; the door's two leaves close it.
     *
     * <p>The leaves meet on the door's centre line and withdraw sideways into
     * pockets in the jambs, so only the part still across the opening is drawn
     * and the hull plating hides the rest. The blue line is the pressure screen,
     * which only shows where the leaves have left the opening uncovered.
     */
    private static void threshold(CanvasContext context, Projection p, BayAperture door, BayDoor leaves) {
        float tangentX = -door.outDy();
        float tangentY = door.outDx();
        float half = door.widthCells() * 0.5f;
        float x = door.centerX();
        float y = door.centerY();
        float travel = leaves == null ? 1f : leaves.leafTravel();
        float reach = half * (1f - travel);
        if (travel > 0f) {
            Color screen = new Color(THRESHOLD.getRed(), THRESHOLD.getGreen(), THRESHOLD.getBlue(),
                    Math.round(THRESHOLD.getAlpha() * travel));
            context.line(p.x(x - tangentX * half), p.y(y - tangentY * half),
                    p.x(x + tangentX * half), p.y(y + tangentY * half), screen, 2f);
        }
        if (reach > 0.02f) {
            for (int side : new int[]{-1, 1}) leaf(context, p, door, side, half, reach,
                    leaves != null && leaves.openness() > 0f && leaves.openness() < 1f);
        }
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

    /**
     * One leaf, standing from its jamb ({@code half} out along the door) in to
     * {@code reach} of it: a plated slab with ribs across it and a striped
     * meeting edge, which shows amber while the leaf is moving.
     */
    private static void leaf(CanvasContext context, Projection p, BayAperture door, int side,
                             float half, float reach, boolean moving) {
        float jamb = half * side;
        float edge = (half - reach) * side;
        float depth = LEAF_DEPTH * 0.5f;
        slab(context, p, door, jamb, edge, -depth, depth, LEAF);
        slab(context, p, door, jamb, edge, -depth, -depth + 0.08f, LEAF_SEAM);
        slab(context, p, door, jamb, edge, depth - 0.08f, depth, LEAF_SEAM);
        // Ribs at whole-cell spacing from the jamb, fixed to the leaf so they travel with it.
        for (float rib = 1f; rib < reach - HAZARD_BAND; rib += 1f) {
            float at = edge + rib * side;
            slab(context, p, door, at - 0.05f, at + 0.05f, -depth + 0.08f, depth - 0.08f, LEAF_RIB);
        }
        float band = Math.min(HAZARD_BAND, reach);
        float inner = edge + band * side;
        slab(context, p, door, edge, inner, -depth, depth, HAZARD_DARK);
        int stripes = 3;
        for (int stripe = 0; stripe < stripes; stripe++) {
            float o0 = -depth + (2f * depth) * stripe / stripes;
            float o1 = o0 + depth / stripes;
            slab(context, p, door, edge, inner, o0, o1, HAZARD);
        }
        if (moving) slab(context, p, door, edge, edge + 0.1f * side, -depth, depth, MOVING);
    }

    /**
     * Fills the door-aligned rectangle between {@code a0} and {@code a1} across
     * the opening and {@code o0} and {@code o1} out of the hull, both measured
     * in cells from the door's centre.
     */
    private static void slab(CanvasContext context, Projection p, BayAperture door,
                             float a0, float a1, float o0, float o1, Color color) {
        float tangentX = -door.outDy();
        float tangentY = door.outDx();
        float cx = door.centerX();
        float cy = door.centerY();
        float x0 = cx + tangentX * a0 + door.outDx() * o0, y0 = cy + tangentY * a0 + door.outDy() * o0;
        float x1 = cx + tangentX * a1 + door.outDx() * o0, y1 = cy + tangentY * a1 + door.outDy() * o0;
        float x2 = cx + tangentX * a1 + door.outDx() * o1, y2 = cy + tangentY * a1 + door.outDy() * o1;
        float x3 = cx + tangentX * a0 + door.outDx() * o1, y3 = cy + tangentY * a0 + door.outDy() * o1;
        context.fillQuad(p.x(x0), p.y(y0), p.x(x1), p.y(y1), p.x(x2), p.y(y2), p.x(x3), p.y(y3), color);
    }

    /** Records hover over the berths drawn during the last frame. */
    public void pointAt(float canvasX, float canvasY) {
        hoveredCard = slotAt(canvasX, canvasY);
        hovered = hoveredCard != null ? -1 : localBerthAt(canvasX, canvasY);
    }

    /**
     * The campaign deck's index for the berth at this canvas-local point, or -1
     * outside every berth. Projected from the last frame's framing, which is
     * the one the player clicked on.
     */
    public int berthAt(float canvasX, float canvasY) {
        if (slotAt(canvasX, canvasY) != null) return -1;
        int local = localBerthAt(canvasX, canvasY);
        return local < 0 ? -1 : berthOffset.getAsInt() + local;
    }

    private int localBerthAt(float canvasX, float canvasY) {
        if (!Float.isFinite(canvasX) || !Float.isFinite(canvasY)) return -1;
        for (BerthTarget target : recoveryTargets) {
            if (target.contains(canvasX, canvasY)) return target.index();
        }
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

    /** A hull bar and campaign clock beside the actual aircraft, also a berth click target. */
    private void markRecovery(CanvasContext context) {
        List<BerthTarget> badges = new ArrayList<>();
        int offset = berthOffset.getAsInt();
        for (BerthTarget berth : targets) {
            RecoveryMarker state = recovery.apply(offset + berth.index());
            if (state == null || (state.daysRemaining() <= 0f
                    && offset + berth.index() != selectedBerth.getAsInt())) continue;
            boolean grounded = state.daysRemaining() > 0f;
            Color ink = grounded ? MARK : HOVER;
            String title = Strings.get(grounded ? "boatSceneGrounded" : "boatReady");
            String hull = Strings.format("boatSceneHull", Math.round(state.condition() * 100f));
            String clock = grounded ? Strings.format("boatSceneCountdown", Math.ceil(state.daysRemaining() * 10f) / 10d) : "";
            float width = Math.max(220f, Math.max(Fonts.INSIGNIA_15_AA.measureWidth(title),
                    Math.max(Fonts.INSIGNIA_15_AA.measureWidth(hull),
                            Fonts.INSIGNIA_15_AA.measureWidth(clock))) + 62f);
            width = Math.min(width, context.metrics().surfaceWidth() - 16f);
            float x = Math.max(8f, Math.min(context.metrics().surfaceWidth() - width - 8f,
                    berth.x() + berth.width() * .5f - width * .5f));
            float y = Math.min(context.metrics().surfaceHeight() - 92f, berth.y() + berth.height() + 56f);
            context.line(berth.x() + berth.width() * .5f, berth.y() + berth.height(),
                    x + width * .5f, y, ink, 1f);
            context.fillRect(x, y, width, 84f, new Color(10, 18, 22, 240));
            context.strokeRect(x, y, width, 84f, ink, 1f);
            context.text(Fonts.INSIGNIA_15_AA, title, x + 12f, y + 8f, ink);
            context.text(Fonts.INSIGNIA_15_AA, hull, x + 12f, y + 27f, Color.LIGHT_GRAY);
            context.fillRect(x + 12f, y + 46f, width - 24f, 5f, new Color(48, 61, 66));
            context.fillRect(x + 12f, y + 46f, (width - 24f) * state.condition(), 5f, ink);
            if (grounded) {
                float cx = x + 19f, cy = y + 67f, radius = 7f;
                for (int segment = 0; segment < 32; segment++) {
                    double a = segment * Math.PI / 16, b = (segment + 1) * Math.PI / 16;
                    context.line(cx + radius * (float) Math.cos(a), cy + radius * (float) Math.sin(a),
                            cx + radius * (float) Math.cos(b), cy + radius * (float) Math.sin(b), ink, 1f);
                }
                context.line(cx, cy, cx, cy - 5f, ink, 1f);
                context.line(cx, cy, cx + 4f, cy + 2f, ink, 1f);
                context.text(Fonts.INSIGNIA_15_AA, clock, x + 36f, y + 59f, ink);
            }
            badges.add(new BerthTarget(berth.index(), x, y, width, 84f));
        }
        recoveryTargets = List.copyOf(badges);
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
