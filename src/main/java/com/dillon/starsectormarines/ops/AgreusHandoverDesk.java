package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import com.dillon.starsectormarines.campaign.AgreusYardAgreement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;

import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The Latchkey custody decision at Company HQ: both written agreements, Castle's comparison,
 * and the explicit signing or declining action behind a confirmation. Reading, switching pages
 * and closing commit nothing; only {@code sign} and {@code decline} reach the campaign, and the
 * letter turns only when the campaign actually recorded the answer. Held by the screen so an
 * open letter survives the hourly rebuild of the page.
 */
final class AgreusHandoverDesk {

    enum Page { CUSTODY, COMPACT, COMPARE, CONFIRM_CUSTODY, CONFIRM_COMPACT, CONFIRM_DECLINE, SIGNED, DECLINED }

    private final Supplier<AgreusArcProgress.HandoverStage> stage;
    private final Supplier<AgreusYardAgreement> signed;
    private final Predicate<AgreusYardAgreement> sign;
    private final BooleanSupplier decline;
    private final Runnable answered;
    private final MutableSignal<Row> row;
    private boolean visible;
    private Page page = Page.CUSTODY;

    AgreusHandoverDesk(Reactor reactor, Supplier<AgreusArcProgress.HandoverStage> stage,
                       Supplier<AgreusYardAgreement> signed, Predicate<AgreusYardAgreement> sign,
                       BooleanSupplier decline, Runnable answered) {
        this.stage = stage;
        this.signed = signed;
        this.sign = sign;
        this.decline = decline;
        this.answered = answered;
        row = reactor.signal(project());
    }

    Signal<Row> row() { return row; }

    void put(Map<String, Object> props) {
        refresh();
        props.put("handover", row);
    }

    void open() {
        visible = true;
        page = Page.CUSTODY;
        refresh();
    }

    void close() {
        visible = false;
        refresh();
    }

    void refresh() { row.set(project()); }

    private void show(Page next) {
        // Once answered, the reading pages are gone; only the recorded reply remains.
        if (stage.get() == AgreusArcProgress.HandoverStage.OPEN) page = next;
        refresh();
    }

    private void onPrimary() {
        switch (page) {
            case CUSTODY -> show(Page.CONFIRM_CUSTODY);
            case COMPACT -> show(Page.CONFIRM_COMPACT);
            case CONFIRM_CUSTODY -> commit(AgreusYardAgreement.HOUSE_CUSTODY);
            case CONFIRM_COMPACT -> commit(AgreusYardAgreement.YARD_COMPACT);
            case CONFIRM_DECLINE -> {
                if (decline.getAsBoolean()) {
                    page = Page.DECLINED;
                    answered.run();
                }
                refresh();
            }
            default -> refresh();
        }
    }

    private void commit(AgreusYardAgreement agreement) {
        if (sign.test(agreement)) {
            page = Page.SIGNED;
            answered.run();
        }
        refresh();
    }

    private void onSecondary() {
        switch (page) {
            case CUSTODY, COMPACT, COMPARE -> show(Page.CONFIRM_DECLINE);
            case CONFIRM_CUSTODY -> show(Page.CUSTODY);
            case CONFIRM_COMPACT -> show(Page.COMPACT);
            case CONFIRM_DECLINE -> show(Page.COMPARE);
            default -> refresh();
        }
    }

    private Row project() {
        boolean awaiting = stage.get() == AgreusArcProgress.HandoverStage.OPEN;
        return new Row(awaiting, visible, page, signed.get(),
                this::open, () -> show(Page.CUSTODY), () -> show(Page.COMPACT), () -> show(Page.COMPARE),
                this::onPrimary, this::onSecondary, this::close);
    }

    /** A still letter for review evidence: no campaign behind it. */
    static Row preview(Page page, AgreusYardAgreement signed) {
        Runnable none = () -> { };
        return new Row(page != Page.SIGNED && page != Page.DECLINED, true, page, signed,
                none, none, none, none, none, none, none);
    }

    static Row hidden() {
        Runnable none = () -> { };
        return new Row(false, false, Page.CUSTODY, null, none, none, none, none, none, none, none);
    }

    record Row(boolean awaiting, boolean visible, Page page, AgreusYardAgreement signed,
               Runnable open, Runnable custody, Runnable compact, Runnable compare,
               Runnable primary, Runnable secondary, Runnable close) implements MarkupPropertySource {

        boolean reading() { return page == Page.CUSTODY || page == Page.COMPACT || page == Page.COMPARE; }
        boolean confirming() { return page == Page.CONFIRM_CUSTODY || page == Page.CONFIRM_COMPACT || page == Page.CONFIRM_DECLINE; }
        boolean agreementPage() {
            return page == Page.CUSTODY || page == Page.COMPACT || page == Page.CONFIRM_CUSTODY || page == Page.CONFIRM_COMPACT;
        }
        boolean custodyText() { return page == Page.CUSTODY || page == Page.CONFIRM_CUSTODY; }

        @Override
        public Object markupProperty(String key) {
            String part = "label aux-part";
            String hiddenPart = "aux-hidden-part";
            return switch (key) {
                case "resumeClasses" -> awaiting ? "hq-wings console-action" : hiddenPart;
                case "resumeLabel" -> "Latchkey custody: two written agreements await your decision";
                case "classes" -> visible ? "aux-backdrop" : "aux-hidden";
                case "crest" -> OpeningBroker.CREST;
                case "title" -> switch (page) {
                    case CUSTODY -> "HOUSE CUSTODY / WRITTEN AGREEMENT";
                    case COMPACT -> "YARD COMPACT / WRITTEN AGREEMENT";
                    case COMPARE -> "CASTLE / COMPARING THE TWO AGREEMENTS";
                    case CONFIRM_CUSTODY -> "SIGN HOUSE CUSTODY?";
                    case CONFIRM_COMPACT -> "SIGN THE YARD COMPACT?";
                    case CONFIRM_DECLINE -> "DECLINE FURTHER LATCHKEY WORK?";
                    case SIGNED -> "SIGNED / " + (signed == null ? "AGREEMENT" : AgreusWorkGuide.agreementName(signed).toUpperCase());
                    case DECLINED -> "FURTHER WORK DECLINED";
                };
                case "from" -> "Latchkey Yard custody / House Rusk, Latchkey Yard Association, Ko Combine registry";
                case "record" -> switch (page) {
                    case CUSTODY, COMPACT -> AgreusArcCampaign.AGREEMENT_COMMON;
                    case COMPARE -> AgreusArcCampaign.CASTLE_COMPARISON;
                    case CONFIRM_CUSTODY, CONFIRM_COMPACT -> AgreusArcCampaign.SIGNING_NOTE;
                    case CONFIRM_DECLINE -> "Declining ends the company's Latchkey work before any signature. Rusk's three completion fees and both recovered section cards are kept. "
                            + "No custody agreement is signed, no fourth commission is offered, and this cannot be reopened.";
                    case SIGNED -> "The signature is recorded. Victory in Terms of Protection ratifies it; defeat leaves it standing for the next attempt. "
                            + "Terms of Protection is in preparation and cannot be deployed yet.";
                    case DECLINED -> AgreusArcCampaign.DECLINED_RECORD;
                };
                case "undertaking" -> agreementPage() ? "UNDERTAKING: " + (custodyText() ? AgreusArcCampaign.CUSTODY_UNDERTAKING : AgreusArcCampaign.COMPACT_UNDERTAKING) : "";
                case "benefit" -> agreementPage() ? "BENEFIT: " + (custodyText() ? AgreusArcCampaign.CUSTODY_BENEFIT : AgreusArcCampaign.COMPACT_BENEFIT) : "";
                case "cost" -> agreementPage() ? "COST: " + (custodyText() ? AgreusArcCampaign.CUSTODY_COST : AgreusArcCampaign.COMPACT_COST) : "";
                case "letter" -> page == Page.SIGNED ? AgreusArcCampaign.VOSS_WARNING.speaker() + ": “" + AgreusArcCampaign.VOSS_WARNING.text() + "”" : "";
                case "termsClasses" -> agreementPage() ? "label aux-part aux-grants" : hiddenPart;
                case "partClasses" -> agreementPage() ? part : hiddenPart;
                case "letterClasses" -> page == Page.SIGNED ? "label tone-muted aux-part" : hiddenPart;
                case "tabClasses" -> reading() ? "aux-controls" : hiddenPart;
                case "custodyTabClasses" -> tab(Page.CUSTODY);
                case "compactTabClasses" -> tab(Page.COMPACT);
                case "compareTabClasses" -> tab(Page.COMPARE);
                case "custodyTabLabel" -> "House Custody";
                case "compactTabLabel" -> "Yard Compact";
                case "compareTabLabel" -> "Ask Castle to compare";
                case "primaryClasses" -> page == Page.CUSTODY || page == Page.COMPACT || confirming() ? "aux-action console-action" : hiddenPart;
                case "primaryLabel" -> switch (page) {
                    case CUSTODY -> "Sign House Custody...";
                    case COMPACT -> "Sign the Yard Compact...";
                    case CONFIRM_CUSTODY -> "Sign House Custody";
                    case CONFIRM_COMPACT -> "Sign the Yard Compact";
                    case CONFIRM_DECLINE -> "Decline further work";
                    default -> "";
                };
                case "secondaryClasses" -> reading() || confirming() ? "aux-action console-action" : hiddenPart;
                case "secondaryLabel" -> reading() ? "Decline further work..." : "Back";
                case "closeLabel" -> reading() || confirming() ? "Later" : "Done";
                case "open" -> open;
                case "custody" -> custody;
                case "compact" -> compact;
                case "compare" -> compare;
                case "primary" -> primary;
                case "secondary" -> secondary;
                case "close" -> close;
                default -> throw new IllegalArgumentException(key);
            };
        }

        private String tab(Page tab) {
            return page == tab ? "aux-action console-action handover-tab-current" : "aux-action console-action";
        }
    }
}
