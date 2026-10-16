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
 * Learnable cargo representation of one {@link MechPattern}: the permanent right to
 * fabricate a mech chassis or weapon component. Learning writes only to the {@link MechBay};
 * it is deliberately not a vanilla blueprint and never touches the Marine Armory.
 */
public final class MechPatternCardItemPlugin extends BaseSpecialItemPlugin {

    public static final String ITEM_ID = MechPatternLearning.ITEM_ID;

    private MechPattern pattern;

    /** Validated cargo payload for market stock, patron rewards and salvage. */
    public static SpecialItemData itemData(String patternId) {
        MechPattern pattern = MechPattern.parse(patternId);
        if (pattern == null) {
            throw new IllegalArgumentException("Unknown mech pattern id '" + patternId + "'");
        }
        if (!pattern.presentationDeclared()) {
            throw new IllegalArgumentException("Mech pattern '" + patternId
                    + "' is a vehicle whose art is still pending; it is not awardable");
        }
        return new SpecialItemData(ITEM_ID, patternId);
    }

    @Override
    public void init(CargoStackAPI stack) {
        super.init(stack);
        pattern = MechPattern.parse(patternId());
    }

    @Override
    public String getName() {
        if (pattern == null) return Strings.get("mechPatternCardInvalidName");
        return MessageFormat.format(Strings.get("mechPatternCardNameFmt"), pattern.displayName());
    }

    @Override
    public String getDesignType() {
        return Strings.get("mechPatternCardDesignType");
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded,
                              CargoTransferHandlerAPI transferHandler, Object stackSource) {
        super.createTooltip(tooltip, expanded, transferHandler, stackSource);
        float pad = 10f;
        if (pattern == null) {
            tooltip.addPara(Strings.get("mechPatternCardInvalid"),
                    Misc.getNegativeHighlightColor(), pad);
            addCostLabel(tooltip, pad, transferHandler, stackSource);
            return;
        }
        MechPattern.Kind kind = pattern.kind();
        String unlockKey = switch (kind) {
            case CHASSIS -> "mechPatternCardChassisUnlockFmt";
            case WEAPON -> "mechPatternCardWeaponUnlockFmt";
            case INTERNAL -> "mechPatternCardInternalUnlockFmt";
            case VEHICLE -> "mechPatternCardVehicleUnlockFmt";
        };
        String kindKey = switch (kind) {
            case CHASSIS -> "mechPatternCardKindChassis";
            case WEAPON -> "mechPatternCardKindWeapon";
            case INTERNAL -> "mechPatternCardKindInternal";
            case VEHICLE -> "mechPatternCardKindVehicle";
        };
        tooltip.addPara(MessageFormat.format(Strings.get(unlockKey),
                pattern.displayName()), pad, Misc.getHighlightColor(), pattern.displayName());
        tooltip.addPara(MessageFormat.format(Strings.get("mechPatternCardKindFmt"),
                Strings.get(kindKey)), pad);
        if (pattern.weightClass() != null) {
            tooltip.addPara(MessageFormat.format(Strings.get("mechPatternCardWeightFmt"),
                    Strings.get("mechPatternCardWeight" + pattern.weightClass().name())), 0f);
        }
        tooltip.addPara(MessageFormat.format(Strings.get("mechPatternCardTierFmt"),
                Strings.get("mechPatternCardTier" + pattern.accessTier().name())), 0f);
        addCostLabel(tooltip, pad, transferHandler, stackSource);

        if (Global.CODEX_TOOLTIP_MODE) return;
        MechPatternLearning.Result status = MechPatternLearning.status(bay(), pattern.id());
        if (status == MechPatternLearning.Result.LEARNED) {
            tooltip.addPara(Strings.get("mechPatternCardLearnPrompt"),
                    Misc.getPositiveHighlightColor(), pad);
        } else if (status == MechPatternLearning.Result.ALREADY_KNOWN) {
            tooltip.addPara(Strings.get("mechPatternCardAlreadyKnown"), Misc.getGrayColor(), pad);
        } else if (status == MechPatternLearning.Result.BAY_UNAVAILABLE) {
            tooltip.addPara(Strings.get("mechPatternCardBayUnavailable"),
                    Misc.getNegativeHighlightColor(), pad);
        }
    }

    @Override
    public boolean hasRightClickAction() {
        return pattern != null;
    }

    @Override
    public boolean shouldRemoveOnRightClickAction() {
        return pattern != null && MechPatternLearning.status(bay(), pattern.id())
                == MechPatternLearning.Result.LEARNED;
    }

    @Override
    public void performRightClickAction() {
        String id = pattern != null ? pattern.id() : patternId();
        MechPatternLearning.Result result = MechPatternLearning.learn(bay(), id);
        String message;
        switch (result) {
            case LEARNED -> {
                Global.getSoundPlayer().playUISound("ui_acquired_blueprint", 1f, 1f);
                message = MessageFormat.format(Strings.get("mechPatternCardLearnedFmt"),
                        pattern.displayName());
            }
            case ALREADY_KNOWN -> message = MessageFormat.format(
                    Strings.get("mechPatternCardAlreadyKnownFmt"), pattern.displayName());
            case UNKNOWN_PATTERN -> message = Strings.get("mechPatternCardInvalid");
            case BAY_UNAVAILABLE -> message = Strings.get("mechPatternCardBayUnavailable");
            default -> throw new IllegalStateException("Unhandled learning result " + result);
        }
        if (Global.getSector() != null && Global.getSector().getCampaignUI() != null) {
            Global.getSector().getCampaignUI().getMessageDisplay().addMessage(message);
        }
    }

    @Override
    public String resolveDropParamsToSpecificItemData(String params, Random random)
            throws JSONException {
        return MechPattern.parse(params) != null ? params : null;
    }

    private String patternId() {
        if (stack == null || stack.getSpecialDataIfSpecial() == null) return null;
        return stack.getSpecialDataIfSpecial().getData();
    }

    private MechBay bay() {
        if (Global.getSector() == null) return null;
        MarineRosterScript script = MarineRosterScript.getInstance();
        return script != null ? script.roster().mechBay() : null;
    }
}
