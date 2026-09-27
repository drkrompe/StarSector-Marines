package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;

import java.util.Comparator;

/** The same traffic run and verdicts, with production battle-review frames. */
public final class SquadTrafficSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {
    private static final ReviewStyle[] SQUAD_STYLES = {
            ReviewStyle.LANDING, ReviewStyle.KEEP, ReviewStyle.OBJECTIVE, ReviewStyle.APPROACH
    };

    public SquadTrafficSceneSnapshotSuite() {
        super(new SquadTrafficScene(), 1280, 384);
    }

    @Override
    protected ReviewAnnotations annotations(BattleSimulation sim) {
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        boolean crossing = sim.getGrid().getHeight() == 160;
        boolean passage = !crossing && !sim.getGrid().isWalkable(70, 0);
        // SceneBuilder authors navigation only, not rendered wall assets. These
        // are explicitly labelled diagnostic bounds, not a substitute terrain renderer.
        if (passage) {
            marks.box(70, 0, 86, 21, "BLOCKED NAV", ReviewStyle.NOTE);
            marks.box(70, 27, 86, 47, "BLOCKED NAV", ReviewStyle.NOTE);
            marks.label(78.5f, 24.5f, "5-cell passage", ReviewStyle.ROUTE);
        }
        marks.box(154, 0, 154, 8, "", ReviewStyle.NOTE);
        marks.box(155, 8, 159, 8, "isolated bystander", ReviewStyle.NOTE);
        if (crossing) {
            marks.label(80, 153, "NAV TEST | S1-S4 = live squad centroids", ReviewStyle.NOTE);
            marks.arrow(30, 72, 140, 72, "eastbound", ReviewStyle.ROUTE);
            marks.arrow(89, 137, 89, 23, "crossing flow", ReviewStyle.ROUTE);
            marks.label(149, 80, "GOAL S1/S2", ReviewStyle.OBJECTIVE);
            marks.label(80, 11, "GOAL S3/S4", ReviewStyle.OBJECTIVE);
        } else {
            marks.label(29, 44, "NAV TEST | S1-S4 = live squad centroids", ReviewStyle.NOTE);
            marks.label(46, 37, "OPEN / SPREAD", ReviewStyle.ROUTE);
            marks.label(113, 37, passage ? "OPEN / RECOVER" : "OPEN / SPREAD", ReviewStyle.ROUTE);
            marks.arrow(32, 10, 63, 10, "travel", ReviewStyle.ROUTE);
            marks.arrow(98, 10, 133, 10, "travel", ReviewStyle.ROUTE);
            marks.label(149, 24, "COMMON GOAL", ReviewStyle.OBJECTIVE);
        }
        UnitRosterService roster = sim.getRoster();
        long[] dense = roster.denseArray();
        var squads = sim.getSquads().stream().filter(squad -> squad.faction == Faction.MARINE)
                .sorted(Comparator.comparingInt(squad -> squad.id)).toList();
        for (int s = 0; s < squads.size(); s++) {
            Squad squad = squads.get(s);
            float x = 0, y = 0;
            int members = 0;
            for (int i = 0; i < roster.liveCount(); i++) {
                long member = dense[i];
                if (!roster.squad().hasSquad(member) || roster.squad().squadId(member) != squad.id) continue;
                x += sim.world().x(member);
                y += sim.world().y(member);
                members++;
            }
            if (members > 0) {
                float cx = x / members, cy = y / members;
                int cellX = (int) Math.floor(cx), cellY = (int) Math.floor(cy);
                ReviewStyle style = SQUAD_STYLES[s % SQUAD_STYLES.length];
                marks.box(cellX, cellY, cellX, cellY, "", style);
                // The label painter centers a cell coordinate with +0.5.
                marks.label(cx - 0.5f, cy - 0.5f, "S" + (s + 1), style);
            }
        }
        return marks.build();
    }
}
