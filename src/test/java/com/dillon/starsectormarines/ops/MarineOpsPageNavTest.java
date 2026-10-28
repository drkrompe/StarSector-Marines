package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiKey;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MarineOpsPageNavTest {
    @Test void engineeringIsAlwaysReachableAndHighlightsItsOwnTabAtBothScales() throws Exception {
        for (int width : new int[]{1920, 1280}) {
            int[] visits = {0};
            var props = new LinkedHashMap<String, Object>();
            MarineOpsPageNav.put(props, MarineOpsPageNav.Page.HQ, room -> MarineOpsPageNav.Aboard.NO,
                    () -> {}, () -> {}, () -> {}, () -> {}, () -> {}, () -> {}, () -> visits[0]++);
            props.put("contextLabel", "COMPANY SHIP / ENGINEERING");
            var loader = new MarkupLoader(path -> Files.readString(Path.of("mod").resolve(path)),
                    List.of("data/ui/components/marine-ops-page-nav.mlx"));
            try (var markup = loader.reloadAndBuild(new Reactor(), "marine-ops-page-nav", props)) {
                var doc = new UiDocument(markup.root()).theme(MarineOpsThemes.standard());
                markup.styles().forEach(doc::addStyleSheet);
                doc.layout(width, 60);
                var button = markup.requireElement("page-nav-engineering");
                assertFalse(button.hasClass("page-nav-absent"));
                click(doc, button);
                assertEquals(1, visits[0]);
                MarineOpsPageNav.refresh(markup, MarineOpsPageNav.Page.SHIP_VIEW, room -> MarineOpsPageNav.Aboard.NO);
                assertTrue(button.hasClass("page-nav-current"));
                assertFalse(markup.requireElement("page-nav-hq").hasClass("page-nav-current"));
                var b = button.box().borderBox();
                var label = markup.requireElement("page-nav-engineering-legend").box().borderBox();
                assertTrue(label.x() + label.width() <= b.x() + b.width(), "Engineering legend fits at " + width);
                assertTrue(b.width() < 220, "The new tab stays compact");
                assertTrue(b.x() + b.width() <= width, "Engineering remains inside the viewport");
            }
        }
    }

    @Test void existingButtonsUnlockAfterDesignationAndFollowSubsequentHullChanges() throws Exception {
        var rooms = new EnumMap<RoomPurpose, MarineOpsPageNav.Aboard>(RoomPurpose.class);
        var counts = new int[4];
        var props = new LinkedHashMap<String, Object>();
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.SHIP_TRANSFER,
                room -> rooms.getOrDefault(room, MarineOpsPageNav.Aboard.NO),
                () -> {}, () -> {}, () -> counts[0]++, () -> counts[1]++, () -> counts[2]++, () -> counts[3]++);
        props.put("contextLabel", "COMPANY FLEET / FOUNDING");
        var loader = new MarkupLoader(path -> Files.readString(Path.of("mod").resolve(path)),
                List.of("data/ui/components/marine-ops-page-nav.mlx"));
        try (var markup = loader.reloadAndBuild(new Reactor(), "marine-ops-page-nav", props)) {
            var document = new UiDocument(markup.root()).theme(MarineOpsThemes.standard());
            markup.styles().forEach(document::addStyleSheet);
            document.layout(1400, 60);
            List<String> ids = List.of("page-nav-barracks", "page-nav-mech-lab", "page-nav-boats");
            for (String id : ids) {
                assertTrue(markup.requireElement(id).hasClass("page-nav-absent"));
                click(document, markup.requireElement(id));
            }
            assertArrayEquals(new int[4], counts);
            for (RoomPurpose room : RoomPurpose.values()) rooms.put(room, MarineOpsPageNav.Aboard.UNKNOWN);
            MarineOpsPageNav.refresh(markup, MarineOpsPageNav.Page.SHIP_TRANSFER, rooms::get);
            for (String id : ids) assertTrue(markup.requireElement(id).hasClass("page-nav-waiting"));
            var boatButton = markup.requireElement("page-nav-boats");
            for (RoomPurpose room : RoomPurpose.values()) rooms.put(room, MarineOpsPageNav.Aboard.YES);
            MarineOpsPageNav.refresh(markup, MarineOpsPageNav.Page.SHIP_TRANSFER, rooms::get);
            document.advance(0);
            for (String id : ids) {
                assertFalse(markup.requireElement(id).hasClass("page-nav-absent"));
                assertFalse(markup.requireElement(id).hasClass("page-nav-waiting"));
                click(document, markup.requireElement(id));
            }
            assertArrayEquals(new int[]{1, 0, 1, 1}, counts, "Original retained controls must open all newly available rooms");
            assertSame(boatButton, markup.requireElement("page-nav-boats"));
            var barracksButton = markup.requireElement("page-nav-barracks");
            MarineOpsPageNav.refresh(markup, MarineOpsPageNav.Page.SHIP_TRANSFER, rooms::get,
                    List.of(CompanyNotices.Notice.FIRST_REFIT));
            assertTrue(barracksButton.hasClass("page-nav-new"));
            MarineOpsPageNav.refresh(markup, MarineOpsPageNav.Page.SHIP_TRANSFER, rooms::get, List.of());
            assertFalse(barracksButton.hasClass("page-nav-new"), "Review clears the retained marker without rebuilding the route");
            document.requestFocus(boatButton, true);
            document.keyPressed(UiKey.ENTER, Set.of(), false);
            assertEquals(2, counts[3]);
            // A later hull may lack a bay. Refuse stale input even before the next visual refresh.
            rooms.put(RoomPurpose.HANGAR, MarineOpsPageNav.Aboard.NO);
            click(document, boatButton);
            assertEquals(2, counts[3]);
            MarineOpsPageNav.refresh(markup, MarineOpsPageNav.Page.SHIP_TRANSFER, rooms::get);
            assertTrue(boatButton.hasClass("page-nav-absent"));
            assertFalse(markup.requireElement("page-nav-barracks").hasClass("page-nav-absent"));
            assertThrows(IllegalArgumentException.class, () -> markup.requireElement("page-nav-armory"));
            MarineOpsPageNav.refresh(markup, MarineOpsPageNav.Page.ARMORY, rooms::get,
                    List.of(CompanyNotices.Notice.FIRST_REFIT));
            assertTrue(markup.requireElement("page-nav-barracks").hasClass("page-nav-current"));
            assertTrue(markup.requireElement("page-nav-barracks").hasClass("page-nav-new"));
            assertEquals(ScreenId.BARRACKS, CompanyNotices.Notice.FIRST_REFIT.screen);
        }
    }

    @Test void storeIsOnlyOpenWhileDockedAtAPortThatTrades() throws Exception {
        var open = new boolean[]{false};
        var visits = new int[1];
        var saved = MarineOpsPageNav.storeOpen;
        MarineOpsPageNav.storeOpen = () -> open[0];
        try {
            var props = new LinkedHashMap<String, Object>();
            MarineOpsPageNav.put(props, MarineOpsPageNav.Page.HQ, MarineOpsPageNav.ANY_SHIP,
                    () -> {}, () -> {}, () -> {}, () -> {}, () -> {}, () -> {}, () -> {}, () -> visits[0]++);
            props.put("contextLabel", "COMPANY SHIP / HQ");
            var loader = new MarkupLoader(path -> Files.readString(Path.of("mod").resolve(path)),
                    List.of("data/ui/components/marine-ops-page-nav.mlx"));
            try (var markup = loader.reloadAndBuild(new Reactor(), "marine-ops-page-nav", props)) {
                var document = new UiDocument(markup.root()).theme(MarineOpsThemes.standard());
                markup.styles().forEach(document::addStyleSheet);
                document.layout(1920, 60);
                var store = markup.requireElement("page-nav-shop");

                // Away from a port the tab is shown as absent and does nothing.
                MarineOpsPageNav.refresh(markup, MarineOpsPageNav.Page.HQ, MarineOpsPageNav.ANY_SHIP);
                assertTrue(store.hasClass("page-nav-absent"));
                click(document, store);
                assertEquals(0, visits[0]);

                // Docked at a market that trades, the same retained button opens.
                open[0] = true;
                MarineOpsPageNav.refresh(markup, MarineOpsPageNav.Page.HQ, MarineOpsPageNav.ANY_SHIP);
                assertFalse(store.hasClass("page-nav-absent"));
                click(document, store);
                assertEquals(1, visits[0]);
                MarineOpsPageNav.refresh(markup, MarineOpsPageNav.Page.STORE, MarineOpsPageNav.ANY_SHIP);
                assertTrue(store.hasClass("page-nav-current"));
                var b = store.box().borderBox();
                var legend = markup.requireElement("page-nav-shop-legend").box().borderBox();
                assertTrue(legend.x() + legend.width() <= b.x() + b.width(), "the legend fits its button");
                assertTrue(b.x() + b.width() <= 1920, "the Store tab stays inside the viewport");
            }
        } finally {
            MarineOpsPageNav.storeOpen = saved;
        }
    }

    private static void click(UiDocument document, UiElement button) {
        var box = button.box().borderBox();
        document.pointerDown(box.x() + 8, box.y() + 8);
        document.pointerUp(box.x() + 8, box.y() + 8);
    }
}
