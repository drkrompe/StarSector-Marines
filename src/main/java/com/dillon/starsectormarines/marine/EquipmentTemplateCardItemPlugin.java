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
 * Learnable cargo representation of one marine equipment template.
 *
 * <p>This deliberately is not a vanilla blueprint provider. Learning writes only to
 * {@link MarineArmory}, so the template never enters ship-production knowledge.
 */
public final class EquipmentTemplateCardItemPlugin extends BaseSpecialItemPlugin {

    public static final String ITEM_ID = "starsector_marines_equipment_template";

    private EquipmentTemplateCard template;

    /** Validated cargo payload for operation rewards, salvage, and market population. */
    public static SpecialItemData itemData(String templateId) {
        EquipmentTemplateCatalog.require(templateId);
        return new SpecialItemData(ITEM_ID, templateId);
    }

    @Override
    public void init(CargoStackAPI stack) {
        super.init(stack);
        template = resolveTemplate(templateId());
    }

    @Override
    public String getName() {
        if (template == null) return Strings.get("equipmentTemplateInvalidName");
        return MessageFormat.format(Strings.get("equipmentTemplateNameFmt"),
                template.displayName());
    }

    @Override
    public String getDesignType() {
        return Strings.get("equipmentTemplateDesignType");
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded,
                              CargoTransferHandlerAPI transferHandler, Object stackSource) {
        super.createTooltip(tooltip, expanded, transferHandler, stackSource);
        float pad = 10f;

        if (template == null) {
            tooltip.addPara(Strings.get("equipmentTemplateInvalid"),
                    Misc.getNegativeHighlightColor(), pad);
            addCostLabel(tooltip, pad, transferHandler, stackSource);
            return;
        }

        String kind = switch (template.kind()) {
            case PRIMARY -> Strings.get("equipmentTemplateKindPrimary");
            case ARMOR -> Strings.get("equipmentTemplateKindArmor");
            case SPECIAL -> Strings.get("equipmentTemplateKindSpecial");
        };
        tooltip.addPara(MessageFormat.format(Strings.get("equipmentTemplateUnlockFmt"),
                        template.displayName(), kind), pad,
                Misc.getHighlightColor(), template.displayName());
        String access = accessLabel(template.accessTier());
        tooltip.addPara(MessageFormat.format(Strings.get("equipmentTemplateAccessFmt"),
                        access), pad, Misc.getHighlightColor(), access);
        if (template.accessTier() == EquipmentAccessTier.COMMON) {
            tooltip.addPara(Strings.get("equipmentTemplateAccessCommonRoute"), pad);
        } else {
            tooltip.addPara(MessageFormat.format(
                    Strings.get("equipmentTemplateAccessGatedRouteFmt"),
                    EquipmentAcquisitionEligibility.requiredMrb(template.accessTier()),
                    EquipmentAcquisitionEligibility.requiredRecoveryVictories(
                            template.accessTier())), pad);
        }
        tooltip.addPara(Strings.get("equipmentTemplateShipBoundary"), pad);
        addCostLabel(tooltip, pad, transferHandler, stackSource);

        if (Global.CODEX_TOOLTIP_MODE) return;
        EquipmentTemplateLearning.Result status = EquipmentTemplateLearning.status(
                armory(), template.id());
        if (status == EquipmentTemplateLearning.Result.LEARNED) {
            tooltip.addPara(Strings.get("equipmentTemplateLearnPrompt"),
                    Misc.getPositiveHighlightColor(), pad);
        } else if (status == EquipmentTemplateLearning.Result.ALREADY_KNOWN) {
            tooltip.addPara(Strings.get("equipmentTemplateAlreadyKnown"),
                    Misc.getGrayColor(), pad);
        } else if (status == EquipmentTemplateLearning.Result.ARMORY_UNAVAILABLE) {
            tooltip.addPara(Strings.get("equipmentTemplateArmoryUnavailable"),
                    Misc.getNegativeHighlightColor(), pad);
        }
    }

    @Override
    public boolean hasRightClickAction() {
        return template != null;
    }

    @Override
    public boolean shouldRemoveOnRightClickAction() {
        return template != null && EquipmentTemplateLearning.status(armory(), template.id())
                == EquipmentTemplateLearning.Result.LEARNED;
    }

    @Override
    public void performRightClickAction() {
        String id = template != null ? template.id() : templateId();
        EquipmentTemplateLearning.Result result = EquipmentTemplateLearning.learn(armory(), id);
        String message;
        switch (result) {
            case LEARNED -> {
                Global.getSoundPlayer().playUISound("ui_acquired_blueprint", 1f, 1f);
                message = MessageFormat.format(Strings.get("equipmentTemplateLearnedFmt"),
                        template.displayName());
            }
            case ALREADY_KNOWN -> message = MessageFormat.format(
                    Strings.get("equipmentTemplateAlreadyKnownFmt"), template.displayName());
            case UNKNOWN_TEMPLATE -> message = Strings.get("equipmentTemplateInvalid");
            case ARMORY_UNAVAILABLE -> message = Strings.get("equipmentTemplateArmoryUnavailable");
            default -> throw new IllegalStateException("Unhandled learning result " + result);
        }
        if (Global.getSector() != null && Global.getSector().getCampaignUI() != null) {
            Global.getSector().getCampaignUI().getMessageDisplay().addMessage(message);
        }
    }

    @Override
    public String resolveDropParamsToSpecificItemData(String params, Random random)
            throws JSONException {
        return EquipmentTemplateCatalog.contains(params) ? params : null;
    }

    private String templateId() {
        if (stack == null || stack.getSpecialDataIfSpecial() == null) return null;
        return stack.getSpecialDataIfSpecial().getData();
    }

    private MarineArmory armory() {
        if (Global.getSector() == null) return null;
        MarineRosterScript script = MarineRosterScript.getInstance();
        return script != null ? script.roster().armory() : null;
    }

    private static EquipmentTemplateCard resolveTemplate(String id) {
        return EquipmentTemplateCatalog.contains(id) ? EquipmentTemplateCatalog.require(id) : null;
    }

    private static String accessLabel(EquipmentAccessTier tier) {
        return Strings.get(switch (tier) {
            case COMMON -> "equipmentTemplateAccessCommon";
            case ADVANCED -> "equipmentTemplateAccessAdvanced";
            case PRESTIGE -> "equipmentTemplateAccessPrestige";
        });
    }
}
