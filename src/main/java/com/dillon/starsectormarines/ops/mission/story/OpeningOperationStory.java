package com.dillon.starsectormarines.ops.mission.story;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.infantry.ExperienceTier;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.ops.Mission;
import com.dillon.starsectormarines.ops.MissionSource;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.Random;

/** One rung of the Independent broker's two-operation green-company ladder. */
public final class OpeningOperationStory implements StoryMissionDef {

    static final int GREEN_COMPANY_MAX_SOLDIERS = 18;

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
        if (ctx.roster.hasCompletedStory(id())) return false;
        return switch (kind) {
            case RELIEF -> !ctx.roster.hasCompletedStory(
                    OpeningOperationKind.COUNTERATTACK.missionId)
                    && isGreenCompany(ctx.roster);
            case COUNTERATTACK -> ctx.roster.hasCompletedStory(
                    OpeningOperationKind.RELIEF.missionId);
        };
    }

    @Override
    public Mission build(StoryEligibilityContext ctx) {
        Random random = new Random(ctx.seed ^ id().hashCode());
        float x = 0.12f + random.nextFloat() * 0.76f;
        float y = 0.12f + random.nextFloat() * 0.76f;
        String planetName = ctx.planet != null ? ctx.planet.getName() : null;

        return switch (kind) {
            case RELIEF -> new Mission(
                    id(), "Hold Until Relieved", MissionType.ASSAULT,
                    MissionSource.STORY, 6_000, RiskLevel.LOW,
                    "Player lift for 1 sortie",
                    "A local militia post is taking fire from bandits with more nerve than kit. "
                            + "Their line is still intact, but it will not stay that way. Land beside "
                            + "them, steady the position, and break the attack.",
                    x, y, FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                    2, 1, planetName, null);
            case COUNTERATTACK -> new Mission(
                    id(), "Take Back the Depot", MissionType.ASSAULT,
                    MissionSource.STORY, 9_000, RiskLevel.LOW,
                    "Player lift for 2 sorties",
                    "The survivors from the relief job found the bandits' supply depot. Local troops "
                            + "will go in with you, but they need a harder outfit to carry the sweep "
                            + "through the occupied blocks.",
                    x, y, FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                    3, 1, planetName, null);
        };
    }

    static boolean isGreenCompany(MarineRoster roster) {
        if (roster == null || roster.soldiers().size() > GREEN_COMPANY_MAX_SOLDIERS) {
            return false;
        }
        for (MarineSoldier soldier : roster.soldiers()) {
            if (soldier.experienceXp() >= ExperienceTier.REGULAR.minimumXp) return false;
        }
        return true;
    }
}
