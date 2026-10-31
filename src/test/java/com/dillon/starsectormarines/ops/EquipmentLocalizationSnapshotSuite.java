package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * Opening issue comparisons and actual hover sheets, 1920x1080 at 100% in English and expanded copy; with
 * {@code -Dsnapshot.translation=<locale>}, also in that translation mod's copy and fonts.
 */
public final class EquipmentLocalizationSnapshotSuite implements SnapshotSuite {
    public String id() { return "equipment-localization"; }
    public String label() { return "Equipment catalog / English and expanded copy"; }

    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        var result = new ArrayList<SnapshotArtifact>();
        for (boolean expanded : List.of(false, true)) {
            try (var strings = new HeadlessStrings(context.modRoot(), text -> expanded ? OpeningLocalizationSnapshotSuite.expand(text) : text)) {
                addFrames(result, expanded ? "expanded/" : "english/", context);
            }
        }
        // -Dsnapshot.translation=zh_CN adds the translation mod's own copy and fonts.
        String locale = System.getProperty(HeadlessStrings.TRANSLATION_PROPERTY, "");
        if (!locale.isBlank()) {
            try (var strings = HeadlessStrings.translation(context.modRoot(), locale)) {
                addFrames(result, locale + "/", context);
            }
        }
        return result;
    }

    private static void addFrames(List<SnapshotArtifact> result, String prefix, SnapshotContext context) throws Exception {
        var frames = new ArrayList<>(new FirstRefitSnapshotSuite().renderLocalized(context));
        frames.addAll(UiSnapshotSuite.renderEquipmentSheets(context));
        for (var frame : frames) {
            if (frame.image().getWidth() != 1920 || frame.image().getHeight() != 1080)
                throw new AssertionError("Equipment evidence must be 1920x1080");
            result.add(new SnapshotArtifact(prefix + frame.relativePath(), frame.image()));
        }
    }
}
