package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Authored location artwork, never a tactical survey or an outcome claim. */
enum StoryScene {
    RELIEF("relief", "WAYLINE CHECKPOINT"),
    DEPOT("counterattack", "WAYLINE FREIGHT DEPOT"),
    JUNCTION("junction", "JUNCTION DISPATCH"),
    RILL("last-lift", "RILL / MOTOR POOL"),
    RELAY("relay-nine", "RELAY NINE");

    private static final Pattern ROUTE_STORY = Pattern.compile(
            Pattern.quote(BrokenSupplyRouteProgress.PREFIX) + "([123])_0_[1-9][0-9]*");
    final String image;
    final String location;

    StoryScene(String file, String location) {
        image = "graphics/illustrations/wayline/" + file + ".jpg";
        this.location = location;
    }

    static StoryScene forMission(Mission mission) {
        if (mission == null || mission.source != MissionSource.STORY) return null;
        return switch (SupplyRouteCampaign.choice(mission)) {
            case BrokenSupplyRouteProgress.LIFT -> RILL;
            case BrokenSupplyRouteProgress.RELAY -> RELAY;
            default -> forRecord(mission.id, mission.source);
        };
    }

    static StoryScene forRecord(String id, MissionSource source) {
        if (id == null || source != MissionSource.STORY) return null;
        if (OpeningOperationKind.RELIEF.missionId.equals(id)) return RELIEF;
        if (OpeningOperationKind.COUNTERATTACK.missionId.equals(id)) return DEPOT;
        Matcher match = ROUTE_STORY.matcher(id);
        if (!match.matches()) return null;
        return switch (match.group(1)) {
            case "1" -> JUNCTION;
            case "2" -> RILL;
            case "3" -> RELAY;
            default -> null;
        };
    }
}
