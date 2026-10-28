package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.marine.BoatGun;
import com.dillon.starsectormarines.marine.BoatParts;
import com.dillon.starsectormarines.marine.EquipmentStore;
import com.dillon.starsectormarines.marine.EquipmentTemplateCatalog;
import com.dillon.starsectormarines.marine.FabricationResources;
import com.dillon.starsectormarines.marine.LoadoutCard;
import com.dillon.starsectormarines.marine.MechPattern;
import com.dillon.starsectormarines.marine.ArmorIssueResolver;
import com.dillon.starsectormarines.marine.SquadArmorPlan;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.marine.SquadWeaponDoctrine;
import com.dillon.starsectormarines.marine.StoreSlot;
import com.dillon.starsectormarines.ops.battleview.StoreSymbolCanvas;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.ComputedSignal;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final Supplier<EquipmentStore.Holdings> holdings;
    private final FabricationResources purse;
    private final Supplier<String> marketName;

    private final MutableSignal<Integer> revision;
    private final MutableSignal<String> selectedKey;
    private final MutableSignal<String> verdictText;
    /**
     * What the shelf is narrowed to: {@code SECTION:<name>} or {@code SLOT:<name>}. There is always
     * one, because the category list is an accordion: exactly one section is open, showing its slots.
     */
    private final MutableSignal<String> filter;
    private final ComputedSignal<List<CategoryRow>> categoryRows;
    private final ComputedSignal<List<OfferRow>> rows;
    private final ComputedSignal<String> shelfNote;
    private final ComputedSignal<String> selectedName;
    private final ComputedSignal<List<FleetArmoryViewModel.DoctrineTile>> selectedCards;
    /** The loadout card each marine offer is shown as, built once per offer; null for none. */
    private final Map<String, FleetArmoryViewModel.DoctrineTile> cardTiles = new HashMap<>();
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

    /** An image a row that shows no flag still names, so its hidden flag loads. */
    private static final String NO_FLAG = "graphics/ui/store/slot-marine.svg";
    private static final String DEFAULT_FILTER = "SECTION:" + StoreSlot.Section.MARINE.name();

    StoreViewModel(Reactor reactor, Supplier<List<EquipmentStore.Offer>> shelfSource,
                   Supplier<EquipmentStore.Holdings> holdings,
                   FabricationResources purse, Supplier<String> marketName) {
        this.shelfSource = shelfSource;
        this.holdings = holdings;
        this.purse = purse;
        this.marketName = marketName;
        revision = reactor.signal(0);
        selectedKey = reactor.signal("");
        verdictText = reactor.signal("");
        filter = reactor.signal(DEFAULT_FILTER);
        categoryRows = reactor.computed(this::buildCategoryRows);
        rows = reactor.computed(this::buildRows);
        shelfNote = reactor.computed(() -> {
            revision.get();
            filter.get();
            return Strings.get(shelf.isEmpty() ? "storeShelfEmpty"
                    : visible().isEmpty() ? "storeShelfEmptyCategory" : "storeShelfNote");
        });
        selectedCards = reactor.computed(() -> {
            EquipmentStore.Offer offer = selected();
            FleetArmoryViewModel.DoctrineTile tile = offer == null ? null : cardTile(offer);
            return tile == null ? List.of() : List.of(tile);
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
        // Open the first section that has something on the shelf, so the page never opens empty.
        String opening = DEFAULT_FILTER;
        for (StoreSlot.Section section : StoreSlot.Section.values()) {
            if (count("SECTION:" + section.name()) > 0) {
                opening = "SECTION:" + section.name();
                break;
            }
        }
        filter.set(opening);
        List<EquipmentStore.Offer> shown = visible();
        selectedKey.set(shown.isEmpty() ? "" : key(shown.get(0)));
        revision.set(revision.get() + 1);
    }

    Signal<List<CategoryRow>> categoryRows() { return categoryRows; }

    /** Narrows the shelf and, when the selected item is no longer shown, selects the first that is. */
    void selectFilter(String key) {
        filter.set(key);
        verdictText.set("");
        List<EquipmentStore.Offer> shown = visible();
        if (shown.stream().noneMatch(offer -> key(offer).equals(selectedKey.get()))) {
            selectedKey.set(shown.isEmpty() ? "" : key(shown.get(0)));
        }
    }

    /** The shelf as narrowed by the filter. */
    private List<EquipmentStore.Offer> visible() {
        String current = filter.get();
        List<EquipmentStore.Offer> shown = new ArrayList<>();
        for (EquipmentStore.Offer offer : shelf) {
            if (matches(current, offer.slot())) shown.add(offer);
        }
        return shown;
    }

    private static boolean matches(String filterKey, StoreSlot slot) {
        if (slot == null) return false;
        return filterKey.equals("SECTION:" + slot.section().name()) || filterKey.equals("SLOT:" + slot.name());
    }

    private List<CategoryRow> buildCategoryRows() {
        revision.get();
        String current = filter.get();
        List<CategoryRow> out = new ArrayList<>();
        StoreSlot.Section open = openSection(current);
        for (StoreSlot.Section section : StoreSlot.Section.values()) {
            String sectionKey = "SECTION:" + section.name();
            boolean expanded = section == open;
            out.add(categoryRow(sectionKey, "store-cat-" + section.name(), "store-cat section", section, null,
                    (expanded ? "- " : "+ ") + Strings.get("storeSection" + section.name()),
                    count(sectionKey), current));
            if (!expanded) continue;
            for (StoreSlot slot : StoreSlot.of(section)) {
                String slotKey = "SLOT:" + slot.name();
                out.add(categoryRow(slotKey, "store-cat-" + slot.name(), "store-cat slot", section, slot,
                        Strings.get("storeSlot" + slot.name()), count(slotKey), current));
            }
        }
        return List.copyOf(out);
    }

    /** The one section whose slots are showing: the filter's own, or the section of its slot. */
    private static StoreSlot.Section openSection(String filterKey) {
        if (filterKey.startsWith("SLOT:")) return StoreSlot.valueOf(filterKey.substring("SLOT:".length())).section();
        return StoreSlot.Section.valueOf(filterKey.substring("SECTION:".length()));
    }

    private CategoryRow categoryRow(String key, String id, String classes, StoreSlot.Section section,
                                    StoreSlot slot, String label, int count, String current) {
        boolean isSection = classes.endsWith("section");
        String icon = "graphics/ui/store/" + (isSection ? "section-" + section.name().toLowerCase(Locale.ROOT)
                : "cat-" + slot.symbol()) + ".svg";
        return new CategoryRow(id, classes + (key.equals(current) ? " selected" : "")
                + (count == 0 ? " empty" : ""), Strings.format("storeCategoryCountFmt", label, count),
                icon, isSection ? "store-cat-icon" : "store-cat-tick",
                "store-cat-strip sec-" + section.name(), () -> selectFilter(key));
    }

    private int count(String filterKey) {
        int n = 0;
        for (EquipmentStore.Offer offer : shelf) {
            if (matches(filterKey, offer.slot())) n++;
        }
        return n;
    }

    Signal<List<OfferRow>> rows() { return rows; }
    Signal<String> shelfNote() { return shelfNote; }
    Signal<String> selectedName() { return selectedName; }
    /** The selected marine loadout as the Armory prints it; empty for any other offer. */
    Signal<List<FleetArmoryViewModel.DoctrineTile>> selectedCards() { return selectedCards; }
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
        EquipmentStore.Sale sale = EquipmentStore.buy(offer, holdings.get(), purse);
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
        for (EquipmentStore.Offer offer : visible()) {
            String key = key(offer);
            boolean owned = owned(offer);
            String id = "store-offer:" + key;
            String flag = flag(offer);
            String classes = "store-row" + (key.equals(selected) ? " selected" : "")
                    + (owned ? " acquired" : "");
            out.add(new OfferRow(id, classes, name(offer), kind(offer) + " / " + shelfLabel(offer.shelf()),
                    owned ? Strings.get("storeRowAcquired") : CompanyResources.creditAmount(offer.price()),
                    offer, StoreSymbolCanvas.has(offer.kind(), offer.id()) ? "store-offer-icon" : "store-offer-icon none",
                    flag.isEmpty() ? NO_FLAG : flag, flag.isEmpty() ? "store-offer-flag none" : "store-offer-flag",
                    () -> select(key)));
        }
        return List.copyOf(out);
    }

    /**
     * A marine loadout offer as the card the Armory and the First Refit print, from the same
     * builders ({@link FleetArmoryViewModel#weaponTile}, {@link FleetArmoryViewModel#armorTile}), so
     * the card cannot drift from the one the company will hold. Null for any other offer, or a
     * loadout whose definition is only written into an Armory once chosen.
     */
    private FleetArmoryViewModel.DoctrineTile cardTile(EquipmentStore.Offer offer) {
        if (offer.kind() != EquipmentStore.Kind.LOADOUT) return null;
        String key = key(offer);
        if (cardTiles.containsKey(key)) return cardTiles.get(key);
        FleetArmoryViewModel.DoctrineTile tile = null;
        LoadoutCard card = LoadoutCard.parse(offer.id());
        if (card != null && card.kind() == LoadoutCard.Kind.WEAPON) {
            SquadWeaponDoctrine doctrine = SquadEquipmentDoctrines.weaponById(card.definitionId());
            if (doctrine != null) {
                tile = FleetArmoryViewModel.weaponTile("store-card:" + offer.id(), doctrine, false, () -> { });
            }
        } else if (card != null) {
            SquadArmorPlan plan = SquadEquipmentDoctrines.armorPlanById(card.definitionId());
            if (plan != null) {
                // The armor accent is read off this prefix.
                tile = FleetArmoryViewModel.armorTile("armor-doctrine:store:" + offer.id(), plan,
                        ArmorIssueResolver.issueSheet(plan), FleetArmoryViewModel.roleComposition(plan, (r, c) -> true),
                        false, () -> { });
            }
        }
        cardTiles.put(key, tile);
        return tile;
    }

    /** The faction flag a marine loadout is drawn as on the shelf; empty for any other offer. */
    private String flag(EquipmentStore.Offer offer) {
        FleetArmoryViewModel.DoctrineTile tile = cardTile(offer);
        return tile == null || tile.factionLogo() == null ? "" : tile.factionLogo();
    }

    private EquipmentStore.Offer selected() {
        String key = selectedKey.get();
        for (EquipmentStore.Offer offer : shelf) {
            if (key(offer).equals(key)) return offer;
        }
        return null;
    }

    private boolean owned(EquipmentStore.Offer offer) {
        return acquired.contains(key(offer)) || EquipmentStore.known(offer, holdings.get());
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
            case BOAT_PART -> BoatParts.displayName(offer.id());
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
            case BOAT_PART -> {
                StoreSlot slot = offer.slot();
                yield slot == null ? "" : Strings.get("storeSlot" + slot.name());
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
            case BOAT_PART -> {
                if (!BoatParts.exists(offer.id())) return Strings.get("storeUnavailable");
                text.append(Strings.format("storeBoatPartUnlockFmt", BoatParts.displayName(offer.id())));
                BoatGun gun = BoatGun.findById(offer.id());
                BoatFitting fitting = gun == null ? BoatFitting.findById(offer.id()) : null;
                String effect = gun != null ? gun.effect() : fitting.provenance();
                if (effect != null && !effect.isBlank()) text.append("\n\n").append(effect);
                text.append("\n\n").append(Strings.format("mechPatternCardTierFmt",
                        Strings.get("mechPatternCardTier" + BoatParts.accessTier(offer.id()).name())));
            }
        }
        text.append("\n\n").append(Strings.get("storeLearnedAtOnce"));
        return text.toString();
    }

    /** One line of the category list: a section header or one of its slots, each with its section's icon. */
    record CategoryRow(String id, String classes, String label, String icon, String iconClasses,
                       String stripClasses, Runnable select)
            implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "classes" -> classes;
                case "labelId" -> id + ":label";
                case "iconId" -> id + ":icon";
                case "label" -> label;
                case "icon" -> icon;
                case "iconClasses" -> iconClasses;
                case "stripId" -> id + ":strip";
                case "stripClasses" -> stripClasses;
                case "select" -> select;
                default -> null;
            };
        }
    }

    /** One line of the shelf. */
    record OfferRow(String id, String classes, String name, String detail, String price,
                    EquipmentStore.Offer offer, String symbolClasses, String flag, String flagClasses,
                    Runnable select)
            implements MarkupPropertySource {
        String symbolId() { return id + ":symbol"; }

        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "classes" -> classes;
                case "nameId" -> id + ":name";
                case "detailId" -> id + ":detail";
                case "priceId" -> id + ":price";
                case "symbolId" -> symbolId();
                case "symbolClasses" -> symbolClasses;
                case "flagId" -> id + ":flag";
                case "flag" -> flag;
                case "flagClasses" -> flagClasses;
                case "copyId" -> id + ":copy";
                case "name" -> name;
                case "detail" -> detail;
                case "price" -> price;
                case "select" -> select;
                default -> null;
            };
        }
    }
}
