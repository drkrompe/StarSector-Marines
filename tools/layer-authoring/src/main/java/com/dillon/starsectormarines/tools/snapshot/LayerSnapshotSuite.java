package com.dillon.starsectormarines.tools.snapshot;

import com.dillon.starsectormarines.tools.layerauthoring.AuthoringDocument;
import com.dillon.starsectormarines.tools.layerauthoring.CompositionRenderer;

import java.util.ArrayList;
import java.util.List;

/** Deterministic combined sheets for every authored marine and mech. */
public final class LayerSnapshotSuite implements SnapshotSuite {

    @Override
    public String id() {
        return "layers";
    }

    @Override
    public String label() {
        return "Marine / mech layers";
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        AuthoringDocument document = AuthoringDocument.load(context.projectRoot());
        CompositionRenderer renderer = new CompositionRenderer(context.projectRoot());
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        for (AuthoringDocument.UnitComposition unit : document.units()) {
            artifacts.add(new SnapshotArtifact(unit.id() + "-sheet.png",
                    renderer.renderSheet(unit, 420, 420)));
        }
        return List.copyOf(artifacts);
    }
}
