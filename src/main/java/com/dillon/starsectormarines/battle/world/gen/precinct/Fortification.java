package com.dillon.starsectormarines.battle.world.gen.precinct;

/**
 * How hard a walled place is to get into.
 *
 * <p>A mission dial, and it has two failure modes rather than one. A wall a
 * handful of low-tier squads cannot breach or flank is not a fight, it is a
 * refusal; a wall a thousand marines walk through is not a climax. Neither is
 * fixed by tuning one number harder, because they are the same number pointed
 * at different forces — so the strength is stated by whoever knows what is being
 * sent, and the generator obeys.
 *
 * <p>Two things carry it, and they are different questions:
 *
 * <ul>
 *   <li>{@link #gates} — how many ways in there are. This is about
 *       <em>manoeuvre</em>: several gates give an attacker somewhere to feint
 *       and somewhere to commit, one gate makes the approach the whole battle.
 *   <li>{@link #wallHp} — what it costs to make a new way in. This is about
 *       <em>materiel</em>: a wall worth less than a demolition charge is
 *       decoration, and one worth more than the force can spend is a detour.
 * </ul>
 *
 * <p>Gate count is capped rather than set: growth decides where roads cross the
 * outline, and this decides how many of those crossings stay open. A place whose
 * roads all leave by one route cannot be given three gates by asking.
 */
public record Fortification(int gates, int wallHp) {

    public Fortification {
        if (gates < 1) {
            throw new IllegalArgumentException(
                    "a walled place with " + gates + " gates is a place nothing leaves");
        }
        if (wallHp < 1) {
            throw new IllegalArgumentException("a wall with " + wallHp + " hp is not a wall");
        }
    }

    /**
     * A screen rather than a defence. Many ways in and a wall that comes down to
     * ordinary fire — what a raid against an outpost should meet.
     */
    public static final Fortification PICKET = new Fortification(6, 80);

    /**
     * The shipped default, and what the conquest fortress wall has always been
     * worth. A company-scale problem.
     */
    public static final Fortification GARRISON = new Fortification(3, 240);

    /** Two ways in and a wall that wants deliberate breaching. */
    public static final Fortification STRONGHOLD = new Fortification(2, 600);

    /**
     * One way in, and a wall nothing short of a siege opens. For the capture a
     * whole force was raised for; against anything smaller it is a refusal.
     */
    public static final Fortification CITADEL = new Fortification(1, 1200);

    public Fortification withGates(int count) {
        return new Fortification(count, wallHp);
    }

    public Fortification withWallHp(int hp) {
        return new Fortification(gates, hp);
    }
}
