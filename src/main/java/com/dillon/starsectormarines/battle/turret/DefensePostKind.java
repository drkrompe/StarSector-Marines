package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.drone.Drone;
import com.dillon.starsectormarines.battle.drone.DroneHubUnit;
import com.dillon.starsectormarines.battle.setup.BattleSetup;

/**
 * Tier of a manned turret emplacement placed by
 * {@link com.dillon.starsectormarines.battle.world.gen.bsp.DefensePostStamper}.
 *
 * <p>Each tier picks a different visual ring (vent grates vs. sandbag
 * embankment), turret count, garrison size, and patrol radius. Higher tiers
 * land deeper into the defender's territory — beach gets LIGHT, port gets
 * MEDIUM, fortress kill zone gets LARGE.
 *
 * <p>The ring cells are non-walkable + {@code SEE_THROUGH} so they grant
 * cover to the turret in the center via the standard cover bake while leaving
 * LoS and projectile paths clear. The militia squad spawned at the
 * {@link com.dillon.starsectormarines.battle.decision.TacticalNode.Kind#GUARDPOST}
 * tactical node patrols a small ring around the post until every turret on
 * the post is destroyed, then releases into normal search-and-destroy.
 */
public enum DefensePostKind {

    /** Single light turret in a 4-cell vent ring. Beach-tier flavor — small footprint, militia-only squad. */
    LIGHT    (1, 4, 60, 12),
    /** Single mid-weight turret in an 8-cell sandbag embankment. Port-tier — readable as deliberate fortification, mostly militia with 1-2 regulars. */
    MEDIUM   (1, 6, 70, 18),
    /** 2-3 turrets in an extended embankment line. Kill-zone tier — mixed regulars + heavy turrets, longest patrol leash. */
    LARGE    (3, 8, 80, 28),
    /** Two-LOCUST rocket battery in a 5×3 bow-out embankment, placed deep in the fortress interior (behind the kremlin wall). Long-range salvo emplacement — battery crew is small (4) and stays on the launchers, hence the tighter patrol radius. Higher priority than LARGE so the defender allocator fills artillery first when the roster runs short — losing the battery means losing the long-range threat that punishes the attacker's approach. */
    ARTILLERY(2, 4, 85, 10),
    /**
     * Drone launch hub — 3×3 embankment ring around a central launch platform.
     * Spawns aerial drones periodically that patrol around the hub and engage
     * intruders; the drones (not infantry) are the defense, so the hub carries
     * a {@code garrisonSize} of 0 and the stamper skips
     * {@link com.dillon.starsectormarines.battle.decision.TacticalNode.Kind#GUARDPOST}
     * node emission for this tier. Priority sits between MEDIUM and LARGE —
     * losing the hub silences the drone screen, but it's not the long-range
     * threat that ARTILLERY represents.
     *
     * <p>Hosts no {@link com.dillon.starsectormarines.battle.turret.MapTurret}
     * — the hub's {@link com.dillon.starsectormarines.battle.turret.DefensePost#turrets}
     * list is empty, and a future {@code DroneHubUnit} spawned by
     * {@link com.dillon.starsectormarines.battle.setup.BattleSetup} will occupy the
     * center cell and drive the drone spawn cadence.
     */
    DRONE_HUB(0, 0, 75, 0);

    /** How many MapTurret cells this tier stamps at the post's center. LARGE spreads them across the extended footprint. */
    public final int turretCount;
    /** Suggested squad size attached to the GUARDPOST tactical node. The defender allocator sponges this many units into the post's garrison during Pass 1. */
    public final int garrisonSize;
    /** Tactical priority [0..100] — higher tiers outrank lower so wall-area LARGE posts get filled before beach LIGHTs when the roster runs short. */
    public final int priorityScore;
    /** Half-extent (cells ≈ metres) of the box the guard squad patrols around the post anchor. Scaled against infantry weapon range (~24): LIGHT ~0.5× rifle (sit near the post), MEDIUM ~0.75×, LARGE ~1.2× (a real perimeter sweep), ARTILLERY ~0.4× (crew stays near the launchers). Tight absolute values would leave a squad leashed to a third of its own weapon range. */
    public final int patrolRadius;

    DefensePostKind(int turretCount, int garrisonSize, int priorityScore, int patrolRadius) {
        this.turretCount   = turretCount;
        this.garrisonSize  = garrisonSize;
        this.priorityScore = priorityScore;
        this.patrolRadius  = patrolRadius;
    }
}
