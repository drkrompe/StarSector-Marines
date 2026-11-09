package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** The Settings page's keys, each reading and writing the setting it shows. */
final class SettingsViewModel {

    /** One on/off setting: where it is read and written, and the property prefix its card binds to. */
    record Setting(String prop, BooleanSupplier read, Consumer<Boolean> write) {}

    private final List<Key> keys;
    private final BooleanSupplier nexerelin;
    private final MutableSignal<String> nexerelinCardClasses;

    /**
     * @param settings  the page's settings, bound as {@code <prop>Label}, {@code <prop>Classes} and
     *                  {@code <prop>Action}
     * @param nexerelin whether Nexerelin is enabled; its cards ({@code nexerelinCardClasses}) show only then
     */
    SettingsViewModel(Reactor reactor, List<Setting> settings, BooleanSupplier nexerelin) {
        this.keys = settings.stream().map(setting -> new Key(reactor, setting)).toList();
        this.nexerelin = nexerelin;
        nexerelinCardClasses = reactor.signal(cardClasses(nexerelin.getAsBoolean()));
    }

    void put(Map<String, Object> props) {
        for (Key key : keys) key.put(props);
        props.put("nexerelinCardClasses", nexerelinCardClasses);
    }

    /** Settings live in the save, so another may have been loaded since this page last showed. */
    void refresh() {
        for (Key key : keys) key.refresh();
        nexerelinCardClasses.set(cardClasses(nexerelin.getAsBoolean()));
    }

    private static final class Key {
        private final Setting setting;
        private final MutableSignal<String> label;
        private final MutableSignal<String> classes;

        Key(Reactor reactor, Setting setting) {
            this.setting = setting;
            boolean on = setting.read().getAsBoolean();
            label = reactor.signal(label(on));
            classes = reactor.signal(classes(on));
        }

        void put(Map<String, Object> props) {
            props.put(setting.prop() + "Label", label);
            props.put(setting.prop() + "Classes", classes);
            props.put(setting.prop() + "Action", (Runnable) this::toggle);
        }

        void refresh() {
            boolean on = setting.read().getAsBoolean();
            label.set(label(on));
            classes.set(classes(on));
        }

        private void toggle() {
            setting.write().accept(!setting.read().getAsBoolean());
            refresh();
        }
    }

    private static String label(boolean on) {
        return Strings.get(on ? "openingUiSettingsOn" : "openingUiSettingsOff");
    }

    private static String classes(boolean on) {
        return "settings-key console-action led-key" + (on ? " led-on" : "");
    }

    private static String cardClasses(boolean shown) {
        return shown ? "settings-card" : "settings-card settings-card-hidden";
    }

    /** The page's properties with no campaign behind them, every setting showing {@code on}. */
    static Map<String, Object> previewProps(boolean on, boolean nexerelin) {
        Map<String, Object> props = new LinkedHashMap<>();
        List<Setting> settings = List.of(
                new Setting("voiceOver", () -> on, value -> { }),
                new Setting("raidFork", () -> on, value -> { }),
                new Setting("invasionFork", () -> on, value -> { }));
        new SettingsViewModel(new Reactor(), settings, () -> nexerelin).put(props);
        Runnable none = () -> { };
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.SETTINGS, MarineOpsPageNav.ANY_SHIP,
                none, none, none, none, none, none, none, none);
        return props;
    }
}
