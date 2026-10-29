package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

/**
 * Splices mock action buttons into a copy of the battle rail's markup for {@link SideObjectiveInteractionMockSuite}: each
 * side-objective row gains a strip (or one row expands), shown per row by an id rule. The rail's own files are untouched.
 */
final class SideObjectiveRailMock {
    private static final String OPTIONAL_TRACK =
            "<div id=\"{card.trackId}\" class=\"objective-progress-track\" style=\"{card.trackStyle}\"><div id=\"{card.progressId}\" class=\"objective-progress-fill\" style=\"{card.progressStyle}\" /></div>";
    private static final String LOCATE =
            "<button id=\"{card.locateId}\" class=\"objective-locate\" disabled=\"{card.disabled}\" onclick=\"{card.locate}\">{i18n.battleHudLocate}</button>";
    private static final int STRIP_EXTRA_PER_ROW = 50, EXPAND_EXTRA = 182;

    private static final String ACTIONS = """
            <div id="{card.stripId}" class="act-strip">
              <button id="{card.sendId}" class="act act-send">SEND SQUAD</button>
              <button id="{card.respectsId}" class="act act-verb act-respects">PAY RESPECTS</button>
              <button id="{card.cutId}" class="act act-verb act-cut">CUT THE FEED</button>
              <button id="{card.crateId}" class="act act-verb act-crate">PICK UP CRATE</button>
            </div>
            <div id="{card.openId}" class="act-open">
              <div id="{card.detailId}" class="act-detail">Tobias Ankrum's watch holds the cantina and spots the yard for you. A squad leader waits at the well house for 8 s.</div>
              <button id="{card.wideOneId}" class="act act-wide" style="color: #ffd464; background-color: #3a3018; border-color: #ffd464aa;">PAY YOUR RESPECTS</button>
              <button id="{card.wideTwoId}" class="act act-wide">OFFER FREIGHT CREDIT (ILLUSTRATIVE)</button>
              <button id="{card.wideThreeId}" class="act act-wide" style="color: #7fc8e8; background-color: #102833; border-color: #7fc8e8aa;">SEND SQUAD</button>
            </div>""";

    private static final String COMMON = """
              .act-strip { width: 100%; height: 20px; flex-direction: row; gap: 4px; }
              .act { height: 18px; padding: 2px 6px; font-family: heading; border-width: 1px; border-color: #c3cec933; background-color: #ffffff0d; text-align: center; color: #c5d6cb; }
              .act-send { width: 96px; color: #7fc8e8; background-color: #102833; border-color: #7fc8e8aa; }
              .act-verb { width: 140px; color: #ffd464; background-color: #3a3018; border-color: #ffd464aa; display: none; }
              .act-open { display: none; }
              .act-chev { display: none; }
            """;

    private static final String STRIP = """
              .mission-optional { height: 98px; }
              #mission-side-dust-ledger-warden .act-respects { display: flex; }
              #mission-side-dust-ledger-feed .act-cut { display: flex; }
              #mission-side-dust-ledger-coupling .act-crate { display: flex; }
            """;

    private static final String EXPAND = """
              .act-strip { display: none; }
              .act-chev { display: flex; width: 34px; height: 17px; font-family: heading; text-align: center; color: #7fc8e8; border-width: 1px; border-color: #7fc8e8aa; }
              #mission-side-dust-ledger-warden { height: 214px; border-color: #ffd464aa; }
              #mission-side-dust-ledger-warden .act-chev { display: none; }
              #mission-side-dust-ledger-warden .act-open { display: flex; flex-direction: column; gap: 4px; width: 100%; }
              .act-detail { width: 100%; height: 42px; white-space: normal; overflow: hidden; color: #d4ddd5; }
              .act-wide { width: 100%; height: 20px; }
            """;

    private SideObjectiveRailMock() { }

    /** The rail's markup, or the mock splice of it when {@code mode} is set. */
    static String markup(SnapshotContext context, String path, SideObjectiveInteractionMockSuite.RowMode mode) throws IOException {
        String text = Files.readString(context.modRoot().resolve(path));
        if (mode == null) return text;
        int track = text.lastIndexOf(OPTIONAL_TRACK);
        int locate = text.lastIndexOf(LOCATE);
        if (track < 0 || locate < 0) throw new IllegalStateException("the rail's optional card changed; update the mock splice");
        text = text.substring(0, track + OPTIONAL_TRACK.length()) + "\n" + ACTIONS + text.substring(track + OPTIONAL_TRACK.length());
        text = text.substring(0, locate) + "<div id=\"{card.chevId}\" class=\"act-chev\">ACT</div>" + text.substring(locate);
        int style = text.lastIndexOf("</style>");
        return text.substring(0, style) + COMMON + (mode == SideObjectiveInteractionMockSuite.RowMode.STRIP ? STRIP : EXPAND)
                + text.substring(style);
    }

    /** Grows the Optional list and the rail's panel to fit the taller rows; returns the extra document pixels. */
    @SuppressWarnings("unchecked")
    static int apply(Map<String, Object> props, BattleHudOverlayModel.Presentation presentation,
                     SideObjectiveInteractionMockSuite.RowMode mode) {
        int extra = mode == SideObjectiveInteractionMockSuite.RowMode.STRIP
                ? presentation.optionalRows() * STRIP_EXTRA_PER_ROW : EXPAND_EXTRA;
        int optional = Math.round(BattleHudOverlayModel.optionalHeight(presentation.optionalRows()));
        ((MutableSignal<String>) props.get("optionalListStyle")).set("height: " + (optional - 6 + extra) + "px;");
        ((MutableSignal<String>) props.get("missionPanelStyle"))
                .set("height: " + (33 + presentation.missionRows() * 174 + optional + extra) + "px;");
        return extra;
    }
}
