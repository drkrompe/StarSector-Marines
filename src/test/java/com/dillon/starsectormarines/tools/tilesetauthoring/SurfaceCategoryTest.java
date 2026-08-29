package com.dillon.starsectormarines.tools.tilesetauthoring;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where each surface is filed for browsing, and that the browser lays the
 * sections out as grids.
 *
 * <p>The filing is a judgement written down rather than a rule to be derived,
 * so the test is that every surface has one and that the few that carry meaning
 * are where they should be — a wall under Structure, water under Outdoors and
 * not among the floors, since it is not something to stand on.
 */
public class SurfaceCategoryTest {

    @Test
    void everySurfaceIsFiledSomewhere() throws Exception {
        for (SurfaceCatalog.Purpose purpose : SurfaceCatalog.scan(Paths.get("").toAbsolutePath())) {
            SurfaceCategory.Section section = SurfaceCategory.of(purpose.name());
            assertNotNull(section, purpose.name() + " is not filed");
            assertNotNull(section.label());
        }
    }

    @Test
    void theOnesThatCarryMeaningAreWhereTheyBelong() {
        assertEquals(new SurfaceCategory.Section(
                        SurfaceCategory.Setting.STRUCTURE, SurfaceCategory.Kind.WALL),
                SurfaceCategory.of("WALL"));
        assertEquals(SurfaceCategory.Kind.FLOOR, SurfaceCategory.of("INDOOR").kind());
        assertEquals(SurfaceCategory.Setting.OUTDOORS, SurfaceCategory.of("GRASS").setting());
        assertEquals(SurfaceCategory.Kind.OTHER, SurfaceCategory.of("WATER").kind(),
                "water is not something to stand on, so it is not a floor");
        assertEquals(SurfaceCategory.Kind.OTHER, SurfaceCategory.of("ROOF").kind());
    }

    /** An unfiled surface lands somewhere visible rather than vanishing. */
    @Test
    void somethingUnknownStillGetsASection() {
        SurfaceCategory.Section section = SurfaceCategory.of("NOT_A_SURFACE");
        assertEquals(SurfaceCategory.Setting.OUTDOORS, section.setting());
        assertEquals(SurfaceCategory.Kind.OTHER, section.kind());
    }

    /** Sections come out in reading order, and empty ones are not headed. */
    @Test
    void sectionsAreInReadingOrderAndNoneAreEmpty() throws Exception {
        List<SurfaceCatalog.Purpose> purposes = SurfaceCatalog.scan(Paths.get("").toAbsolutePath());
        List<SurfaceCategory.Section> sections = SurfaceCategory.sectionsOf(purposes);
        assertTrue(sections.size() >= 2, "the surfaces span more than one section");
        assertEquals(SurfaceCategory.Setting.STRUCTURE, sections.get(0).setting(),
                "structure comes before outdoors");
        for (SurfaceCategory.Section section : sections) {
            assertTrue(purposes.stream().anyMatch(p -> SurfaceCategory.of(p.name()).equals(section)),
                    section.label() + " is headed but holds nothing");
        }
    }

    /**
     * A resize that arrives before the pane has a width must not re-column the
     * grid. Acting on it puts every section into one column, and nothing
     * re-columns them afterwards, so that is what gets drawn.
     */
    @Test
    void aResizeToNothingDoesNotCollapseTheGrid() throws Exception {
        SurfaceBrowserView view = new SurfaceBrowserView(
                new BlockPreview(Paths.get("").toAbsolutePath()), candidate -> { });
        view.setPurposes(SurfaceCatalog.scan(Paths.get("").toAbsolutePath()));
        assertTrue(view.sectionCount() > 0, "the listing should have built sections");

        view.reflow(960);
        int[] wide = new int[view.sectionCount()];
        for (int i = 0; i < wide.length; i++) wide[i] = view.rowsInSection(i);

        view.reflow(0);
        for (int i = 0; i < wide.length; i++) {
            assertEquals(wide[i], view.rowsInSection(i),
                    "a zero-width resize must be ignored, not laid out to");
        }
    }
}
