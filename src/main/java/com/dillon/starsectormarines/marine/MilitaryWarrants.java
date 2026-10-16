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
 * Military warrants: the only way {@link EquipmentAccessTier#RESTRICTED} equipment becomes
 * acquirable ({@code progression-nouns.md}).
 *
 * <p>A military warrant is a named, persistent authorisation from one tradition that opens
 * the Restricted templates it lists. Holding one teaches nothing. It lets that tradition's
 * own licence and patron channels offer the templates, and no other channel or faction.
 * It is not the <em>civil-security procurement warrant</em> of the Agreus contracts, which
 * is a contract term and opens no access tier.
 *
 * <p>The issuer of a Restricted template is the tradition that builds it, read from the
 * armour catalog rather than authored a second time. A warrant is permanent once granted;
 * what it costs to keep is its obligations (same section), kept by {@code WarrantObligationSystem}.
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

    /** One tradition's warrant as the company holds it. */
    public record Held(ArmorTradition issuer, List<String> templateIds, Reason reason, int day) {
        public Held {
            templateIds = List.copyOf(templateIds);
        }
    }

    private MilitaryWarrants() {}

    /** The tradition that builds {@code card}; null for equipment no tradition owns. */
    public static ArmorTradition issuerOf(EquipmentTemplateCard card) {
        if (card == null || card.kind() != EquipmentTemplateCard.Kind.ARMOR) return null;
        return MarineArmorCatalogRegistry.require(card.equipmentId()).tradition();
    }

    /** Every Restricted template {@code issuer} builds, in catalog order. */
    public static List<String> restrictedTemplatesOf(ArmorTradition issuer) {
        List<String> ids = new ArrayList<>();
        if (issuer == null) return ids;
        for (EquipmentTemplateCard card : EquipmentTemplateCatalog.all()) {
            if (card.accessTier() == EquipmentAccessTier.RESTRICTED && issuerOf(card) == issuer) {
                ids.add(card.id());
            }
        }
        return ids;
    }

    /** Traditions that build at least one Restricted template, in declaration order. */
    public static Set<ArmorTradition> issuers() {
        Set<ArmorTradition> issuers = EnumSet.noneOf(ArmorTradition.class);
        for (EquipmentTemplateCard card : EquipmentTemplateCatalog.all()) {
            if (card.accessTier() != EquipmentAccessTier.RESTRICTED) continue;
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

    /** Grants {@code issuer}'s standing warrant, opening every Restricted template it builds. */
    public static List<String> grantStanding(CampaignState state, ArmorTradition issuer, int day) {
        return grant(state, issuer, restrictedTemplatesOf(issuer), Reason.STANDING, day);
    }

    /**
     * Records {@code issuer}'s warrant over {@code templateIds}. Idempotent per template:
     * one already warranted keeps its original grant.
     *
     * @return the templates this call newly opened, in the order given
     * @throws IllegalArgumentException when a template is not Restricted or is built by
     *         another tradition. A warrant speaks only for what its issuer builds.
     */
    public static List<String> grant(CampaignState state, ArmorTradition issuer,
                                     Collection<String> templateIds, Reason reason, int day) {
        if (state == null || issuer == null || templateIds == null || reason == null) {
            return List.of();
        }
        for (String id : templateIds) {
            EquipmentTemplateCard card = EquipmentTemplateCatalog.require(id);
            if (card.accessTier() != EquipmentAccessTier.RESTRICTED) {
                throw new IllegalArgumentException("A military warrant opens only Restricted equipment: " + id);
            }
            if (issuerOf(card) != issuer) {
                throw new IllegalArgumentException("A " + issuer.key
                        + " military warrant cannot open " + id + ", which " + issuerOf(card) + " builds");
            }
        }
        int issuerSlot = state.factionRegistry.intern(issuer.key);
        List<String> opened = new ArrayList<>();
        for (String id : templateIds) {
            int templateSlot = state.equipmentTemplateRegistry.intern(id);
            if (state.recordMilitaryWarrant(issuerSlot, templateSlot, reason.code, day) >= 0) {
                opened.add(id);
            }
        }
        return List.copyOf(opened);
    }

    /** Every template some held warrant opens. A template the catalog has dropped is skipped. */
    public static Set<String> warrantedTemplateIds(CampaignState state) {
        return templateIds(state, false);
    }

    /**
     * Every template a suspended or revoked warrant opens ({@code progression-nouns.md}, Kit and
     * armory). These are not warranted for acquisition, and no marine may be newly fitted with
     * one; marines already wearing one keep it. A suit learned before the Restricted tier, which
     * no warrant names, is never withheld.
     */
    public static Set<String> withheldTemplateIds(CampaignState state) {
        return templateIds(state, true);
    }

    /** {@link #withheldTemplateIds} in the running campaign; empty outside one. */
    public static Set<String> liveWithheldTemplateIds() {
        CampaignStateScript script = Global.getSector() != null ? CampaignStateScript.getInstance() : null;
        return script != null ? withheldTemplateIds(script.state()) : Set.of();
    }

    private static Set<String> templateIds(CampaignState state, boolean withheld) {
        Set<String> ids = new LinkedHashSet<>();
        if (state == null || state.equipmentTemplateRegistry == null) return ids;
        Set<String> withheldIssuers = state.warrantObligations().withheldIssuers();
        for (int i = 0; i < state.militaryWarrantCount; i++) {
            String id = state.equipmentTemplateRegistry.get(state.militaryWarrantTemplateId[i]);
            if (id == null || !EquipmentTemplateCatalog.contains(id)) continue;
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
     * The company's warrants, one per issuer in grant order, each with the templates it
     * opens. Its reason and day are those of the first grant from that issuer.
     */
    public static List<Held> held(CampaignState state) {
        if (state == null || state.equipmentTemplateRegistry == null) return List.of();
        Map<ArmorTradition, List<String>> templates = new LinkedHashMap<>();
        Map<ArmorTradition, Integer> firstRow = new LinkedHashMap<>();
        for (int i = 0; i < state.militaryWarrantCount; i++) {
            ArmorTradition issuer = tradition(state.factionRegistry.get(state.militaryWarrantIssuerId[i]));
            String id = state.equipmentTemplateRegistry.get(state.militaryWarrantTemplateId[i]);
            if (issuer == null || id == null || !EquipmentTemplateCatalog.contains(id)) continue;
            templates.computeIfAbsent(issuer, ignored -> new ArrayList<>()).add(id);
            firstRow.putIfAbsent(issuer, i);
        }
        List<Held> held = new ArrayList<>();
        for (Map.Entry<ArmorTradition, List<String>> entry : templates.entrySet()) {
            int row = firstRow.get(entry.getKey());
            held.add(new Held(entry.getKey(), entry.getValue(),
                    Reason.fromCode(state.militaryWarrantReason[row]), state.militaryWarrantDay[row]));
        }
        return List.copyOf(held);
    }

    /**
     * The sentence a Restricted card shows instead of a reputation and victory threshold:
     * who issues it, and whether the company already holds that tradition's warrant.
     */
    public static String restrictedRoute(EquipmentTemplateCard card) {
        ArmorTradition issuer = issuerOf(card);
        if (issuer == null) return Strings.get("equipmentTemplateAccessRestrictedRoute");
        CampaignStateScript script = Global.getSector() != null ? CampaignStateScript.getInstance() : null;
        boolean held = script != null && warrantedTemplateIds(script.state()).contains(card.id());
        return Strings.format(held ? "equipmentTemplateAccessRestrictedHeldFmt"
                : "equipmentTemplateAccessRestrictedRouteFmt", issuer.displayName());
    }

    private static ArmorTradition tradition(String key) {
        if (key == null) return null;
        for (ArmorTradition tradition : ArmorTradition.values()) {
            if (tradition.key.equals(key)) return tradition;
        }
        return null;
    }
}
