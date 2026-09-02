package com.dillon.starsectormarines.campaign.polity;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.marine.EquipmentAccessTier;
import com.dillon.starsectormarines.marine.EquipmentTemplateCard;
import com.dillon.starsectormarines.marine.EquipmentTemplateCatalog;
import com.dillon.starsectormarines.marine.MarineArmory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the company has released to its own polity ({@code polity-ground-doctrine.md},
 * law 2): the equipment templates the derived roster is allowed to draw from.
 *
 * <p>A release grants a <em>definition</em>. It puts a weapon, pattern, or item in
 * the polity's candidate set and nothing else: no stock changes hands, nothing
 * flows back, and {@link MarineArmory} is never written here. The Armory is read
 * for one purpose only — to say which cards the company owns and could therefore
 * release ({@link #releasable}).
 *
 * <p><b>The Common band is released by construction and is never listed.</b> Every
 * market sells it, so a polity that has been given nothing still arms itself; and
 * writing those ids into the save would put one fact in two places, where a later
 * catalog change could make the save disagree with the catalog. Only Advanced and
 * Prestige cards occupy a row.
 *
 * <p>Stored as interned slots in {@code CampaignState.equipmentTemplateRegistry},
 * in the shape every other id column here uses. Reads resolve back through
 * {@link EquipmentTemplateCatalog} and <em>skip</em> anything it no longer knows,
 * so a save that outlived a card is a polity with one fewer template rather than a
 * game that will not load.
 */
public final class ReleasedKit {

    private ReleasedKit() {}

    /**
     * Whether the polity may issue {@code card}: true for every Common-band card by
     * construction, and for a higher-band card only once it has been released.
     */
    public static boolean isReleased(CampaignState state, EquipmentTemplateCard card) {
        if (card == null) return false;
        if (card.accessTier() == EquipmentAccessTier.COMMON) return true;
        return state != null && isListed(state, card.id());
    }

    /**
     * Releases one card to the polity. Idempotent, and permanent: there is no
     * counterpart that takes it back.
     *
     * @return true when this call is what put the card in the polity's hands. A
     *         Common-band id is a no-op returning false, because it was already
     *         released by construction.
     * @throws IllegalArgumentException if the catalog does not know {@code cardId}.
     *         A release names a definition, so an id nobody can resolve is a caller
     *         mistake rather than a polity with a mystery template.
     */
    public static boolean release(CampaignState state, String cardId) {
        if (state == null) return false;
        EquipmentTemplateCard card = EquipmentTemplateCatalog.require(cardId);
        if (card.accessTier() == EquipmentAccessTier.COMMON) return false;
        int slot = state.equipmentTemplateRegistry.intern(card.id());
        return state.recordReleasedKit(slot) >= 0;
    }

    /**
     * Every card the polity may draw from: the Common floor unioned with what has
     * been released, in catalog order with no duplicates. This is the set
     * {@link PolityRosterDerivation#derive} takes.
     */
    public static List<EquipmentTemplateCard> releasedCards(CampaignState state) {
        Map<String, EquipmentTemplateCard> cards = new LinkedHashMap<>();
        for (EquipmentTemplateCard card : PolityRosterDerivation.commonFloor()) {
            cards.put(card.id(), card);
        }
        if (state != null && state.equipmentTemplateRegistry != null) {
            for (int i = 0; i < state.releasedKitCount; i++) {
                String id = state.equipmentTemplateRegistry.get(state.releasedKitTemplateId[i]);
                // A card the catalog has since dropped is one template fewer, not a crash.
                if (id != null && EquipmentTemplateCatalog.contains(id)) {
                    cards.put(id, EquipmentTemplateCatalog.require(id));
                }
            }
        }
        return List.copyOf(cards.values());
    }

    /**
     * What the company could release next: the cards its Armory owns, above the
     * Common band, that the polity does not already hold. The Armory is the source
     * of what <em>can</em> be released and is never written by any of this.
     */
    public static List<EquipmentTemplateCard> releasable(CampaignState state, MarineArmory armory) {
        if (armory == null) return List.of();
        List<EquipmentTemplateCard> out = new ArrayList<>();
        for (String cardId : armory.ownedEquipmentTemplateIds()) {
            if (!EquipmentTemplateCatalog.contains(cardId)) continue;
            EquipmentTemplateCard card = EquipmentTemplateCatalog.require(cardId);
            if (card.accessTier() == EquipmentAccessTier.COMMON) continue;
            if (isListed(state, cardId)) continue;
            out.add(card);
        }
        out.sort((left, right) -> left.id().compareTo(right.id()));
        return List.copyOf(out);
    }

    private static boolean isListed(CampaignState state, String cardId) {
        if (state == null || state.equipmentTemplateRegistry == null) return false;
        for (int i = 0; i < state.releasedKitCount; i++) {
            if (cardId.equals(state.equipmentTemplateRegistry.get(state.releasedKitTemplateId[i]))) {
                return true;
            }
        }
        return false;
    }
}
