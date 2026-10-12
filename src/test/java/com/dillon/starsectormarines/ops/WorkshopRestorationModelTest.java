package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.ship.WorkshopState;
import com.dillon.starsectormarines.campaign.start.CompanyWorkshopRestoration.Offer;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class WorkshopRestorationModelTest {
    @Test void everyStageAndReclamationNeedsItsOwnReview() {
        Offer[] live = {new Offer(WorkshopState.BURIED, 20000, null, "Locked", 2, 0)};
        int[] inspected = {0}, paid = {0}, changed = {0}, reclaimed = {0};
        var model = new WorkshopRestorationModel(new Reactor(), () -> live[0], state -> {
            paid[0]++; live[0] = new Offer(state.next(), 19000, null, "Locked", 2, 10); return null;
        }, () -> { reclaimed[0]++; return null; }, () -> inspected[0]++, () -> changed[0]++);
        model.row().get().action().run();
        assertEquals(1, inspected[0]); assertEquals(0, paid[0]);
        model.row().get().action().run();
        assertEquals(1, paid[0]); assertEquals(1, changed[0]);
        model.row().get().action().run();
        assertEquals(2, inspected[0]); assertEquals(1, paid[0]);
        model.row().get().reclaim().run(); assertEquals(0, reclaimed[0]);
        model.row().get().reclaim().run(); assertEquals(1, reclaimed[0]);
    }
    @Test void shippedPanelUsesCompactButtonsAndPointerReviewBeforePayment() throws Exception {
        for (int width : new int[]{1920, 1280}) {
            var reactor = new Reactor();
            Offer[] offer = {new Offer(WorkshopState.BURIED, 12000, null, "Locked", 2, 0)};
            int[] paid = {0};
            var model = new WorkshopRestorationModel(reactor, () -> offer[0], stage -> {
                paid[0]++; offer[0] = new Offer(stage.next(), 11250, null, "Locked", 2, 10); return null;
            }, () -> null, () -> {}, () -> {});
            var loader = new MarkupLoader(path -> Files.readString(Path.of("mod").resolve(path)), List.of(
                    "data/ui/components/marine-ops-page-nav.mlx", "data/ui/components/company/ship-view.mlx"));
            loader.reload();
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("workshop", model.row()); props.put("roomTitle", "SHIP");
            props.put("roomCopy", "Inspect her workshop."); props.put("contextLabel", "SECOND WIND");
            MarineOpsPageNav.put(props, MarineOpsPageNav.Page.SHIP_VIEW, MarineOpsPageNav.ANY_SHIP,
                    () -> {}, () -> {}, () -> {}, () -> {}, () -> {}, () -> {});
            try (var instance = loader.build(reactor, "ship-view", props)) {
                var document = new UiDocument(instance.root());
                instance.styles().forEach(document::addStyleSheet);
                document.theme(MarineOpsThemes.standard());
                document.layout(width, width * 9f / 16f);
                var button = instance.requireElement("workshop-action");
                assertTrue(button.box().borderBox().width() <= 220);
                for (int click = 0; click < 2; click++) {
                    var bounds = button.box().borderBox();
                    float x = bounds.x() + bounds.width() / 2, y = bounds.y() + bounds.height() / 2;
                    document.pointerMoved(x, y); document.pointerDown(x, y); document.pointerUp(x, y);
                    instance.flush(); document.advance(0f);
                    assertEquals(click, paid[0]);
                }
                assertFalse(model.row().get().reviewed(), "The next stage requires a fresh inspection");
            }
        }
    }

}
