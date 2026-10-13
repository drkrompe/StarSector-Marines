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
            List<String> ids = List.of("page-nav-barracks", "page-nav-armory", "page-nav-mech-lab", "page-nav-boats");
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
            assertArrayEquals(new int[]{1, 1, 1, 1}, counts, "Original retained controls must open all newly available rooms");
            assertSame(boatButton, markup.requireElement("page-nav-boats"));
            MarineOpsPageNav.refresh(markup, MarineOpsPageNav.Page.SHIP_TRANSFER, rooms::get,
                    List.of(CompanyNotices.Notice.SECONDHAND_WINGS));
            assertTrue(boatButton.hasClass("page-nav-new"));
            MarineOpsPageNav.refresh(markup, MarineOpsPageNav.Page.SHIP_TRANSFER, rooms::get, List.of());
            assertFalse(boatButton.hasClass("page-nav-new"), "Review clears the retained marker without rebuilding the route");
            document.requestFocus(boatButton, true);
            document.keyPressed(UiKey.ENTER, Set.of(), false);
            assertEquals(2, counts[3]);
            // A later hull may lack a bay. Refuse stale input even before the next visual refresh.
            rooms.put(RoomPurpose.HANGAR, MarineOpsPageNav.Aboard.NO);
            click(document, boatButton);
            assertEquals(2, counts[3]);
            MarineOpsPageNav.refresh(markup, MarineOpsPageNav.Page.SHIP_TRANSFER, rooms::get);
            assertTrue(boatButton.hasClass("page-nav-absent"));
            assertFalse(markup.requireElement("page-nav-armory").hasClass("page-nav-absent"));
        }
    }

    private static void click(UiDocument document, UiElement button) {
        var box = button.box().borderBox();
        document.pointerDown(box.x() + 8, box.y() + 8);
        document.pointerUp(box.x() + 8, box.y() + 8);
    }
}
