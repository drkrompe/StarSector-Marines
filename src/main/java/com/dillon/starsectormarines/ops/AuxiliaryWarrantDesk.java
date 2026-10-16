package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.AuxiliaryWarrantProgress;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;

import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The Hegemony auxiliary warrant's correspondence at Company HQ: the terms, then the office's
 * reply to the company's answer. Held by the screen so an open letter survives the hourly
 * rebuild of the page. It never decides eligibility or grants anything itself; answering calls
 * {@link com.dillon.starsectormarines.marine.HegemonyAuxiliaryWarrant}.
 */
final class AuxiliaryWarrantDesk {

    enum Page { TERMS, ACCEPTED, DECLINED }

    static final String CREST = "graphics/factions/crest_hegemony.png";

    private final Supplier<AuxiliaryWarrantProgress.Stage> stage;
    private final BooleanSupplier accept;
    private final BooleanSupplier decline;
    private final Runnable answered;
    private final MutableSignal<Row> row;
    private boolean visible;
    private Page page = Page.TERMS;

    AuxiliaryWarrantDesk(Reactor reactor, Supplier<AuxiliaryWarrantProgress.Stage> stage,
                         BooleanSupplier accept, BooleanSupplier decline, Runnable answered) {
        this.stage = stage;
        this.accept = accept;
        this.decline = decline;
        this.answered = answered;
        row = reactor.signal(project());
    }

    Signal<Row> row() { return row; }

    void put(Map<String, Object> props) {
        refresh();
        props.put("auxiliary", row);
    }

    void open() {
        visible = true;
        page = Page.TERMS;
        refresh();
    }

    void close() {
        visible = false;
        refresh();
    }

    void refresh() { row.set(project()); }

    private void onAccept() {
        if (accept.getAsBoolean()) {
            page = Page.ACCEPTED;
            answered.run();
        }
        refresh();
    }

    private void onDecline() {
        if (decline.getAsBoolean()) {
            page = Page.DECLINED;
            answered.run();
        }
        refresh();
    }

    private Row project() {
        boolean awaiting = stage.get() == AuxiliaryWarrantProgress.Stage.OFFERED;
        return new Row(awaiting, visible, page, this::open, this::onAccept, this::onDecline, this::close);
    }

    /** A still letter for review evidence: no campaign behind it. */
    static Row preview(Page page) {
        return new Row(page == Page.TERMS, true, page, () -> { }, () -> { }, () -> { }, () -> { });
    }

    static Row hidden() {
        return new Row(false, false, Page.TERMS, () -> { }, () -> { }, () -> { }, () -> { });
    }

    record Row(boolean awaiting, boolean visible, Page page, Runnable open, Runnable accept,
               Runnable decline, Runnable close) implements MarkupPropertySource {
        @Override
        public Object markupProperty(String key) {
            boolean terms = page == Page.TERMS;
            String part = terms ? "label aux-part" : "aux-hidden-part";
            return switch (key) {
                case "resumeClasses" -> awaiting ? "hq-wings console-action" : "aux-hidden-part";
                case "classes" -> visible ? "aux-backdrop" : "aux-hidden";
                case "crest" -> CREST;
                case "title" -> Strings.get(switch (page) {
                    case TERMS -> "auxiliaryWarrantTitle";
                    case ACCEPTED -> "auxiliaryWarrantAcceptedTitle";
                    case DECLINED -> "auxiliaryWarrantDeclinedTitle";
                });
                case "from" -> Strings.get("auxiliaryWarrantFrom");
                case "record" -> switch (page) {
                    case TERMS -> Strings.get("auxiliaryWarrantRecord");
                    case ACCEPTED -> Strings.get("auxiliaryWarrantAcceptedCopy");
                    case DECLINED -> Strings.get("auxiliaryWarrantDeclinedCopy");
                };
                case "grants" -> terms ? Strings.get("auxiliaryWarrantGrants") : "";
                case "obligations" -> terms ? Strings.get("auxiliaryWarrantObligations") : "";
                case "limits" -> terms ? Strings.get("auxiliaryWarrantLimits") : "";
                case "declineNote" -> terms ? Strings.get("auxiliaryWarrantDeclineNote") : "";
                case "partClasses" -> part;
                case "grantsClasses" -> terms ? "label aux-part aux-grants" : "aux-hidden-part";
                case "noteClasses" -> terms ? "label tone-muted aux-part" : "aux-hidden-part";
                case "answerClasses" -> terms ? "aux-action console-action" : "aux-hidden-part";
                case "acceptLabel" -> Strings.get("auxiliaryWarrantAccept");
                case "declineLabel" -> Strings.get("auxiliaryWarrantDecline");
                case "closeLabel" -> Strings.get(terms ? "auxiliaryWarrantLater" : "auxiliaryWarrantDone");
                case "resumeLabel" -> Strings.get("auxiliaryWarrantResume");
                case "open" -> open;
                case "accept" -> accept;
                case "decline" -> decline;
                case "close" -> close;
                default -> throw new IllegalArgumentException(key);
            };
        }
    }
}
