package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.ops.fitting.BoardHover;
import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketType;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.vehicle.VehicleGunCard;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.marine.CampaignMech;
import com.dillon.starsectormarines.marine.CampaignMechSquad;
import com.dillon.starsectormarines.marine.MechFrameBudget;
import com.dillon.starsectormarines.marine.MechCompartment;
import com.dillon.starsectormarines.marine.MechInternal;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;
import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Canonically composed lance inside a top-down battle-tileset fabrication garage. */
public final class MechLabDollCanvas implements CanvasProducer {

    private static final String ROOT = "graphics/battle/mech-modular-topdown/";
    private static final Color BACKGROUND = new Color(0x06, 0x0A, 0x10);
    private static final Color WHITE = Color.WHITE;
    private static final Color BUILD_WASH = new Color(0xe2, 0xb3, 0x4a, 22);
    private static final Color BUILD_WASH_HOT = new Color(0xe2, 0xb3, 0x4a, 52);
    private static final Color BUILD_EDGE = new Color(0xe2, 0xb3, 0x4a, 190);
    private static final Color BUILD_HOT = new Color(0xff, 0xd4, 0x64, 255);
    private static final Color LETTER_PLATE = new Color(0xe2, 0xb3, 0x4a, 235);
    private static final Color LETTER_RIM = new Color(0x14, 0x11, 0x0a, 200);
    private static final Color LETTER_INK = new Color(0x14, 0x11, 0x0a, 255);
    private final SvgAsset buildMark = SvgAsset.load("graphics/ui/mech-hardware/fab.svg");
    /** A previewed machine is drawn a little translucent: it is not built yet. */
    private static final float PREVIEW_ALPHA = 0.82f;
    private Supplier<Object> padPreview;
    private CampaignMech previewMech;
    private MechChassisPreviewCanvas previewMechArt;
    private VehicleType previewVehicleType;
    private VehiclePreviewCanvas previewVehicleArt;
    private static final float VACANT_PAD_INSET_RATIO = 0.045f;

    private static final EnumSet<RenderLayer> BACKDROP_LAYERS = EnumSet.of(
            RenderLayer.GROUND, RenderLayer.DOODADS);
    /** The machines on the pads: mechs are units, a company vehicle a live hull on the convoy layer. */
    private static final EnumSet<RenderLayer> ACTOR_LAYERS = EnumSet.of(
            RenderLayer.UNITS, RenderLayer.VEHICLES, RenderLayer.CONVOY);

    private final Supplier<List<MechDeploymentSpec>> deployments;
    private final IntSupplier selectedGantry;
    private final Supplier<SocketId> selectedSocket;
    private final Supplier<LayeredMechAssets> assets;
    private final BooleanSupplier fittingOverlaysVisible;
    /**
     * The ship the lab is aboard.
     *
     * <p>May supply null before she is built, and then the panel composes over
     * a plain ground rather than over a substitute room. There is no second
     * garage to fall back to: a fallback is a second answer to what the bay
     * looks like, and the one time it was consulted - a headless UI snapshot -
     * it was the only answer anybody ever saw.
     */
    private final Supplier<ShipDeckBattleScene> ship;
    private final Supplier<ShipDeckBattleScene.RoomView> roomView;
    private final Supplier<List<Gantry>> berths;
    private final IntSupplier workSite;
    private final DoubleSupplier elapsedSeconds;
    /** Which pads hold a mech or a vehicle; the doll draws mechs only, so the lance alone cannot say. */
    private IntPredicate occupiedPads;
    private DoubleSupplier sideShift;
    private final Supplier<MechWeaponComponent> candidateWeapon;
    /** Every pad a press selects: vacant pads always, occupied ones while the lance is in overview. */
    private List<GantryTarget> gantryTargets = List.of();
    private List<SocketDropTarget> lastSocketTargets = List.of();
    /** Each compartment part's box from the last frame, {left, top, right, bottom}, for lifting it off. */
    private Map<MechCompartment.Placement, float[]> lastPartBoxes = Map.of();
    private DragPreview dragPreview;
    private SocketId hoveredSocket;
    /** The fitted part under the pointer in the last frame, or null. */
    private PointedPart pointed;
    /** The pointer on the board, shared with the room's drag; @see BoardHover */
    private BoardHover hover = new BoardHover();
    private Function<SocketId, MechInternal> installedInternals = socket -> null;
    private Function<SocketId, MechWeaponComponent> installedWeapons = socket -> null;
    private Supplier<List<MechCompartment.Placement>> installedCompartment = List::of;
    private Supplier<VehicleType> selectedVehicle = () -> null;
    private BooleanSupplier machineArmed = () -> true;
    private BooleanSupplier machineUnarmed = () -> false;
    private BooleanSupplier vehicleOwnGun = () -> false;

    public void previewWeaponDrag(MechWeaponComponent component, SocketId source,
                                  SocketId target, boolean valid) {
        dragPreview = component != null ? new DragPreview(component, null, source, target, valid) : null;
    }

    /**
     * An internal carried over the doll: the target socket is lit as a fit or a refusal, and an
     * internal it takes is drawn snapped into it as a weapon is.
     */
    public void previewPartDrag(MechInternal internal, SocketId source, SocketId target, boolean valid) {
        dragPreview = new DragPreview(null, internal, source, target, valid);
    }

    public void clearWeaponDragPreview() { dragPreview = null; }

    /**
     * Whether the doll draws the carried part in the socket under the pointer, so the room
     * draws no ghost on the pointer; otherwise the ghost carries it.
     */
    public boolean carriedSnapped() {
        return dragPreview != null && snaps(dragPreview.target(), dragPreview.source(), dragPreview.valid());
    }

    /**
     * The one rule for where a carried part is drawn: snapped into the socket under the pointer
     * when it would go there on the drop, or back where it came from; anywhere else, on the
     * pointer. A part is never drawn nowhere.
     */
    static boolean snaps(SocketId target, SocketId source, boolean fits) {
        return target != null && (fits || target == source);
    }

    /**
     * The compartment as it would stand with {@code part} dropped on {@code bay}, its placement last: every fitted part
     * where it sits, the new one where the drop puts it. Null when it would not fit.
     */
    static List<MechCompartment.Placement> packedWith(List<SocketDef> grids, List<MechCompartment.Placement> installed,
                                                      MechInternal part, SocketId bay) {
        List<MechInternal> parts = new ArrayList<>();
        List<MechCompartment.Spot> wanted = new ArrayList<>();
        for (MechCompartment.Placement placed : installed) {
            parts.add(placed.part());
            wanted.add(placed.spot());
        }
        parts.add(part);
        wanted.add(MechCompartment.Spot.in(bay));
        return MechCompartment.arrange(grids, parts, wanted);
    }

    /**
     * What each internal socket of the selected machine holds. The frozen deployment
     * carries the frame's values, not which parts produced them, so the lab supplies the
     * names its wells show; without a source the wells show only their socket type.
     */
    public void installedInternals(Function<SocketId, MechInternal> source) {
        installedInternals = source != null ? source : socket -> null;
    }

    /** Whether the selected vehicle's empty gun card still fires the gun the hull was built with. */
    /** Whether the machine on the doll has no weapon anywhere, so its empty weapon points warn in yellow. */
    public void machineUnarmed(BooleanSupplier source) {
        machineUnarmed = source != null ? source : () -> false;
    }

    public void vehicleOwnGun(BooleanSupplier source) {
        vehicleOwnGun = source != null ? source : () -> false;
    }

    /** The weapon in the selected vehicle's gun card; null for an empty card, and for a mech. */
    public void installedWeapons(Function<SocketId, MechWeaponComponent> source) {
        installedWeapons = source != null ? source : socket -> null;
    }

    /** What the selected machine's auxiliary compartment holds, each part where it sits, in fitted order. */
    public void installedCompartment(Supplier<List<MechCompartment.Placement>> source) {
        installedCompartment = source != null ? source : List::of;
    }

    /** The selected vehicle's hull, for its sealed doll; null while a mech or nothing is selected. */
    public void selectedVehicle(Supplier<VehicleType> source) {
        selectedVehicle = source != null ? source : () -> null;
    }

    /** Whether the machine on the doll carries a gun, so a compartment with no capacitor bank keeps it off the drop. */
    public void machineArmed(BooleanSupplier source) {
        machineArmed = source != null ? source : () -> true;
    }

    private record DragPreview(MechWeaponComponent component, MechInternal internal, SocketId source,
                               SocketId target, boolean valid) { }

    private int hoveredGantry = -1;

    public MechLabDollCanvas(Supplier<List<MechDeploymentSpec>> deployments,
                             IntSupplier selectedGantry,
                             Supplier<SocketId> selectedSocket,
                             Supplier<LayeredMechAssets> assets,
                             BooleanSupplier fittingOverlaysVisible,
                             Supplier<ShipDeckBattleScene> ship,
                             Supplier<ShipDeckBattleScene.RoomView> roomView,
                             Supplier<List<Gantry>> berths,
                             IntSupplier workSite,
                             DoubleSupplier elapsedSeconds) {
        this(deployments, selectedGantry, selectedSocket, assets, fittingOverlaysVisible, ship, roomView, berths,
                workSite, elapsedSeconds, () -> null);
    }

    public MechLabDollCanvas(Supplier<List<MechDeploymentSpec>> deployments,
                             IntSupplier selectedGantry,
                             Supplier<SocketId> selectedSocket,
                             Supplier<LayeredMechAssets> assets,
                             BooleanSupplier fittingOverlaysVisible,
                             Supplier<ShipDeckBattleScene> ship,
                             Supplier<ShipDeckBattleScene.RoomView> roomView,
                             Supplier<List<Gantry>> berths,
                             IntSupplier workSite,
                             DoubleSupplier elapsedSeconds,
                             Supplier<MechWeaponComponent> candidateWeapon) {
        if (deployments == null || selectedGantry == null || selectedSocket == null
                || assets == null
                || fittingOverlaysVisible == null
                || ship == null || roomView == null || berths == null || workSite == null
                || elapsedSeconds == null || candidateWeapon == null) {
            throw new IllegalArgumentException(
                    "deployments, gantry, socket, mech assets, a ship, work site, candidate weapon and elapsed time are required");
        }
        this.deployments = deployments;
        this.selectedGantry = selectedGantry;
        this.selectedSocket = selectedSocket;
        this.assets = assets;
        this.fittingOverlaysVisible = fittingOverlaysVisible;
        this.ship = ship;
        this.roomView = roomView;
        this.berths = berths;
        this.workSite = workSite;
        this.elapsedSeconds = elapsedSeconds;
        this.candidateWeapon = candidateWeapon;
    }

    @Override
    public void draw(CanvasContext context) {
        pointed = null;
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        List<MechDeploymentSpec> lance = deployments.get();
        int gantryIndex = selectedIndex(selectedGantry.getAsInt());
        MechDeploymentSpec selected = gantryIndex < lance.size() ? lance.get(gantryIndex) : null;
        MechVariant selectedVariant = selected != null ? selected.variant() : null;
        if (assets.get() == null) {
            lastSocketTargets = List.of();
            return;
        }

        ShipDeckBattleScene aboard = ship.get();
        ShipDeckBattleScene.RoomView bay = aboard != null ? shifted(aboard, roomView.get(), width, height) : null;
        if (bay == null) {
            gantryTargets = List.of();
            lastSocketTargets = List.of();
            context.fillRect(0f, 0f, width, height, BACKGROUND);
            return;
        }
        CanvasHostViewport[] host = new CanvasHostViewport[1];
        BattleSceneHostPass backdrop = aboard.pass(bay, BACKDROP_LAYERS);
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
            gantryTargets = List.of();
            lastSocketTargets = List.of();
            context.fillRect(0f, 0f, width, height, BACKGROUND);
            return;
        }

        List<Gantry> standing = berths.get();
        BattleCamera sceneCamera = aboard.cameraFor(
                bay, 0f, 0f, host[0].width(), host[0].height());

        context.hostPass(aboard.pass(bay, ACTOR_LAYERS));
        drawVacantGantryActions(context, sceneCamera, host[0], standing,
                occupiedPads != null ? occupiedPads
                        : pad -> pad < lance.size() && lance.get(pad) != null,
                !fittingOverlaysVisible.getAsBoolean());
        VehicleType vehicle = selected == null ? selectedVehicle.get() : null;
        if (selected != null && fittingOverlaysVisible.getAsBoolean()) {
            MechFittingLayout layout = MechFittingLayout.forVariant(selectedVariant);
            drawSchematic(context, layout.sockets(), null, id -> weaponAt(selected, id),
                    MechLabDollCanvas::isSocketCompatible, id -> HardpointBricks.tone(layout.socket(id).type()),
                    width, height);
        } else if (vehicle != null && fittingOverlaysVisible.getAsBoolean()) {
            VehicleGunCard card = VehicleGunCard.of(vehicle);
            drawSchematic(context, VehicleDoll.sockets(vehicle), vehicle, installedWeapons,
                    (socket, weapon) -> socket.id() == SocketId.ARMS && card != null && card.accepts(weapon),
                    id -> VehicleDoll.tone(vehicle, id), width, height);
        } else {
            lastSocketTargets = List.of();
            Object preview = padPreview != null && fittingOverlaysVisible.getAsBoolean() ? padPreview.get() : null;
            if (preview instanceof MechVariant pattern) drawPatternSchematic(context, pattern, width, height);
            else if (preview instanceof VehicleType type) {
                drawBareVehicle(context, type, panelWidth(VehicleDoll.sockets(type), width), height, elapsedSeconds.getAsDouble(), true);
            }
        }
    }

    /**
     * A previewed pattern's doll, where a built mech's stands: the same schematic on the left of
     * the view, as the chassis is bought: a bare frame, its engine and capacitor sockets warning
     * empty and only factory-locked hardware fitted. Read-only; a vehicle pattern draws its bare
     * hull's ({@link #drawBareVehicle}).
     */
    private void drawPatternSchematic(CanvasContext c, MechVariant variant, float width, float height) {
        drawBareChassis(c, variant, panelWidth(MechFittingLayout.forVariant(variant).sockets(), width), height,
                assets.get(), elapsedSeconds.getAsDouble(), true);
    }

    /**
     * A chassis's doll as the lab shows a bought pattern and the company store shows a selected one:
     * the drafting panel, the sockets laid out and wired to the engine core, and each card as it stands
     * on a bare frame. {@code assets} may be null where no machine art is loaded; only a part
     * being carried over the doll needs it.
     */
    static void drawBareChassis(CanvasContext c, MechVariant variant, float panelWidth, float height,
                                LayeredMechAssets assets, double seconds, boolean warnMissing) {
        MechFrameBudget.Fit frame = MechFrameBudget.Fit.frame(variant);
        MechFittingLayout layout = MechFittingLayout.forVariant(variant);
        drawSchematicPanel(c, panelWidth, height);
        List<SocketDropTarget> targets = schematicTargets(layout.sockets(), panelWidth, height);
        drawWires(c, targets, id -> HardpointBricks.tone(layout.socket(id).type()));
        for (SocketDropTarget target : targets) {
            SocketDef socket = layout.socket(target.id());
            MechMountSlot mount = mountFor(socket.id());
            MechSocketConsole.draw(c, target, socket, mount != null ? frame.weaponAt(mount) : null, null,
                    null, false,
                    socket.id().compartment() ? List.of() : null, null, assets,
                    false, false, false, false, false, seconds,
                    new MechSocketConsole.Warnings(warnMissing, true, warnMissing), null);
        }
    }

    /**
     * A vehicle hull's doll as the hull is bought
     * ({@link com.dillon.starsectormarines.marine.CampaignVehicle#hull}), laid out as the lab lays out a
     * selected vehicle's ({@link VehicleDoll}): the engine core, armor point, gun card and auxiliary compartment all
     * empty. {@code warnMissing} marks what keeps the hull off the manifest, as on a chassis.
     */
    static void drawBareVehicle(CanvasContext c, VehicleType type, float panelWidth, float height, double seconds,
                                boolean warnMissing) {
        List<SocketDef> sockets = VehicleDoll.sockets(type);
        drawSchematicPanel(c, panelWidth, height);
        List<SocketDropTarget> targets = schematicTargets(sockets, panelWidth, height);
        drawWires(c, targets, id -> VehicleDoll.tone(type, id));
        MechSocketConsole.Warnings warnings = new MechSocketConsole.Warnings(warnMissing, type.hasTurretWeapon(), warnMissing);
        for (SocketDropTarget target : targets) {
            SocketDef socket = socketById(sockets, target.id());
            MechSocketConsole.draw(c, target, socket, null, null, null, false,
                    socket.id().compartment() ? List.of() : null, null, null,
                    false, false, false, false, false, seconds, warnings,
                    socket.factoryLocked() ? sealedOf(type, socket.id()) : null);
        }
    }

    /**
     * The schematic's wires: each card but the armor point wired back to the engine core in its own
     * tone, a mechanical tick where it meets the core.
     */
    static void drawWires(CanvasContext c, List<SocketDropTarget> targets, Function<SocketId, Color> tone) {
        SocketDropTarget core = null;
        for (SocketDropTarget target : targets) if (target.id() == SocketId.CORE) core = target;
        for (SocketDropTarget target : targets) {
            if (core == null || target == core || target.id() == SocketId.PLATING) continue;
            Color wire = tone.apply(target.id());
            List<float[]> path = wireToCore(target, core);
            for (int i = 0; i < path.size() - 1; i++) {
                float[] a = path.get(i), b = path.get(i + 1);
                c.line(a[0], a[1], b[0], b[1], new Color(0, 0, 0, 190), 5f);
                c.line(a[0], a[1], b[0], b[1], withAlpha(wire, 150), 2f);
            }
            float[] end = path.get(path.size() - 1);
            drawAnchorMechanicalTick(c, end[0], end[1], wire, false);
        }
    }

    /**
     * The framing moved so its subject stands {@link #sideShift} of the width right of centre: the
     * look-at point slides left by that many of the view's own cells.
     */
    private ShipDeckBattleScene.RoomView shifted(ShipDeckBattleScene aboard, ShipDeckBattleScene.RoomView view,
                                                 float width, float height) {
        float share = sideShift != null ? (float) sideShift.getAsDouble() : 0f;
        if (view == null || share == 0f || !(width > 0f) || !(height > 0f)) return view;
        float cellPx = aboard.cameraFor(view, 0f, 0f, width, height).cellPxSize();
        if (!(cellPx > 0f)) return view;
        return view.lookingAt(view.centerCellX() - share * width / cellPx, view.centerCellY(), view.zoomNotches());
    }

    /**
     * The fitted weapon or internal under the pointer and its box in canvas pixels, for the
     * room to open that part's spec sheet beside; null while a part is in hand or nothing
     * fitted is pointed at.
     */
    public PointedPart pointedPart() {
        return hover.names() ? pointed : null;
    }

    /** A fitted part on the board: one of {@code weapon} and {@code internal}, and where it is drawn. */
    public record PointedPart(MechWeaponComponent weapon, MechInternal internal,
                              float left, float top, float right, float bottom) { }

    /** The board's pointer, which the room's drag holds while a part is in hand. */
    public MechLabDollCanvas withHover(BoardHover hover) {
        this.hover = hover;
        return this;
    }

    /** Where the subject stands across the view, from the lab camera's eased pose. */
    public MechLabDollCanvas withSideShift(DoubleSupplier shift) {
        this.sideShift = shift;
        return this;
    }

    /** A pad that holds a vehicle is not vacant; the host says which are taken. */
    public MechLabDollCanvas withOccupiedPads(IntPredicate occupied) {
        this.occupiedPads = occupied;
        return this;
    }

    /** Records hover over the physical pad actions drawn during the last frame. */
    public void pointAt(float canvasX, float canvasY) {
        hover.at(canvasX, canvasY);
        hoveredGantry = gantryAt(canvasX, canvasY);
        hoveredSocket = socketAt(canvasX, canvasY);
    }

    /** The socket dock target at this canvas-local point, or null outside all sockets. */
    public SocketId socketAt(float canvasX, float canvasY) {
        if (!Float.isFinite(canvasX) || !Float.isFinite(canvasY)) return null;
        for (SocketDropTarget target : lastSocketTargets) {
            if (target.contains(canvasX, canvasY)) return target.id();
        }
        return null;
    }

    /** The compartment part drawn at this canvas-local point, or null. */
    public MechInternal partAt(float canvasX, float canvasY) {
        MechCompartment.Placement placed = placementAt(canvasX, canvasY);
        return placed != null ? placed.part() : null;
    }

    /** The compartment part drawn at this canvas-local point, where it sits, or null: two of a part are two places. */
    public MechCompartment.Placement placementAt(float canvasX, float canvasY) {
        for (Map.Entry<MechCompartment.Placement, float[]> part : lastPartBoxes.entrySet()) {
            float[] box = part.getValue();
            if (canvasX >= box[0] && canvasX <= box[2] && canvasY >= box[1] && canvasY <= box[3]) return part.getKey();
        }
        return null;
    }

    /**
     * The pad a press at this canvas-local point selects, or -1 outside every pad: a vacant pad
     * opens the pattern catalog, and in overview an occupied one opens its machine for fitting.
     */
    public int gantryAt(float canvasX, float canvasY) {
        if (!Float.isFinite(canvasX) || !Float.isFinite(canvasY)) return -1;
        for (GantryTarget target : gantryTargets) {
            if (target.contains(canvasX, canvasY)) return target.index();
        }
        return -1;
    }

    private void drawVacantGantryActions(CanvasContext context, BattleCamera camera,
                                          CanvasHostViewport viewport,
                                          List<Gantry> standing, IntPredicate occupied, boolean letters) {
        int count = Math.min(CampaignMechSquad.CAPACITY, standing.size());
        List<GantryTarget> targets = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            boolean held = occupied.test(index);
            if (held && !letters) continue;
            Gantry gantry = standing.get(index);
            PadBounds pad = padBounds(gantry);
            float left = camera.cellToScreenX(pad.left()) / viewport.scaleX();
            float right = camera.cellToScreenX(pad.right()) / viewport.scaleX();
            float top = (viewport.height() - camera.cellToScreenY(pad.top()))
                    / viewport.scaleY();
            float bottom = (viewport.height() - camera.cellToScreenY(pad.bottom()))
                    / viewport.scaleY();
            float width = Math.abs(right - left);
            float height = Math.abs(bottom - top);
            float inset = Math.max(4f, Math.min(width, height) * VACANT_PAD_INSET_RATIO);
            GantryTarget target = new GantryTarget(index,
                    Math.min(left, right) + inset, Math.min(top, bottom) + inset,
                    Math.max(1f, width - inset * 2f),
                    Math.max(1f, height - inset * 2f));
            if (!held) {
                targets.add(target);
                Object preview = index == selectedIndex(selectedGantry.getAsInt()) && padPreview != null
                        ? padPreview.get() : null;
                if (preview != null) {
                    drawPadPreview(context, target, preview, camera.cellPxSize() / viewport.scaleX());
                } else {
                    drawBuildMark(context, target, index == hoveredGantry);
                }
            } else {
                // In overview an occupied pad is the way into its machine, bracketed under the pointer.
                targets.add(target);
                if (index == hoveredGantry) {
                    float shortSide = Math.min(target.width(), target.height());
                    drawCornerBrackets(context, target.x(), target.y(), target.width(), target.height(),
                            BUILD_HOT, 3f, shortSide * 0.24f, Math.max(3f, shortSide * 0.06f));
                }
            }
            if (letters) drawPadLetter(context, target, index);
        }
        gantryTargets = List.copyOf(targets);
    }

    /**
     * A vacant pad is marked as a build site in the BUY amber: a faint wash, corner brackets and
     * the fabrication cog with BUILD beneath it, all brighter under the pointer.
     */
    private void drawBuildMark(CanvasContext context, GantryTarget target, boolean hot) {
        float shortSide = Math.min(target.width(), target.height());
        context.fillRect(target.x(), target.y(), target.width(), target.height(), hot ? BUILD_WASH_HOT : BUILD_WASH);
        Color edge = hot ? BUILD_HOT : BUILD_EDGE;
        drawCornerBrackets(context, target.x(), target.y(), target.width(), target.height(), edge,
                hot ? 3f : 2f, shortSide * 0.24f, Math.max(3f, shortSide * 0.06f));
        float cog = Math.min(56f, shortSide * 0.34f);
        boolean caption = shortSide >= 64f;
        float block = cog + (caption ? 6f + Fonts.ORBITRON_12_BOLD.getLineHeight() : 0f);
        float centerX = target.x() + target.width() * 0.5f;
        float top = target.y() + (target.height() - block) * 0.5f;
        context.svg(buildMark, centerX - cog * 0.5f, top, cog, cog, edge);
        if (caption) {
            String label = "BUILD";
            context.text(Fonts.ORBITRON_12_BOLD, label,
                    centerX - Fonts.ORBITRON_12_BOLD.measureWidth(label) * 0.5f, top + cog + 6f, edge);
        }
    }

    /**
     * The selected pattern standing on the vacant pad at the scene's own scale, inside the build
     * brackets, so the player sees what BUILD will put there before anything is spent.
     */
    private void drawPadPreview(CanvasContext context, GantryTarget target, Object preview, float cellPx) {
        float shortSide = Math.min(target.width(), target.height());
        context.fillRect(target.x(), target.y(), target.width(), target.height(), BUILD_WASH);
        drawCornerBrackets(context, target.x(), target.y(), target.width(), target.height(), BUILD_EDGE,
                2f, shortSide * 0.24f, Math.max(3f, shortSide * 0.06f));
        float centerX = target.x() + target.width() * 0.5f;
        float centerY = target.y() + target.height() * 0.5f;
        if (preview instanceof MechVariant variant) {
            if (previewMech == null || previewMech.variant() != variant) {
                previewMech = CampaignMech.frame("pad-preview", variant);
                previewMechArt = new MechChassisPreviewCanvas(previewMech, assets);
            }
            previewMechArt.drawAt(context, centerX, centerY,
                    UnitRenderService.layeredMechHullWidth(cellPx, variant.renderScale), PREVIEW_ALPHA);
        } else if (preview instanceof VehicleType type) {
            if (previewVehicleArt == null || previewVehicleType != type) {
                previewVehicleType = type;
                previewVehicleArt = VehiclePreviewCanvas.asBought(type);
            }
            previewVehicleArt.drawAt(context, centerX, centerY, cellPx, true, PREVIEW_ALPHA);
        }
        String label = "PREVIEW";
        if (shortSide >= 64f) {
            context.text(Fonts.ORBITRON_12_BOLD, label,
                    centerX - Fonts.ORBITRON_12_BOLD.measureWidth(label) * 0.5f,
                    target.y() + target.height() - Fonts.ORBITRON_12_BOLD.getLineHeight() - 8f, BUILD_EDGE);
        }
    }

    /** What the pad shows as a preview: a {@link MechVariant}, a {@link VehicleType}, or null. */
    public MechLabDollCanvas withPadPreview(Supplier<Object> preview) {
        this.padPreview = preview;
        return this;
    }

    /** The pad's letter, stencilled on an amber plate in its corner; the cards and headings use the same letter. */
    private static void drawPadLetter(CanvasContext context, GantryTarget target, int index) {
        float shortSide = Math.min(target.width(), target.height());
        boolean large = shortSide >= 150f;
        BitmapFont font = large ? Fonts.ORBITRON_24_BOLD : Fonts.ORBITRON_12_BOLD;
        float plate = large ? 34f : 20f;
        float x = target.x() + 4f;
        float y = target.y() + 4f;
        String letter = String.valueOf((char) ('A' + index));
        context.fillRect(x, y, plate, plate, LETTER_PLATE);
        context.strokeRect(x, y, plate, plate, LETTER_RIM, 1f);
        context.text(font, letter, x + (plate - font.measureWidth(letter)) * 0.5f,
                y + (plate - font.getLineHeight()) * 0.5f, LETTER_INK);
    }

    /**
     * The physical five-by-seven work pad around a machine berth.
     *
     * <p>The clear berth is three cells across and six deep. Its frame adds one
     * cell down each flank, while the control station adds the seventh cell at
     * the head opposite the exit direction. UI actions project this rectangle
     * as a whole; they are not a fixed pixel box or a pretend one-cell button.
     */
    static PadBounds padBounds(Gantry gantry) {
        float left = gantry.left();
        float bottom = gantry.bottom();
        float right = gantry.right() + 1f;
        float top = gantry.top() + 1f;
        if (gantry.facing.dx == 0) {
            left -= 1f;
            right += 1f;
            if (gantry.facing.dy > 0) bottom -= 1f;
            else top += 1f;
        } else {
            bottom -= 1f;
            top += 1f;
            if (gantry.facing.dx > 0) left -= 1f;
            else right += 1f;
        }
        return new PadBounds(left, bottom, right, top);
    }

    private static int selectedIndex(int requested) {
        return Math.max(0, Math.min(CampaignMechSquad.CAPACITY - 1, requested));
    }

    /**
     * A selected machine's doll, a mech's or a vehicle's: every card lit as a fit or a refusal while a part is carried,
     * drawn snapped in where it would go, and opening a fitted part's sheet under the pointer.
     *
     * @param hull     the vehicle on the doll, whose locked cards and older save's own gun are drawn as built in; null for a mech
     * @param weaponAt the weapon fitted at a socket
     * @param takes    whether a socket's mount takes a weapon: by mount and size on a mech, by the gun card on a vehicle
     * @param tone     a socket's colour on its wire
     */
    private void drawSchematic(CanvasContext c, List<SocketDef> sockets, VehicleType hull,
                               Function<SocketId, MechWeaponComponent> weaponAt,
                               BiPredicate<SocketDef, MechWeaponComponent> takes, Function<SocketId, Color> tone,
                               float canvasWidth, float canvasHeight) {
        SocketId selectedSocket = this.selectedSocket.get();
        MechWeaponComponent candidate = candidateWeapon.get();
        float panelWidth = panelWidth(sockets, canvasWidth);
        drawSchematicPanel(c, panelWidth, canvasHeight);
        List<SocketDropTarget> targets = schematicTargets(sockets, panelWidth, canvasHeight);
        List<SocketDef> grids = grids(sockets);
        SocketDropTarget core = null;
        for (SocketDropTarget target : targets) if (target.id() == SocketId.CORE) core = target;
        // A part in hand that would go where the pointer is is drawn there; @see #snaps
        boolean snapped = carriedSnapped();
        SocketId snapTarget = snapped ? dragPreview.target() : null;
        // Lifted off the doll, an internal is still drawn where it sits; only a stores part is added.
        MechInternal snapInternal = snapped && dragPreview.source() == null ? dragPreview.internal() : null;
        List<MechCompartment.Placement> packed = packed();
        List<MechCompartment.Placement> compartmentParts = packed;
        MechCompartment.Placement previewPart = null;
        if (snapInternal != null && snapTarget.compartment()) {
            List<MechCompartment.Placement> with = packedWith(grids, installedCompartment.get(), snapInternal, snapTarget);
            if (with != null) {
                compartmentParts = with;
                previewPart = with.get(with.size() - 1);
            }
        }
        for (SocketDropTarget target : targets) {
            if (core == null || target == core || target.id() == SocketId.PLATING) continue;
            SocketDef socket = socketById(sockets, target.id());
            boolean selected = selects(socket.id(), selectedSocket);
            MechWeaponComponent active = dragPreview != null ? dragPreview.component() : candidate;
            boolean compatible = active != null && takes.test(socket, active);
            boolean draggingHere = dragPreview != null && dragPreview.target() == socket.id();
            boolean rejected = draggingHere && !snapped;
            if (draggingHere) compatible = snapped;
            Color wire = rejected ? new Color(0xe98b83) : compatible ? new Color(0xc8eef4)
                    : selected ? new Color(0xedc273) : tone.apply(socket.id());
            List<float[]> path = wireToCore(target, core);
            for (int i = 0; i < path.size() - 1; i++) {
                float[] a = path.get(i), b = path.get(i + 1);
                c.line(a[0], a[1], b[0], b[1], new Color(0, 0, 0, 190), 5f);
                c.line(a[0], a[1], b[0], b[1], withAlpha(wire, selected || compatible ? 235 : 150), 2f);
                float phase = (float) ((elapsedSeconds.getAsDouble() * (selected || compatible ? .45 : .18)
                        + i * .18) % 1);
                float px = a[0] + (b[0] - a[0]) * phase;
                float py = a[1] + (b[1] - a[1]) * phase;
                c.fillRect(px - 1.5f, py - 1.5f, 3, 3, withAlpha(wire, selected || compatible ? 255 : 170));
            }
            float[] end = path.get(path.size() - 1);
            drawAnchorMechanicalTick(c, end[0], end[1], wire, selected);
        }
        MechSocketConsole.Warnings warnings = new MechSocketConsole.Warnings(true, machineArmed.getAsBoolean(),
                machineUnarmed.getAsBoolean());
        for (SocketDropTarget target : targets) {
            SocketDef socket = socketById(sockets, target.id());
            boolean selected = selects(socket.id(), selectedSocket);
            MechWeaponComponent component = weaponAt.apply(socket.id());
            MechWeaponComponent active = dragPreview != null ? dragPreview.component() : candidate;
            boolean draggingHere = dragPreview != null && dragPreview.target() == socket.id();
            boolean rejected = draggingHere && !snapped;
            boolean compatible = draggingHere ? snapped : active != null && takes.test(socket, active);
            boolean compartment = socket.id().compartment();
            boolean snapsHere = snapTarget == socket.id();
            boolean internalPreview = !compartment && snapsHere && snapInternal != null;
            MechSocketConsole.draw(c, target, socket, component,
                    snapsHere ? dragPreview.component() : null,
                    compartment ? null : internalPreview ? snapInternal : installedInternals.apply(socket.id()),
                    internalPreview,
                    compartment ? compartmentParts : null, previewPart, assets.get(), selected,
                    hoveredSocket == socket.id(), compatible, rejected, draggingHere,
                    elapsedSeconds.getAsDouble(), warnings, builtIn(hull, socket, component));
        }
        // A locked card takes no drops and no selection.
        List<SocketDropTarget> live = new ArrayList<>();
        for (SocketDropTarget target : targets) {
            if (!socketById(sockets, target.id()).factoryLocked()) live.add(target);
        }
        lastSocketTargets = List.copyOf(live);
        // A part comes off by being dragged off; each compartment part is lifted by its own box.
        Map<MechCompartment.Placement, float[]> partBoxes = new LinkedHashMap<>();
        for (SocketDropTarget target : targets) {
            SocketDef socket = socketById(sockets, target.id());
            if (socket.factoryLocked() || dragPreview != null || !socket.id().compartment()) continue;
            for (MechCompartment.Placement part : MechCompartment.in(packed, socket.id())) {
                partBoxes.put(part, MechSocketConsole.partBox(target, socket, part));
            }
        }
        lastPartBoxes = partBoxes;
        for (SocketDropTarget target : targets) {
            if (!target.contains(hover.x(), hover.y())) continue;
            SocketDef socket = socketById(sockets, target.id());
            MechWeaponComponent weapon = weaponAt.apply(socket.id());
            if (builtIn(hull, socket, weapon) != null) {
                // Built in has no sheet of its own; the board names what it is.
                BoardTooltip.draw(c, hover, canvasWidth, canvasHeight,
                        VehicleDoll.caption(hull, socket.id(), 0, null, null, null, true), socket.label());
                continue;
            }
            if (hoveredSocket != socket.id()) continue;
            MechInternal part = socket.id().compartment() ? partAt(hover.x(), hover.y())
                    : installedInternals.apply(socket.id());
            if (part != null || weapon != null) {
                // A fitted part is described by its spec sheet, the one its stores card opens.
                float[] box = socket.id().compartment() ? partBoxes.get(part) : null;
                pointed = box != null ? new PointedPart(weapon, part, box[0], box[1], box[2], box[3])
                        : new PointedPart(weapon, part, target.left(), target.top(), target.right(), target.bottom());
            } else if (!socket.id().compartment()) {
                // An empty socket has no sheet of its own; the board names what it takes.
                BoardTooltip.draw(c, hover, canvasWidth, canvasHeight, socket.label(),
                        socket.type().label() + " / " + socket.gridColumns() + "x" + socket.gridRows());
            }
        }
    }

    /**
     * What is built in at a selected hull's card: a locked card's bay or systems, or the gun of a hull from an older
     * save while its gun card stands empty and nothing is carried. Null for anything fitted, and on any mech.
     */
    private MechSocketConsole.Sealed builtIn(VehicleType hull, SocketDef socket, MechWeaponComponent fitted) {
        if (hull == null) return null;
        boolean carrying = dragPreview != null && dragPreview.component() != null;
        boolean ownGun = socket.id() == SocketId.ARMS && fitted == null && !carrying && vehicleOwnGun.getAsBoolean();
        return socket.factoryLocked() || ownGun ? sealedOf(hull, socket.id()) : null;
    }

    private static MechSocketConsole.Sealed sealedOf(VehicleType hull, SocketId card) {
        return new MechSocketConsole.Sealed(VehicleDoll.art(hull, card), VehicleDoll.tone(hull, card));
    }

    private static SocketDef socketById(List<SocketDef> sockets, SocketId id) {
        for (SocketDef socket : sockets) if (socket.id() == id) return socket;
        throw new IllegalArgumentException("no card " + id);
    }

    /** The selected machine's compartment parts where each sits, every bay. */
    private List<MechCompartment.Placement> packed() {
        List<MechCompartment.Placement> placed = installedCompartment.get();
        return placed != null ? placed : List.of();
    }

    /** The compartment bays among a machine's sockets, in packing order. */
    private static List<SocketDef> grids(List<SocketDef> sockets) {
        List<SocketDef> grids = new ArrayList<>();
        for (SocketId id : MechCompartment.GRIDS) {
            for (SocketDef socket : sockets) if (socket.id() == id) grids.add(socket);
        }
        return grids;
    }

    /** Whether a card is the selected one: either compartment bay lights for the compartment. */
    private static boolean selects(SocketId card, SocketId selected) {
        return card == selected || card.compartment() && selected != null && selected.compartment();
    }

    static void drawCornerBrackets(CanvasContext c, float left, float top, float width, float height,
                                   Color color, float stroke, float bracketLength, float chamfer) {
        float right = left + width;
        float bottom = top + height;
        float bl = Math.min(bracketLength, Math.min(width, height) * 0.4f);
        float ch = Math.min(chamfer, bl * 0.5f);

        // Top-Left
        c.line(left, top + bl, left, top + ch, color, stroke);
        c.line(left, top + ch, left + ch, top, color, stroke);
        c.line(left + ch, top, left + bl, top, color, stroke);

        // Top-Right
        c.line(right - bl, top, right - ch, top, color, stroke);
        c.line(right - ch, top, right, top + ch, color, stroke);
        c.line(right, top + ch, right, top + bl, color, stroke);

        // Bottom-Left
        c.line(left, bottom - bl, left, bottom - ch, color, stroke);
        c.line(left, bottom - ch, left + ch, bottom, color, stroke);
        c.line(left + ch, bottom, left + bl, bottom, color, stroke);

        // Bottom-Right
        c.line(right - bl, bottom, right - ch, bottom, color, stroke);
        c.line(right - ch, bottom, right, bottom - ch, color, stroke);
        c.line(right, bottom - ch, right, bottom - bl, color, stroke);
    }

    static void drawAnchorMechanicalTick(CanvasContext c, float x, float y, Color base, boolean selected) {
        float size = selected ? 7f : 5f;
        c.fillRect(x - size * 0.5f - 1f, y - size * 0.5f - 1f, size + 2f, size + 2f,
                new Color(0x02, 0x07, 0x0F, 220));
        c.fillRect(x - size * 0.5f, y - size * 0.5f, size, size,
                withAlpha(base, selected ? 255 : 200));
        float tick = selected ? 7f : 5.5f;
        Color tickColor = withAlpha(base, selected ? 240 : 180);
        c.line(x - tick, y, x + tick, y, new Color(0x02, 0x07, 0x0F, 200), 2.5f);
        c.line(x, y - tick, x, y + tick, new Color(0x02, 0x07, 0x0F, 200), 2.5f);
        c.line(x - tick, y, x + tick, y, tickColor, 1.5f);
        c.line(x, y - tick, x, y + tick, tickColor, 1.5f);
    }

    static boolean isSocketCompatible(SocketDef socket, MechWeaponComponent candidate) {
        if (socket == null || candidate == null) return false;
        MechMountSlot mount = mountFor(socket.id());
        if (mount == null || !candidate.accepts(mount)) return false;
        boolean typeMatches = socket.type() == SocketType.OMNI
                || socket.type().name().equals(candidate.hardpointType.name());
        return !socket.factoryLocked() && typeMatches && socket.accommodates(candidate.footprintColumns, candidate.footprintRows);
    }

    static String mountCallout(SocketDef socket) {
        if (socket == null) return "";
        String label = socket.label();
        String name = switch (label) {
            case "ARM ASSEMBLY" -> "ARMS";
            case "ENGINE CORE" -> "CORE";
            case "L. SHOULDER" -> "L. SHLDR";
            case "R. SHOULDER" -> "R. SHLDR";
            case "AUXILIARY" -> "AUX";
            case "L. AUXILIARY" -> "L. AUX";
            case "R. AUXILIARY" -> "R. AUX";
            default -> label;
        };
        return name + " [" + socket.gridColumns() + "×" + socket.gridRows() + "]";
    }

    static String mountCallout(SocketId id, int cols, int rows) {
        String name = switch (id) {
            case CORE -> "CORE";
            case ARMS -> "ARMS";
            case LEFT_ARM -> "L. ARM";
            case RIGHT_ARM -> "R. ARM";
            case LEFT_SHOULDER -> "L. SHLDR";
            case RIGHT_SHOULDER -> "R. SHLDR";
            case AUXILIARY, AUXILIARY_2 -> "AUX";
            case PLATING -> "PLATE";
        };
        return name + " [" + cols + "×" + rows + "]";
    }

    static MechMountSlot mountFor(SocketId slot) {
        return switch (slot) {
            case ARMS -> MechMountSlot.ARMS;
            case LEFT_ARM -> MechMountSlot.LEFT_ARM;
            case RIGHT_ARM -> MechMountSlot.RIGHT_ARM;
            case LEFT_SHOULDER -> MechMountSlot.LEFT_SHOULDER;
            case RIGHT_SHOULDER -> MechMountSlot.RIGHT_SHOULDER;
            default -> null;
        };
    }

    private static boolean occupied(MechDeploymentSpec deployment, SocketId socket) {
        if (socket == SocketId.CORE || socket == SocketId.PLATING || socket.compartment()) return true;
        return weaponAt(deployment, socket) != null;
    }

    private static MechWeaponComponent weaponAt(MechDeploymentSpec deployment,
                                                SocketId socket) {
        MechMountSlot mount = mountFor(socket);
        return mount != null ? deployment.weaponAt(mount) : null;
    }

    /** The share of the canvas the schematic panel takes; the fitting camera stands the machine in the rest. */
    static final float SCHEMATIC_SHARE = 0.56f;
    static final float SCHEMATIC_MARGIN = 18f;
    static final float SCHEMATIC_GAP_X = 26f;
    static final float SCHEMATIC_GAP_Y = 30f;
    /** What the gaps and margins close to on a small page, as at a large UI scale, before a card would leave the panel. */
    private static final float SCHEMATIC_TIGHT_GAP = 6f;
    private static final float SCHEMATIC_TIGHT_MARGIN = 4f;
    private static final Color PANEL = new Color(0x05, 0x08, 0x0a, 214);
    private static final Color PANEL_GRID = new Color(0x5e, 0x96, 0xd2, 20);
    private static final Color PANEL_EDGE = new Color(0x8e, 0x9a, 0x8c, 120);

    /** A dark drafting board under the schematic, so the cards read against one ground, not the deck. */
    private static void drawSchematicPanel(CanvasContext c, float width, float height) {
        c.fillRect(0f, 0f, width, height, PANEL);
        for (float x = 24f; x < width; x += 24f) c.line(x, 0f, x, height, PANEL_GRID, 1f);
        for (float y = 24f; y < height; y += 24f) c.line(0f, y, width, y, PANEL_GRID, 1f);
        c.line(width, 0f, width, height, PANEL_EDGE, 2f);
        for (int i = 1; i <= 6; i++) {
            c.line(width + i * 2f, 0f, width + i * 2f, height, new Color(0x05, 0x08, 0x0a, 120 - i * 18), 2f);
        }
    }

    /**
     * Lays the sockets out as the machine is built: the engine core in the middle of the panel,
     * the arm guns ahead of it (a centreline mount straight ahead, or one arm ahead-left and one
     * ahead-right), a shoulder either side, and the auxiliary compartment behind it (two flank bays
     * side by side where the chassis has them). Every card
     * keeps its fixed brick size; the rows are centred in the panel, and their gaps close up where
     * the panel is too small for them.
     */
    static List<SocketDropTarget> schematicTargets(List<SocketDef> sockets, float panelWidth, float panelHeight) {
        EnumMap<SocketId, SocketDef> byId = new EnumMap<>(SocketId.class);
        for (SocketDef socket : sockets) byId.put(socket.id(), socket);
        if (byId.isEmpty()) return List.of();
        float coreW = cardWidth(byId.get(SocketId.CORE));
        SocketDef left = byId.get(SocketId.LEFT_SHOULDER);
        SocketDef right = byId.get(SocketId.RIGHT_SHOULDER);
        // The middle row is as tall as its tallest card, so a deep shoulder rack never reaches
        // into the arm or compartment row whatever the brick size.
        float coreH = Math.max(cardHeight(byId.get(SocketId.CORE)), Math.max(cardHeight(left), cardHeight(right)));
        SocketDef leftArm = byId.get(SocketId.LEFT_ARM);
        SocketDef rightArm = byId.get(SocketId.RIGHT_ARM);
        float armsH = Math.max(cardHeight(byId.get(SocketId.ARMS)),
                Math.max(cardHeight(leftArm), cardHeight(rightArm)));
        SocketDef bay = byId.get(SocketId.AUXILIARY);
        SocketDef secondBay = byId.get(SocketId.AUXILIARY_2);
        float backH = Math.max(cardHeight(bay), cardHeight(secondBay));
        SocketDef plating = byId.get(SocketId.PLATING);
        float platingH = cardHeight(plating);
        int fullGaps = (armsH > 0f ? 1 : 0) + (backH > 0f ? 1 : 0) + (platingH > 0f ? 1 : 0);
        // Too short for its own row, the armor point stands beside a lone compartment.
        boolean platingBeside = plating != null && bay != null && secondBay == null
                && armsH + coreH + backH + platingH + fullGaps * SCHEMATIC_TIGHT_GAP + 2f * SCHEMATIC_TIGHT_MARGIN > panelHeight;
        int rowGaps = platingBeside ? fullGaps - 1 : fullGaps;
        float cards = armsH + coreH + backH + (platingBeside ? 0f : platingH);
        float gapY = rowGaps == 0 ? SCHEMATIC_GAP_Y : closed(SCHEMATIC_GAP_Y, (panelHeight - cards - 2f * SCHEMATIC_MARGIN) / rowGaps);
        float gapX = closed(SCHEMATIC_GAP_X, (panelWidth - minimumRowWidth(sockets) + 2f * SCHEMATIC_TIGHT_GAP - 2f * SCHEMATIC_TIGHT_MARGIN) * 0.5f);
        float total = cards + rowGaps * gapY;
        float top = Math.max(SCHEMATIC_TIGHT_MARGIN, (panelHeight - total) * 0.5f);
        float centerX = panelWidth * 0.5f;
        float coreY = top + armsH + (armsH > 0f ? gapY : 0f) + coreH * 0.5f;

        List<SocketDropTarget> targets = new ArrayList<>();
        place(targets, byId.get(SocketId.CORE), centerX, coreY);
        float armsY = top + armsH * 0.5f;
        place(targets, byId.get(SocketId.ARMS), centerX, armsY);
        place(targets, leftArm, centerX - gapX * 0.5f - cardWidth(leftArm) * 0.5f, armsY);
        place(targets, rightArm, centerX + gapX * 0.5f + cardWidth(rightArm) * 0.5f, armsY);
        place(targets, left, centerX - coreW * 0.5f - gapX - cardWidth(left) * 0.5f, coreY);
        place(targets, right, centerX + coreW * 0.5f + gapX + cardWidth(right) * 0.5f, coreY);
        float backY = coreY + coreH * 0.5f + gapY + backH * 0.5f;
        if (secondBay == null) {
            place(targets, bay, centerX, backY);
        } else {
            place(targets, bay, centerX - gapX * 0.5f - cardWidth(bay) * 0.5f, backY);
            place(targets, secondBay, centerX + gapX * 0.5f + cardWidth(secondBay) * 0.5f, backY);
        }
        // The armor point stands last, under everything: it covers the frame and is wired to nothing.
        float frameY = (backH > 0f ? backY + backH * 0.5f : coreY + coreH * 0.5f) + gapY + platingH * 0.5f;
        if (platingBeside) place(targets, plating, centerX + cardWidth(bay) * 0.5f + gapX + cardWidth(plating) * 0.5f, backY);
        else place(targets, plating, centerX, frameY);
        return List.copyOf(targets);
    }

    /** The panel for a canvas: its share, or as much more as the widest row needs, the machine behind it giving way. */
    static float panelWidth(List<SocketDef> sockets, float canvasWidth) {
        return Math.min(canvasWidth, Math.max(canvasWidth * SCHEMATIC_SHARE,
                minimumRowWidth(sockets) + 2f * SCHEMATIC_TIGHT_MARGIN));
    }

    /** The widest row with its gaps closed, centred on the core as the rows are. */
    private static float minimumRowWidth(List<SocketDef> sockets) {
        EnumMap<SocketId, SocketDef> byId = new EnumMap<>(SocketId.class);
        for (SocketDef socket : sockets) byId.put(socket.id(), socket);
        float shoulders = cardWidth(byId.get(SocketId.CORE)) + 2f * SCHEMATIC_TIGHT_GAP
                + 2f * Math.max(cardWidth(byId.get(SocketId.LEFT_SHOULDER)), cardWidth(byId.get(SocketId.RIGHT_SHOULDER)));
        float arms = SCHEMATIC_TIGHT_GAP + 2f * Math.max(cardWidth(byId.get(SocketId.LEFT_ARM)), cardWidth(byId.get(SocketId.RIGHT_ARM)));
        float bays = SCHEMATIC_TIGHT_GAP + 2f * Math.max(cardWidth(byId.get(SocketId.AUXILIARY)), cardWidth(byId.get(SocketId.AUXILIARY_2)));
        return Math.max(shoulders, Math.max(arms, bays));
    }

    /** A gap at its full size where there is room for it, down to the tight one. */
    private static float closed(float full, float room) {
        return Math.max(SCHEMATIC_TIGHT_GAP, Math.min(full, room));
    }

    private static float cardWidth(SocketDef socket) {
        return socket != null ? MechSocketConsole.width(socket) : 0f;
    }

    private static float cardHeight(SocketDef socket) {
        return socket != null ? MechSocketConsole.height(socket) : 0f;
    }

    private static void place(List<SocketDropTarget> targets, SocketDef socket, float centerX, float centerY) {
        if (socket == null) return;
        targets.add(new SocketDropTarget(socket.id(), socket.gridColumns(), socket.gridRows(),
                centerX, centerY, centerX, centerY, cardWidth(socket), cardHeight(socket)));
    }

    /**
     * The wire from a part back to the engine core: straight across where the two face each
     * other, otherwise out of the card towards the core, along, and into the core's near side.
     */
    static List<float[]> wireToCore(SocketDropTarget part, SocketDropTarget core) {
        List<float[]> points = new ArrayList<>();
        if (part.bottom() <= core.top()) {
            points.add(new float[]{part.centerX(), part.bottom()});
            points.add(new float[]{core.centerX(), core.top()});
        } else if (part.top() >= core.bottom()) {
            float x = Math.max(core.left() + 18f, Math.min(core.right() - 18f, part.centerX()));
            float midY = (part.top() + core.bottom()) * 0.5f;
            points.add(new float[]{part.centerX(), part.top()});
            points.add(new float[]{part.centerX(), midY});
            points.add(new float[]{x, midY});
            points.add(new float[]{x, core.bottom()});
        } else if (part.right() <= core.left()) {
            points.add(new float[]{part.right(), core.centerY()});
            points.add(new float[]{core.left(), core.centerY()});
        } else {
            points.add(new float[]{part.left(), core.centerY()});
            points.add(new float[]{core.right(), core.centerY()});
        }
        if (points.size() == 2 && points.get(0)[0] != points.get(1)[0] && points.get(0)[1] != points.get(1)[1]) {
            float[] a = points.get(0), b = points.get(1);
            points.add(1, new float[]{a[0], (a[1] + b[1]) * 0.5f});
            points.add(2, new float[]{b[0], (a[1] + b[1]) * 0.5f});
        }
        return points;
    }

    record SocketDropTarget(SocketId id, int gridColumns, int gridRows,
                            float anchorX, float anchorY,
                            float centerX, float centerY,
                            float width, float height) {
        float left() { return centerX - width * 0.5f; }
        float top() { return centerY - height * 0.5f; }
        float right() { return centerX + width * 0.5f; }
        float bottom() { return centerY + height * 0.5f; }

        boolean contains(float x, float y) {
            return x >= left() && x <= right() && y >= top() && y <= bottom();
        }
    }

    record GantryTarget(int index, float x, float y, float width, float height) {
        boolean contains(float pointX, float pointY) {
            return pointX >= x && pointX <= x + width
                    && pointY >= y && pointY <= y + height;
        }
    }

    record PadBounds(float left, float bottom, float right, float top) {
        float width() { return right - left; }
        float height() { return top - bottom; }
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static int appearance(MechWeaponComponent component) {
        return component != null ? component.appearanceSelector : LayeredMechAppearance.POD_NONE;
    }

    private record CanvasSink(CanvasContext context, float surfaceHeight)
            implements LayeredMechComposer.Sink {
        @Override
        public void sprite(LayeredSpriteCache sprite, float centerX, float centerY,
                           float width, float height, float angleDegrees, float alpha) {
            context.sprite(sprite.sourcePath, sprite.sprite,
                    centerX, surfaceHeight - centerY, width, height,
                    angleDegrees, new Color(1f, 1f, 1f, alpha));
        }
    }

    /** Asset identity for snapshot rendering without live Starsector sprites. */
    public static LayeredMechAssets headlessAssets() {
        return new LayeredMechAssets(
                token("chassis.png", 208, 208),
                token("chassis-socketed-variant.png", 208, 208),
                token("chassis-hound.png", 208, 208),
                token("chassis-warden.png", 208, 208),
                token("foot.png", 44, 38), token("thigh-bone.png", 40, 112),
                token("chaingun-arm.png", 62, 112),
                token("hc-10.png", 40, 116),
                token("hc-20.png", 64, 128), token("srm-rack.png", 62, 88),
                token("lrm-rack.png", 76, 96),
                token("ll-3.png", 76, 128),
                token("pl-2-ap.png", 62, 112),
                token("ac-6.png", 64, 132),
                  token("dc-5.png", 72, 124),
                  token("lions-guard-thermal-lance.png", 76, 132),
                  token("ac-6-qs.png", 62, 128),
                  token("dc-4-qs.png", 72, 124),
                  token("srm-3-qs.png", 76, 112),
                  token("light-machine-gun.png", 34, 88),
                  token("bl-1.png", 35, 84),
                  token("pl-3.png", 54, 136),
                  token("bl-2.png", 40, 112),
                  token("pl-3-sweep.png", 50, 136),
                  token("pl-2.png", 40, 112),
                  token("pl-1.png", 26, 84),
                  LayeredSpriteCache.headless(
                        "graphics/battle/marine-modular-topdown/marine-muzzle-flash.png",
                        48, 48));
    }

    private static LayeredSpriteCache token(String name, int width, int height) {
        return LayeredSpriteCache.headless(ROOT + name, width, height);
    }
}
