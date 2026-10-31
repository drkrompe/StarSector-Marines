package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.campaign.CorvusWaylineProgress;
import com.dillon.starsectormarines.campaign.SilentColonyMissionKey;
import com.dillon.starsectormarines.campaign.ShepherdRecoveryProgress;
import com.dillon.starsectormarines.campaign.WellhollowBloomProgress;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Authored location artwork, never a tactical survey or an outcome claim. */
enum StoryScene {
    RELIEF("relief", "WAYLINE CHECKPOINT"),
    DEPOT("counterattack", "WAYLINE FREIGHT DEPOT"),
    JUNCTION("junction", "JUNCTION DISPATCH"),
    RILL("last-lift", "RILL / MOTOR POOL"),
    RELAY("relay-nine", "RELAY NINE"),
    THUNDER("thunder-run", "FREIGHT CORRIDOR"),
    DUST_LEDGER("dust-ledger", "ASHARU / DISPATCH ARCHIVE"),
    BLIND_WATCH("blind-watch", "WARION / RELAY INSTALLATIONS"),
    COLD_ACCOUNT("cold-account", "GARNIR / DISPATCH STRONGHOLD"),
    VETERANS_JOB("veterans-job", "BROKER'S CACHE"),
    SILENT_COLONY("silent-colony", "DISTRESS BEACON / COLONY"),
    SALVAGE_RIGHTS("salvage-rights", "ASHARU / MORROW RECOVERY BASIN"),
    COLD_START("salvage-rights", "ASHARU / SAME MORROW RECOVERY BASIN"),
    WORKING_PASSAGE("salvage-rights", "ASHARU / MORROW DEPARTURE CORRIDOR"),
    WELLHOLLOW_COLONY("wellhollow-colony", "ASHARU / THE WELLHOLLOW FLOOR"),
    WELLHOLLOW_RIM("wellhollow-rim", "ASHARU / THE WELLHOLLOW RIM"),
    WELLHOLLOW_HEART("wellhollow-heart", "ASHARU / THE WELLHOLLOW HEART");

    private static final Pattern ROUTE_RECORD = Pattern.compile(
            Pattern.quote(BrokenSupplyRouteProgress.PREFIX) + "([134])_(0|[1-9][0-9]*)_[1-9][0-9]*");
    final String image;
    final String location;

    StoryScene(String file, String location) {
        image = "graphics/illustrations/wayline/" + file + ".jpg";
        this.location = location;
    }

    static StoryScene forMission(Mission mission) {
        if (mission == null) return null;
        return forRecord(mission.id, mission.source);
    }

    static StoryScene forRecord(String id, MissionSource source) {
        if (id == null || source == null) return null;
        // A replay shows its chapter's art, as the debug run of that chapter does.
        var debug = DebugMissionCatalog.scenario(id, source);
        if (debug == null) debug = CompletedReplays.scenario(id, source);
        if (debug != null) return switch (debug) {
            case MISSILE_AA, CORDON_LINE, CORDON_ROAD, BROAD_FRONT, THE_BURN, WATER_UNDER_SEAL, LIMITED_WARRANT, YARD_OWNERS, TERMS_OF_PROTECTION, SMALL_WAR, DISTRICT_ESCORT -> null;
            case RELIEF, MILITIA_RELIEF -> RELIEF;
            case COUNTERATTACK, DEPOT_CLEARANCE -> DEPOT;
            case JUNCTION -> JUNCTION;
            case LAST_LIFT, CREW_RECOVERY -> RILL;
            case RELAY_NINE, ROUTE_SECURITY -> RELAY;
            case THUNDER_RUN -> THUNDER;
            case DUST_LEDGER -> DUST_LEDGER;
            case BLIND_WATCH -> BLIND_WATCH;
            case COLD_ACCOUNT -> COLD_ACCOUNT;
            case VETERANS_JOB -> VETERANS_JOB;
            case SILENT_COLONY -> SILENT_COLONY;
            case SALVAGE_RIGHTS -> SALVAGE_RIGHTS;
            case COLD_START -> COLD_START;
            case WORKING_PASSAGE -> WORKING_PASSAGE;
            case WELLHOLLOW_BREACH -> WELLHOLLOW_COLONY;
            case WELLHOLLOW_ROAD -> WELLHOLLOW_RIM;
            case WELLHOLLOW_BURN, WELLHOLLOW_FRONT -> WELLHOLLOW_HEART;
        };
        if (source == MissionSource.CAMPAIGN_EVENT)
            return SilentColonyMissionKey.parse(id) == null ? null : SILENT_COLONY;
        if (source == MissionSource.RECURRING) {
            if (OpeningOperationKind.RELIEF.recurringId.equals(id)) return RELIEF;
            if (OpeningOperationKind.COUNTERATTACK.recurringId.equals(id)) return DEPOT;
        } else if (source == MissionSource.STORY) {
            var wellhollow = WellhollowBloomProgress.chapterOf(id);
            if (wellhollow != null) return switch (wellhollow) {
                case COLONY_BREACH -> WELLHOLLOW_COLONY;
                case CORDON_ROAD -> WELLHOLLOW_RIM;
                case THE_BURN, BROAD_FRONT -> WELLHOLLOW_HEART;
            };
            if(ShepherdRecoveryCampaign.owns(id))return ShepherdRecoveryProgress.workingPassage(id)?WORKING_PASSAGE:ShepherdRecoveryProgress.coldStart(id)?COLD_START:SALVAGE_RIGHTS;
            var corvus = CorvusWaylineProgress.forMission(id);
            if (corvus != null) return switch (corvus) {
                case DUST_LEDGER -> DUST_LEDGER;
                case BLIND_WATCH -> BLIND_WATCH;
                case COLD_ACCOUNT -> COLD_ACCOUNT;
            };
            if (OpeningOperationKind.RELIEF.missionId.equals(id)) return RELIEF;
            if (OpeningOperationKind.COUNTERATTACK.missionId.equals(id)) return DEPOT;
        } else return null;
        Matcher match = ROUTE_RECORD.matcher(id);
        if (!match.matches()) return null;
        if ((source == MissionSource.STORY) != match.group(2).equals("0")) return null;
        if (source == MissionSource.RECURRING && !match.group(1).equals("3")) return null;
        return switch (match.group(1)) {
            case "1" -> JUNCTION;
            case "3" -> RELAY;
            case "4" -> THUNDER;
            default -> null;
        };
    }
}
