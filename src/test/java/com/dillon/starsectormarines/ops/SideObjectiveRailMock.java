package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

/**
 * Re-cuts the battle rail's markup for {@link SideObjectiveInteractionMockSuite}: ways to give the Dust Ledger's rows
 * less of the rail, since the prompts on the field now carry the actions. A copy of the rail's markup is patched in
 * memory with extra rules; the rail's own files are untouched.
 */
final class SideObjectiveRailMock {
    /** Which way the rail saves space. */
    enum Mode {
        /** Every row keeps its name and state and drops its reward, steps, instruction and blocker lines. */
        COMPACT_ALL,
        /** Only the side jobs a company unit is in reach of are listed, under a count of the rest. */
        IN_REACH,
        /** Both: compact rows, and only the jobs in reach. */
        BOTH
    }

    private static final String OPTIONAL_HEADING = "{i18n.battleHudOptionalObjectives}";
    private static final int OPTIONAL_HEADING_HEIGHT = 29, COMPACT_ROW = 68, FULL_ROW = 78, COMPACT_MISSION_ROW = 90;

    private static final String COMPACT = """
              .mission-steps, .mission-instruction, .mission-blocker { display: none; }
              .mission-card { height: 84px; }
              .mission-optional .mission-secondary { display: none; }
              .mission-optional { height: 62px; }
            """;

    private static final String IN_REACH = """
              #mission-side-dust-ledger-coupling { display: none; }
              #mission-side-dust-ledger-feed { display: none; }
              #mission-side-dust-ledger-broker { display: none; }
            """;

    private SideObjectiveRailMock() { }

    private static boolean compact(Mode mode) { return mode != Mode.IN_REACH; }

    private static boolean inReach(Mode mode) { return mode != Mode.COMPACT_ALL; }

    /** The rail's markup, patched for {@code mode} when it is set. */
    static String markup(SnapshotContext context, String path, Mode mode) throws IOException {
        String text = Files.readString(context.modRoot().resolve(path));
        if (mode == null) return text;
        if (inReach(mode)) {
            if (!text.contains(OPTIONAL_HEADING)) throw new IllegalStateException("the rail's Optional heading changed; update the mock");
            text = text.replace(OPTIONAL_HEADING, "OPTIONAL  1 OF 4 IN REACH");
        }
        int style = text.lastIndexOf("</style>");
        return text.substring(0, style) + (compact(mode) ? COMPACT : "") + (inReach(mode) ? IN_REACH : "") + text.substring(style);
    }

    /** Resizes the rail's lists to the patched rows; returns the document pixels the rail gains (negative: loses). */
    @SuppressWarnings("unchecked")
    static int apply(Map<String, Object> props, BattleHudOverlayModel.Presentation presentation, Mode mode) {
        int rows = inReach(mode) ? 1 : presentation.optionalRows(), missions = presentation.missionRows();
        int optionalNow = Math.round(BattleHudOverlayModel.optionalHeight(presentation.optionalRows()));
        int optional = OPTIONAL_HEADING_HEIGHT + rows * (compact(mode) ? COMPACT_ROW : FULL_ROW);
        int missionList = missions * (compact(mode) ? COMPACT_MISSION_ROW : 174);
        ((MutableSignal<String>) props.get("optionalListStyle")).set("height: " + (optional - 6) + "px;");
        ((MutableSignal<String>) props.get("missionListStyle")).set("height: " + missionList + "px;");
        ((MutableSignal<String>) props.get("missionPanelStyle")).set("height: " + (33 + missionList + optional) + "px;");
        return missionList + optional - (missions * 174 + optionalNow);
    }
}
