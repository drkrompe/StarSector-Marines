package com.dillon.starsectormarines.battle.air;

/**
 * A bulk freighter standing on a spaceport's pad ({@code raid-site-templates.md}, spaceport): the planet's traffic to
 * orbit, the size of the ships that carry a planet's freight rather than a company's drop boats. Parked only; it never
 * flies a sortie, carries nothing the battle asks about and mounts no guns.
 *
 * <p>Drawn at its hull's own length ({@code HullFootprintResolver}), as every aircraft on the ground is, so an Atlas
 * stands nearly three times a Buffalo's length on its pad. {@link #beamCells} is the drawn breadth across the hull,
 * which the stand's footprint is sized from, since a sprite's width is not known before the battle draws.
 */
public enum PortFreighter implements Airframe {

    ATLAS("graphics/ships/atlas/atlas.png", "atlas", 13.5f),
    ATLAS_MK2("graphics/ships/atlas/atlas_mk2_base.png", "atlas2", 13.5f),
    PROMETHEUS("graphics/ships/tanker_large.png", "prometheus", 16.7f);

    /** A parked freighter's hull points: a large hull, never a target the battle sets. UNTUNED. */
    private static final float PARKED_HP = 400f;

    private final String spritePath;
    private final String hullId;
    /** Drawn breadth across the hull, in cells, at the ground scale. */
    public final float beamCells;

    PortFreighter(String spritePath, String hullId, float beamCells) {
        this.spritePath = spritePath;
        this.hullId = hullId;
        this.beamCells = beamCells;
    }

    @Override public String spritePath() { return spritePath; }

    @Override public String renderHullId() { return hullId; }

    @Override public AirOrdnance ordnance() { return null; }

    @Override public int hardpoints() { return 0; }

    /** It never flies here; asked, it handles as the heaviest transport does. */
    @Override public AirHandling flight() { return ShuttleType.MULE; }

    @Override public float maxHp() { return PARKED_HP; }
}
