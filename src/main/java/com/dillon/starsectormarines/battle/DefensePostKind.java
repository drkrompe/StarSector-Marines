package com.dillon.starsectormarines.battle;

/**
 * Tier of a manned turret emplacement placed by
 * {@link com.dillon.starsectormarines.battle.mapgen.bsp.DefensePostStamper}.
 *
 * <p>Each tier picks a different visual ring (vent grates vs. sandbag
 * embankment), turret count, garrison size, and patrol radius. Higher tiers
 * land deeper into the defender's territory — beach gets LIGHT, port gets
 * MEDIUM, fortress kill zone gets LARGE.
 *
 * <p>The ring cells are non-walkable + {@code SEE_THROUGH} so they grant
 * cover to the turret in the center via the standard cover bake while leaving
 * LoS and projectile paths clear. The militia squad spawned at the
 * {@link com.dillon.starsectormarines.battle.tactical.TacticalNode.Kind#GUARDPOST}
 * tactical node patrols a small ring around the post until every turret on
 * the post is destroyed, then releases into normal search-and-destroy.
 */
public enum DefensePostKind {

    /** Single light turret in a 4-cell vent ring. Beach-tier flavor — small footprint, militia-only squad. */
    LIGHT (1, 4, 60, 4),
    /** Single mid-weight turret in an 8-cell sandbag embankment. Port-tier — readable as deliberate fortification, mostly militia with 1-2 regulars. */
    MEDIUM(1, 6, 70, 6),
    /** 2-3 turrets in an extended embankment line. Kill-zone tier — mixed regulars + heavy turrets, longest patrol leash. */
    LARGE (3, 8, 80, 8);

    /** How many MapTurret cells this tier stamps at the post's center. LARGE spreads them across the extended footprint. */
    public final int turretCount;
    /** Suggested squad size attached to the GUARDPOST tactical node. The defender allocator sponges this many units into the post's garrison during Pass 1. */
    public final int garrisonSize;
    /** Tactical priority [0..100] — higher tiers outrank lower so wall-area LARGE posts get filled before beach LIGHTs when the roster runs short. */
    public final int priorityScore;
    /** Cells from the post anchor the garrison patrol may wander. Tighter for LIGHT (sit-on-the-post), looser for LARGE (loose perimeter sweep). */
    public final int patrolRadius;

    DefensePostKind(int turretCount, int garrisonSize, int priorityScore, int patrolRadius) {
        this.turretCount   = turretCount;
        this.garrisonSize  = garrisonSize;
        this.priorityScore = priorityScore;
        this.patrolRadius  = patrolRadius;
    }
}
