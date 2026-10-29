package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.objective.side.SideField;
import com.dillon.starsectormarines.battle.objective.side.SideObjectiveService.Offer;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.starsector.StarsectorUiInputAdapter;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import java.util.List;

/**
 * A battle-glass prompt hung over the place of a side objective that waits for a verb ({@code SideTrigger.Interact}):
 * shown only while the company is near it and it is in view, never under the right rail. Pressing a small button picks
 * the verb through {@code SideObjectiveService.choose}.
 */
final class BattleSidePromptOverlay {
    static final String COMPONENT = "battle-side-prompt";
    static final String COMPONENT_PATH = "data/ui/components/battle/" + COMPONENT + ".mlx";
    /** Document pixels between the place and the prompt's near edge. */
    private static final float GAP = 36f;
    private static final float EDGE = 8f;
    /** Real seconds a pressed offer stays hidden, so a paused battle still answers the press. */
    private static final float PRESSED_HIDE = 1.5f;

    private final Reactor reactor = new Reactor();
    private final BattleSidePromptModel model = new BattleSidePromptModel(reactor);
    private BattleCamera camera;
    private MarkupInstance markup;
    private UiDocument document;
    private StarsectorUiInputAdapter input;
    private UiViewport viewport;
    private PositionAPI position;
    private BattleSimulation sim;
    private Offer shown;
    private String pressedKey = "";
    private float pressedFor;

    void attach(PositionAPI position) { this.position = position; }

    void update(float dt, BattleSimulation sim, BattleCamera camera) {
        this.camera = camera;
        this.sim = sim;
        pressedFor = Math.max(0f, pressedFor - dt);
        Offer offer = sim == null || position == null || camera == null ? null : choose(sim);
        if (offer == null) { hide(); return; }
        float scale = MarineOpsUiViewport.from(position).documentScale();
        int w = BattleSidePromptModel.width(offer), h = BattleSidePromptModel.height(offer);
        float[] at = place(camera.cellToScreenX(offer.cellX() + .5f), camera.cellToScreenY(offer.cellY() + .5f),
                w * scale, h * scale, camera.vpX(), camera.vpY(), camera.vpW(), camera.vpH(),
                camera.coverLeft(), camera.coverBottom(), GAP * scale);
        if (at == null) { hide(); return; }
        if (document == null) install();
        if (!offer.equals(shown)) { model.show(offer, this::press); shown = offer; }
        markup.flush();
        UiViewport next = new UiViewport(at[0], at[1], w * scale, h * scale, scale);
        if (!next.equals(viewport) || input == null) {
            document.deactivateInput();
            viewport = next;
            document.layout(w, h);
            input = new StarsectorUiInputAdapter(document, next);
        }
        document.advance(dt);
    }

    /** The offer to draw: of those on offer and in view, the one nearest the centre of the view. */
    private Offer choose(BattleSimulation sim) {
        Offer best = null;
        float bestD = Float.MAX_VALUE;
        for (Offer offer : sim.getSideObjectives().offers(SideField.of(sim))) {
            if (pressedFor > 0f && offer.key().equals(pressedKey)) continue;
            float sx = camera.cellToScreenX(offer.cellX() + .5f), sy = camera.cellToScreenY(offer.cellY() + .5f);
            if (!inView(sx, sy, camera.vpX(), camera.vpY(), camera.vpW(), camera.vpH(), camera.coverLeft(), camera.coverBottom())) continue;
            float d = Math.abs(sx - (camera.vpX() + camera.vpW() / 2f)) + Math.abs(sy - (camera.vpY() + camera.vpH() / 2f));
            if (d < bestD) { bestD = d; best = offer; }
        }
        return best;
    }

    private void press(String key, String verb) {
        if (sim != null) sim.getSideObjectives().choose(key, verb);
        pressedKey = key;
        pressedFor = PRESSED_HIDE;
    }

    private void install() {
        MarkupLoader loader = new MarkupLoader(path -> Global.getSettings().loadText(path), List.of(COMPONENT_PATH));
        markup = loader.reloadAndBuild(reactor, COMPONENT, model.props());
        document = new UiDocument(markup.root());
        for (var style : markup.styles()) document.addStyleSheet(style);
        document.theme(MarineOpsThemes.standard());
    }

    /** Whether a point is in the camera's view and clear of the rail the HUD covers. */
    static boolean inView(float x, float y, float vpX, float vpY, float vpW, float vpH, float coverLeft, float coverBottom) {
        return x >= vpX + EDGE && x < vpX + vpW - EDGE && y >= vpY + EDGE && y < vpY + vpH - EDGE
                && !(x >= coverLeft && y >= coverBottom);
    }

    /**
     * Where a {@code w} by {@code h} prompt goes for a place at {@code (x, y)}: centred above it, or below when there is
     * no room, clamped into the view and kept left of the rail. Screen pixels, y up; the prompt's bottom-left corner,
     * or null when the place is out of view.
     */
    static float[] place(float x, float y, float w, float h, float vpX, float vpY, float vpW, float vpH,
                         float coverLeft, float coverBottom, float gap) {
        if (!inView(x, y, vpX, vpY, vpW, vpH, coverLeft, coverBottom)) return null;
        float bottom = y + gap;
        if (bottom + h > vpY + vpH - EDGE) bottom = y - gap - h;
        float left = Math.max(vpX + EDGE, Math.min(x - w / 2f, vpX + vpW - EDGE - w));
        if (bottom + h > coverBottom) left = Math.min(left, coverLeft - EDGE - w);
        return new float[] {left, bottom};
    }

    void processInput(List<InputEventAPI> events) {
        if (viewport == null || input == null || events == null) return;
        var pointer = events.stream().filter(event -> !event.isConsumed()
                && (event.isMouseMoveEvent() || blocksWorldPointer(event.getX(), event.getY())
                && (event.isLMBDownEvent() || event.isLMBUpEvent() || event.isRMBDownEvent() || event.isRMBUpEvent()))).toList();
        input.process(pointer);
        for (var event : pointer) if (!event.isMouseMoveEvent()) event.consume();
    }

    boolean blocksWorldPointer(float x, float y) {
        return viewport != null && input != null && x >= viewport.screenX() && x < viewport.screenX() + viewport.width()
                && y >= viewport.screenY() && y < viewport.screenY() + viewport.height();
    }

    void render(float alpha) {
        if (viewport != null && document != null && input != null) document.render(viewport, alpha);
    }

    void detach() { hide(); }

    private void hide() {
        if (document != null) document.deactivateInput();
        input = null;
        viewport = null;
        shown = null;
    }
}
