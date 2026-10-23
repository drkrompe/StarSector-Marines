package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Render-free state of the screen a battle shows while its textures load
 * ({@code battle-textures.md}, Loading screen).
 */
final class BattleLoadingOverlayModel {

    /** Where the progress sits on the screen. */
    enum Look {
        /** A card in the middle of a dark screen. */
        CARD("battle-loading-overlay"),
        /** A strip along the bottom of the screen. */
        STRIP("battle-loading-overlay loading-strip"),
        /** The bar alone. */
        MINIMAL("battle-loading-overlay loading-minimal");

        final String classes;

        Look(String classes) {
            this.classes = classes;
        }
    }

    /** What the line under the bar says. */
    enum Detail {
        /** What is loading now: "Armor: Charcoal". */
        STEP,
        /** How far along it is: "42%". */
        PERCENT,
        /** How much is on the card so far: "212 MB". */
        MEGABYTES
    }

    private final MutableSignal<String> rootClasses;
    private final MutableSignal<String> title;
    private final MutableSignal<String> area;
    private final MutableSignal<String> detail;
    private final MutableSignal<String> progressStyle;
    private final Detail detailMode;

    BattleLoadingOverlayModel(Reactor reactor, Look look, Detail detailMode) {
        this.detailMode = detailMode;
        rootClasses = reactor.signal(look.classes);
        title = reactor.signal("");
        area = reactor.signal("");
        detail = reactor.signal("");
        progressStyle = reactor.signal("width: 0%;");
    }

    Map<String, Object> props() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("rootClasses", rootClasses);
        props.put("title", title);
        props.put("area", area);
        props.put("detail", detail);
        props.put("progressStyle", progressStyle);
        return props;
    }

    /** The operation's name and where it is fought. */
    void heading(String operation, String where) {
        title.set(operation == null ? "" : operation);
        area.set(where == null ? "" : where);
    }

    /**
     * @param progress share done, 0 to 1
     * @param step what is loading now
     * @param bytesLoaded what the battle holds on the card so far
     */
    void update(float progress, String step, long bytesLoaded) {
        int percent = Math.round(100f * Math.max(0f, Math.min(1f, progress)));
        progressStyle.set("width: " + percent + "%;");
        detail.set(switch (detailMode) {
            case STEP -> step == null ? "" : step;
            case PERCENT -> percent + "%";
            case MEGABYTES -> megabytes(bytesLoaded) + " MB";
        });
    }

    static long megabytes(long bytes) {
        return Math.round(bytes / (1024.0 * 1024.0));
    }

    String detail() {
        return detail.get();
    }

    String progressStyle() {
        return progressStyle.get();
    }
}
