package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyHqNewsDeskTest {
    @Test
    void filteringSelectsOnlyKnownReportsAndTeasersRouteToTheirExactStory() {
        CompanyHqNewsDesk desk = new CompanyHqNewsDesk(new Reactor());
        desk.put(props(List.of(
                post("confirmed", false, false), post("rumor", true, false),
                post("operation", false, true))));
        assertEquals("confirmed", desk.snapshot().headline());
        desk.choose(CompanyHqNewsDesk.Channel.RUMOR);
        assertEquals("rumor", desk.snapshot().headline());
        assertEquals(1, desk.snapshot().reports().size());
        assertTrue(desk.snapshot().caution().startsWith("UNCONFIRMED"));
        desk.snapshot().sources().get(0).reports().get(0).action().run();
        assertEquals("operation", desk.snapshot().headline());
        assertEquals(3, desk.snapshot().reports().size());
        desk.select("unknown");
        assertEquals("operation", desk.snapshot().headline());
    }

    @Test
    void refreshPreservesStableSelectionAndEmptyChannelsNeverExposeAnotherReport() {
        CompanyHqNewsDesk desk = new CompanyHqNewsDesk(new Reactor());
        var confirmed = post("stable", false, false);
        desk.put(props(List.of(confirmed, post("rumor", true, false))));
        desk.select("rumor");
        desk.put(props(List.of(confirmed, post("rumor", true, false))));
        assertEquals("rumor", desk.snapshot().headline());
        desk.choose(CompanyHqNewsDesk.Channel.COMPANY);
        assertTrue(desk.snapshot().reports().isEmpty());
        assertEquals("No report selected", desk.snapshot().headline());
        desk.choose(CompanyHqNewsDesk.Channel.ALL);
        desk.select("rumor");
        desk.put(props(List.of(confirmed)));
        assertEquals("stable", desk.snapshot().headline());
    }

    @Test
    void sourceAndRundownClicksUpdateNestedMarkupThroughTheDeskSignal() throws Exception {
        Reactor reactor = new Reactor();
        CompanyHqNewsDesk desk = new CompanyHqNewsDesk(reactor);
        Map<String, Object> props = CompanyHqViewModel.preview().props();
        desk.put(props);
        Path mod = Path.of("mod");
        SvgAsset.Source previous = SvgAsset.setSource(path -> Files.readString(mod.resolve(path)));
        try {
            MarkupLoader loader = new MarkupLoader(path -> Files.readString(mod.resolve(path)),
                    List.of("data/ui/components/marine-ops-page-nav.mlx",
                            "data/ui/components/company/company-hq.mlx"));
            loader.reload();
            try (MarkupInstance instance = loader.build(reactor, "company-hq", props)) {
                UiDocument document = new UiDocument(instance.root());
                for (var style : instance.styles()) document.addStyleSheet(style);
                document.theme(MarineOpsThemes.standard());
                document.layout(1920f, 1080f);
                click(document, instance.requireElement("hq-source-rumor-preview-news-2"));
                instance.flush();
                document.advance(0f);
                assertEquals("Reports of Compact action against House Umbra",
                        instance.requireElement("company-hq-focus-headline").text());
                assertTrue(instance.requireElement("company-hq-focus").hasClass("rumor-story"));
                click(document, instance.requireElement("hq-wire-tab-company"));
                instance.flush();
                document.advance(0f);
                assertEquals(1, instance.requireElement("company-hq-news-list").childCount());
                assertEquals("Silent colony operation concluded",
                        instance.requireElement("company-hq-focus-headline").text());
                click(document, instance.requireElement("hq-wire-tab-all"));
                instance.flush();
                document.advance(0f);
                click(document, instance.requireElement("hq-rundown-preview-news-1"));
                instance.flush();
                document.advance(0f);
                assertEquals("House Cavor displaces House Tobin",
                        instance.requireElement("company-hq-focus-headline").text());
                assertTrue(instance.requireElement("hq-rundown-preview-news-1").hasClass("selected"));
            }
        } finally {
            SvgAsset.setSource(previous);
        }
    }

    private static void click(UiDocument document, UiElement element) {
        var box = element.box().borderBox();
        assertTrue(document.pointerDown(box.x() + box.width() / 2f, box.y() + box.height() / 2f));
        assertTrue(document.pointerUp(box.x() + box.width() / 2f, box.y() + box.height() / 2f));
    }
    private static Map<String, Object> props(List<CompanyHqViewModel.NewsPost> posts) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("newsPosts", posts);
        return props;
    }

    private static CompanyHqViewModel.NewsPost post(String id, boolean rumor, boolean company) {
        return new CompanyHqViewModel.NewsPost(id, "news-card", "TODAY", id,
                "Known detail for " + id, rumor, company);
    }
}