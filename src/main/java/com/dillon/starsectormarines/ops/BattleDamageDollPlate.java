package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.vehicle.HullTurretSection;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.battleview.DamageDollCanvas;
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
 * One machine's damage doll as a small read-only plate: the {@link DamageDollCanvas} above two
 * state flags (CRIPPLED / SENSORS LOST for a mech, IMMOBILE / GUN LOST for a
 * vehicle). The same plate stands beside the direct-control status panel while the player drives
 * a mech or a vehicle, and above the selected-mech panel; each host owns one and places it.
 * Display only: it takes no input.
 */
final class BattleDamageDollPlate {

    static final String COMPONENT = "battle-damage-doll";
    static final String COMPONENT_PATH = "data/ui/components/battle/" + COMPONENT + ".mlx";
    static final float DOCUMENT_WIDTH = 178f;
    static final float DOCUMENT_HEIGHT = 145f;
    static final float GAP = 6f;

    private static final String FLAG_OFF = "flag flag-off";
    private static final String FLAG_ON = "flag flag-on";
    private static final String WIDE_OFF = "flag flag-wide flag-off";
    private static final String WIDE_ON = "flag flag-wide flag-on";

    private final Reactor reactor = new Reactor();
    private final String crippled;
    private final String sensorsLost;
    private final String immobile;
    private final String gunLost;
    private final MutableSignal<String> firstFlag;
    private final MutableSignal<String> secondFlag;
    private final MutableSignal<String> firstClasses = reactor.signal(FLAG_OFF);
    private final MutableSignal<String> secondClasses = reactor.signal(WIDE_OFF);
    private final DamageDollCanvas.View view = new DamageDollCanvas.View();
    private final MarkupInstance markup;
    private final UiDocument document;
    private boolean vehicle;
    private boolean firstOn;
    private boolean secondOn;

    BattleDamageDollPlate() {
        this(path -> Global.getSettings().loadText(path), Strings::get);
    }

    BattleDamageDollPlate(MarkupSource source, Function<String, String> copy) {
        crippled = copy.apply("battleMechSectionsCrippled");
        sensorsLost = copy.apply("battleMechSectionsSensorsLost");
        immobile = copy.apply("battleDamageDollImmobile");
        gunLost = copy.apply("battleDamageDollGunLost");
        firstFlag = reactor.signal(crippled);
        secondFlag = reactor.signal(sensorsLost);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("first", firstFlag);
        props.put("second", secondFlag);
        props.put("firstClasses", firstClasses);
        props.put("secondClasses", secondClasses);
        MarkupLoader loader = new MarkupLoader(source, List.of(COMPONENT_PATH));
        markup = loader.reloadAndBuild(reactor, COMPONENT, props);
        document = BattleDirectControlOverlay.document(markup);
        document.layout(DOCUMENT_WIDTH, DOCUMENT_HEIGHT);
        document.canvases().set(markup.requireElement("damage-doll-canvas"), new DamageDollCanvas(view));
    }

    /** Shows {@code loadout}, or hides the plate when null. */
    void update(MechLoadoutComponent loadout) {
        view.read(loadout);
        vehicle = false;
        firstOn = view.crippled();
        secondOn = view.sensorsLost();
        refresh();
    }

    /** Shows a vehicle from its hull pools and turret section (null when its gun is part of the hull). */
    void updateVehicle(VehicleType type, float hp, float maxHp, float armor, float maxArmor,
                       HullTurretSection turret) {
        view.readVehicle(type, hp, maxHp, armor, maxArmor, turret);
        vehicle = true;
        firstOn = false;
        secondOn = turret != null && turret.destroyed();
        refresh();
    }

    void clear() {
        view.clear();
        refresh();
    }

    /** The state the doll draws; a fixture may write it directly, then {@link #refresh}. */
    DamageDollCanvas.View view() {
        return view;
    }

    void refresh() {
        firstFlag.set(vehicle ? immobile : crippled);
        secondFlag.set(vehicle ? gunLost : sensorsLost);
        firstClasses.set(firstOn ? FLAG_ON : FLAG_OFF);
        secondClasses.set(secondOn ? WIDE_ON : WIDE_OFF);
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
