package com.dillon.starsectormarines.ops.battleview;

/** Presentation features a battle-renderer host elects to own. */
public enum BattleRenderHostProfile {
    /** Full battle screen with combat decorators and optional surface-relief FBOs. */
    STANDALONE_BATTLE(true, true, true),
    /** Bounded room scene: world art only, with host UI and effects kept detached. */
    EMBEDDED_SCENE(false, false, true),
    /**
     * A ship's deck, where the world does not fill its own grid.
     *
     * <p>A ground map is ground everywhere, so a backing quad under the whole
     * grid is honest there. A deck is a shape inside a hull, and the same quad
     * paints decking across the space the ship is not — which hides whatever
     * the host has put behind her, including the hull herself.
     */
    DECK_SCENE(false, false, false);

    private final boolean unitDecorationsVisible;
    private final boolean surfaceReliefEnabled;
    private final boolean worldBackingPainted;

    BattleRenderHostProfile(boolean unitDecorationsVisible,
                            boolean surfaceReliefEnabled,
                            boolean worldBackingPainted) {
        this.unitDecorationsVisible = unitDecorationsVisible;
        this.surfaceReliefEnabled = surfaceReliefEnabled;
        this.worldBackingPainted = worldBackingPainted;
    }

    /** Whether the world paints a solid quad under its whole grid. */
    public boolean worldBackingPainted() {
        return worldBackingPainted;
    }

    public boolean unitDecorationsVisible() {
        return unitDecorationsVisible;
    }

    public boolean surfaceReliefEnabled() {
        return surfaceReliefEnabled;
    }
}
