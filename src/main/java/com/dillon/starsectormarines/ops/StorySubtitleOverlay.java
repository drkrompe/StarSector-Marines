package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.audio.WaylineNarration;
import com.dillon.starsectormarines.battle.audio.CompanyOfficer;
import com.dillon.starsectormarines.battle.audio.OperationRadio;
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
    private Object current;

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
        return Map.of("speaker", "MARA VENN / WAYLINE",
                "portrait", OpeningBroker.PORTRAIT, "subtitle", subtitle);
    }

    void update(WaylineNarration.Line line, float dt) {
        update(line, null, dt);
    }

    void update(WaylineNarration.Line line, CompanyOfficer.Line officer, float dt) {
        update(line, officer, null, dt);
    }

    void update(WaylineNarration.Line line, CompanyOfficer.Line officer,
                OperationRadio.Transmission radio, float dt) {
        Object next = line != null ? line : radio != null ? radio : officer;
        if (next != current) {
            if (markup != null) markup.close();
            markup = null;
            document = null;
            current = next;
            if (next != null) {
                markup = line != null
                        ? (line.portrait().isBlank()
                            ? loader.reloadAndBuild(reactor, "company-subtitle", Map.of("speaker", line.speaker(), "subtitle", line.text()))
                            : loader.reloadAndBuild(reactor, COMPONENT, Map.of("speaker", line.speaker(), "portrait", line.portrait(), "subtitle", line.text())))
                        : (radio != null ? radio.line().speaker().equals("command") : officer != null)
                        ? loader.reloadAndBuild(reactor, COMPONENT, Map.of(
                                "speaker", "CASTLE / COMPANY COMMAND", "portrait", "graphics/portraits/castle.png",
                                "subtitle", radio != null ? radio.line().text() : officer.text()))
                        : loader.reloadAndBuild(reactor, "company-subtitle",
                                Map.of("speaker", radio != null ? radio.speaker() : "COMPANY COMMAND",
                                        "subtitle", radio != null ? radio.line().text() : officer.text()));
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
