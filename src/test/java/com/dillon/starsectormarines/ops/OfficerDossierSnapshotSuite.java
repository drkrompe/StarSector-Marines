package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import java.util.List;

/** The officer dossier with every specialty, and Castle's induction through the specialty lesson. */
public final class OfficerDossierSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "officer-dossier"; }
    @Override public String label() { return "Officer dossier and induction"; }
    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        return UiSnapshotSuite.renderBarracksCommand(context, List.of("dossier", "dossier-trained"), false, true);
    }
}
