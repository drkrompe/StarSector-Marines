package com.dillon.starsectormarines.battle;

/**
 * A fireteam of marines that deboarded from one shuttle. Squads are the unit
 * of cohesion and field-of-fire spreading: members try to stay within radius
 * of squadmates, and target-selection penalizes squadmates already engaging
 * the same enemy more heavily than non-squadmate allies.
 *
 * <p>Squad identity is just an integer key on {@link Unit#squadId}. The
 * {@link Squad} object holds metadata the AI consults — currently the leader
 * pointer and a cached center cell, both refreshed lazily. As behaviors
 * grow (bounding overwatch, role differentiation), squad state goes here.
 *
 * <p>Created by {@link BattleSimulation} on first deboard from each shuttle;
 * marines are added to it in deboard order. The first marine in is the
 * squad leader by convention.
 */
public final class Squad {

    public final int id;
    public final Faction faction;
    /** First marine to deboard. May die — leader-promotion logic isn't in yet, so a leaderless squad just has a null leader and falls back to "follow the centroid." */
    public Unit leader;

    public Squad(int id, Faction faction) {
        this.id = id;
        this.faction = faction;
    }
}
