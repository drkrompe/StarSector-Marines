package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.objective.side.SideObjectiveService.Offer;
import com.dillon.starsectormarines.battle.objective.side.SideObjectiveService.OfferVerb;
import com.dillon.starsectormarines.battle.objective.side.SideTrigger.Interact.Layout;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** What the world prompt for one side-objective offer shows: its heading, a line of what it does and its small buttons. */
final class BattleSidePromptModel {
    private static final String HIDDEN = "p-hidden";
    /** Document pixels: padding, the heading row, the detail line, a button row and the gap under each. */
    static final int PAD = 4, HEAD = 16, GAP = 3, DETAIL = 15, BUTTON = 20, LINE_GAP = 4;
    static final int CARD_WIDTH = 236, LINES_WIDTH = 360;

    private final MutableSignal<String> title, tag, detail, detailClasses, verbsClasses, linesClasses;
    private final MutableSignal<List<Verb>> verbs;
    private final MutableSignal<List<Line>> lines;

    BattleSidePromptModel(Reactor reactor) {
        title = reactor.signal("");
        tag = reactor.signal("");
        detail = reactor.signal("");
        detailClasses = reactor.signal(HIDDEN);
        verbsClasses = reactor.signal(HIDDEN);
        linesClasses = reactor.signal(HIDDEN);
        verbs = reactor.signal(List.of());
        lines = reactor.signal(List.of());
    }

    /** Shows {@code offer}; {@code press} gets its key and the verb id when a button is pressed. */
    void show(Offer offer, BiConsumer<String, String> press) {
        title.set(offer.title());
        tag.set(offer.tag());
        detail.set(offer.detail());
        boolean card = offer.layout() == Layout.CARD;
        detailClasses.set(card && !offer.detail().isEmpty() ? "p-detail" : HIDDEN);
        verbsClasses.set(card ? "p-verbs-row" : HIDDEN);
        linesClasses.set(card ? HIDDEN : "p-lines");
        verbs.set(card ? offer.verbs().stream().map(verb -> new Verb(verb, () -> press.accept(offer.key(), verb.id()))).toList() : List.of());
        lines.set(card ? List.of() : offer.verbs().stream().map(verb -> new Line(verb, () -> press.accept(offer.key(), verb.id()))).toList());
    }

    Map<String, Object> props() {
        return Map.of("title", title, "tag", tag, "detailClasses", detailClasses, "detail", detail,
                "verbsClasses", verbsClasses, "verbs", verbs, "linesClasses", linesClasses, "lines", lines);
    }

    /** The prompt's document width, which its layout fixes. */
    static int width(Offer offer) { return offer.layout() == Layout.CARD ? CARD_WIDTH : LINES_WIDTH; }

    /** The prompt's document height for {@code offer}: heading, then a detail line and a row of buttons, or a line per verb. */
    static int height(Offer offer) {
        int h = PAD + HEAD + GAP;
        if (offer.layout() == Layout.CARD) return h + (offer.detail().isEmpty() ? 0 : DETAIL + GAP) + BUTTON + GAP;
        int n = offer.verbs().size();
        return h + n * BUTTON + (n - 1) * LINE_GAP + GAP;
    }

    private record Verb(OfferVerb verb, Runnable action) implements MarkupPropertySource {
        @Override public Object markupProperty(String key) {
            return switch (key) {
                case "id" -> "verb-" + verb.id();
                case "nameId" -> "verb-" + verb.id() + "-name";
                case "name" -> verb.name();
                case "action" -> action;
                default -> throw new IllegalArgumentException(key);
            };
        }
    }

    private record Line(OfferVerb verb, Runnable action) implements MarkupPropertySource {
        @Override public Object markupProperty(String key) {
            return switch (key) {
                case "id" -> "line-" + verb.id();
                case "buttonId" -> "line-" + verb.id() + "-button";
                case "nameId" -> "line-" + verb.id() + "-name";
                case "textId" -> "line-" + verb.id() + "-text";
                case "name" -> verb.name();
                case "result" -> verb.result();
                case "action" -> action;
                default -> throw new IllegalArgumentException(key);
            };
        }
    }
}
