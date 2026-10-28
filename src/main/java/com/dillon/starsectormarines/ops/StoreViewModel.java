package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.EquipmentStore;
import com.dillon.starsectormarines.marine.EquipmentTemplateCatalog;
import com.dillon.starsectormarines.marine.FabricationResources;
import com.dillon.starsectormarines.marine.LoadoutCard;
import com.dillon.starsectormarines.marine.MarineArmory;
import com.dillon.starsectormarines.marine.MechBay;
import com.dillon.starsectormarines.marine.MechPattern;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.ComputedSignal;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The Store page's state: the docked market's shelf, which offer is selected, and what buying it
 * would do. The rules (what is for sale, what it costs, what a purchase does) belong to
 * {@link EquipmentStore}; this class only presents them.
 *
 * <p>The shelf is read once when the page opens. Buying a card removes it from what the company
 * lacks, and a shelf recomputed after that would quietly fill the slot with something else under
 * the player's cursor; instead an item bought here stays on the shelf, marked acquired, until the
 * page is opened again.
 */
final class StoreViewModel {

    private final Supplier<List<EquipmentStore.Offer>> shelfSource;
    private final Supplier<MarineArmory> armory;
    private final Supplier<MechBay> bay;
    private final FabricationResources purse;
    private final Supplier<String> marketName;

    private final MutableSignal<Integer> revision;
    private final MutableSignal<String> selectedKey;
    private final MutableSignal<String> verdictText;
    private final ComputedSignal<List<OfferRow>> rows;
    private final ComputedSignal<String> shelfNote;
    private final ComputedSignal<String> selectedName;
    private final ComputedSignal<String> selectedKind;
    private final ComputedSignal<String> selectedDetail;
    private final ComputedSignal<String> priceLabel;
    private final ComputedSignal<String> buyLabel;
    private final ComputedSignal<String> buyClasses;
    private final ComputedSignal<List<CompanyResources.Row>> resourceStock;
    private final ComputedSignal<String> contextLabel;

    private List<EquipmentStore.Offer> shelf = List.of();
    /** Offers bought on this visit; they stay listed so the shelf does not shift under the cursor. */
    private final Set<String> acquired = new LinkedHashSet<>();

    StoreViewModel(Reactor reactor, Supplier<List<EquipmentStore.Offer>> shelfSource,
                   Supplier<MarineArmory> armory, Supplier<MechBay> bay,
                   FabricationResources purse, Supplier<String> marketName) {
        this.shelfSource = shelfSource;
        this.armory = armory;
        this.bay = bay;
        this.purse = purse;
        this.marketName = marketName;
        revision = reactor.signal(0);
        selectedKey = reactor.signal("");
        verdictText = reactor.signal("");
        rows = reactor.computed(this::buildRows);
        shelfNote = reactor.computed(() -> {
            revision.get();
            return Strings.get(shelf.isEmpty() ? "storeShelfEmpty" : "storeShelfNote");
        });
        selectedName = reactor.computed(() -> {
            EquipmentStore.Offer offer = selected();
            return offer == null ? Strings.get("storeNothingSelected") : name(offer);
        });
        selectedKind = reactor.computed(() -> {
            EquipmentStore.Offer offer = selected();
            return offer == null ? "" : kind(offer) + " / " + shelfLabel(offer.shelf());
        });
        selectedDetail = reactor.computed(() -> {
            EquipmentStore.Offer offer = selected();
            return offer == null ? Strings.get("storeSelectPrompt") : detail(offer);
        });
        priceLabel = reactor.computed(() -> {
            EquipmentStore.Offer offer = selected();
            revision.get();
            return offer == null ? "" : priceBill(offer);
        });
        buyLabel = reactor.computed(() -> {
            EquipmentStore.Offer offer = selected();
            revision.get();
            if (offer == null) return Strings.get("storeBuyNothing");
            if (owned(offer)) return Strings.get("storeBuyAcquired");
            return Strings.format("storeBuyFmt", CompanyResources.creditAmount(offer.price()));
        });
        buyClasses = reactor.computed(() -> {
            EquipmentStore.Offer offer = selected();
            revision.get();
            if (offer == null || owned(offer)) return "store-buy current";
            return purse.credits() < offer.price() ? "store-buy unaffordable" : "store-buy";
        });
        resourceStock = reactor.computed(() -> {
            revision.get();
            return CompanyResources.credits("store-credit-stock", purse.credits(), 0);
        });
        contextLabel = reactor.computed(() -> Strings.format("storeContextFmt", marketName.get()));
        refresh();
    }

    /** Reads the shelf afresh; called when the page opens. */
    void refresh() {
        shelf = shelfSource.get();
        acquired.clear();
        verdictText.set("");
        EquipmentStore.Offer first = shelf.isEmpty() ? null : shelf.get(0);
        selectedKey.set(first == null ? "" : key(first));
        revision.set(revision.get() + 1);
    }

    Signal<List<OfferRow>> rows() { return rows; }
    Signal<String> shelfNote() { return shelfNote; }
    Signal<String> selectedName() { return selectedName; }
    Signal<String> selectedKind() { return selectedKind; }
    Signal<String> selectedDetail() { return selectedDetail; }
    Signal<String> priceLabel() { return priceLabel; }
    Signal<String> buyLabel() { return buyLabel; }
    Signal<String> buyClasses() { return buyClasses; }
    Signal<String> verdict() { return verdictText; }
    Signal<List<CompanyResources.Row>> resourceStock() { return resourceStock; }
    Signal<String> contextLabel() { return contextLabel; }

    boolean shelfEmpty() { return shelf.isEmpty(); }

    void select(String key) {
        selectedKey.set(key);
        verdictText.set("");
    }

    /** Buys the selected offer and says what happened. */
    EquipmentStore.Sale buySelected() {
        EquipmentStore.Offer offer = selected();
        if (offer == null) return EquipmentStore.Sale.UNAVAILABLE;
        EquipmentStore.Sale sale = EquipmentStore.buy(offer, armory.get(), bay.get(), purse);
        if (sale == EquipmentStore.Sale.SOLD) acquired.add(key(offer));
        verdictText.set(switch (sale) {
            case SOLD -> Strings.format("storeSoldFmt", name(offer));
            case ALREADY_KNOWN -> Strings.format("storeAlreadyKnownFmt", name(offer));
            case UNAVAILABLE -> Strings.get("storeUnavailable");
            case NOT_AT_MARKET -> Strings.get("storeNotAtMarket");
            case CANNOT_AFFORD -> Strings.format("storeCannotAffordFmt",
                    CompanyResources.number(Math.max(0, offer.price() - purse.credits())));
        });
        revision.set(revision.get() + 1);
        return sale;
    }

    private List<OfferRow> buildRows() {
        revision.get();
        String selected = selectedKey.get();
        List<OfferRow> out = new ArrayList<>();
        for (EquipmentStore.Offer offer : shelf) {
            String key = key(offer);
            boolean owned = owned(offer);
            String id = "store-offer:" + key;
            String classes = "store-row" + (key.equals(selected) ? " selected" : "")
                    + (owned ? " acquired" : "");
            out.add(new OfferRow(id, classes, name(offer), kind(offer) + " / " + shelfLabel(offer.shelf()),
                    owned ? Strings.get("storeRowAcquired") : CompanyResources.creditAmount(offer.price()),
                    () -> select(key)));
        }
        return List.copyOf(out);
    }

    private EquipmentStore.Offer selected() {
        String key = selectedKey.get();
        for (EquipmentStore.Offer offer : shelf) {
            if (key(offer).equals(key)) return offer;
        }
        return null;
    }

    private boolean owned(EquipmentStore.Offer offer) {
        return acquired.contains(key(offer)) || EquipmentStore.known(offer, armory.get(), bay.get());
    }

    private static String key(EquipmentStore.Offer offer) {
        return offer.kind().name() + ":" + offer.id();
    }

    private String priceBill(EquipmentStore.Offer offer) {
        if (owned(offer)) return "";
        return CompanyResources.creditBill(offer.price(), purse.credits());
    }

    static String name(EquipmentStore.Offer offer) {
        return switch (offer.kind()) {
            case LOADOUT -> {
                LoadoutCard card = LoadoutCard.parse(offer.id());
                yield card == null ? offer.id() : card.displayName();
            }
            case MECH_PATTERN -> {
                MechPattern pattern = MechPattern.parse(offer.id());
                yield pattern == null ? offer.id() : pattern.displayName();
            }
        };
    }

    static String kind(EquipmentStore.Offer offer) {
        return switch (offer.kind()) {
            case LOADOUT -> {
                LoadoutCard card = LoadoutCard.parse(offer.id());
                yield Strings.get(card != null && card.kind() == LoadoutCard.Kind.SHEET
                        ? "storeKindSheet" : "storeKindWeapon");
            }
            case MECH_PATTERN -> {
                MechPattern pattern = MechPattern.parse(offer.id());
                yield pattern == null ? "" : Strings.get(switch (pattern.kind()) {
                    case CHASSIS -> "mechPatternCardKindChassis";
                    case WEAPON -> "mechPatternCardKindWeapon";
                    case INTERNAL -> "mechPatternCardKindInternal";
                    case VEHICLE -> "mechPatternCardKindVehicle";
                    case KIT -> "mechPatternCardKindKit";
                });
            }
        };
    }

    static String shelfLabel(EquipmentStore.Shelf shelf) {
        return Strings.get(switch (shelf) {
            case LISTED -> "storeShelfListed";
            case MARKET -> "storeShelfMarket";
            case LICENSE -> "storeShelfLicense";
        });
    }

    /** What buying it teaches, in the words the card item used for the same thing. */
    private static String detail(EquipmentStore.Offer offer) {
        StringBuilder text = new StringBuilder();
        switch (offer.kind()) {
            case LOADOUT -> {
                LoadoutCard card = LoadoutCard.parse(offer.id());
                if (card == null) return Strings.get("loadoutCardInvalid");
                text.append(Strings.format(card.kind() == LoadoutCard.Kind.WEAPON
                        ? "loadoutCardWeaponUnlockFmt" : "loadoutCardSheetUnlockFmt", card.displayName()));
                StringBuilder contents = new StringBuilder();
                for (String templateId : card.templateIds()) {
                    if (contents.length() > 0) contents.append(", ");
                    contents.append(EquipmentTemplateCatalog.require(templateId).displayName());
                }
                if (contents.length() > 0) {
                    text.append("\n\n").append(Strings.format("loadoutCardContentsFmt", contents));
                }
                text.append("\n\n").append(Strings.format("mechPatternCardTierFmt",
                        Strings.get("mechPatternCardTier" + card.accessTier().name())));
            }
            case MECH_PATTERN -> {
                MechPattern pattern = MechPattern.parse(offer.id());
                if (pattern == null) return Strings.get("mechPatternCardInvalid");
                text.append(Strings.format(switch (pattern.kind()) {
                    case CHASSIS -> "mechPatternCardChassisUnlockFmt";
                    case WEAPON -> "mechPatternCardWeaponUnlockFmt";
                    case INTERNAL -> "mechPatternCardInternalUnlockFmt";
                    case VEHICLE -> "mechPatternCardVehicleUnlockFmt";
                    case KIT -> "mechPatternCardKitUnlockFmt";
                }, pattern.displayName()));
                if (pattern.weightClass() != null) {
                    text.append("\n\n").append(Strings.format("mechPatternCardWeightFmt",
                            Strings.get("mechPatternCardWeight" + pattern.weightClass().name())));
                }
                text.append("\n").append(Strings.format("mechPatternCardTierFmt",
                        Strings.get("mechPatternCardTier" + pattern.accessTier().name())));
            }
        }
        text.append("\n\n").append(Strings.get("storeLearnedAtOnce"));
        return text.toString();
    }

    /** One line of the shelf. */
    record OfferRow(String id, String classes, String name, String detail, String price, Runnable select)
            implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "classes" -> classes;
                case "nameId" -> id + ":name";
                case "detailId" -> id + ":detail";
                case "priceId" -> id + ":price";
                case "name" -> name;
                case "detail" -> detail;
                case "price" -> price;
                case "select" -> select;
                default -> null;
            };
        }
    }
}
