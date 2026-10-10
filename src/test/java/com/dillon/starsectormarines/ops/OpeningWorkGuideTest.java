package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import org.junit.jupiter.api.Test;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpeningWorkGuideTest {
    @Test void aFreshCompanyGetsDirectionsAndPreparationWithoutCreatingPersonnel() {
        MarineRoster roster = new MarineRoster();
        OpeningWorkGuide guide = OpeningWorkGuide.from(roster);
        assertTrue(guide.visible());
        assertEquals(OpeningOperationKind.RELIEF, guide.kind());
        assertTrue(guide.preparation().contains("first squad"));
        assertTrue(roster.squads().isEmpty());
        assertFalse(roster.hasCompletedStory(OpeningOperationKind.RELIEF.missionId));
    }

    @Test void guidanceAdvancesOnlyWithTheExistingCompletionFacts() {
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(12);
        assertFalse(OpeningWorkGuide.from(roster).preparation().contains("first squad"));
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        OpeningWorkGuide followup = OpeningWorkGuide.from(roster);
        assertTrue(followup.visible());
        assertEquals(OpeningOperationKind.COUNTERATTACK, followup.kind());
        roster.markStoryComplete(OpeningOperationKind.COUNTERATTACK.missionId);
        assertFalse(OpeningWorkGuide.from(roster).visible());
        assertEquals(true, OpeningWorkGuide.from(roster,
                new OpeningBroker.Destination("jangala", "Jangala", "Corvus", null), () -> { })
                .markupProperty("courseDisabled"));
        assertFalse(OpeningWorkGuide.from(null).visible());
    }
    @Test void courseButtonDispatchesOnlyWhenAnOfficeExists() throws Exception {
        Path mod = Path.of("mod");
        SvgAsset.Source previous = SvgAsset.setSource(path -> Files.readString(mod.resolve(path)));
        try {
            for (boolean available : List.of(true, false)) {
                AtomicInteger calls = new AtomicInteger();
                Reactor reactor = new Reactor();
                Map<String, Object> props = CompanyHqViewModel.openingPreview(new MarineRoster(),
                        available ? new OpeningBroker.Destination("jangala", "Jangala", "Corvus", null) : null,
                        calls::incrementAndGet).props();
                new CompanyHqNewsDesk(reactor).put(props);
                MarkupLoader loader = new MarkupLoader(path -> Files.readString(mod.resolve(path)), List.of(
                        "data/ui/components/marine-ops-page-nav.mlx", "data/ui/components/company/company-hq.mlx"));
                loader.reload();
                try (MarkupInstance instance = loader.build(reactor, "company-hq", props)) {
                    UiDocument document = new UiDocument(instance.root());
                    for (var style : instance.styles()) document.addStyleSheet(style);
                    document.theme(MarineOpsThemes.standard());
                    document.layout(1920, 1080);
                    var box = instance.requireElement("company-hq-opening-course").box().borderBox();
                    document.pointerDown(box.x() + box.width() / 2, box.y() + box.height() / 2);
                    document.pointerUp(box.x() + box.width() / 2, box.y() + box.height() / 2);
                    assertEquals(available ? 1 : 0, calls.get());
                }
            }
        } finally {
            SvgAsset.setSource(previous);
        }
    }
}
