package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.campaign.start.CompanyBayRestoration;
import com.dillon.starsectormarines.campaign.start.CompanyBayRestoration.Offer;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;
import java.util.Locale;
import java.util.function.Supplier;

/** A review is inspection, never payment. The yard rechecks everything on confirmation. */
public final class BoatBayRestorationModel {
    private final Supplier<Offer> read;
    private final Supplier<String> repair;
    private final Runnable inspect, changed;
    private final MutableSignal<Row> row;
    private final Runnable action = this::act;
    private boolean reviewed;

    public BoatBayRestorationModel(Reactor reactor, Supplier<Offer> read,
                                   Supplier<String> repair, Runnable inspect, Runnable changed) {
        this.read = read;
        this.repair = repair;
        this.inspect = inspect;
        this.changed = changed;
        row = reactor.signal(project());
    }
    public Signal<Row> row() { return row; }
    public void refresh() { row.set(project()); }
    private Row project() {
        Offer offer = read.get();
        return new Row(offer.visible(), reviewed, offer.credits(), offer.refusal(), action);
    }
    private void act() {
        if (!reviewed) {
            reviewed = true;
            inspect.run();
        } else {
            String refused = repair.get();
            if (refused == null) { reviewed = false; changed.run(); }
        }
        refresh();
    }
    public record Row(boolean visible, boolean reviewed, int credits, String refusal,
                      Runnable action) implements MarkupPropertySource {
        public static Row hidden() { return new Row(false, false, 0, "", () -> { }); }
        @Override public Object markupProperty(String key) {
            return switch (key) {
                case "classes" -> visible ? "bay-restoration" : "bay-restoration hidden";
                case "portrait" -> CompanyManagementGuide.portrait();
                case "copy" -> Strings.get("openingBoatBayRestorationThereIsAnotherWreckedHermesBehindThat");
                case "bill" -> String.format(Locale.ROOT, Strings.get("openingBoatBayRestorationCreditsCostDAvailableDShortBy"),
                        CompanyBayRestoration.CREDIT_COST, credits, Math.max(0, CompanyBayRestoration.CREDIT_COST - credits));
                case "reason" -> refusal == null
                        ? Strings.get("openingBoatBayRestorationYardLaborAndMaterialsIncludedOpensThe") : refusal;
                case "actionLabel" -> reviewed ? String.format(Locale.ROOT, Strings.get("openingBoatBayRestorationRepairBayD"), CompanyBayRestoration.CREDIT_COST) : Strings.get("openingBoatBayRestorationInspectSecondBay");
                case "disabled" -> reviewed && refusal != null;
                case "action" -> action;
                default -> throw new IllegalArgumentException("Unknown restoration property: " + key);
            };
        }
    }
}
