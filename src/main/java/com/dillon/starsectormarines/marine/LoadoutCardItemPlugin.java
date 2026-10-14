package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.i18n.Strings;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.json.JSONException;

import java.text.MessageFormat;
import java.util.Random;

/**
 * Learnable cargo representation of one Weapon Loadout or Tactic Sheet.
 *
 * <p>Learning teaches the loadout wholesale: the definition itself and every piece of
 * equipment it needs. Like the template item before it, this is deliberately not a
 * vanilla blueprint provider; learning writes only to {@link MarineArmory}.
 */
public final class LoadoutCardItemPlugin extends BaseSpecialItemPlugin {

    public static final String ITEM_ID = "starsector_marines_loadout_card";

    private LoadoutCard card;

    /** Validated cargo payload for market stock, patron rewards and salvage. */
    public static SpecialItemData itemData(String cardId) {
        if (LoadoutCard.parse(cardId) == null) {
            throw new IllegalArgumentException("Unknown loadout card id '" + cardId + "'");
        }
        return new SpecialItemData(ITEM_ID, cardId);
    }

    @Override
    public void init(CargoStackAPI stack) {
        super.init(stack);
        card = LoadoutCard.parse(cardId());
    }

    @Override
    public String getName() {
        if (card == null) return Strings.get("loadoutCardInvalidName");
        return MessageFormat.format(Strings.get(card.kind() == LoadoutCard.Kind.WEAPON
                ? "loadoutCardWeaponNameFmt" : "loadoutCardSheetNameFmt"), card.displayName());
    }

    @Override
    public String getDesignType() {
        return Strings.get("loadoutCardDesignType");
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded,
                              CargoTransferHandlerAPI transferHandler, Object stackSource) {
        super.createTooltip(tooltip, expanded, transferHandler, stackSource);
        float pad = 10f;
        if (card == null) {
            tooltip.addPara(Strings.get("loadoutCardInvalid"),
                    Misc.getNegativeHighlightColor(), pad);
            addCostLabel(tooltip, pad, transferHandler, stackSource);
            return;
        }
        tooltip.addPara(MessageFormat.format(Strings.get(card.kind() == LoadoutCard.Kind.WEAPON
                        ? "loadoutCardWeaponUnlockFmt" : "loadoutCardSheetUnlockFmt"),
                card.displayName()), pad, Misc.getHighlightColor(), card.displayName());
        StringBuilder contents = new StringBuilder();
        for (String templateId : card.templateIds()) {
            if (contents.length() > 0) contents.append(", ");
            contents.append(EquipmentTemplateCatalog.require(templateId).displayName());
        }
        if (contents.length() > 0) {
            tooltip.addPara(MessageFormat.format(Strings.get("loadoutCardContentsFmt"), contents),
                    pad);
        }
        tooltip.addPara(Strings.get("loadoutCardShipBoundary"), pad);
        addCostLabel(tooltip, pad, transferHandler, stackSource);

        if (Global.CODEX_TOOLTIP_MODE) return;
        LoadoutCardLearning.Result status = LoadoutCardLearning.status(armory(), card.id());
        if (status == LoadoutCardLearning.Result.LEARNED) {
            tooltip.addPara(Strings.get("loadoutCardLearnPrompt"),
                    Misc.getPositiveHighlightColor(), pad);
        } else if (status == LoadoutCardLearning.Result.ALREADY_KNOWN) {
            tooltip.addPara(Strings.get("loadoutCardAlreadyKnown"), Misc.getGrayColor(), pad);
        } else if (status == LoadoutCardLearning.Result.ARMORY_UNAVAILABLE) {
            tooltip.addPara(Strings.get("loadoutCardArmoryUnavailable"),
                    Misc.getNegativeHighlightColor(), pad);
        }
    }

    @Override
    public boolean hasRightClickAction() {
        return card != null;
    }

    @Override
    public boolean shouldRemoveOnRightClickAction() {
        return card != null && LoadoutCardLearning.status(armory(), card.id())
                == LoadoutCardLearning.Result.LEARNED;
    }

    @Override
    public void performRightClickAction() {
        String id = card != null ? card.id() : cardId();
        LoadoutCardLearning.Result result = LoadoutCardLearning.learn(armory(), id);
        String message;
        switch (result) {
            case LEARNED -> {
                Global.getSoundPlayer().playUISound("ui_acquired_blueprint", 1f, 1f);
                message = MessageFormat.format(Strings.get("loadoutCardLearnedFmt"),
                        card.displayName());
            }
            case ALREADY_KNOWN -> message = MessageFormat.format(
                    Strings.get("loadoutCardAlreadyKnownFmt"), card.displayName());
            case UNKNOWN_CARD -> message = Strings.get("loadoutCardInvalid");
            case ARMORY_UNAVAILABLE -> message = Strings.get("loadoutCardArmoryUnavailable");
            default -> throw new IllegalStateException("Unhandled learning result " + result);
        }
        if (Global.getSector() != null && Global.getSector().getCampaignUI() != null) {
            Global.getSector().getCampaignUI().getMessageDisplay().addMessage(message);
        }
    }

    @Override
    public String resolveDropParamsToSpecificItemData(String params, Random random)
            throws JSONException {
        return LoadoutCard.parse(params) != null ? params : null;
    }

    private String cardId() {
        if (stack == null || stack.getSpecialDataIfSpecial() == null) return null;
        return stack.getSpecialDataIfSpecial().getData();
    }

    private MarineArmory armory() {
        if (Global.getSector() == null) return null;
        MarineRosterScript script = MarineRosterScript.getInstance();
        return script != null ? script.roster().armory() : null;
    }
}
