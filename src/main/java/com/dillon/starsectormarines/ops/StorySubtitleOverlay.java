package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.audio.WaylineNarration;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.PositionAPI;

import java.util.List;
import java.util.Map;

/** Non-interactive, retained subtitle surface shared by strategic and direct-control views. */
final class StorySubtitleOverlay {
    static final String COMPONENT = "story-subtitle";
    static final String COMPONENT_PATH = "data/ui/components/battle/story-subtitle.mlx";
    private final Reactor reactor = new Reactor();
    private final MarkupLoader loader = new MarkupLoader(
            path -> Global.getSettings().loadText(path), List.of(COMPONENT_PATH, "data/ui/components/battle/company-subtitle.mlx"));
    private UiViewport viewport;
    private UiDocument document;
    private MarkupInstance markup;
    private WaylineNarration.Line current;

    void attach(PositionAPI position) {
        viewport = viewport(MarineOpsUiViewport.from(position));
        if (document != null) document.layout(viewport.documentWidth(), viewport.documentHeight());
    }

    /** Shared message lane between the left command and right objective rails. */
    static UiViewport viewport(UiViewport host) {
        float scale = host.documentScale();
        float width = Math.min(640, host.documentWidth() - 24) * scale;
        float height = 116 * scale;
        return new UiViewport(host.screenX() + (host.width() - width) / 2f, host.screenTopFor(12) - height,
                width, height, scale);
    }

    BattleHudSurface surface() {
        return document == null || viewport == null ? null : new BattleHudSurface(document, viewport);
    }

    boolean blocksWorldPointer(float x, float y) {
        return document != null && viewport != null && x >= viewport.screenX()
                && x < viewport.screenX() + viewport.width() && y >= viewport.screenY()
                && y < viewport.screenY() + viewport.height();
    }

    static Map<String, Object> props(String subtitle) {
        return Map.of("speaker", "ZOEY VENN / WAYLINE",
                "portrait", OpeningBroker.PORTRAIT, "subtitle", subtitle);
    }

    void update(WaylineNarration.Line line, float dt) {
        if (line != current) {
            if (markup != null) markup.close();
            markup = null;
            document = null;
            current = line;
            if (line != null) {
                Map<String, Object> props = Map.of(
                        "speaker", line.speaker(), "portrait", line.portrait(), "subtitle", line.text());
                markup = loader.reloadAndBuild(reactor,
                        ((String) props.get("portrait")).isBlank() ? "company-subtitle" : COMPONENT, props);
                document = new UiDocument(markup.root());
                for (var style : markup.styles()) document.addStyleSheet(style);
                document.theme(MarineOpsThemes.battle());
                document.layout(viewport.documentWidth(), viewport.documentHeight());
            }
        }
        if (document != null) document.advance(dt);
    }

    void render(float alpha) {
        if (document != null && viewport != null) document.render(viewport, alpha);
    }

    void detach() {
        if (markup != null) markup.close();
        markup = null;
        document = null;
        current = null;
    }
}
