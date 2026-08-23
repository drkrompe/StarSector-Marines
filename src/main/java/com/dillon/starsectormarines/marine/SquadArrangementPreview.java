package com.dillon.starsectormarines.marine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Read-only result of evaluating a three-team squad arrangement transaction. */
public final class SquadArrangementPreview {

    private final FireTeamTemplateResult result;
    private final List<FireTeamGearDelta> gear;

    public SquadArrangementPreview(FireTeamTemplateResult result,
                                   List<FireTeamGearDelta> gear) {
        this.result = result;
        this.gear = gear == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(gear));
    }

    public FireTeamTemplateResult result() { return result; }
    public List<FireTeamGearDelta> gear() { return gear; }
    public boolean canApply() { return result == FireTeamTemplateResult.APPLIED; }
}
