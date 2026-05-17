package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;

/**
 * A contract offered to the player. {@link #normalizedX}/{@link #normalizedY}
 * are 0..1 within the tactical map area — the tactical panel converts them to
 * absolute screen coords at render time.
 *
 * <p>{@link #targetPlanetName} + {@link #targetIndustryId} encode the mission's
 * physical target on the planet; on a successful Sabotage/Raid/Assault, the
 * resolver flags the industry as {@code disrupted}, which feeds back into the
 * next visit's mission generator.
 *
 * <p>{@link #clientFighterSupport} / {@link #enemyFighterSupport} are the air
 * support each side brings into the battle. Both default to {@link FlybyRoster#EMPTY}
 * when the generator decides nobody can spare anything.
 */
public final class Mission {

    public final String        id;
    public final String        name;
    public final MissionType   type;
    public final MissionSource source;
    public final int           payout;
    public final RiskLevel     risk;
    public final String        requirements;
    public final String        flavor;
    public final float         normalizedX;
    public final float         normalizedY;
    public final FlybyRoster   clientFighterSupport;
    public final FlybyRoster   enemyFighterSupport;
    /**
     * Total marine drops the mission needs delivered. With cycling, one
     * physical transport can cover multiple drops by flying repeat sorties —
     * so this is "marines that have to land," not "transports required."
     * Mission is gated when {@link #employerShuttles} covers all the drops
     * AND the player has zero transports to contribute (i.e., {@code
     * employerShuttles >= requiredDrops || playerTransports >= 1}).
     */
    public final int requiredDrops;
    /**
     * Drops the employer covers via single-sortie Aeroshuttles. Each contributes
     * one drop; the employer doesn't cycle. The player's transports cycle to
     * cover {@code max(0, requiredDrops - employerShuttles)} additional drops.
     */
    public final int employerShuttles;
    /** Planet name (campaign-unique) the mission targets; null for missions not tied to a place. */
    public final String targetPlanetName;
    /** Industry id (e.g. {@code "refining"}) the mission targets; null for non-industry ops. */
    public final String targetIndustryId;

    public Mission(String id,
                   String name,
                   MissionType type,
                   MissionSource source,
                   int payout,
                   RiskLevel risk,
                   String requirements,
                   String flavor,
                   float normalizedX,
                   float normalizedY,
                   FlybyRoster clientFighterSupport,
                   FlybyRoster enemyFighterSupport,
                   int requiredDrops,
                   int employerShuttles,
                   String targetPlanetName,
                   String targetIndustryId) {
        this.id           = id;
        this.name         = name;
        this.type         = type;
        this.source       = source != null ? source : MissionSource.GENERATED;
        this.payout       = payout;
        this.risk         = risk;
        this.requirements = requirements;
        this.flavor       = flavor;
        this.normalizedX  = normalizedX;
        this.normalizedY  = normalizedY;
        this.clientFighterSupport = clientFighterSupport != null ? clientFighterSupport : FlybyRoster.EMPTY;
        this.enemyFighterSupport  = enemyFighterSupport  != null ? enemyFighterSupport  : FlybyRoster.EMPTY;
        this.requiredDrops = Math.max(0, requiredDrops);
        this.employerShuttles = Math.max(0, Math.min(employerShuttles, this.requiredDrops));
        this.targetPlanetName = targetPlanetName;
        this.targetIndustryId = targetIndustryId;
    }
}
