package com.dillon.starsectormarines.ops.mission.story;

import com.dillon.starsectormarines.ops.Mission;
import com.dillon.starsectormarines.ops.MissionSource;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.Random;

/**
 * A small militia-support job carried by Independent brokers.
 *
 * <p><b>Recurring work, not a ladder the company climbs off.</b> These were
 * once a two-rung on-ramp gated on the roster being small and wearing nothing
 * above starting issue, so the offer vanished the moment the player collected
 * anything worth having. That made the work read as a tutorial being taken
 * away rather than as a kind of contract that exists in the world.
 *
 * <p>Nothing retires them now except the player. The payout is fixed and small,
 * so it decays against the rest of the board as the company grows and a captain
 * stops spending a sortie on it. That judgement is the intended retirement, and
 * it is the player's to make rather than a predicate's.
 *
 * <p>Every eligible Independent broker carries this work. Only map placement
 * uses the per-(planet, client) seed; finding the first job is never a lottery.
 *
 * <p>The relief job comes first: the depot counterattack is written as the
 * follow-up to one, so it stays behind a completed relief. Past that both
 * recur independently and indefinitely.
 */
public final class OpeningOperationStory implements StoryMissionDef {

    private final OpeningOperationKind kind;

    public OpeningOperationStory(OpeningOperationKind kind) {
        if (kind == null) throw new IllegalArgumentException("opening operation kind is required");
        this.kind = kind;
    }

    @Override
    public String id() {
        return kind.missionId;
    }

    @Override
    public boolean isEligible(StoryEligibilityContext ctx) {
        if (ctx == null || ctx.roster == null || ctx.client == null) return false;
        if (!"independent".equals(ctx.client.factionId)) return false;
        if (kind == OpeningOperationKind.COUNTERATTACK
                && !ctx.roster.hasCompletedStory(OpeningOperationKind.RELIEF.missionId)) {
            return false;
        }
        return true;
    }

    @Override
    public Mission build(StoryEligibilityContext ctx) {
        Random random = new Random(ctx.seed ^ id().hashCode());
        float x = 0.12f + random.nextFloat() * 0.76f;
        float y = 0.12f + random.nextFloat() * 0.76f;
        String planetName = ctx.planet != null ? ctx.planet.getName() : null;

        return switch (kind) {
            case RELIEF -> Mission.builder()
                    .id(id())
                    .name(kind.displayName)
                    .type(MissionType.ASSAULT)
                    .source(MissionSource.STORY)
                    .payout(kind.payout)
                    .risk(RiskLevel.LOW)
                    .requirements("Player lift for 1 sortie")
                    .flavor("A local militia post is taking fire from bandits with more nerve than kit. "
                            + "Their line is still intact, but it will not stay that way. Land beside "
                            + "them, steady the position, and break the attack.")
                    .mapPosition(x, y)
                    .requiredDrops(2)
                    .employerShuttles(1)
                    .targetPlanetName(planetName)
                    .build();
            case COUNTERATTACK -> Mission.builder()
                    .id(id())
                    .name(kind.displayName)
                    .type(MissionType.ASSAULT)
                    .source(MissionSource.STORY)
                    .payout(kind.payout)
                    .risk(RiskLevel.LOW)
                    .requirements("Player lift for 2 sorties")
                    .flavor("The survivors from the relief job found the bandits' supply depot. Local troops "
                            + "will go in with you, but they need a harder outfit to carry the sweep "
                            + "through the occupied blocks.")
                    .mapPosition(x, y)
                    .requiredDrops(3)
                    .employerShuttles(1)
                    .targetPlanetName(planetName)
                    .build();
        };
    }

}
