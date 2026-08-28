package com.dillon.starsectormarines.marine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Exact immutable preview consumed by squad equipment apply and the Armory. */
public final class SquadEquipmentPreview {

    private final SquadEquipmentResult result;
    private final List<SquadEquipmentBillet> billets;
    private final EquipmentTemplateCost issueCost;
    private final EquipmentTemplateCost availableCargo;

    public SquadEquipmentPreview(SquadEquipmentResult result,
                                 List<SquadEquipmentBillet> billets) {
        this(result, billets, EquipmentTemplateCost.ZERO, EquipmentTemplateCost.ZERO);
    }

    public SquadEquipmentPreview(SquadEquipmentResult result,
                                 List<SquadEquipmentBillet> billets,
                                 EquipmentTemplateCost issueCost,
                                 EquipmentTemplateCost availableCargo) {
        this.result = result != null ? result : SquadEquipmentResult.INVALID_SQUAD;
        this.billets = Collections.unmodifiableList(new ArrayList<>(
                billets != null ? billets : Collections.emptyList()));
        this.issueCost = issueCost != null ? issueCost : EquipmentTemplateCost.ZERO;
        this.availableCargo = availableCargo != null
                ? availableCargo : EquipmentTemplateCost.ZERO;
    }

    public SquadEquipmentResult result() { return result; }
    public boolean canApply() { return result == SquadEquipmentResult.APPLIED; }
    public List<SquadEquipmentBillet> billets() { return billets; }
    public SquadEquipmentBillet billet(int index) { return billets.get(index); }
    public EquipmentTemplateCost issueCost() { return issueCost; }
    public EquipmentTemplateCost availableCargo() { return availableCargo; }
}
