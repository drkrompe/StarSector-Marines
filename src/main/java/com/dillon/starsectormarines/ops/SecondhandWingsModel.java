package com.dillon.starsectormarines.ops;

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
        if (trade.isEmpty()) trade = "No airworthy Hermes aboard. Any unrepaired original wrecks are included in the exchange.";
        return new Row(live.visible(), visible, page, live.credits(), live.refusal() != null,
                feedback != null ? feedback : live.refusal(), trade, this::open, this::act, this::dismiss);
    }

    record Row(boolean available, boolean visible, int page, int credits, boolean blocked, String refusal,
               String trade, Runnable open, Runnable action, Runnable dismiss) implements MarkupPropertySource {
        static Row hidden() { return new Row(false, false, 0, 0, false, null, "", () -> { }, () -> { }, () -> { }); }
        @Override public Object markupProperty(String key) {
            return switch (key) {
                case "resumeClasses" -> available ? "wings-resume console-action" : "hidden";
                case "classes" -> visible ? "wings-backdrop" : "wings-hidden";
                case "portrait" -> page == 1 ? OpeningBroker.PORTRAIT : "graphics/portraits/castle.png";
                case "title" -> page == 1 ? "MARA VENN / SECONDHAND WINGS" : "CASTLE / SECONDHAND WINGS";
                case "copy" -> switch (page) {
                    case 0 -> "Those Hermes wrecks got us started. We patched one for the first contracts; opening the sealed bay gives us the other. But four seats and a courier's engine mean too many trips under fire. Mara has found two surplus Aeroshuttles. Rough paint, sound frames. We pay the yard to make them ours.";
                    case 1 -> "You gave our cooperative a place to start again. I want your company around to share in the work. Two airframes: 3,000 credits. Refurbishment, standard fittings and bay adapters: 2,000. Restore Second Wind's second bay separately before delivery. The yard takes the old Hermes, their installed fittings and any remaining wrecks; those fittings do not transfer. This is optional. The offer will wait.";
                    default -> "Those are ours, Captain. Proper landing craft, paid for by our work. Two Aeroshuttles, six seats each. A twelve-person squad can land together when both fly; larger forces still need repeat trips. The bay adapters belong to Second Wind. Select a boat, then PLATING or DRIVE to compare upgrades. Plating improves survival; drives shorten the approach. Nothing else needs buying now. The next briefing uses these actual boats.";
                };
                case "bill" -> page == 2 ? "DELIVERED / 2 AEROSHUTTLES / 12 SEATS PER LIFT"
                        : String.format("PAIR + REFIT  5,000 cr   /   AVAILABLE  %,d cr", credits);
                case "trade" -> page == 1 ? "EXCHANGE INCLUDES\n" + trade : "HERMES  4 SEATS   >   AEROSHUTTLE  6 SEATS\nTwo repaired landers replace the ship's old courier boats.";
                case "reason" -> page == 2 ? "Delivered at standard fit. Plating and drive upgrades are optional."
                        : page == 1 && refusal != null ? refusal : "No additional material bill. Bay restoration is a separate 8,000-credit order.";
                case "actionLabel" -> page == 0 ? "REVIEW OFFER" : page == 1 ? "BUY PAIR - 5,000" : "INSPECT FITTINGS";
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
