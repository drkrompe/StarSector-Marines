package com.dillon.starsectormarines.marine;

/** Pure transition used by the cargo item to teach an Armory one whole loadout. */
public final class LoadoutCardLearning {

    public enum Result {
        LEARNED,
        ALREADY_KNOWN,
        UNKNOWN_CARD,
        ARMORY_UNAVAILABLE
    }

    private LoadoutCardLearning() {}

    public static Result status(MarineArmory armory, String cardId) {
        if (armory == null) return Result.ARMORY_UNAVAILABLE;
        LoadoutCard card = LoadoutCard.parse(cardId);
        if (card == null) return Result.UNKNOWN_CARD;
        for (String templateId : card.templateIds()) {
            if (!armory.ownsEquipmentTemplate(templateId)) return Result.LEARNED;
        }
        if (card.sheetId() != null && !armory.knowsArmorPlan(card.sheetId())) return Result.LEARNED;
        return Result.ALREADY_KNOWN;
    }

    public static Result learn(MarineArmory armory, String cardId) {
        Result status = status(armory, cardId);
        if (status != Result.LEARNED) return status;
        LoadoutCard card = LoadoutCard.parse(cardId);
        for (String templateId : card.templateIds()) armory.acquireEquipmentTemplate(templateId);
        if (card.sheetId() != null) armory.acquireArmorPlan(card.sheetId());
        return Result.LEARNED;
    }
}
