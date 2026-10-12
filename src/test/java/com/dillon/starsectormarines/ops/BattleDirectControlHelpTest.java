package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.BattleDirectControlHelp.Hover;
import com.dillon.starsectormarines.ops.BattleDirectControlHelp.Layout;
import com.dillon.starsectormarines.ops.BattleDirectControlHelp.Line;
import com.dillon.starsectormarines.ops.BattleDirectControlStatus.Carrier;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleDirectControlHelpTest {
    private static final Function<String, String> COPY = ModStrings.fromDisk();

    private static boolean has(List<Line> lines, String keys, String textKey) {
        return lines.contains(new Line(keys, "battleDirectHelp" + textKey));
    }

    private static boolean present(MarkupInstance markup, String id) {
        try {
            markup.requireElement(id);
            return true;
        } catch (IllegalArgumentException missing) {
            return false;
        }
    }

    @Test
    void nothingControlledHasNothingToExplain() {
        assertTrue(BattleDirectControlHelp.lines(Carrier.NONE).isEmpty());
        assertTrue(BattleDirectControlHelp.lines(null).isEmpty());
        assertEquals("", BattleDirectControlHelp.titleKey(Carrier.NONE));
        assertEquals("", BattleDirectControlHelp.titleKey(null));
    }

    @Test
    void warshipListsEveryBindingTheShipActuallyHas() {
        var lines = BattleDirectControlHelp.lines(Carrier.WARSHIP);
        assertTrue(has(lines, "WASD", "ShipFly"));
        assertTrue(has(lines, "LMB hold", "ShipGuns"));
        assertTrue(has(lines, "RMB hold", "ShipMissiles"));
        assertTrue(has(lines, "1", "ShipHold"));
        assertTrue(has(lines, "2", "ShipDirector"));
        assertTrue(has(lines, "3", "ShipPreset"));
        assertTrue(has(lines, "4", "ShipPolicy"));
        assertTrue(has(lines, "E", "ShipShield"));
        assertTrue(has(lines, "Tab / Esc", "Leave"));
        assertTrue(has(lines, "", "ShipDanger"));
        assertTrue(has(lines, "", "ShipSeen"));
        assertTrue(has(lines, "", "ShipFlux"));
        assertFalse(lines.stream().anyMatch(l -> l.keys().contains("Shift") || l.keys().equals("G")));
    }

    @Test
    void marineMechAndApcListOnlyTheirOwnBindings() {
        var marine = BattleDirectControlHelp.lines(Carrier.MARINE);
        assertTrue(has(marine, "LMB hold", "MarineFire"));
        assertTrue(has(marine, "RMB", "MarineSecondary"));
        assertTrue(has(marine, "E", "MarineShield"));
        assertTrue(has(marine, "G", "MarineSmoke"));
        assertTrue(has(marine, "Shift", "MarineSprint"));
        assertTrue(has(marine, "Tab / Esc", "Leave"));

        var mech = BattleDirectControlHelp.lines(Carrier.MECH);
        assertTrue(has(mech, "LMB hold", "MechPrimary"));
        assertTrue(has(mech, "RMB hold", "MechSecondary"));
        assertTrue(has(mech, "1", "MechDefault"));
        assertTrue(has(mech, "2 3 4", "MechPick"));
        assertFalse(mech.stream().anyMatch(l -> l.keys().equals("E") || l.keys().equals("G")
                || l.keys().equals("Shift")));

        var apc = BattleDirectControlHelp.lines(Carrier.VEHICLE);
        assertTrue(has(apc, "W / S", "VehicleDrive"));
        assertTrue(has(apc, "A / D", "VehicleSteer"));
        assertTrue(has(apc, "LMB hold", "VehicleFire"));
        assertFalse(apc.stream().anyMatch(l -> l.keys().equals("RMB") || l.keys().equals("RMB hold")));
    }

    @Test
    void everyRowAndTitleHasRealCopyThatFitsThePanel() {
        for (Carrier carrier : Carrier.values()) {
            if (carrier == Carrier.NONE) continue;
            String title = BattleDirectControlHelp.titleKey(carrier);
            assertNotEquals(title, COPY.apply(title), title);
            for (Line line : BattleDirectControlHelp.lines(carrier)) {
                String text = COPY.apply(line.textKey());
                assertNotEquals(line.textKey(), text, "missing copy " + line.textKey());
                assertTrue(text.length() <= BattleDirectControlHelp.MAX_TEXT_CHARS,
                        text.length() + " chars: " + text);
            }
        }
        assertNotEquals("battleDirectHelpTab", COPY.apply("battleDirectHelpTab"));
        assertNotEquals("battleDirectHelpTabMark", COPY.apply("battleDirectHelpTabMark"));
    }

    @Test
    void tabSitsBesideTheSessionStripAndPanelHangsUnderTheTab() {
        Layout layout = BattleDirectControlHelp.layout(1700f, 1068f, 1f, 15);
        var tab = layout.tab();
        var panel = layout.panel();
        assertTrue(tab.x() + tab.width() < 1700f, "left of the exit/pause strip");
        assertEquals(1068f, tab.y() + tab.height(), .001f, "top aligned with the strip");
        assertEquals(tab.y(), panel.y() + panel.height(), .001f, "panel abuts the tab");
        assertEquals(tab.x() + tab.width(), panel.x() + panel.width(), .001f, "right edges flush");
        var union = layout.union();
        assertTrue(union.contains(tab.x(), tab.y()) && union.contains(panel.x(), panel.y()));
        Layout doubled = BattleDirectControlHelp.layout(3400f, 2136f, 2f, 15);
        assertEquals(tab.width() * 2f, doubled.tab().width(), .001f);
        assertEquals(panel.height() * 2f, doubled.panel().height(), .001f);
    }

    @Test
    void hoverOpensOnTheTabStaysOverThePanelAndClosesOnLeave() {
        Layout layout = BattleDirectControlHelp.layout(1700f, 1068f, 1f, 10);
        var tab = layout.tab();
        var panel = layout.panel();
        float tabX = tab.x() + 5f, tabY = tab.y() + 5f;
        float panelX = panel.x() + 5f, panelY = panel.y() + 5f;
        Hover hover = new Hover();

        assertFalse(hover.update(true, true, panelX, panelY, layout), "the hidden panel is not a hover target");
        assertFalse(hover.blocks(true, panelX, panelY, layout), "collapsed bounds capture nothing");
        assertTrue(hover.blocks(true, tabX, tabY, layout), "the visible tab is chrome");

        assertTrue(hover.update(true, true, tabX, tabY, layout));
        assertTrue(hover.update(true, true, panelX, panelY, layout), "travelling down to read keeps it open");
        assertTrue(hover.blocks(true, panelX, panelY, layout), "world fire is suspended over the panel");
        assertFalse(hover.update(true, true, 100f, 100f, layout), "leaving collapses");
        assertFalse(hover.blocks(true, panelX, panelY, layout));
    }

    @Test
    void hoverNeverSurvivesLosingControlOrThePointer() {
        Layout layout = BattleDirectControlHelp.layout(1700f, 1068f, 1f, 10);
        float x = layout.tab().x() + 5f, y = layout.tab().y() + 5f;
        Hover hover = new Hover();
        assertTrue(hover.update(true, true, x, y, layout));
        assertFalse(hover.update(false, true, x, y, layout));
        assertFalse(hover.blocks(false, x, y, layout), "no controlled body, no chrome");
        assertTrue(hover.update(true, true, x, y, layout));
        assertFalse(hover.update(true, false, x, y, layout), "an unknown pointer opens nothing");
        hover.update(true, true, x, y, layout);
        hover.collapse();
        assertFalse(hover.expanded());
    }

    @Test
    void markupShowsOnlyTheTabUntilExpandedThenOneRowPerLine() throws Exception {
        Reactor reactor = new Reactor();
        var plate = new BattleDirectControlHelpPlate(reactor, COPY);
        var loader = new MarkupLoader(path -> Files.readString(Path.of("mod").resolve(path)),
                List.of(BattleDirectControlHelpPlate.COMPONENT_PATH));
        try (MarkupInstance markup = loader.reloadAndBuild(reactor, BattleDirectControlHelpPlate.COMPONENT,
                plate.props())) {
            UiDocument document = BattleDirectControlOverlay.document(markup);
            plate.update(false, Carrier.WARSHIP);
            markup.flush();
            document.layout(BattleDirectControlHelp.TAB_WIDTH, BattleDirectControlHelp.TAB_HEIGHT);
            assertEquals("HELP", markup.requireElement("direct-help-label").text());
            assertEquals("!", markup.requireElement("direct-help-mark").text());
            assertEquals("", markup.requireElement("direct-help-title").text());
            assertTrue(!present(markup, "direct-help-row-0"), "no rows while collapsed");

            plate.update(true, Carrier.WARSHIP);
            markup.flush();
            float height = BattleDirectControlHelp.TAB_HEIGHT
                    + BattleDirectControlHelp.panelHeight(BattleDirectControlHelpPlate.rowCount(Carrier.WARSHIP));
            document.layout(BattleDirectControlHelp.PANEL_WIDTH, height);
            assertEquals("WARSHIP CONTROLS", markup.requireElement("direct-help-title").text());
            int rows = BattleDirectControlHelp.lines(Carrier.WARSHIP).size();
            assertEquals("LMB hold", markup.requireElement("direct-help-row-1-keys").text());
            assertTrue(present(markup, "direct-help-row-" + (rows - 1)));
            assertTrue(!present(markup, "direct-help-row-" + rows));
            var panel = markup.requireElement("direct-help-panel").box().borderBox();
            assertTrue(panel.bottom() <= height + .5f, "every row fits inside the document");
        }
    }
}
