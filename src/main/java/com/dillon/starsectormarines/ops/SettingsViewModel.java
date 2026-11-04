package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** The Settings page's keys, each reading and writing the setting it shows. */
final class SettingsViewModel {
    private final BooleanSupplier voiceOver;
    private final Consumer<Boolean> setVoiceOver;
    private final MutableSignal<String> voiceOverLabel;
    private final MutableSignal<String> voiceOverClasses;

    SettingsViewModel(Reactor reactor, BooleanSupplier voiceOver, Consumer<Boolean> setVoiceOver) {
        this.voiceOver = voiceOver;
        this.setVoiceOver = setVoiceOver;
        voiceOverLabel = reactor.signal(label(voiceOver.getAsBoolean()));
        voiceOverClasses = reactor.signal(classes(voiceOver.getAsBoolean()));
    }

    void put(Map<String, Object> props) {
        props.put("voiceOverLabel", voiceOverLabel);
        props.put("voiceOverClasses", voiceOverClasses);
        props.put("voiceOverAction", (Runnable) this::toggleVoiceOver);
    }

    /** Settings live in the save, so another may have been loaded since this page last showed. */
    void refresh() {
        voiceOverLabel.set(label(voiceOver.getAsBoolean()));
        voiceOverClasses.set(classes(voiceOver.getAsBoolean()));
    }

    private void toggleVoiceOver() {
        setVoiceOver.accept(!voiceOver.getAsBoolean());
        refresh();
    }

    private static String label(boolean on) {
        return Strings.get(on ? "openingUiSettingsOn" : "openingUiSettingsOff");
    }

    private static String classes(boolean on) {
        return "settings-key console-action led-key" + (on ? " led-on" : "");
    }

    /** The page's properties with no campaign behind them. */
    static Map<String, Object> previewProps(boolean voiceOverOn) {
        Map<String, Object> props = new LinkedHashMap<>();
        new SettingsViewModel(new Reactor(), () -> voiceOverOn, on -> { }).put(props);
        Runnable none = () -> { };
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.SETTINGS, MarineOpsPageNav.ANY_SHIP,
                none, none, none, none, none, none, none, none);
        return props;
    }
}
