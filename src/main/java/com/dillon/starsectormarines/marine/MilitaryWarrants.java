package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.i18n.Strings;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.RepLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Military warrants: the only way a Restricted loadout card becomes acquirable
 * ({@code progression-nouns.md}).
 *
 * <p>A military warrant is a named, persistent authorisation from one faction that opens
 * the Restricted <b>cards</b> it lists. It names cards, never a weapon, a suit or any other
 * single piece of equipment: the card is what is restricted, and access to it is granted
 * or withheld whole. Holding a warrant teaches nothing. It lets that faction's own licence
 * and patron channels offer the cards, and no other channel or faction. It is not the
 * <em>civil-security procurement warrant</em> of the Agreus contracts, which is a contract
 * term and opens no access tier.
 *
 * <p>A card is Restricted when it carries Restricted equipment, and its issuer is the
 * tradition that builds that equipment, read from the armour catalog rather than authored
 * a second time. A warrant is permanent once granted; what it costs to keep is its
 * obligations (same section), kept by {@code WarrantObligationSystem}.
 */
public final class MilitaryWarrants {

    /**
     * The relationship a tradition requires, at the Prestige reputation band, before it
     * grants its standing warrant: well above the Favorable a licence asks for.
     */
    public static final RepLevel STANDING_RELATIONSHIP = RepLevel.COOPERATIVE;

    /** Why a warrant was granted. Stored as {@link #code}. */
    public enum Reason {
        /** The tradition's own rule: Prestige band and close standing. */
        STANDING((byte) 0),
        /** An authored story beat. */
        STORY((byte) 1);

        public final byte code;

        Reason(byte code) {
            this.code = code;
        }

        public static Reason fromCode(byte code) {
            for (Reason reason : values()) {
                if (reason.code == code) return reason;
            }
            return null;
        }
    }

    /** One tradition's warrant as the company holds it: the cards it opens. */
    public record Held(ArmorTradition issuer, List<String> cardIds, Reason reason, int day) {
        public Held {
            cardIds = List.copyOf(cardIds);
        }
    }

    private MilitaryWarrants() {}

    /** The tradition that builds {@code card}; null for equipment no tradition owns. */
    public static ArmorTradition issuerOf(EquipmentTemplateCard card) {
        if (card == null || card.kind() != EquipmentTemplateCard.Kind.ARMOR) return null;
        return MarineArmorCatalogRegistry.require(card.equipmentId()).tradition();
    }

    /**
     * The tradition whose warrant opens {@code card}: the builder of the Restricted
     * equipment it carries. Null for a card that is not Restricted, or whose Restricted
     * equipment no single tradition builds (no warrant can speak for it).
     */
    public static ArmorTradition issuerOf(LoadoutCard card) {
        if (card == null || card.accessTier() != EquipmentAccessTier.RESTRICTED) return null;
        ArmorTradition issuer = null;
        for (String templateId : card.templateIds()) {
            EquipmentTemplateCard template = EquipmentTemplateCatalog.require(templateId);
            if (template.accessTier() != EquipmentAccessTier.RESTRICTED) continue;
            ArmorTradition builder = issuerOf(template);
            if (builder == null || (issuer != null && issuer != builder)) return null;
            issuer = builder;
        }
        return issuer;
    }

    /** Every Restricted card {@code issuer}'s warrant can open, in catalog order. */
    public static List<String> restrictedCardsOf(ArmorTradition issuer) {
        List<String> ids = new ArrayList<>();
        if (issuer == null) return ids;
        for (LoadoutCard card : LoadoutCards.all()) {
            if (issuerOf(card) == issuer) ids.add(card.id());
        }
        return ids;
    }

    /** Traditions that issue at least one Restricted card, in declaration order. */
    public static Set<ArmorTradition> issuers() {
        Set<ArmorTradition> issuers = EnumSet.noneOf(ArmorTradition.class);
        for (LoadoutCard card : LoadoutCards.all()) {
            ArmorTradition issuer = issuerOf(card);
            if (issuer != null) issuers.add(issuer);
        }
        return issuers;
    }

    /**
     * Whether a tradition grants its standing warrant: the company at the Prestige
     * reputation band, and a relationship at {@link #STANDING_RELATIONSHIP} or better.
     */
    public static boolean standingWarrantDue(int mrbRep, RepLevel relationship) {
        return mrbRep >= EquipmentAcquisitionEligibility.requiredMrb(EquipmentAccessTier.PRESTIGE)
                && relationship != null && relationship.isAtWorst(STANDING_RELATIONSHIP);
    }

    /** Grants {@code issuer}'s standing warrant, opening every Restricted card it issues. */
    public static List<String> grantStanding(CampaignState state, ArmorTradition issuer, int day) {
        return grant(state, issuer, restrictedCardsOf(issuer), Reason.STANDING, day);
    }

    /**
     * Records {@code issuer}'s warrant over {@code cardIds}. Idempotent per card: one
     * already warranted keeps its original grant.
     *
     * @return the cards this call newly opened, in the order given
     * @throws IllegalArgumentException when a card is unknown, not Restricted, or issued by
     *         another tradition. A warrant speaks only for its issuer's cards.
     */
    public static List<String> grant(CampaignState state, ArmorTradition issuer,
                                     Collection<String> cardIds, Reason reason, int day) {
        if (state == null || issuer == null || cardIds == null || reason == null) {
            return List.of();
        }
        convertLegacyRows(state);
        for (String id : cardIds) {
            LoadoutCard card = LoadoutCard.parse(id);
            if (card == null) throw new IllegalArgumentException("A military warrant names cards: " + id);
            if (card.accessTier() != EquipmentAccessTier.RESTRICTED) {
                throw new IllegalArgumentException("A military warrant opens only Restricted cards: " + id);
            }
            if (issuerOf(card) != issuer) {
                throw new IllegalArgumentException("A " + issuer.key
                        + " military warrant cannot open " + id + ", which " + issuerOf(card) + " issues");
            }
        }
        int issuerSlot = state.factionRegistry.intern(issuer.key);
        List<String> opened = new ArrayList<>();
        for (String id : cardIds) {
            if (state.recordMilitaryWarrant(issuerSlot, id, reason.code, day) >= 0) opened.add(id);
        }
        return List.copyOf(opened);
    }

    /** Every card some held warrant opens. A card this build no longer defines is skipped. */
    public static Set<String> warrantedCardIds(CampaignState state) {
        return cardIds(state, false);
    }

    /**
     * Every card a suspended or revoked warrant opens ({@code progression-nouns.md}, Kit and
     * armory). These are not warranted for acquisition and are not issued to a squad that does
     * not already hold them; a squad that does keeps its card.
     */
    public static Set<String> withheldCardIds(CampaignState state) {
        return cardIds(state, true);
    }

    /** {@link #withheldCardIds} in the running campaign; empty outside one. */
    public static Set<String> liveWithheldCardIds() {
        CampaignStateScript script = Global.getSector() != null ? CampaignStateScript.getInstance() : null;
        return script != null ? withheldCardIds(script.state()) : Set.of();
    }

    private static Set<String> cardIds(CampaignState state, boolean withheld) {
        Set<String> ids = new LinkedHashSet<>();
        if (state == null) return ids;
        convertLegacyRows(state);
        Set<String> withheldIssuers = state.warrantObligations().withheldIssuers();
        for (int i = 0; i < state.militaryWarrantCount; i++) {
            String id = state.militaryWarrantCardId[i];
            if (LoadoutCard.parse(id) == null) continue;
            String issuer = state.factionRegistry.get(state.militaryWarrantIssuerId[i]);
            if (withheldIssuers.contains(issuer) == withheld) ids.add(id);
        }
        return ids;
    }

    /** Whether the company holds any warrant from {@code issuer}. */
    public static boolean holds(CampaignState state, ArmorTradition issuer) {
        if (state == null || issuer == null) return false;
        for (Held held : held(state)) {
            if (held.issuer() == issuer) return true;
        }
        return false;
    }

    /**
     * The company's warrants, one per issuer in grant order, each with the cards it opens.
     * Its reason and day are those of the first grant from that issuer.
     */
    public static List<Held> held(CampaignState state) {
        if (state == null) return List.of();
        convertLegacyRows(state);
        Map<ArmorTradition, List<String>> cards = new LinkedHashMap<>();
        Map<ArmorTradition, Integer> firstRow = new LinkedHashMap<>();
        for (int i = 0; i < state.militaryWarrantCount; i++) {
            ArmorTradition issuer = tradition(state.factionRegistry.get(state.militaryWarrantIssuerId[i]));
            String id = state.militaryWarrantCardId[i];
            if (issuer == null || LoadoutCard.parse(id) == null) continue;
            cards.computeIfAbsent(issuer, ignored -> new ArrayList<>()).add(id);
            firstRow.putIfAbsent(issuer, i);
        }
        List<Held> held = new ArrayList<>();
        for (Map.Entry<ArmorTradition, List<String>> entry : cards.entrySet()) {
            int row = firstRow.get(entry.getKey());
            held.add(new Held(entry.getKey(), entry.getValue(),
                    Reason.fromCode(state.militaryWarrantReason[row]), state.militaryWarrantDay[row]));
        }
        return List.copyOf(held);
    }

    /**
     * A save from before warrants named cards holds one row per warranted suit. Such a
     * warrant opened exactly the cards whose every Restricted suit it named, so each issuer's
     * rows become those cards, keeping the first row's reason and day. Runs once: afterwards
     * no row names a template.
     */
    static void convertLegacyRows(CampaignState state) {
        if (state == null || state.equipmentTemplateRegistry == null) return;
        boolean legacy = false;
        for (int i = 0; i < state.militaryWarrantCount && !legacy; i++) {
            legacy = state.militaryWarrantCardId[i] == null && state.militaryWarrantTemplateId[i] >= 0;
        }
        if (!legacy) return;
        record Row(int issuerSlot, String templateId, byte reason, int day) { }
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < state.militaryWarrantCount; i++) {
            rows.add(new Row(state.militaryWarrantIssuerId[i],
                    state.equipmentTemplateRegistry.get(state.militaryWarrantTemplateId[i]),
                    state.militaryWarrantReason[i], state.militaryWarrantDay[i]));
        }
        state.clearMilitaryWarrants();
        Map<Integer, Set<String>> warrantedSuits = new LinkedHashMap<>();
        Map<Integer, Row> firstRow = new LinkedHashMap<>();
        for (Row row : rows) {
            if (row.issuerSlot() < 0 || row.templateId() == null) continue;
            warrantedSuits.computeIfAbsent(row.issuerSlot(), ignored -> new LinkedHashSet<>()).add(row.templateId());
            firstRow.putIfAbsent(row.issuerSlot(), row);
        }
        for (Map.Entry<Integer, Set<String>> entry : warrantedSuits.entrySet()) {
            ArmorTradition issuer = tradition(state.factionRegistry.get(entry.getKey()));
            if (issuer == null) continue;
            Row first = firstRow.get(entry.getKey());
            for (String cardId : restrictedCardsOf(issuer)) {
                LoadoutCard card = LoadoutCard.parse(cardId);
                boolean opened = true;
                for (String templateId : card.templateIds()) {
                    if (EquipmentTemplateCatalog.require(templateId).accessTier() == EquipmentAccessTier.RESTRICTED
                            && !entry.getValue().contains(templateId)) opened = false;
                }
                if (opened) state.recordMilitaryWarrant(entry.getKey(), cardId, first.reason(), first.day());
            }
        }
    }

    /**
     * The sentence a Restricted card shows instead of a reputation and victory threshold:
     * who issues it, and whether the company already holds that warrant.
     */
    public static String restrictedRoute(LoadoutCard card) {
        ArmorTradition issuer = issuerOf(card);
        if (issuer == null) return Strings.get("equipmentTemplateAccessRestrictedRoute");
        CampaignStateScript script = Global.getSector() != null ? CampaignStateScript.getInstance() : null;
        boolean held = script != null && warrantedCardIds(script.state()).contains(card.id());
        return Strings.format(held ? "equipmentTemplateAccessRestrictedHeldFmt"
                : "equipmentTemplateAccessRestrictedRouteFmt", issuer.displayName());
    }

    /**
     * The issuer line for a held single-equipment item from an old save. Such an item opens
     * nothing on its own any more, so it names the issuer and never claims a warrant is held.
     */
    public static String restrictedRoute(EquipmentTemplateCard template) {
        ArmorTradition issuer = issuerOf(template);
        if (issuer == null) return Strings.get("equipmentTemplateAccessRestrictedRoute");
        return Strings.format("equipmentTemplateAccessRestrictedRouteFmt", issuer.displayName());
    }

    private static ArmorTradition tradition(String key) {
        if (key == null) return null;
        for (ArmorTradition tradition : ArmorTradition.values()) {
            if (tradition.key.equals(key)) return tradition;
        }
        return null;
    }
}
