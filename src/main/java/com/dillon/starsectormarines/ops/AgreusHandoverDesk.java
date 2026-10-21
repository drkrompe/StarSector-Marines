package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
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
                case "resumeLabel" -> Strings.get("agreusDeskResume");
                case "classes" -> visible ? "aux-backdrop" : "aux-hidden";
                case "crest" -> OpeningBroker.CREST;
                case "title" -> switch (page) {
                    case CUSTODY -> Strings.get("agreusDeskTitleCustody");
                    case COMPACT -> Strings.get("agreusDeskTitleCompact");
                    case COMPARE -> Strings.get("agreusDeskTitleCompare");
                    case CONFIRM_CUSTODY -> Strings.get("agreusDeskTitleConfirmCustody");
                    case CONFIRM_COMPACT -> Strings.get("agreusDeskTitleConfirmCompact");
                    case CONFIRM_DECLINE -> Strings.get("agreusDeskTitleConfirmDecline");
                    case SIGNED -> Strings.format("agreusDeskTitleSigned", Strings.upper(signed == null ? Strings.get("agreusDeskAgreement") : AgreusWorkGuide.agreementName(signed)));
                    case DECLINED -> Strings.get("agreusDeskTitleDeclined");
                };
                case "from" -> Strings.get("agreusDeskFrom");
                case "record" -> switch (page) {
                    case CUSTODY, COMPACT -> Strings.get("agreusArcAgreementCommon");
                    case COMPARE -> Strings.get("agreusArcCastleComparison");
                    case CONFIRM_CUSTODY, CONFIRM_COMPACT -> Strings.get("agreusArcSigningNote");
                    case CONFIRM_DECLINE -> Strings.get("agreusDeskRecordConfirmDecline");
                    case SIGNED -> Strings.get("agreusDeskRecordSigned");
                    case DECLINED -> Strings.get("agreusArcDeclinedRecord");
                };
                case "undertaking" -> agreementPage() ? Strings.get("agreusDeskUndertaking") + Strings.get(custodyText() ? "agreusArcCustodyUndertaking" : "agreusArcCompactUndertaking") : "";
                case "benefit" -> agreementPage() ? Strings.get("agreusDeskBenefit") + Strings.get(custodyText() ? "agreusArcCustodyBenefit" : "agreusArcCompactBenefit") : "";
                case "cost" -> agreementPage() ? Strings.get("agreusDeskCost") + Strings.get(custodyText() ? "agreusArcCustodyCost" : "agreusArcCompactCost") : "";
                case "letter" -> page == Page.SIGNED ? Strings.format("agreusDeskLetter", AgreusArcCampaign.VOSS_WARNING.speaker(), AgreusArcCampaign.VOSS_WARNING.text()) : "";
                case "termsClasses" -> agreementPage() ? "label aux-part aux-grants" : hiddenPart;
                case "partClasses" -> agreementPage() ? part : hiddenPart;
                case "letterClasses" -> page == Page.SIGNED ? "label tone-muted aux-part" : hiddenPart;
                case "tabClasses" -> reading() ? "aux-controls" : hiddenPart;
                case "custodyTabClasses" -> tab(Page.CUSTODY);
                case "compactTabClasses" -> tab(Page.COMPACT);
                case "compareTabClasses" -> tab(Page.COMPARE);
                case "custodyTabLabel" -> Strings.get("agreusDeskTabCustody");
                case "compactTabLabel" -> Strings.get("agreusDeskTabCompact");
                case "compareTabLabel" -> Strings.get("agreusDeskTabCompare");
                case "primaryClasses" -> page == Page.CUSTODY || page == Page.COMPACT || confirming() ? "aux-action console-action" : hiddenPart;
                case "primaryLabel" -> switch (page) {
                    case CUSTODY -> Strings.get("agreusDeskPrimaryCustody");
                    case COMPACT -> Strings.get("agreusDeskPrimaryCompact");
                    case CONFIRM_CUSTODY -> Strings.get("agreusDeskPrimaryConfirmCustody");
                    case CONFIRM_COMPACT -> Strings.get("agreusDeskPrimaryConfirmCompact");
                    case CONFIRM_DECLINE -> Strings.get("agreusDeskPrimaryConfirmDecline");
                    default -> "";
                };
                case "secondaryClasses" -> reading() || confirming() ? "aux-action console-action" : hiddenPart;
                case "secondaryLabel" -> Strings.get(reading() ? "agreusDeskSecondaryDecline" : "agreusDeskSecondaryBack");
                case "closeLabel" -> Strings.get(reading() || confirming() ? "agreusDeskLater" : "agreusDeskDone");
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
