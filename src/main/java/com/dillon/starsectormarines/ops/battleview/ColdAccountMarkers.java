package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.setup.ColdAccountIntervention.Phase;
import com.dillon.starsectormarines.battle.setup.ColdAccountLayout;
import java.util.List;

/** Mission knowledge controls disclosure; every danger ring uses the actual firing boundary. */
public final class ColdAccountMarkers {
    public enum Kind { EVIDENCE, PICKUP, DANGER }
    public record Marker(Kind kind, String label, float x, float y, float radius) { }
    private ColdAccountMarkers() { }

    public static List<Marker> forPhase(Phase phase, ColdAccountLayout layout, boolean companyRecovered) {
        return switch (phase) {
            case EVIDENCE -> List.of(new Marker(Kind.EVIDENCE, "SECURE DISPATCH OFFICE",
                    layout.evidenceX() + .5f, layout.evidenceY() + .5f, 2f));
            case WITHDRAW, WARNING, BOMBARD -> List.of(
                    new Marker(Kind.PICKUP, "SHUTTLE PICKUP", layout.withdrawalX() + .5f,
                            layout.withdrawalY() + .5f, ColdAccountLayout.WITHDRAWAL_RADIUS),
                    new Marker(Kind.DANGER, "BOMBARDMENT / KEEP CLEAR", layout.strikeX(),
                            layout.strikeY(), layout.strikeRadius() + ColdAccountLayout.BLAST_MARGIN));
            case PATROL, AFTERMATH -> companyRecovered ? List.of() : List.of(
                    new Marker(Kind.PICKUP, "SHUTTLE PICKUP", layout.withdrawalX() + .5f,
                            layout.withdrawalY() + .5f, ColdAccountLayout.WITHDRAWAL_RADIUS));
            case COMPLETE, FAILED -> List.of();
        };
    }
}
