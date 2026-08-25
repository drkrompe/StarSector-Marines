package com.dillon.starsectormarines.marine;

/** Pure transition used by campaign cargo items to add a template to the Armory. */
public final class EquipmentTemplateLearning {

    public enum Result {
        LEARNED,
        ALREADY_KNOWN,
        UNKNOWN_TEMPLATE,
        ARMORY_UNAVAILABLE
    }

    private EquipmentTemplateLearning() {
    }

    public static Result status(MarineArmory armory, String templateId) {
        if (armory == null) return Result.ARMORY_UNAVAILABLE;
        if (!EquipmentTemplateCatalog.contains(templateId)) return Result.UNKNOWN_TEMPLATE;
        if (armory.ownsEquipmentTemplate(templateId)) return Result.ALREADY_KNOWN;
        return Result.LEARNED;
    }

    public static Result learn(MarineArmory armory, String templateId) {
        Result status = status(armory, templateId);
        if (status != Result.LEARNED) return status;
        return armory.acquireEquipmentTemplate(templateId)
                ? Result.LEARNED : Result.ALREADY_KNOWN;
    }
}
