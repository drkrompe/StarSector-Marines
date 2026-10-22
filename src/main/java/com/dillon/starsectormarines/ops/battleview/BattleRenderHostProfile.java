package com.dillon.starsectormarines.ops.battleview;

/** Presentation features a battle-renderer host elects to own. */
public enum BattleRenderHostProfile {
    /** Full battle screen with combat decorators and optional surface-relief FBOs. */
    STANDALONE_BATTLE(true, true, true, true, true, true),
    /** Player-view evidence: battle visibility and decorations, with a non-GL drain. */
    HEADLESS_BATTLE(true, false, true, false, true, true),
    /** Bounded room scene: world art only, with host UI and effects kept detached. */
    EMBEDDED_SCENE(false, false, true, false, false, true),
    /**
     * A neutral observer's frame: an embedded scene that draws every body on the field, whoever
     * can see it.
     *
     * <p>A review frame does not draw the fog, so a body the fog hides is one the frame withholds
     * with nothing on screen to say why: a Matriarch behind a wall the review has no art for
     * stood invisible beside its own marker until the wall broke, which read as a culling bug.
     */
    OBSERVER_SCENE(false, false, true, false, false, false),
    /**
     * A ship's deck, where the world does not fill its own grid.
     *
     * <p>A ground map is ground everywhere, so a backing quad under the whole
     * grid is honest there. A deck is a shape inside a hull, and the same quad
     * paints decking across the space the ship is not — which hides whatever
     * the host has put behind her, including the hull herself.
     */
    DECK_SCENE(false, false, false, false, false, true);

    private final boolean unitDecorationsVisible;
    private final boolean surfaceReliefEnabled;
    private final boolean worldBackingPainted;
    private final boolean residentGroundAllowed;
    private final boolean fogClipsLighting;
    private final boolean fogHidesUnits;

    BattleRenderHostProfile(boolean unitDecorationsVisible,
                            boolean surfaceReliefEnabled,
                            boolean worldBackingPainted,
                            boolean residentGroundAllowed,
                            boolean fogClipsLighting,
                            boolean fogHidesUnits) {
        this.unitDecorationsVisible = unitDecorationsVisible;
        this.surfaceReliefEnabled = surfaceReliefEnabled;
        this.worldBackingPainted = worldBackingPainted;
        this.residentGroundAllowed = residentGroundAllowed;
        this.fogClipsLighting = fogClipsLighting;
        this.fogHidesUnits = fogHidesUnits;
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

    /**
     * Whether this host's fog may live in a {@link FogField} instead of being
     * submitted cell by cell.
     *
     * <p>Reads the same election, and deliberately not a second flag: both
     * answers turn on the same two facts about a host — whether it can run a
     * custom pass owning its own GL at all, and whether it has enough cells for
     * residency to be worth the buffers. A host that keeps a resident ground
     * keeps a resident fog, and one that cannot do the first cannot do the
     * second. A flag per resident thing would be a list to keep in step for no
     * decision anyone actually makes separately.
     */
    public boolean residentFogAllowed() {
        return residentGroundAllowed;
    }

    /** Hosts without a GL accumulator submit retained decal sources as ordinary quads. */
    public boolean residentDecalsAllowed() {
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

    /** Player-view lamps obey current observation independently of the graphics drain. */
    public boolean fogClipsLighting() {
        return fogClipsLighting;
    }

    /** Whether a body the player's side cannot see is withheld or faded, as the battle screen does. */
    public boolean fogHidesUnits() {
        return fogHidesUnits;
    }
}
