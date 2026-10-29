package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveProjection;
import com.dillon.starsectormarines.battle.command.objective.SideObjectiveRows;
import com.dillon.starsectormarines.battle.objective.side.SideField;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.battle.air.LiftQueueReadout;
import com.dillon.starsectormarines.battle.power.ArmorDropPower;
import com.dillon.starsectormarines.battle.power.CommandPowerService;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.retained.Rect;
import com.dillon.starsectormarines.ui.retained.UiAlign;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.starsector.StarsectorUiInputAdapter;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Host bridge for the MLX-authored battle overlay: time and objectives on the right rail, the
 * lift queue on the left.
 */
final class BattleHudOverlay {

    static final String COMPONENT = "battle-hud-overlay";
    static final String COMPONENT_PATH =
            "data/ui/components/battle/battle-hud-overlay.mlx";
    static final float RAIL_WIDTH = 360f;
    static final float TIME_ONLY_HEIGHT = 54f;
    static final float OBJECTIVE_HEIGHT = BattleLayout.COMMAND_RAIL_H;
    /** Document pixels of the lift queue's border and padding, outside its content box. */
    private static final float LIFT_FRAME = 2f + 14f;
    /** Document pixels of the lift queue's frame, header and the gap under it. */
    private static final float LIFT_CHROME = LIFT_FRAME + 17f + 4f;
    /** A row's outer height: 14 px of content, 2 px padding and a 1 px border above and below. */
    private static final float LIFT_ROW = 20f;
    private static final float LIFT_ROW_GAP = 3f;
    private static final float EDGE_INSET = 12f;

    private final Reactor reactor = new Reactor();
    private final MarkupLoader markup = new MarkupLoader(
            path -> Global.getSettings().loadText(path), List.of(COMPONENT_PATH));
    private final BattleHudOverlayModel model;
    private final BattleCamera camera;

    private PositionAPI position;
    private UiViewport viewport;
    private UiDocument document;
    private MarkupInstance markupInstance;
    private StarsectorUiInputAdapter input;
    private boolean primaryPointerActive;
    private boolean secondaryPointerActive;
    private BattleHudOverlayModel.Presentation presentation =
            new BattleHudOverlayModel.Presentation(false);
    /** The battle the lift queue's cancel buttons act on: the one last projected. */
    private BattleSimulation liftSim;

    BattleHudOverlay(Consumer<Float> speedSetter, BattleCamera camera) {
        this.camera = camera;
        model = new BattleHudOverlayModel(reactor, speedSetter,
                Strings.get("battleSpeedPause"), Strings.get("battleSpeed1x"),
                Strings.get("battleSpeed2x"), Strings.get("battleSpeed4x"));
    }

    void attach(PositionAPI nextPosition, BattleSimulation sim, float speedMultiplier) {
        position = nextPosition;
        presentation = updateModel(sim, speedMultiplier);
        viewport = viewport(nextPosition, presentation);
        if (document == null) installDocument();
        else {
            markupInstance.flush();
            document.layout(viewport.documentWidth(), viewport.documentHeight());
        }
        input = new StarsectorUiInputAdapter(document, viewport);
        coverCamera();
    }

    /** Tells the camera where the right rail sits, so world labels stay out from under it. */
    private void coverCamera() {
        Rect rail = markupInstance.requireElement("battle-command-rail").box().borderBox();
        camera.coverTopRight(viewport.screenXFor(rail.x()), viewport.screenBottomFor(rail));
    }

    private void installDocument() {
        MarkupInstance candidate = markup.reloadAndBuild(
                reactor, COMPONENT, model.props());
        UiDocument built;
        try {
            requireWiredElements(candidate);
            wireLayout(candidate);
            built = new UiDocument(candidate.root());
            for (var style : candidate.styles()) built.addStyleSheet(style);
            built.theme(MarineOpsThemes.standard());
            built.layout(viewport.documentWidth(), viewport.documentHeight());
        } catch (RuntimeException failure) {
            candidate.close();
            throw failure;
        }
        document = built;
        markupInstance = candidate;
    }

    private static void requireWiredElements(MarkupInstance instance) {
        for (String id : List.of(
                "battle-hud-overlay", "battle-time-control", "battle-time-pause",
                "battle-time-normal", "battle-time-double", "battle-time-quad",
                "battle-command-rail", "battle-hud-spacer",
                "battle-objectives", "battle-objective-chips",
                "battle-objective-score", "battle-objective-tally",
                "battle-objective-focus", "battle-objective-progress-fill",
                "battle-lift-queue", "battle-lift-queue-rows")) {
            instance.requireElement(id);
        }
    }

    /** CSS has no item-alignment property yet; keep the progress fill left-anchored. */
    static void wireLayout(MarkupInstance instance) {
        instance.requireElement("battle-lift-queue").align(UiAlign.START, UiAlign.START);
        instance.requireElement("battle-objective-progress-fill")
                .align(UiAlign.START, UiAlign.STRETCH);
    }

    void update(float realDt, BattleSimulation sim, float speedMultiplier) {
        BattleHudOverlayModel.Presentation next = updateModel(sim, speedMultiplier);
        if (markupInstance != null) markupInstance.flush();
        if (!next.equals(presentation) && position != null) {
            presentation = next;
            viewport = viewport(position, presentation);
            document.layout(viewport.documentWidth(), viewport.documentHeight());
            input = new StarsectorUiInputAdapter(document, viewport);
            coverCamera();
        }
        if (document != null) document.advance(realDt);
    }

    void render(float alphaMult) {
        if (document != null && viewport != null) document.render(viewport, alphaMult);
    }

    BattleHudSurface surface() {
        return document == null || viewport == null ? null : new BattleHudSurface(document, viewport);
    }

    float rightRailBottom() {
        return viewport == null ? Float.POSITIVE_INFINITY : viewport.screenY();
    }

    void processInput(List<InputEventAPI> events) {
        if (input == null || events == null || viewport == null
                || markupInstance == null) return;
        List<InputEventAPI> retained = new ArrayList<>(events.size());
        for (InputEventAPI event : events) {
            if (event.isConsumed()) {
                clearReleasedPointer(event);
                continue;
            }
            boolean pointerEvent = event.isLMBDownEvent() || event.isLMBUpEvent()
                    || event.isRMBDownEvent() || event.isRMBUpEvent()
                    || event.isMouseScrollEvent();
            boolean pointerMovement = event.isMouseMoveEvent();
            boolean inside = (pointerEvent || pointerMovement)
                    && insideInteractiveSurface(
                    viewport.documentX(event.getX()),
                    viewport.documentY(event.getY()));
            boolean activeRelease = (event.isLMBUpEvent() && primaryPointerActive)
                    || (event.isRMBUpEvent() && secondaryPointerActive);
            if (pointerMovement || !pointerEvent || inside || activeRelease) {
                retained.add(event);
            }
            if (event.isLMBDownEvent() && inside) primaryPointerActive = true;
            if (event.isRMBDownEvent() && inside) secondaryPointerActive = true;
            clearReleasedPointer(event);
        }
        input.process(retained);
    }

    private void clearReleasedPointer(InputEventAPI event) {
        if (event.isLMBUpEvent()) primaryPointerActive = false;
        if (event.isRMBUpEvent()) secondaryPointerActive = false;
    }

    private boolean insideInteractiveSurface(float documentX, float documentY) {
        return insideInteractiveSurface(markupInstance, documentX, documentY);
    }

    boolean blocksWorldPointer(float screenX, float screenY) {
        return viewport != null && insideInteractiveSurface(
                viewport.documentX(screenX), viewport.documentY(screenY));
    }

    /** The wide retained document uses a transparent spacer between its two rails. */
    static boolean insideInteractiveSurface(MarkupInstance instance,
                                            float documentX, float documentY) {
        if (instance == null) return false;
        return instance.requireElement("battle-lift-queue")
                .box().borderBox().contains(documentX, documentY)
                || instance.requireElement("battle-command-rail")
                .box().borderBox().contains(documentX, documentY);
    }

    void detach() {
        if (document != null) document.deactivateInput();
        input = null;
        camera.coverTopRight(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        primaryPointerActive = false;
        secondaryPointerActive = false;
    }

    static UiViewport viewport(PositionAPI position,
                               BattleHudOverlayModel.Presentation presentation) {
        UiViewport host = MarineOpsUiViewport.from(position);
        float scale = host.documentScale();
        float documentHeight = documentHeight(presentation);
        float physicalWidth = Math.max(0f, position.getWidth() - 2f * EDGE_INSET);
        float physicalHeight = documentHeight * scale;
        return new UiViewport(
                position.getX() + EDGE_INSET,
                position.getY() + position.getHeight() - EDGE_INSET - physicalHeight,
                physicalWidth, physicalHeight, scale);
    }

    static float documentHeight(BattleHudOverlayModel.Presentation presentation) {
        float rail = presentation.missionRows() > 0 || presentation.optionalTotal() > 0
                ? Math.max(300, 96 + presentation.missionRows() * (presentation.compact()
                        ? BattleHudOverlayModel.COMPACT_MISSION_ROW_HEIGHT : BattleHudOverlayModel.MISSION_ROW_HEIGHT)
                        + (presentation.compact()
                        ? BattleHudOverlayModel.optionalHeight(presentation.optionalRows(), presentation.optionalTotal())
                        : BattleHudOverlayModel.optionalHeight(presentation.optionalRows())))
                : presentation.objectivesVisible() ? OBJECTIVE_HEIGHT : TIME_ONLY_HEIGHT;
        return presentation.liftRows() > 0 ? Math.max(rail, liftHeight(presentation.liftRows())) : rail;
    }

    /** The lift queue's height in document pixels for {@code rows} rows. */
    static float liftHeight(int rows) {
        int shown = Math.max(1, rows);
        return LIFT_CHROME + shown * LIFT_ROW + (shown - 1) * LIFT_ROW_GAP;
    }

    /** The lift queue's content-box height, which its style sets, for {@code rows} rows. */
    static float liftContentHeight(int rows) {
        return liftHeight(rows) - LIFT_FRAME;
    }

    private BattleHudOverlayModel.Presentation updateModel(
            BattleSimulation sim, float speedMultiplier) {
        int liftRows = updateLiftQueue(sim);
        return projectObjectives(sim, speedMultiplier).withLiftRows(liftRows);
    }

    private int updateLiftQueue(BattleSimulation sim) {
        liftSim = sim;
        LiftQueueReadout.Reading reading = sim == null ? LiftQueueReadout.Reading.EMPTY
                : LiftQueueReadout.capture(sim);
        List<BattleHudOverlayModel.Placed> placed = new ArrayList<>();
        if (sim != null) {
            CommandPowerService service = sim.getCommandPowerService();
            for (CommandPowerService.PendingDrop drop : service.pendingDrops()) {
                if (service.getPower(drop.powerId()) instanceof ArmorDropPower power) {
                    placed.add(new BattleHudOverlayModel.Placed(drop.powerId(), drop.manifestIndex(),
                            power.manifest().get(drop.manifestIndex()).load()));
                }
            }
        }
        return model.updateLiftQueue(reading, placed,
                job -> { if (liftSim != null) liftSim.getCommandPowerService().requestDropCancel(job); },
                drop -> {
                    if (liftSim != null) {
                        liftSim.getCommandPowerService().withdrawPendingDrop(drop.powerId(), drop.manifestIndex());
                    }
                });
    }

    private BattleHudOverlayModel.Presentation projectObjectives(BattleSimulation sim, float speedMultiplier) {
        var result = model.update(speedMultiplier,
                sim == null ? List.of() : sim.getCompoundService().getRecords());
        var mission = MissionObjectiveProjection.capture(sim);
        if (!mission.objectives().isEmpty()) return model.updateMission(mission,
                sim == null ? Set.of() : SideObjectiveRows.far(sim.getSideObjectives(), SideField.of(sim)),
                marker -> camera.centerOn(marker.x() + .5f, marker.y() + .5f));
        if (sim != null && sim.getThunderRun() != null) {
            var column = sim.getThunderRun();
            model.updateFreight(column.phase(), column.cargoIdentities(), column.cargoStates(), column.deliveredCount(), column.lostCount());
            model.updateFreightHold(column.holdSite(), column.holdRemainingTicks());
        }
        if (sim != null && sim.getColdAccount() != null) {
            var intervention = sim.getColdAccount();
            return model.updateIntervention(intervention.phase(), intervention.displayName(),
                    intervention.remainingWalls(), intervention.initialWalls(), intervention.companyRecovered());
        }
        if (sim != null) {
            for (var objective : sim.getObjectives()) {
                if (objective instanceof RaidObjective raid) {
                    return model.updateRaid(raid.targetName(), raid.phase(),
                            raid.serviceProgress(), raid.serviceDuration(), () -> {
                                boolean returning = raid.targetSecured();
                                camera.centerOn((returning ? raid.egressCellX() : raid.targetCellX()) + .5f,
                                        (returning ? raid.egressCellY() : raid.targetCellY()) + .5f);
                            });
                }
            }
        }
        return result;
    }
}
