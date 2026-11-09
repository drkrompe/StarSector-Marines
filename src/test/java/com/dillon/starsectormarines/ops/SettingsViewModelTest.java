package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsViewModelTest {
    @Test
    void theVoiceOverKeyWritesTheSettingAndShowsIt() throws Exception {
        Reactor reactor = new Reactor();
        boolean[] on = {true};
        Map<String, Object> props = SettingsViewModel.previewProps(true, true);
        new SettingsViewModel(reactor, List.of(new SettingsViewModel.Setting("voiceOver", () -> on[0],
                value -> on[0] = value))).put(props);
        Path mod = Path.of("mod");
        SvgAsset.Source previous = SvgAsset.setSource(path -> Files.readString(mod.resolve(path)));
        try {
            MarkupLoader loader = new MarkupLoader(path -> Files.readString(mod.resolve(path)), SettingsScreen.COMPONENT_PATHS);
            loader.reload();
            try (MarkupInstance page = loader.build(reactor, SettingsScreen.ROOT_COMPONENT, props)) {
                UiDocument document = new UiDocument(page.root());
                for (var style : page.styles()) document.addStyleSheet(style);
                document.theme(MarineOpsThemes.standard());
                document.layout(1920f, 1080f);
                var key = page.requireElement("settings-voice-over-key");
                assertTrue(key.hasClass("led-on"));

                var box = key.box().borderBox();
                float x = box.x() + box.width() / 2f, y = box.y() + box.height() / 2f;
                assertTrue(document.pointerDown(x, y));
                assertTrue(document.pointerUp(x, y));
                page.flush();

                assertFalse(on[0]);
                assertFalse(page.requireElement("settings-voice-over-key").hasClass("led-on"));
                assertEquals("Off", page.requireElement("settings-voice-over-legend").text());
            }
        } finally {
            SvgAsset.setSource(previous);
        }
    }

    @Test
    void thePageBarSitsInTheCornerWithItsRoutesCutAtAllFourCorners() throws Exception {
        Reactor reactor = new Reactor();
        Map<String, Object> props = SettingsViewModel.previewProps(true, true);
        Path mod = Path.of("mod");
        SvgAsset.Source previous = SvgAsset.setSource(path -> Files.readString(mod.resolve(path)));
        try {
            MarkupLoader loader = new MarkupLoader(path -> Files.readString(mod.resolve(path)), SettingsScreen.COMPONENT_PATHS);
            loader.reload();
            try (MarkupInstance page = loader.build(reactor, SettingsScreen.ROOT_COMPONENT, props)) {
                UiDocument document = new UiDocument(page.root());
                for (var style : page.styles()) document.addStyleSheet(style);
                document.theme(MarineOpsThemes.standard());
                document.layout(1920f, 1080f);
                var holder = page.requireElement("marine-ops-page-holder").box().borderBox();
                assertEquals(0f, holder.x());
                assertEquals(1080f, holder.y() + holder.height());
                var cut = page.requireElement("page-nav-settings").cornerCut();
                assertTrue(cut.topLeft() > 0f && cut.topRight() > 0f && cut.bottomRight() > 0f && cut.bottomLeft() > 0f);
            }
        } finally {
            SvgAsset.setSource(previous);
        }
    }

    @Test
    void refreshShowsTheSettingOfAnotherSave() {
        boolean[] on = {true};
        Map<String, Object> props = new LinkedHashMap<>();
        boolean[] nexerelin = {false};
        SettingsViewModel model = new SettingsViewModel(new Reactor(), List.of(new SettingsViewModel.Setting(
                "invasionFork", () -> on[0], value -> on[0] = value, () -> nexerelin[0])));
        model.put(props);
        assertEquals("settings-card settings-card-hidden",
                ((MutableSignal<?>) props.get("invasionForkCardClasses")).peek());
        on[0] = false;
        nexerelin[0] = true;
        model.refresh();
        assertEquals("Off", ((MutableSignal<?>) props.get("invasionForkLabel")).peek());
        assertEquals("settings-card", ((MutableSignal<?>) props.get("invasionForkCardClasses")).peek(),
                "a setting's card shows once what it needs is there");
    }
}
