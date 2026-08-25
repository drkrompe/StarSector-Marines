package com.dillon.starsectormarines.ops.battleview;

/** Presentation features a battle-renderer host elects to own. */
public enum BattleRenderHostProfile {
    /** Full battle screen with combat decorators and optional surface-relief FBOs. */
    STANDALONE_BATTLE(true, true),
    /** Bounded room scene: world art only, with host UI and effects kept detached. */
    EMBEDDED_SCENE(false, false);

    private final boolean unitDecorationsVisible;
    private final boolean surfaceReliefEnabled;

    BattleRenderHostProfile(boolean unitDecorationsVisible,
                            boolean surfaceReliefEnabled) {
        this.unitDecorationsVisible = unitDecorationsVisible;
        this.surfaceReliefEnabled = surfaceReliefEnabled;
    }

    public boolean unitDecorationsVisible() {
        return unitDecorationsVisible;
    }

    public boolean surfaceReliefEnabled() {
        return surfaceReliefEnabled;
    }
}
