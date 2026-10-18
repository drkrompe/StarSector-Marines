package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.intel.SideJobLead;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.HqActionItem.Filter;
import com.dillon.starsectormarines.ops.HqActionItem.Kind;
import com.dillon.starsectormarines.ops.HqActionItem.Verb;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HqActionDeskTest {

    private static HqActionDesk desk(List<HqActionItem> items) {
        Map<String, Object> props = new HashMap<>();
        props.put("actionItems", items);
        props.put("newsPosts", List.of());
        HqActionDesk desk = new HqActionDesk(new Reactor(), new CompanyHqNewsDesk(new Reactor()));
        desk.put(props);
        return desk;
    }

    @SuppressWarnings("unchecked")
    private static HqActionDesk.Desk projection(Map<String, Object> props) {
        return ((Signal<HqActionDesk.Desk>) props.get("actions")).get();
    }

    @Test
    void choosingAMarkerUnderAnotherFilterShowsAllAndTheItem() {
        HqActionDesk desk = desk(CompanyHqViewModel.previewItems());
        desk.choose(Filter.STORY);
        assertEquals("preview-story", desk.selected().id());

        desk.select("preview-side");
        assertEquals(Filter.ALL, desk.filter());
        assertEquals("preview-side", desk.selected().id());
    }

    @Test
    void thePlotTableSelectsTheMostUrgentItemAndAStationShowsItsPanel() {
        HqActionDesk desk = desk(CompanyHqViewModel.previewItems());
        desk.select("preview-term");
        desk.show(HqActionDesk.View.FINANCE);
        assertEquals(HqActionDesk.View.FINANCE, desk.view());

        desk.selectMostUrgent();
        assertEquals("preview-due", desk.selected().id());
        assertEquals(HqActionDesk.View.ITEM, desk.view());
    }

    @Test
    void aStoryItemPutsItsBriefInTheDetailPane() {
        Map<String, Object> props = new HashMap<>();
        props.put("actionItems", CompanyHqViewModel.previewItems());
        HqActionDesk desk = new HqActionDesk(new Reactor(), new CompanyHqNewsDesk(new Reactor()));
        desk.put(props);
        desk.select("preview-story");

        @SuppressWarnings("unchecked")
        Signal<Object> guide = (Signal<Object>) props.get("openingGuide");
        assertSame(desk.selected().detail(), guide.get());
        assertEquals("hq-gone", projection(props).markupProperty("plainClasses"));

        desk.select("preview-due");
        assertEquals("detail-item console-readout", projection(props).markupProperty("plainClasses"));
    }

    @Test
    void aTrackedItemSaysWhyInsteadOfOfferingAButton() {
        Map<String, Object> props = new HashMap<>();
        props.put("actionItems", CompanyHqViewModel.previewItems());
        HqActionDesk desk = new HqActionDesk(new Reactor(), new CompanyHqNewsDesk(new Reactor()));
        desk.put(props);
        desk.select("preview-term");

        HqActionDesk.Desk view = projection(props);
        assertEquals("hq-gone", view.markupProperty("plainActionClasses"));
        assertFalse(String.valueOf(view.markupProperty("plainNote")).isBlank());
        HqActionDesk.Row row = view.rows().stream()
                .filter(candidate -> candidate.id().equals(HqActionDesk.rowId(desk.selected()))).findFirst().orElseThrow();
        assertEquals("hq-gone", row.markupProperty("actionClasses"));
    }

    @Test
    void anEmptyBoardSelectsNothing() {
        HqActionDesk desk = desk(List.of());
        desk.selectMostUrgent();
        assertNull(desk.selected());
    }

    @Test
    void aStoryLeadPlotsItsOwnCourseAndAnUnreachableOneSaysWhy() {
        AtomicInteger plotted = new AtomicInteger();
        OpeningWorkGuide guide = OpeningWorkGuide.from(new MarineRoster(),
                new OpeningBroker.Destination("jangala", "Jangala", "Corvus", null), plotted::incrementAndGet);
        HqActionItem lead = HqActionGatherer.story("story:opening", guide, null);
        assertEquals(Kind.STORY, lead.kind());
        assertFalse(lead.action().available(), "no destination, no course");
        assertFalse(lead.action().reason().isBlank());

        HqActionItem fixture = CompanyHqViewModel.storyFixture("story:opening", guide, true);
        fixture.action().run().run();
        assertEquals(1, plotted.get(), "the lead's course is its own arc's, not a shared pick");
    }

    @Test
    void aSideJobWithoutAMarketCannotPlotACourse() {
        HqActionItem job = HqActionGatherer.sideJob(new SideJobLead(7L, "Dead Letter", "unknown", null, 3),
                entity -> { });
        assertEquals("side:7", job.id());
        assertEquals(Verb.PLOT_COURSE, job.action().verb());
        assertFalse(job.action().available());
        assertNull(job.sector());
    }

    @Test
    void aLetterOpensItsCorrespondence() {
        AtomicInteger opened = new AtomicInteger();
        HqActionItem letter = HqActionGatherer.letter("letter:auxiliary", "Warrant", "Office", opened::incrementAndGet);
        assertTrue(letter.action().available());
        letter.action().run().run();
        assertEquals(1, opened.get());
        assertEquals(Filter.OTHER, letter.kind().filter);
    }
}
