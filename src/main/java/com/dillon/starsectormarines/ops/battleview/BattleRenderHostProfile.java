package com.dillon.starsectormarines.ops.battleview;

/** Presentation features a battle-renderer host elects to own. */
public enum BattleRenderHostProfile {
    /** Full battle screen with combat decorators and optional surface-relief FBOs. */
    STANDALONE_BATTLE(true, true, true, true),
    /** Bounded room scene: world art only, with host UI and effects kept detached. */
    EMBEDDED_SCENE(false, false, true, false),
    /**
     * A ship's deck, where the world does not fill its own grid.
     *
     * <p>A ground map is ground everywhere, so a backing quad under the whole
     * grid is honest there. A deck is a shape inside a hull, and the same quad
     * paints decking across the space the ship is not — which hides whatever
     * the host has put behind her, including the hull herself.
     */
    DECK_SCENE(false, false, false, false);

    private final boolean unitDecorationsVisible;
    private final boolean surfaceReliefEnabled;
    private final boolean worldBackingPainted;
    private final boolean residentGroundAllowed;

    BattleRenderHostProfile(boolean unitDecorationsVisible,
                            boolean surfaceReliefEnabled,
                            boolean worldBackingPainted,
                            boolean residentGroundAllowed) {
        this.unitDecorationsVisible = unitDecorationsVisible;
        this.surfaceReliefEnabled = surfaceReliefEnabled;
        this.worldBackingPainted = worldBackingPainted;
        this.residentGroundAllowed = residentGroundAllowed;
    }

    /**
     * Whether this host's ground may live in a {@link GroundMesh} instead of
     * being submitted cell by cell.
     *
     * <p>Off for the embedded hosts, and not as a performance judgement: a
     * resident mesh draws through a custom pass owning its own GL, and an
     * embedded scene may be draining through Java2D with no context at all. It
     * is also the wrong trade for them — a room is tens of cells, where the
     * whole point of the mesh is the hundred and eighty thousand of a Conquest.
     */
    public boolean residentGroundAllowed() {
        return residentGroundAllowed;
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
