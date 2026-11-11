package com.dillon.starsectormarines.battle.air;

/**
 * A ship standing on a spaceport's pad ({@code raid-site-templates.md}, spaceport): the planet's own traffic to orbit,
 * from the shuttle pods that run passengers up to the bulk freighters that lift its cargo. Parked only; it never flies
 * a sortie, carries nothing the battle asks about and mounts no guns.
 *
 * <p>Drawn at its hull's own size, as every aircraft on the ground is: the sprite's pixels at the ground scale
 * ({@link #CELLS_PER_PX}), so a Colossus stands ten times a Shuttle Pod's length. {@link #lengthCells} and
 * {@link #beamCells} are that drawn size, which a stand's berth is matched to ({@link #forBerth}) and its footprint
 * sized from, since a sprite's size is not known before the battle draws.
 */
public enum PortFreighter implements Airframe {

    SHUTTLE("graphics/ships/shuttle.png", "shuttlepod", 22, 18),
    HOUND("graphics/ships/hound/hound_base.png", "hound", 72, 62),
    COLOSSUS("graphics/ships/colossus/colossus.png", "colossus", 224, 184);

    /** Cells a sprite pixel draws across on the ground: the hull scale's 0.045 at {@link AirAppearance#GROUND_SCALE}. */
    public static final float CELLS_PER_PX = .045f * AirAppearance.GROUND_SCALE;

    /** A parked ship's hull points: never a target the battle sets. UNTUNED. */
    private static final float PARKED_HP = 400f;

    private final String spritePath;
    private final String hullId;
    /** Drawn length, nose to tail, in cells. */
    public final float lengthCells;
    /** Drawn breadth across the hull, in cells. */
    public final float beamCells;

    PortFreighter(String spritePath, String hullId, int lengthPx, int beamPx) {
        this.spritePath = spritePath;
        this.hullId = hullId;
        this.lengthCells = lengthPx * CELLS_PER_PX;
        this.beamCells = beamPx * CELLS_PER_PX;
    }

    /**
     * The largest ship a berth {@code longCells} by {@code acrossCells} holds, nose along its long side; null when not
     * even the smallest fits.
     */
    public static PortFreighter forBerth(int longCells, int acrossCells) {
        PortFreighter fits = null;
        for (PortFreighter p : values()) {
            if (p.lengthCells <= longCells && p.beamCells <= acrossCells
                    && (fits == null || p.lengthCells > fits.lengthCells)) fits = p;
        }
        return fits;
    }

    @Override public String spritePath() { return spritePath; }

    @Override public String renderHullId() { return hullId; }

    @Override public AirOrdnance ordnance() { return null; }

    @Override public int hardpoints() { return 0; }

    /** It never flies here; asked, it handles as the heaviest transport does. */
    @Override public AirHandling flight() { return ShuttleType.MULE; }

    @Override public float maxHp() { return PARKED_HP; }
}
