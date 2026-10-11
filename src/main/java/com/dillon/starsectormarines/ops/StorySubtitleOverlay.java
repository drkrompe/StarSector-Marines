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
            path -> Global.getSettings().loadText(path), List.of(COMPONENT_PATH));
    private UiViewport viewport;
    private UiDocument document;
    private MarkupInstance markup;
    private WaylineNarration.Line current;

    void attach(PositionAPI position) {
        viewport = viewport(MarineOpsUiViewport.from(position));
        if (document != null) document.layout(viewport.documentWidth(), viewport.documentHeight());
    }

    /** Shared live/preview placement, inset from the granted host's top-left corner. */
    static UiViewport viewport(UiViewport host) {
        float scale = host.documentScale();
        float width = Math.min(640, host.documentWidth() - 24) * scale;
        float height = 116 * scale;
        return new UiViewport(host.screenXFor(12), host.screenTopFor(12) - height,
                width, height, scale);
    }

    static Map<String, Object> props(String subtitle) {
        return Map.of("speaker", "MARA VENN / WAYLINE", "subtitle", subtitle);
    }

    void update(WaylineNarration.Line line, float dt) {
        if (line != current) {
            if (markup != null) markup.close();
            markup = null;
            document = null;
            current = line;
            if (line != null) {
                markup = loader.reloadAndBuild(reactor, COMPONENT, props(line.text()));
                document = new UiDocument(markup.root());
                for (var style : markup.styles()) document.addStyleSheet(style);
                document.theme(MarineOpsThemes.standard());
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
