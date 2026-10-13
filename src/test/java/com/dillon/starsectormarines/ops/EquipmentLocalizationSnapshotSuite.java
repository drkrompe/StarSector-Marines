package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;

/** Opening issue comparisons and actual hover sheets, 1920x1080 at 100% in both languages. */
public final class EquipmentLocalizationSnapshotSuite implements SnapshotSuite {
    public String id() { return "equipment-localization"; }
    public String label() { return "Equipment catalog / English and expanded copy"; }

    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        var result = new ArrayList<SnapshotArtifact>();
        for (boolean expanded : List.of(false, true)) {
            try (var strings = new HeadlessStrings(context.modRoot(), text -> expanded ? OpeningLocalizationSnapshotSuite.expand(text) : text)) {
                var frames = new ArrayList<>(new FirstRefitSnapshotSuite().renderLocalized(context));
                frames.addAll(UiSnapshotSuite.renderEquipmentSheets(context));
                for (var frame : frames) {
                    if (frame.image().getWidth() != 1920 || frame.image().getHeight() != 1080)
                        throw new AssertionError("Equipment evidence must be 1920x1080");
                    result.add(new SnapshotArtifact((expanded ? "expanded/" : "english/") + frame.relativePath(), frame.image()));
                }
            }
        }
        return result;
    }
}
