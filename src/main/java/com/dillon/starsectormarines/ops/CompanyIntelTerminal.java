package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.Rect;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The command deck's live wiring: the banner canvas, the station buttons laid over its
 * people, and the dossier read sweep. Its clocks survive ordinary document refresh.
 *
 * <p>Stations are real buttons, positioned after layout from the same cover-fit the
 * banner paints with, so a station stays over its people at any banner size. Using
 * one moves focus to the panel that already holds that information and briefly marks
 * it, or, for the force, opens Barracks; the picture never holds a fact of its own.
 */
final class CompanyIntelTerminal {
    static final String STATION_PREFIX = "company-hq-station-";
    private static final String PULSE_CLASS = "deck-pulse";
    private static final float PULSE_SECONDS = 1.2f;
    /**
     * Inline because the theme is the cascade's final layer and dresses every button;
     * the station's outline is drawn by the banner instead, so it sits on the picture.
     */
    private static final String CLEAR = "background-color: transparent; background-image: none; "
            + "border-width: 0px; opacity: 1;";

    private final CommandDeckBanner banner;
    private final Runnable openForce;
    private final Map<String, UiElement> stations = new LinkedHashMap<>();
    private UiDocument document;
    private MarkupInstance instance;
    private UiElement deckElement;
    private UiElement canvasElement;
    private UiElement scanElement;
    private float placedWidth = -1f;
    private float placedHeight = -1f;
    private UiElement pulsed;
    private float pulseLeft;

    /** {@code openForce} is where the force station sends the player: the roster lives in Barracks. */
    CompanyIntelTerminal(CompanyHqNewsDesk desk, CommandDeckLayout layout, Runnable openForce) {
        banner = new CommandDeckBanner(desk, layout);
        this.openForce = openForce;
    }

    /** The station actions the markup binds, before the document exists. */
    void put(Map<String, Object> props) {
        for (CommandDeckLayout.Station station : banner.layout().stations()) {
            String id = station.id();
            props.put("deck" + Character.toUpperCase(id.charAt(0)) + id.substring(1), (Runnable) () -> use(id));
        }
    }

    void bind(UiDocument document, MarkupInstance instance) {
        this.document = document;
        this.instance = instance;
        deckElement = instance.requireElement("company-hq-deck");
        canvasElement = instance.requireElement("company-hq-deck-canvas");
        scanElement = instance.requireElement("company-hq-dossier-scan");
        stations.clear();
        for (CommandDeckLayout.Station station : banner.layout().stations()) {
            stations.put(station.id(), instance.requireElement(STATION_PREFIX + station.id()));
        }
        pulsed = null;
        placedWidth = placedHeight = -1f;
        document.canvases().set(canvasElement, banner);
        document.canvases().set(scanElement, this::dossierScan);
        advance(0f);
    }

    /**
     * After layout: give the canvas a surface the size of its box, so the plate is
     * cover-fitted rather than stretched, and lay each station over its people.
     * Returns true when anything moved, so the caller can lay the document out again.
     */
    boolean place() {
        if (deckElement == null) return false;
        Rect box = deckElement.box().contentBox();
        float width = box.width();
        float height = box.height();
        if (width <= 0f || height <= 0f) return false;
        if (Math.abs(width - placedWidth) < 0.5f && Math.abs(height - placedHeight) < 0.5f) return false;
        placedWidth = width;
        placedHeight = height;
        canvasElement.canvasSize(Math.max(1, Math.round(width)), Math.max(1, Math.round(height)));
        CommandDeckLayout layout = banner.layout();
        CommandDeckLayout.Fit fit = CommandDeckLayout.Fit.cover(width, height, layout.width(), layout.height());
        for (CommandDeckLayout.Station station : layout.stations()) {
            Rect rect = stationRect(station, fit, layout, width, height);
            stations.get(station.id()).style(String.format(Locale.ROOT,
                    "left: %.0fpx; top: %.0fpx; width: %.0fpx; height: %.0fpx; " + CLEAR,
                    rect.x(), rect.y(), rect.width(), rect.height()));
        }
        return true;
    }

    /** A station's rectangle in banner pixels, clipped to the banner. */
    static Rect stationRect(CommandDeckLayout.Station station, CommandDeckLayout.Fit fit,
                            CommandDeckLayout layout, float width, float height) {
        float x0 = Math.max(0f, fit.x(station.fx() * layout.width()));
        float y0 = Math.max(0f, fit.y(station.fy() * layout.height()));
        float x1 = Math.min(width, fit.x((station.fx() + station.fw()) * layout.width()));
        float y1 = Math.min(height, fit.y((station.fy() + station.fh()) * layout.height()));
        return new Rect(x0, y0, Math.max(0f, x1 - x0), Math.max(0f, y1 - y0));
    }

    void advance(float dt) {
        banner.advance(dt);
        String hot = null;
        for (Map.Entry<String, UiElement> entry : stations.entrySet()) {
            UiElement button = entry.getValue();
            if (button.hovered() || button.focusVisible()) hot = entry.getKey();
        }
        banner.hot(hot);
        if (pulsed != null && Float.isFinite(dt) && dt > 0f) {
            pulseLeft -= dt;
            if (pulseLeft <= 0f) {
                pulsed.removeClass(PULSE_CLASS);
                pulsed = null;
            }
        }
        if (document != null) {
            document.canvases().invalidate(canvasElement);
            document.canvases().invalidate(scanElement);
        }
    }

    /** Point at the panel a station stands for. */
    void use(String stationId) {
        if (instance == null) return;
        switch (stationId) {
            case "comms" -> {
                focusFirst(instance.requireElement("company-hq-channel-tabs"));
                pulse("company-hq-source-windows");
            }
            case "situation" -> {
                if (!focusFirst(instance.requireElement("company-hq-obligation-list"))) {
                    pulse("company-hq-board-heading");
                } else {
                    pulse("company-hq-obligations");
                }
            }
            case "castle" -> {
                UiElement guide = instance.requireElement("company-hq-opening-guide");
                if (!guide.hasClass("opening-guide-hidden")) focusFirst(guide);
                pulse("company-hq-deck-caption");
            }
            case "finance" -> pulse("company-hq-finance");
            case "force" -> openForce.run();
            default -> { }
        }
    }

    private void pulse(String id) {
        if (pulsed != null) pulsed.removeClass(PULSE_CLASS);
        pulsed = instance.requireElement(id).addClass(PULSE_CLASS);
        pulseLeft = PULSE_SECONDS;
    }

    private boolean focusFirst(UiElement root) {
        UiElement target = firstFocusable(root);
        if (target == null || document == null) return false;
        document.requestFocus(target, true);
        return true;
    }

    private static UiElement firstFocusable(UiElement element) {
        if (element.focusable() && !element.disabled()) return element;
        for (UiElement child : element.children()) {
            UiElement found = firstFocusable(child);
            if (found != null) return found;
        }
        return null;
    }

    private void dossierScan(CanvasContext context) {
        float reveal = banner.motion().selectionReveal();
        if (reveal >= 1f) return;
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        float edge = width * reveal;
        // A brief archive read sweep; report text never changes to fake decoded prose.
        context.fillRect(edge, 0f, width - edge, height, new Color(10, 21, 23, 205));
        for (int trail = 5; trail >= 0; trail--) {
            float x = Math.max(0f, edge - trail * 5f);
            context.fillRect(x, 0f, 3f, height, new Color(98, 210, 194, 12 + (5 - trail) * 8));
        }
    }
}
