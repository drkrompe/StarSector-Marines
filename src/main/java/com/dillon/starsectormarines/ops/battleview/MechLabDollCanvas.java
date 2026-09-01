package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.ambient.AmbientActivity;
import com.dillon.starsectormarines.battle.ambient.AmbientTaskPose;
import com.dillon.starsectormarines.battle.ambient.AmbientTaskService;
import com.dillon.starsectormarines.battle.ambient.JobBoard;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.DollDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketType;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.marine.CampaignMechSquad;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.ui.retained.CanvasBlend;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;
import com.dillon.starsectormarines.ui.retained.CanvasSpriteRegion;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Canonically composed lance inside a top-down battle-tileset fabrication garage. */
public final class MechLabDollCanvas implements CanvasProducer {

    private static final String ROOT = "graphics/battle/mech-modular-topdown/";
    static final String WELDING_TORCH_PATH =
            "graphics/battle/mech-lab/welding-torch.png";
    static final String WELDING_SPARKS_PATH =
            "graphics/battle/mech-lab/welding-sparks-sheet.png";
    private static final float MIN_DROP_TARGET_WIDTH = 128f;
    private static final float MIN_DROP_TARGET_HEIGHT = 76f;
    private static final float CAPACITY_INSET = 4f;
    private static final float CAPACITY_GAP = 2f;
    private static final Color BACKGROUND = new Color(0x06, 0x0A, 0x10);
    private static final Color WHITE = Color.WHITE;
    private static final Color VACANT_FILL = new Color(0x08, 0x18, 0x24, 214);
    private static final Color VACANT_EDGE = new Color(0x6D, 0xD5, 0xF2, 226);
    private static final Color VACANT_HOVER = new Color(0xFF, 0xD4, 0x64, 246);
    private static final float VACANT_PAD_INSET_RATIO = 0.045f;

    private static final EnumSet<RenderLayer> BACKDROP_LAYERS = EnumSet.of(
            RenderLayer.GROUND, RenderLayer.DOODADS);
    private static final EnumSet<RenderLayer> ACTOR_LAYERS = EnumSet.of(
            RenderLayer.UNITS, RenderLayer.VEHICLES);

    private final Supplier<List<MechDeploymentSpec>> deployments;
    private final IntSupplier selectedGantry;
    private final Supplier<SocketId> selectedSocket;
    private final Supplier<LayeredMechAssets> assets;
    private final Supplier<SpriteAPI> weldingTorch;
    private final Supplier<SpriteAPI> weldingSparks;
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
    private List<VacantGantryTarget> vacantGantryTargets = List.of();
    private int hoveredVacantGantry = -1;

    public MechLabDollCanvas(Supplier<List<MechDeploymentSpec>> deployments,
                             IntSupplier selectedGantry,
                             Supplier<SocketId> selectedSocket,
                             Supplier<LayeredMechAssets> assets,
                             Supplier<SpriteAPI> weldingTorch,
                             Supplier<SpriteAPI> weldingSparks,
                             BooleanSupplier fittingOverlaysVisible,
                             Supplier<ShipDeckBattleScene> ship,
                             Supplier<ShipDeckBattleScene.RoomView> roomView,
                             Supplier<List<Gantry>> berths,
                             IntSupplier workSite,
                             DoubleSupplier elapsedSeconds) {
        if (deployments == null || selectedGantry == null || selectedSocket == null
                || assets == null || weldingTorch == null || weldingSparks == null
                || fittingOverlaysVisible == null
                || ship == null || roomView == null || berths == null || workSite == null
                || elapsedSeconds == null) {
            throw new IllegalArgumentException(
                    "deployments, gantry, socket, mech assets, a ship, work site and elapsed time are required");
        }
        this.deployments = deployments;
        this.selectedGantry = selectedGantry;
        this.selectedSocket = selectedSocket;
        this.assets = assets;
        this.weldingTorch = weldingTorch;
        this.weldingSparks = weldingSparks;
        this.fittingOverlaysVisible = fittingOverlaysVisible;
        this.ship = ship;
        this.roomView = roomView;
        this.berths = berths;
        this.workSite = workSite;
        this.elapsedSeconds = elapsedSeconds;
    }

    @Override
    public void draw(CanvasContext context) {
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        float time = (float) elapsedSeconds.getAsDouble();
        List<MechDeploymentSpec> lance = deployments.get();
        int gantryIndex = selectedIndex(selectedGantry.getAsInt());
        MechDeploymentSpec selected = gantryIndex < lance.size() ? lance.get(gantryIndex) : null;
        MechVariant selectedVariant = selected != null ? selected.variant() : null;
        if (assets.get() == null) return;

        ShipDeckBattleScene aboard = ship.get();
        ShipDeckBattleScene.RoomView bay = aboard != null ? roomView.get() : null;
        if (bay == null) {
            vacantGantryTargets = List.of();
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
            vacantGantryTargets = List.of();
            context.fillRect(0f, 0f, width, height, BACKGROUND);
            return;
        }

        List<Gantry> standing = berths.get();
        float berthX = berthCellX(standing, gantryIndex);
        float berthY = berthCellY(standing, gantryIndex);
        BattleCamera sceneCamera = aboard.cameraFor(
                bay, 0f, 0f, host[0].width(), host[0].height());
        SceneProjection projection = selectedVariant != null
                ? SceneProjection.forLive(sceneCamera, host[0], selectedVariant, berthX, berthY)
                : null;

        context.hostPass(aboard.pass(bay, ACTOR_LAYERS));
        drawVacantGantryActions(context, sceneCamera, host[0], standing, lance.size());
        if (selected != null && fittingOverlaysVisible.getAsBoolean()) {
            drawSocketOverlays(context, MechFittingLayout.forVariant(selectedVariant),
                    selected, selectedSocket.get(), projection);
        }
        drawTechnicianFx(context, sceneCamera, host[0],
                workingPoses(aboard, standing, workSite.getAsInt()), time,
                weldingTorch.get(), weldingSparks.get());
    }

    /** Records hover over the physical vacant-pad actions drawn during the last frame. */
    public void pointAt(float canvasX, float canvasY) {
        hoveredVacantGantry = vacantGantryAt(canvasX, canvasY);
    }

    /** The vacant gantry action at this canvas-local point, or -1 outside every action. */
    public int vacantGantryAt(float canvasX, float canvasY) {
        if (!Float.isFinite(canvasX) || !Float.isFinite(canvasY)) return -1;
        for (VacantGantryTarget target : vacantGantryTargets) {
            if (target.contains(canvasX, canvasY)) return target.index();
        }
        return -1;
    }

    private void drawVacantGantryActions(CanvasContext context, BattleCamera camera,
                                          CanvasHostViewport viewport,
                                          List<Gantry> standing, int occupied) {
        int count = Math.min(CampaignMechSquad.CAPACITY, standing.size());
        List<VacantGantryTarget> targets = new ArrayList<>();
        for (int index = Math.max(0, occupied); index < count; index++) {
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
            VacantGantryTarget target = new VacantGantryTarget(index,
                    Math.min(left, right) + inset, Math.min(top, bottom) + inset,
                    Math.max(1f, width - inset * 2f),
                    Math.max(1f, height - inset * 2f));
            targets.add(target);
            Color edge = index == hoveredVacantGantry ? VACANT_HOVER : VACANT_EDGE;
            context.fillRect(target.x(), target.y(), target.width(), target.height(), VACANT_FILL);
            context.strokeRect(target.x(), target.y(), target.width(), target.height(), edge, 3f);
            float centerX = target.x() + target.width() * 0.5f;
            float centerY = target.y() + target.height() * 0.5f;
            float shortSide = Math.min(target.width(), target.height());
            float arm = shortSide * 0.23f;
            float stroke = Math.max(4f, Math.min(10f, shortSide * 0.055f));
            context.line(centerX - arm, centerY, centerX + arm, centerY, edge, stroke);
            context.line(centerX, centerY - arm, centerX, centerY + arm, edge, stroke);
        }
        vacantGantryTargets = List.copyOf(targets);
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

    /**
     * Where the technicians are actually working.
     *
     * <p>Read off the ship rather than off a script laid over the scene. The
     * welding sparks belong at the point somebody is welding, and on a generated
     * bay that is wherever the crew have got to — a fixed list would light up
     * over empty deck the moment the room was laid out differently.
     */
    private static List<TechnicianWork> workingPoses(ShipDeckBattleScene aboard,
                                                     List<Gantry> berths,
                                                     int workSite) {
        List<TechnicianWork> working = new ArrayList<>();
        AmbientTaskService tasks = aboard.simulation().ambientTasks();
        for (long actor : tasks.assigned()) {
            String pointGroup = tasks.jobInHand(actor);
            AmbientTaskPose pose = tasks.pose(actor);
            if (pose == null) continue;
            boolean visibleRepairTarget = !focusInsideServicePad(
                    berths, pose.focusX(), pose.focusY())
                    && focusHitsFixture(aboard.simulation().getDoodads(),
                    pose.focusX(), pose.focusY());
            if (isWeldingJob(pointGroup, workSite, visibleRepairTarget)) {
                boolean fixtureTarget = !inGroup(pointGroup,
                        JobBoard.group(workSite, Affordance.SERVICE));
                working.add(new TechnicianWork(pose, fixtureTarget));
            }
        }
        return working;
    }

    /**
     * Whether this live job warrants a torch inside the currently framed bay.
     *
     * <p>A repair only qualifies when its focus actually overlaps a rendered
     * fixture. A bay's port-rail defect is real work but is represented by the
     * deck-edge paving; adding a torch there reads as somebody welding the
     * floor. Service focuses are resolved onto the berthed machine and
     * fabrication focuses belong to workshop fixtures, so both always provide
     * an unambiguous visible target.
     */
    static boolean isWeldingJob(String pointGroup, int workSite,
                                boolean visibleRepairTarget) {
        if (pointGroup == null || workSite < 0) return false;
        return inGroup(pointGroup, JobBoard.group(workSite, Affordance.SERVICE))
                || inGroup(pointGroup, JobBoard.group(workSite, Affordance.FABRICATE))
                || visibleRepairTarget
                && inGroup(pointGroup, JobBoard.group(workSite, Affordance.REPAIR));
    }

    private static boolean focusHitsFixture(List<Doodad> fixtures, float focusX, float focusY) {
        int cellX = (int) Math.floor(focusX);
        int cellY = (int) Math.floor(focusY);
        for (Doodad fixture : fixtures) {
            if (cellX >= fixture.cellX && cellX < fixture.cellX + fixture.footprintCellsX
                    && cellY >= fixture.cellY && cellY < fixture.cellY + fixture.footprintCellsY) {
                return true;
            }
        }
        return false;
    }

    /** The pad's own rail defects must not masquerade as work on its occupant. */
    static boolean focusInsideServicePad(List<Gantry> berths, float focusX, float focusY) {
        for (Gantry berth : berths) {
            PadBounds pad = padBounds(berth);
            if (focusX >= pad.left() && focusX <= pad.right()
                    && focusY >= pad.bottom() && focusY <= pad.top()) {
                return true;
            }
        }
        return false;
    }

    private static boolean inGroup(String actual, String expected) {
        return actual.equals(expected) || actual.startsWith(expected + "@");
    }

    private static float berthCellX(List<Gantry> berths, int index) {
        return berths.isEmpty() ? 0f
                : berths.get(Math.max(0, Math.min(berths.size() - 1, index))).worldCenterX();
    }

    private static float berthCellY(List<Gantry> berths, int index) {
        return berths.isEmpty() ? 0f
                : berths.get(Math.max(0, Math.min(berths.size() - 1, index))).worldCenterY();
    }

    private static int selectedIndex(int requested) {
        return Math.max(0, Math.min(CampaignMechSquad.CAPACITY - 1, requested));
    }

    private static void drawSocketOverlays(CanvasContext c, MechFittingLayout layout,
                                           MechDeploymentSpec deployment, SocketId selectedSocket,
                                           SceneProjection projection) {
        float radians = (float) Math.toRadians(layout.doll().facingDegrees());
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        for (SocketDef socket : layout.sockets()) {
            SocketDropTarget target = socketDropTarget(socket, projection.actorX(),
                    projection.actorY(), projection.hullX(), projection.hullY(), cos, sin);
            boolean occupied = occupied(deployment, socket.id());
            boolean selected = socket.id() == selectedSocket;
            Color base = socketColor(socket.type());
            int fillAlpha = selected ? 138 : occupied ? 72 : 112;
            int strokeAlpha = selected ? 240 : occupied ? 128 : 210;
            c.line(target.anchorX(), target.anchorY(), target.centerX(), target.centerY(),
                    withAlpha(base, selected ? 210 : occupied ? 90 : 165),
                    selected ? 2f : 1f);
            c.fillRect(target.anchorX() - 3f, target.anchorY() - 3f, 6f, 6f,
                    withAlpha(base, selected ? 245 : 180));
            c.fillRect(target.left(), target.top(), target.width(), target.height(),
                    withAlpha(base, fillAlpha));
            c.strokeRect(target.left(), target.top(), target.width(), target.height(),
                    withAlpha(base, strokeAlpha),
                    selected ? 2f : 1f);
            MechWeaponComponent component = weaponAt(deployment, socket.id());
            int occupiedColumns = component != null ? component.footprintColumns
                    : occupied ? socket.gridColumns() : 0;
            int occupiedRows = component != null ? component.footprintRows
                    : occupied ? socket.gridRows() : 0;
            drawCapacityCells(c, target, base, selected,
                    occupiedColumns, occupiedRows);
        }
    }

    private static boolean occupied(MechDeploymentSpec deployment, SocketId socket) {
        return switch (socket) {
            case CORE, AMMO_RESERVE, MINI_FAB -> true;
            case ARMS -> deployment.arms() != null;
            case LEFT_SHOULDER -> deployment.leftShoulder() != null;
            case RIGHT_SHOULDER -> deployment.rightShoulder() != null;
        };
    }

    private static MechWeaponComponent weaponAt(MechDeploymentSpec deployment,
                                                SocketId socket) {
        return switch (socket) {
            case ARMS -> deployment.arms();
            case LEFT_SHOULDER -> deployment.leftShoulder();
            case RIGHT_SHOULDER -> deployment.rightShoulder();
            default -> null;
        };
    }

    static SocketDropTarget socketDropTarget(DollDef doll, SocketDef socket,
                                             float actorX, float actorY,
                                             float hullWidth, float hullHeight) {
        float radians = (float) Math.toRadians(doll.facingDegrees());
        return socketDropTarget(socket, actorX, actorY, hullWidth, hullHeight,
                (float) Math.cos(radians), (float) Math.sin(radians));
    }

    private static SocketDropTarget socketDropTarget(SocketDef socket,
                                                      float actorX, float actorY,
                                                      float hullWidth, float hullHeight,
                                                      float cos, float sin) {
        float anchorLocalX = socket.anchorRight() * hullWidth;
        float anchorLocalY = socket.anchorForward() * hullHeight;
        float anchorWorldX = anchorLocalX * cos - anchorLocalY * sin;
        float anchorWorldY = anchorLocalX * sin + anchorLocalY * cos;
        float dockLocalX = socket.dockRight() * hullWidth;
        float dockLocalY = socket.dockForward() * hullHeight;
        float dockWorldX = dockLocalX * cos - dockLocalY * sin;
        float dockWorldY = dockLocalX * sin + dockLocalY * cos;
        float width = Math.max(MIN_DROP_TARGET_WIDTH,
                socket.footprintWidthHull() * hullWidth);
        float height = Math.max(MIN_DROP_TARGET_HEIGHT,
                socket.footprintHeightHull() * hullHeight);
        return new SocketDropTarget(socket.id(), socket.gridColumns(), socket.gridRows(),
                actorX + anchorWorldX, actorY - anchorWorldY,
                actorX + dockWorldX, actorY - dockWorldY, width, height);
    }

    static List<CapacityCell> capacityCells(SocketDropTarget target) {
        float availableWidth = target.width() - CAPACITY_INSET * 2f
                - CAPACITY_GAP * (MechFittingLayout.MAX_GRID_COLUMNS - 1);
        float availableHeight = target.height() - CAPACITY_INSET * 2f
                - CAPACITY_GAP * (MechFittingLayout.MAX_GRID_ROWS - 1);
        float cellWidth = availableWidth / MechFittingLayout.MAX_GRID_COLUMNS;
        float cellHeight = availableHeight / MechFittingLayout.MAX_GRID_ROWS;
        float x = target.left() + CAPACITY_INSET;
        float y = target.top() + CAPACITY_INSET;
        List<CapacityCell> cells = new ArrayList<>(
                MechFittingLayout.MAX_GRID_COLUMNS * MechFittingLayout.MAX_GRID_ROWS);
        int index = 0;
        for (int row = 0; row < MechFittingLayout.MAX_GRID_ROWS; row++) {
            for (int column = 0; column < MechFittingLayout.MAX_GRID_COLUMNS; column++) {
                cells.add(new CapacityCell(index++, column, row,
                        column < target.gridColumns() && row < target.gridRows(),
                        x + column * (cellWidth + CAPACITY_GAP),
                        y + row * (cellHeight + CAPACITY_GAP), cellWidth, cellHeight));
            }
        }
        return List.copyOf(cells);
    }

    private static void drawCapacityCells(CanvasContext c, SocketDropTarget target,
                                          Color base, boolean selected,
                                          int occupiedColumns, int occupiedRows) {
        for (CapacityCell cell : capacityCells(target)) {
            boolean filled = cell.active() && cell.column() < occupiedColumns
                    && cell.row() < occupiedRows;
            c.fillRect(cell.x(), cell.y(), cell.width(), cell.height(),
                    cell.active() ? withAlpha(base, filled ? selected ? 220 : 135 : 32)
                            : new Color(0x18, 0x1E, 0x24, 215));
            c.strokeRect(cell.x(), cell.y(), cell.width(), cell.height(),
                    cell.active() ? withAlpha(base, selected ? 240 : 180)
                            : new Color(0x4B, 0x53, 0x5A, 190), 1f);
            if (!cell.active()) {
                c.line(cell.x() + 2f, cell.y() + 2f,
                        cell.x() + cell.width() - 2f,
                        cell.y() + cell.height() - 2f,
                        new Color(0x5A, 0x61, 0x68, 180), 1f);
            }
        }
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

    record CapacityCell(int index, int column, int row, boolean active,
                        float x, float y, float width, float height) { }

    record VacantGantryTarget(int index, float x, float y, float width, float height) {
        boolean contains(float pointX, float pointY) {
            return pointX >= x && pointX <= x + width
                    && pointY >= y && pointY <= y + height;
        }
    }

    record PadBounds(float left, float bottom, float right, float top) {
        float width() { return right - left; }
        float height() { return top - bottom; }
    }

    private static Color socketColor(SocketType type) {
        return switch (type) {
            case CORE -> new Color(0xF0, 0xC9, 0x52);
            case BALLISTIC -> new Color(0xE5, 0x83, 0x45);
            case MISSILE -> new Color(0x6D, 0xD5, 0xF2);
            case OMNI -> new Color(0xB5, 0x87, 0xF4);
            case AMMO -> new Color(0x9E, 0xBD, 0x6A);
            case UTILITY -> new Color(0xB1, 0x8B, 0xE8);
        };
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static void drawTechnicianFx(CanvasContext c, BattleCamera camera,
                                         CanvasHostViewport viewport,
                                         List<TechnicianWork> work, float time,
                                         SpriteAPI torch, SpriteAPI sparks) {
        float cellX = camera.cellPxSize() / viewport.scaleX();
        float cellY = camera.cellPxSize() / viewport.scaleY();
        float surfaceWidth = c.metrics().surfaceWidth();
        float surfaceHeight = c.metrics().surfaceHeight();
        for (int index = 0; index < work.size(); index++) {
            TechnicianWork technicianWork = work.get(index);
            AmbientTaskPose pose = technicianWork.pose();
            if (pose.activity() != AmbientActivity.WORKING) {
                continue;
            }
            float technicianX = camera.cellToScreenX(pose.worldX()) / viewport.scaleX();
            float technicianY = (viewport.height() - camera.cellToScreenY(pose.worldY()))
                    / viewport.scaleY();
            float focusX = camera.cellToScreenX(pose.focusX()) / viewport.scaleX();
            float focusY = (viewport.height() - camera.cellToScreenY(pose.focusY()))
                    / viewport.scaleY();
            if (focusX < -cellX || focusY < -cellY
                    || focusX > surfaceWidth + cellX || focusY > surfaceHeight + cellY) {
                continue;
            }
            float torchX = lerp(technicianX, focusX, 0.48f);
            float torchY = lerp(technicianY, focusY, 0.48f);
            float effectScale = technicianWork.fixtureTarget() ? 0.58f : 1f;
            c.sprite(WELDING_TORCH_PATH, torch,
                    torchX, torchY,
                    cellX * 0.42f * effectScale, cellY * 0.72f * effectScale,
                    pose.facingDegrees(), WHITE);
            int frame = Math.floorMod(
                    (int) Math.floor(time * 12f + index * 1.7f) + 2, 8);
            c.sprite(WELDING_SPARKS_PATH, sparks,
                    focusX, focusY,
                    cellX * 1.08f * effectScale, cellY * 1.08f * effectScale,
                    0f, WHITE, CanvasSpriteRegion.frame(4, 2, frame), CanvasBlend.ADDITIVE);
        }
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    /** One active torch, retaining whether its target is a compact deck fixture. */
    private record TechnicianWork(AmbientTaskPose pose, boolean fixtureTarget) { }

    private static int appearance(MechWeaponComponent component) {
        return component != null ? component.appearanceSelector : LayeredMechAppearance.POD_NONE;
    }

    /** Scene geometry expressed back in the canvas surface's top-left coordinates. */
    private record SceneProjection(float actorX, float actorY,
                                   float cellX, float cellY,
                                   float hullX, float hullY) {

        private static SceneProjection forLive(BattleCamera camera,
                                               CanvasHostViewport viewport,
                                               MechVariant variant,
                                               float berthX, float berthY) {
            float cell = camera.cellPxSize();
            float hull = UnitRenderService.layeredMechHullWidth(cell, variant.renderScale);
            float scaleX = viewport.scaleX();
            float scaleY = viewport.scaleY();
            return new SceneProjection(
                    camera.cellToScreenX(berthX) / scaleX,
                    (viewport.height() - camera.cellToScreenY(berthY)) / scaleY,
                    cell / scaleX, cell / scaleY,
                    hull / scaleX, hull / scaleY);
        }
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
                token("chassis-sirocco.png", 208, 208),
                token("foot.png", 44, 38), token("thigh-bone.png", 40, 112),
                token("chaingun-arm.png", 62, 112),
                token("linear-cannon-variant.png", 58, 138),
                token("heavy-cannon.png", 64, 128), token("srm-pod.png", 62, 88),
                token("lrm-pod.png", 76, 96),
                token("shoulder-laser-cannon.png", 76, 128),
                token("pulse-laser-arm.png", 62, 112),
                token("hegemony-bastion-autocannon.png", 64, 132),
                  token("pather-demolition-cannon.png", 72, 124),
                  token("lions-guard-thermal-lance.png", 76, 132),
                  token("muster-autogun.png", 62, 128),
                  token("quarry-breaker-cannon.png", 72, 124),
                  token("pioneer-rocket-cradle.png", 76, 112),
                  LayeredSpriteCache.headless(
                        "graphics/battle/marine-modular-topdown/marine-muzzle-flash.png",
                        48, 48));
    }

    private static LayeredSpriteCache token(String name, int width, int height) {
        return LayeredSpriteCache.headless(ROOT + name, width, height);
    }
}
