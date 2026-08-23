package com.dillon.starsectormarines.marine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Read-only result of evaluating one or more fire-team refits as one transaction. */
public final class FireTeamRefitPreview {

    private final FireTeamTemplateResult result;
    private final List<FireTeamGearDelta> gear;

    public FireTeamRefitPreview(FireTeamTemplateResult result,
                                List<FireTeamGearDelta> gear) {
        this.result = result;
        this.gear = gear == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(gear));
    }

    public FireTeamTemplateResult result() {
        return result;
    }

    public List<FireTeamGearDelta> gear() {
        return gear;
    }

    public boolean canApply() {
        return result == FireTeamTemplateResult.APPLIED;
    }
}
