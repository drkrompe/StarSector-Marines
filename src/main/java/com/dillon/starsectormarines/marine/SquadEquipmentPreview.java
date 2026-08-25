package com.dillon.starsectormarines.marine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Exact immutable preview consumed by squad equipment apply and the Armory. */
public final class SquadEquipmentPreview {

    private final SquadEquipmentResult result;
    private final List<SquadEquipmentBillet> billets;
    private final List<FireTeamGearDelta> gear;

    public SquadEquipmentPreview(SquadEquipmentResult result,
                                 List<SquadEquipmentBillet> billets,
                                 List<FireTeamGearDelta> gear) {
        this.result = result != null ? result : SquadEquipmentResult.INVALID_SQUAD;
        this.billets = Collections.unmodifiableList(new ArrayList<>(
                billets != null ? billets : Collections.emptyList()));
        this.gear = Collections.unmodifiableList(new ArrayList<>(
                gear != null ? gear : Collections.emptyList()));
    }

    public SquadEquipmentResult result() { return result; }
    public boolean canApply() { return result == SquadEquipmentResult.APPLIED; }
    public List<SquadEquipmentBillet> billets() { return billets; }
    public SquadEquipmentBillet billet(int index) { return billets.get(index); }
    public List<FireTeamGearDelta> gear() { return gear; }
}
