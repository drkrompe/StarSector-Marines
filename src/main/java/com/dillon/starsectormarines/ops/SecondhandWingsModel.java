package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.campaign.start.SecondhandWingsCampaign.Offer;
import com.dillon.starsectormarines.marine.BoatDeck;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.marine.SecondhandWings;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** A portrait conversation over the room, followed by the actual fitting controls. */
final class SecondhandWingsModel {
    private final BoatDeck deck;
    private final Supplier<Offer> offer;
    private final Function<SecondhandWings.Review, String> purchase;
    private final Runnable changed, inspect;
    private final Runnable reviewedNotice;
    private final MutableSignal<Row> row;
    private SecondhandWings.Review reviewed;
    private boolean visible;
    private int page;
    private String feedback;

    SecondhandWingsModel(Reactor reactor, BoatDeck deck, Supplier<Offer> offer,
                        Function<SecondhandWings.Review, String> purchase, Runnable changed, Runnable inspect) {
        this(reactor, deck, offer, purchase, changed, inspect, () -> { });
    }

    SecondhandWingsModel(Reactor reactor, BoatDeck deck, Supplier<Offer> offer,
                        Function<SecondhandWings.Review, String> purchase, Runnable changed, Runnable inspect,
                        Runnable reviewedNotice) {
        this.reviewedNotice = reviewedNotice;
        this.deck = deck; this.offer = offer; this.purchase = purchase;
        this.changed = changed; this.inspect = inspect;
        row = reactor.signal(project());
    }
    Signal<Row> row() { return row; }
    boolean visible() { return visible; }
    void dismiss() { visible = false; refresh(); }
    void open() {
        reviewed = SecondhandWings.review(deck); page = 0; feedback = null; visible = true;
        reviewedNotice.run(); refresh();
    }
    void refresh() { row.set(project()); }

    private void act() {
        if (page == 0) { reviewed = SecondhandWings.review(deck); page = 1; }
        else if (page == 1) {
            feedback = purchase.apply(reviewed);
            if (feedback == null) { page = 2; changed.run(); }
            else reviewed = SecondhandWings.review(deck);
        } else { dismiss(); inspect.run(); }
        refresh();
    }
    private Row project() {
        Offer live = offer.get();
        String trade = reviewed == null ? "" : reviewed.outgoing().stream().map(boat ->
                boat.name() + " / " + BoatFitting.findById(boat.plating()).displayName()
                        + " / " + BoatFitting.findById(boat.drive()).displayName()).collect(Collectors.joining("\n"));
        if (trade.isEmpty()) trade = Strings.get("openingSecondhandWingsNoAirworthyHermesAboardAnyUnrepairedOriginal");
        return new Row(live.visible(), visible, page, live.credits(), live.price(), live.refusal() != null,
                feedback != null ? feedback : live.refusal(), trade, this::open, this::act, this::dismiss);
    }

    record Row(boolean available, boolean visible, int page, int credits, int price, boolean blocked, String refusal,
               String trade, Runnable open, Runnable action, Runnable dismiss) implements MarkupPropertySource {
        private long shortfall() { return Math.max(0, price - credits); }
        private boolean bundlesBay() { return price > SecondhandWings.CREDIT_COST; }
        static Row hidden() { return new Row(false, false, 0, 0, SecondhandWings.CREDIT_COST, false, null, "", () -> { }, () -> { }, () -> { }); }
        @Override public Object markupProperty(String key) {
            return switch (key) {
                case "resumeClasses" -> available ? "wings-resume console-action" : "hidden";
                case "classes" -> visible ? "wings-backdrop" : "wings-hidden";
                case "portrait" -> page == 1 ? OpeningBroker.PORTRAIT : "graphics/portraits/castle.png";
                case "title" -> page == 1 ? Strings.get("openingSecondhandWingsMaraVennSecondhandWings") : Strings.get("openingSecondhandWingsCastleSecondhandWings");
                case "copy" -> switch (page) {
                    case 0 -> Strings.get("openingSecondhandWingsThoseHermesWrecksGotUsStartedWe");
                    case 1 -> Strings.get(bundlesBay() ? "openingSecondhandWingsYouGaveOurCooperativeBayIncluded"
                            : "openingSecondhandWingsYouGaveOurCooperativeAPlaceTo");
                    default -> Strings.get("openingSecondhandWingsThoseAreOursCaptainProperLandingCraft");
                };
                case "priceClasses" -> page == 2 ? "hidden" : shortfall() > 0 ? "wings-price short" : "wings-price";
                case "priceLabel" -> Strings.get(bundlesBay() ? "openingSecondhandWingsPriceLabelWithBay" : "openingSecondhandWingsPriceLabel");
                case "price" -> CompanyResources.number(price);
                case "stock" -> shortfall() > 0 ? Strings.format("companyResourceShortBy", CompanyResources.number(shortfall()))
                        : Strings.get("companyResourceAvailable") + "  " + CompanyResources.number(credits);
                case "stockClasses" -> shortfall() > 0 ? "label wings-price-stock short" : "label wings-price-stock tone-muted";
                case "oldName" -> ShuttleType.HERMES.displayName().toUpperCase(Strings.locale());
                case "newName" -> ShuttleType.AEROSHUTTLE.displayName().toUpperCase(Strings.locale());
                case "oldSeats" -> Strings.format("openingSecondhandWingsCraftSeats", ShuttleType.HERMES.capacity);
                case "newSeats" -> Strings.format("openingSecondhandWingsCraftSeats", ShuttleType.AEROSHUTTLE.capacity);
                case "arrow" -> ">";
                case "trade" -> page == 1 ? Strings.format("openingSecondhandWingsExchangeIncludes", trade) : Strings.get("openingSecondhandWingsTwoRepairedLandersReplace");
                case "reason" -> page == 2 ? Strings.get("openingSecondhandWingsDeliveredAtStandardFitPlatingAndDrive")
                        : page == 1 && refusal != null ? refusal : Strings.get(bundlesBay()
                        ? "openingSecondhandWingsBayRestorationIncluded" : "openingSecondhandWingsNoAdditionalMaterialBill");
                case "actionLabel" -> page == 0 ? Strings.get("openingSecondhandWingsReviewOffer") : page == 1
                        ? Strings.format("openingSecondhandWingsBuyPair", CompanyResources.number(price)) : Strings.get("openingSecondhandWingsInspectFittings");
                case "disabled" -> page == 1 && blocked;
                case "oldArt" -> ShuttleType.HERMES.spritePath;
                case "newArt" -> ShuttleType.AEROSHUTTLE.spritePath;
                case "open" -> open;
                case "action" -> action;
                case "dismiss" -> dismiss;
                default -> throw new IllegalArgumentException("Unknown Secondhand Wings property: " + key);
            };
        }
    }
}
