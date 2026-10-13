package com.dillon.starsectormarines.battle.audio;

import com.dillon.starsectormarines.ops.DebugMissionCatalog;
import com.dillon.starsectormarines.ops.Mission;
import com.dillon.starsectormarines.ops.MissionSource;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.campaign.CorvusWaylineProgress;
import com.dillon.starsectormarines.ops.CorvusChapter;
import com.dillon.starsectormarines.ops.ShepherdRecoveryCampaign;
import com.dillon.starsectormarines.ops.AgreusArcCampaign;
import com.dillon.starsectormarines.campaign.ShepherdRecoveryProgress;
import com.fs.starfarer.api.Global;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Packaged dialogue, independent of its authoring provider and campaign state. */
public final class WaylineNarration {
    public static final String PATH = "data/marines/wayline_narration.json";
    private static final Pattern SUPPLY_STORY_ID = Pattern.compile(
            Pattern.quote(BrokenSupplyRouteProgress.PREFIX) + "([1-4])_0_[1-9][0-9]*");
    private static final Map<Integer, String> SUPPLY_CHAPTERS = Map.of(
            BrokenSupplyRouteProgress.JUNCTION, "story_supply_route_junction",
            BrokenSupplyRouteProgress.LIFT, "story_supply_route_last_lift",
            BrokenSupplyRouteProgress.RELAY, "story_supply_route_relay_nine",
            BrokenSupplyRouteProgress.THUNDER, "story_supply_route_thunder_run");
    private static WaylineNarration builtins = new WaylineNarration(List.of());
    private final Map<String, Line> lines = new LinkedHashMap<>();

    public record Line(String id, String missionId, String event, String text,
                       float durationSeconds, int priority, float expiresSeconds,
                       List<String> supersedes, String speaker, String portrait,
                       boolean voiced, boolean terminal) {
        public Line(String id, String missionId, String event, String text,
                    float durationSeconds, int priority, float expiresSeconds, List<String> supersedes,
                    String speaker, String portrait) {
            this(id, missionId, event, text, durationSeconds, priority, expiresSeconds, supersedes,
                    speaker, portrait, true, false);
        }
        public Line(String id, String missionId, String event, String text,
                    float durationSeconds, int priority, float expiresSeconds, List<String> supersedes) {
            this(id, missionId, event, text, durationSeconds, priority, expiresSeconds, supersedes,
                    "MARA VENN / WAYLINE", "graphics/portraits/mara-venn.png");
        }
        public Line {
            if (id == null || id.isBlank() || missionId == null || event == null
                    || text == null || text.isBlank() || !Float.isFinite(durationSeconds)
                    || durationSeconds <= 0 || !Float.isFinite(expiresSeconds) || expiresSeconds <= 0)
                throw new IllegalArgumentException("Invalid narration line");
            supersedes = List.copyOf(supersedes);
        }
    }

    public WaylineNarration(List<Line> entries) {
        for (Line line : entries) {
            if (lines.putIfAbsent(line.missionId() + "/" + line.event(), line) != null)
                throw new IllegalArgumentException("Duplicate narration event: " + line.event());
        }
    }

    public static WaylineNarration builtins() { return builtins; }

    public static void loadBuiltins() {
        try {
            builtins = parse(Global.getSettings().loadJSON(PATH));
        } catch (Exception failure) {
            builtins = new WaylineNarration(List.of());
            Global.getLogger(WaylineNarration.class).warn("Wayline narration unavailable; text remains enabled", failure);
        }
    }

    public static WaylineNarration parse(JSONObject root) throws JSONException {
        JSONArray entries = root.getJSONArray("lines");
        List<Line> result = new ArrayList<>();
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            List<String> supersedes = new ArrayList<>();
            JSONArray prior = entry.optJSONArray("supersedes");
            if (prior != null) for (int j = 0; j < prior.length(); j++) supersedes.add(prior.getString(j));
            result.add(new Line(entry.getString("id"), entry.getString("missionId"),
                    entry.getString("event"), entry.getString("text"),
                    (float) entry.getDouble("durationSeconds"), entry.optInt("priority", 10),
                    (float) entry.optDouble("expiresSeconds", 15), supersedes,
                    entry.optString("speaker", "MARA VENN / WAYLINE"),
                    entry.optString("portrait", "graphics/portraits/mara-venn.png"),
                    entry.optBoolean("voiced", true), entry.optBoolean("terminal", false)));
        }
        return new WaylineNarration(result);
    }

    /** Scenario hooks are shared with recurring jobs, but Mara's chapter recordings are not. */
    public static String storyMissionId(Mission mission) {
        if (mission == null) return null;
        if (mission.source == MissionSource.DEBUG) return debugStoryMissionId(DebugMissionCatalog.scenario(mission));
        if (mission.source != MissionSource.STORY) return null;
        for (OpeningOperationKind kind : OpeningOperationKind.values())
            if (kind.missionId.equals(mission.id)) return mission.id;
        if (mission.id == null) return null;
        if (AgreusArcCampaign.isBattle(mission)) return "story_agreus_water_under_seal";
        if(ShepherdRecoveryCampaign.isBattle(mission))return ShepherdRecoveryProgress.workingPassage(mission.id)?"story_shepherd_working_passage":ShepherdRecoveryProgress.coldStart(mission.id)
                ? "story_shepherd_cold_start" : "story_shepherd_salvage";
        if (CorvusWaylineProgress.forMission(mission.id) == CorvusChapter.COLD_ACCOUNT)
            return "story_corvus_cold_account";
        // Retry IDs vary; the historical chapter does not. Job zero excludes recurring work
        // even if a caller accidentally labels it STORY. Choice dossiers are not battle briefings.
        Matcher attempt = SUPPLY_STORY_ID.matcher(mission.id);
        if (attempt.matches()) return SUPPLY_CHAPTERS.get(Integer.parseInt(attempt.group(1)));
        return null;
    }

    /** A debug scenario replays a story chapter, so it hears that chapter's recordings; repeatable and event scenarios have none. */
    private static String debugStoryMissionId(DebugMissionCatalog.Scenario scenario) {
        if (scenario == null) return null;
        return switch (scenario) {
            case RELIEF -> OpeningOperationKind.RELIEF.missionId;
            case COUNTERATTACK -> OpeningOperationKind.COUNTERATTACK.missionId;
            case JUNCTION -> SUPPLY_CHAPTERS.get(BrokenSupplyRouteProgress.JUNCTION);
            case LAST_LIFT -> SUPPLY_CHAPTERS.get(BrokenSupplyRouteProgress.LIFT);
            case RELAY_NINE -> SUPPLY_CHAPTERS.get(BrokenSupplyRouteProgress.RELAY);
            case THUNDER_RUN -> SUPPLY_CHAPTERS.get(BrokenSupplyRouteProgress.THUNDER);
            case COLD_ACCOUNT -> "story_corvus_cold_account";
            case SALVAGE_RIGHTS -> "story_shepherd_salvage";
            case COLD_START -> "story_shepherd_cold_start";
            case WORKING_PASSAGE -> "story_shepherd_working_passage";
            default -> null;
        };
    }

    public Line find(String missionId, String event) { return lines.get(missionId + "/" + event); }
    public List<Line> lines() { return List.copyOf(lines.values()); }
}
