package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.battleview.MechSectionDollCanvas;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.markup.MarkupSource;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.fs.starfarer.api.Global;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * One mech's section paper-doll as a small read-only plate: the {@link MechSectionDollCanvas}
 * with a title and the CRIPPLED / SENSORS LOST flags. The same plate stands beside the
 * direct-control status panel while the player drives a mech and above the selected-mech panel; each host
 * owns one and places it. Display only: it takes no input.
 */
final class BattleMechSectionPlate {

    static final String COMPONENT = "battle-mech-sections";
    static final String COMPONENT_PATH = "data/ui/components/battle/" + COMPONENT + ".mlx";
    static final float DOCUMENT_WIDTH = 164f;
    static final float DOCUMENT_HEIGHT = 176f;
    static final float GAP = 6f;

    private static final String FLAG_OFF = "flag flag-off";
    private static final String FLAG_ON = "flag flag-on";
    private static final String SENSORS_OFF = "flag flag-sensors flag-off";
    private static final String SENSORS_ON = "flag flag-sensors flag-on";

    private final Reactor reactor = new Reactor();
    private final MutableSignal<String> crippledClasses = reactor.signal(FLAG_OFF);
    private final MutableSignal<String> sensorsClasses = reactor.signal(SENSORS_OFF);
    private final MechSectionDollCanvas.View view = new MechSectionDollCanvas.View();
    private final MarkupInstance markup;
    private final UiDocument document;

    BattleMechSectionPlate() {
        this(path -> Global.getSettings().loadText(path), Strings::get);
    }

    BattleMechSectionPlate(MarkupSource source, Function<String, String> copy) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("title", copy.apply("battleMechSectionsTitle"));
        props.put("crippled", copy.apply("battleMechSectionsCrippled"));
        props.put("sensors", copy.apply("battleMechSectionsSensorsLost"));
        props.put("crippledClasses", crippledClasses);
        props.put("sensorsClasses", sensorsClasses);
        MarkupLoader loader = new MarkupLoader(source, List.of(COMPONENT_PATH));
        markup = loader.reloadAndBuild(reactor, COMPONENT, props);
        document = BattleDirectControlOverlay.document(markup);
        document.layout(DOCUMENT_WIDTH, DOCUMENT_HEIGHT);
        document.canvases().set(markup.requireElement("mech-sections-doll"),
                new MechSectionDollCanvas(view, copy));
    }

    /** Shows {@code loadout}, or hides the plate when null. Allocation-free once laid out. */
    void update(MechLoadoutComponent loadout) {
        view.read(loadout);
        refresh();
    }

    /** The state the doll draws; a host or fixture may write it directly, then {@link #refresh}. */
    MechSectionDollCanvas.View view() {
        return view;
    }

    void refresh() {
        crippledClasses.set(view.crippled() ? FLAG_ON : FLAG_OFF);
        sensorsClasses.set(view.sensorsLost() ? SENSORS_ON : SENSORS_OFF);
        markup.flush();
    }

    boolean visible() {
        return view.present();
    }

    UiDocument document() {
        return document;
    }

    void render(UiViewport viewport, float alphaMult) {
        if (view.present() && viewport != null) document.render(viewport, alphaMult);
    }

    BattleHudSurface surface(UiViewport viewport) {
        return view.present() && viewport != null ? new BattleHudSurface(document, viewport) : null;
    }

    /** Above {@code anchor}, left edges level: over the selected-mech panel. */
    static UiViewport above(UiViewport anchor) {
        float scale = anchor.documentScale();
        return new UiViewport(anchor.screenX(), anchor.screenY() + anchor.height() + GAP * scale,
                DOCUMENT_WIDTH * scale, DOCUMENT_HEIGHT * scale, scale);
    }
}
