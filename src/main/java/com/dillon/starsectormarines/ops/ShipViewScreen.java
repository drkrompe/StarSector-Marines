package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.battleview.ShipViewCanvas;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.CameraControls;
import com.dillon.starsectormarines.ui.retained.CanvasMetrics;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.starsector.StarsectorUiInputAdapter;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The company ship as a vessel, with the camera in the player's hands.
 *
 * <p>The one page that is not standing in a compartment. Every other room view
 * answers "what is this room like"; this answers "what is this ship like", and
 * that is a question a fixed frame cannot be asked — the interesting facts
 * about a hull are how far the berthings run, what sits between the bay and the
 * armory, and how much of her is passage, none of which survive being cropped
 * to one room.
 *
 * <p>Passive by design. Nothing here is commanded and nothing is selected; the
 * watch goes about its business and the player watches it. See
 * {@link ShipViewCanvas}.
 *
 * <p>The camera and the gestures that fly it are the same ones the battle uses
 * ({@link CameraControls}), because a second set would be a second answer to
 * which button pans.
 */
public final class ShipViewScreen implements Screen {

    private static final String ROOT_COMPONENT = "ship-view";
    private static final List<String> COMPONENT_PATHS = List.of(
            "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/company/ship-view.mlx");

    private final Reactor reactor = new Reactor();
    private final MarkupLoader markup = new MarkupLoader(
            path -> Global.getSettings().loadText(path), COMPONENT_PATHS);
    /** Nothing on this page claims shift plus right-drag, so nothing is reserved. */
    private final CameraControls controls = new CameraControls();

    private MarineOpsContext context;
    private Runnable dismissDialog;
    private UiViewport viewport;
    private UiDocument document;
    private MarkupInstance markupInstance;
    private StarsectorUiInputAdapter input;
    private ShipViewCanvas deck;
    private UiElement deckElement;

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        context = ctx;
        this.dismissDialog = dismissDialog;
        // There is no ship to look at until the company has one. Headquarters
        // is reachable without a ship on purpose — it is the company rather
        // than a room — so this page can be asked for by a company that has
        // nowhere to live, and the answer is the choice rather than a crash.
        if (ctx.companyDeck() == null) {
            ctx.goTo(ScreenId.SHIP_TRANSFER);
            return;
        }
        viewport = MarineOpsUiViewport.from(position);
        installDocument();
        document.layout(viewport.documentWidth(), viewport.documentHeight());
        input = new StarsectorUiInputAdapter(document, viewport);
    }

    private void installDocument() {
        MarkupInstance candidate = markup.reloadAndBuild(reactor, ROOT_COMPONENT, props());
        UiDocument built;
        try {
            for (String id : List.of("ship-view-root", "marine-ops-page-nav",
                    "page-nav-return", "ship-view-stage", "ship-view-deck")) {
                candidate.requireElement(id);
            }
            built = new UiDocument(candidate.root());
            for (var style : candidate.styles()) built.addStyleSheet(style);
            built.theme(MarineOpsThemes.standard()).onCancel(this::close);
            deck = new ShipViewCanvas(context.companyDeck());
            deckElement = candidate.requireElement("ship-view-deck");
            built.canvases().set(deckElement, deck);
            if (viewport != null) {
                built.layout(viewport.documentWidth(), viewport.documentHeight());
            }
        } catch (RuntimeException failure) {
            candidate.close();
            throw failure;
        }

        UiDocument previousDocument = document;
        MarkupInstance previousInstance = markupInstance;
        document = built;
        markupInstance = candidate;
        if (previousDocument != null) previousDocument.deactivateInput();
        if (previousInstance != null) previousInstance.close();
    }

    private Map<String, Object> props() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("roomTitle", "COMPANY SHIP  //  UNDERWAY");
        props.put("roomCopy", "Drag to look around her. Wheel to close in, WASD to walk the view.");
        props.put("contextLabel", "COMPANY SHIP / UNDERWAY");
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.SHIP_VIEW,
                context::roomAboard,
                this::close,
                () -> context.goTo(ScreenId.COMPANY_HQ),
                () -> context.goTo(ScreenId.BARRACKS),
                () -> context.openCompanyArmoryFrom(ScreenId.SHIP_VIEW),
                () -> context.goTo(ScreenId.MECH_LAB));
        return props;
    }

    private void close() {
        if (dismissDialog != null) dismissDialog.run();
    }

    @Override
    public void advance(float dt) {
        if (markupInstance != null) markupInstance.flush();
        if (document != null) document.advance(dt);
        if (deck != null) controls.advance(dt, deck.camera());
    }

    @Override
    public void render(float alphaMult) {
        if (document != null && viewport != null) document.render(viewport, alphaMult);
    }

    /**
     * The camera is flown from raw events rather than from pointer callbacks,
     * because the wheel is not a gesture the retained layer carries and a zoom
     * that only worked with a button held would be a strange one. The controls
     * are told how to read a pointer into the camera's own pixels rather than
     * being handed translated events, so nothing has to impersonate an
     * {@code InputEventAPI}.
     *
     * <p>The canvas is asked for that last step rather than it being repeated
     * here. Where the camera lives is the canvas's business — it is the thing
     * that has to draw a backdrop in one space and a deck in the other — and a
     * second copy of the conversion is how a drag ends up running at a
     * different speed from the picture it is dragging.
     */
    @Override
    public void processInput(List<InputEventAPI> events) {
        BattleCamera camera = deck == null ? null : deck.camera();
        if (camera != null && document != null && deckElement != null
                && viewport != null && events != null) {
            CanvasMetrics metrics = document.canvasMetrics(deckElement, 1f);
            controls.process(events, camera, new CameraControls.PointerSpace() {
                @Override
                public float x(float screenX) {
                    return deck.toCameraX(metrics.toCanvasX(viewport.documentX(screenX)));
                }

                @Override
                public float y(float screenY) {
                    return deck.toCameraY(metrics.toCanvasY(viewport.documentY(screenY)));
                }
            });
        }
        if (input != null) input.process(events);
    }

    @Override
    public void detach() {
        if (document != null) document.deactivateInput();
        // A screen that stops receiving input never sees the key-up, and a
        // camera left panning is what the player comes back to.
        controls.release();
        input = null;
    }
}
