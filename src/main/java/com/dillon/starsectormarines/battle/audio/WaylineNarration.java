package com.dillon.starsectormarines.battle.audio;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.CompletedReplays;
import com.dillon.starsectormarines.ops.DebugMissionCatalog;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.Mission;
import com.dillon.starsectormarines.ops.MissionSource;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.campaign.CorvusWaylineProgress;
import com.dillon.starsectormarines.ops.CorvusChapter;
import com.dillon.starsectormarines.ops.ShepherdRecoveryCampaign;
import com.dillon.starsectormarines.ops.AgreusArcCampaign;
import com.dillon.starsectormarines.ops.WellhollowBloomCampaign;
import com.dillon.starsectormarines.campaign.WellhollowBloomProgress;
import com.dillon.starsectormarines.campaign.AgreusArcProgress;
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
    /** Per-feature catalogs of the same shape, merged into the built-ins; a missing one costs only its own lines. */
    public static final List<String> FEATURE_PATHS = List.of("data/marines/colony_breach_narration.json",
            "data/marines/wellhollow_narration.json", "data/marines/cordon_road_narration.json");
    /** The Colony Breach catalog's mission id: the breach is a campaign event, not a story chapter, so it is keyed by the battle. */
    public static final String COLONY_BREACH = "story_colony_breach";
    private static final Pattern SUPPLY_ROUTE_ID = Pattern.compile(
            Pattern.quote(BrokenSupplyRouteProgress.PREFIX) + "([1-4])_(0|[1-9][0-9]*)_[1-9][0-9]*");
    private static final Map<Integer, String> SUPPLY_CHAPTERS = Map.of(
            BrokenSupplyRouteProgress.JUNCTION, "story_supply_route_junction",
            BrokenSupplyRouteProgress.LIFT, "story_supply_route_last_lift",
            BrokenSupplyRouteProgress.RELAY, "story_supply_route_relay_nine",
            BrokenSupplyRouteProgress.THUNDER, "story_supply_route_thunder_run");
    /** A numbered beat's trailing index, which a catalog may answer once for every index as {@code _N}. */
    private static final Pattern NUMBERED_EVENT = Pattern.compile("_[0-9]+$");
    private static WaylineNarration builtins = new WaylineNarration(List.of());
    private final Map<String, Line> lines = new LinkedHashMap<>();
    private final Map<String, List<Line>> briefings = new LinkedHashMap<>();

    public record Line(String id, String missionId, String event, String text,
                       float durationSeconds, int priority, float expiresSeconds,
                       List<String> supersedes, String speaker, String portrait,
                       boolean voiced, boolean terminal, boolean interrupts) {
        /** Resolve at presentation time so catalog loading does not freeze a translation. */
        public String text() { return Strings.resolve(text); }
        /** A speaker may be an {@code @key} reference, resolved at presentation time like the text. */
        public String speaker() { return Strings.resolve(speaker); }

        public Line(String id, String missionId, String event, String text,
                    float durationSeconds, int priority, float expiresSeconds, List<String> supersedes,
                    String speaker, String portrait, boolean voiced, boolean terminal) {
            this(id, missionId, event, text, durationSeconds, priority, expiresSeconds, supersedes,
                    speaker, portrait, voiced, terminal, false);
        }
        public Line(String id, String missionId, String event, String text,
                    float durationSeconds, int priority, float expiresSeconds, List<String> supersedes,
                    String speaker, String portrait) {
            this(id, missionId, event, text, durationSeconds, priority, expiresSeconds, supersedes,
                    speaker, portrait, true, false);
        }
        public Line(String id, String missionId, String event, String text,
                    float durationSeconds, int priority, float expiresSeconds, List<String> supersedes) {
            this(id, missionId, event, text, durationSeconds, priority, expiresSeconds, supersedes,
                    "ZOEY VENN / WAYLINE", "graphics/portraits/mara-venn.png");
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
            if (line.event().equals("briefing") || line.event().startsWith("briefing."))
                briefings.computeIfAbsent(line.missionId(), ignored -> new ArrayList<>()).add(line);
        }
        briefings.replaceAll((missionId, turns) -> List.copyOf(turns));
    }

    public static WaylineNarration builtins() { return builtins; }

    public static void loadBuiltins() {
        try {
            WaylineNarration merged = parse(Global.getSettings().loadJSON(PATH));
            for (String path : FEATURE_PATHS) {
                try {
                    merged = merged.with(parse(Global.getSettings().loadJSON(path)));
                } catch (Exception failure) {
                    Global.getLogger(WaylineNarration.class).warn("Narration catalog unavailable: " + path, failure);
                }
            }
            builtins = merged;
        } catch (Exception failure) {
            builtins = new WaylineNarration(List.of());
            Global.getLogger(WaylineNarration.class).warn("Wayline narration unavailable; text remains enabled", failure);
        }
    }

    /** This catalog's lines followed by another's; a duplicate mission event is rejected as in the constructor. */
    public WaylineNarration with(WaylineNarration other) {
        List<Line> all = new ArrayList<>(lines.values());
        all.addAll(other.lines.values());
        return new WaylineNarration(all);
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
                    entry.getString("event"), entry.optString("textKey", "").isBlank()
                            ? entry.getString("text") : "@" + entry.getString("textKey"),
                    (float) entry.getDouble("durationSeconds"), entry.optInt("priority", 10),
                    (float) entry.optDouble("expiresSeconds", 15), supersedes,
                    entry.optString("speaker", "ZOEY VENN / WAYLINE"),
                    entry.optString("portrait", "graphics/portraits/mara-venn.png"),
                    entry.optBoolean("voiced", true), entry.optBoolean("terminal", false),
                    entry.optBoolean("interrupts", false)));
        }
        return new WaylineNarration(result);
    }

    /**
     * The catalog a battle's beats are looked up in: Colony Breach's when the battle carries the breach
     * sequence, whichever mission launched it, else its story chapter's. A chapter flown as a breach keeps its
     * own id for the pre-mission briefing only, since {@link #storyMissionId} is what the briefing reads.
     */
    public static String narrationMissionId(Mission mission, BattleSimulation sim) {
        return narrationMissionId(storyMissionId(mission), sim != null && sim.getColonyBreach() != null);
    }

    static String narrationMissionId(String story, boolean colonyBreach) {
        return colonyBreach ? COLONY_BREACH : story;
    }

    /**
     * The chapter a mission flies, whatever its source: a retry, repeat, replay or debug run hears the story's
     * lines wherever a story version exists. Source decides what a mission settles, never what it says. Depot
     * Clearance and Route Security are the two exceptions (owner): their jobs keep their own written terms, since
     * the story's lines speak of a Hound the depot job lacks and of a first trip to Relay Nine.
     */
    public static String storyMissionId(Mission mission) {
        if (mission == null || mission.id == null) return null;
        DebugMissionCatalog.Scenario scenario = DebugMissionCatalog.scenario(mission);
        if (scenario == null) scenario = CompletedReplays.scenario(mission);
        if (scenario != null) return scenarioMissionId(scenario);
        for (OpeningOperationKind kind : OpeningOperationKind.values())
            if (kind.missionId.equals(mission.id)
                    || kind != OpeningOperationKind.COUNTERATTACK && kind.recurringId.equals(mission.id)) return kind.missionId;
        if (WellhollowBloomCampaign.owns(mission.id))
            return WellhollowBloomCampaign.narrationMissionId(WellhollowBloomProgress.chapterOf(mission.id));
        if (AgreusArcCampaign.owns(mission.id)) return agreusMissionId(mission.id);
        if (ShepherdRecoveryCampaign.owns(mission.id)) return ShepherdRecoveryProgress.workingPassage(mission.id)
                ? "story_shepherd_working_passage" : ShepherdRecoveryProgress.coldStart(mission.id)
                ? "story_shepherd_cold_start" : "story_shepherd_salvage";
        CorvusChapter corvus = CorvusWaylineProgress.forMission(mission.id);
        if (corvus != null) return switch (corvus) {
            case DUST_LEDGER -> "story_corvus_dust_ledger";
            case BLIND_WATCH -> "story_corvus_blind_watch";
            case COLD_ACCOUNT -> "story_corvus_cold_account";
        };
        Matcher attempt = SUPPLY_ROUTE_ID.matcher(mission.id);
        if (!attempt.matches()) return null;
        int chapter = Integer.parseInt(attempt.group(1));
        boolean routeSecurity = chapter == BrokenSupplyRouteProgress.RELAY && !attempt.group(2).equals("0");
        return routeSecurity ? null : SUPPLY_CHAPTERS.get(chapter);
    }

    private static String agreusMissionId(String id) {
        return AgreusArcProgress.smallWar(id) ? "story_agreus_small_war"
                : AgreusArcProgress.termsOfProtection(id) ? "story_agreus_terms_of_protection"
                : AgreusArcProgress.yardOwners(id) ? "story_agreus_yard_owners"
                : AgreusArcProgress.limitedWarrant(id) ? "story_agreus_limited_warrant"
                : "story_agreus_water_under_seal";
    }

    /** The chapter a debug or replay scenario flies, or null for one with no story version. */
    private static String scenarioMissionId(DebugMissionCatalog.Scenario scenario) {
        if (scenario.wellhollow() != null) return WellhollowBloomCampaign.narrationMissionId(scenario.wellhollow());
        return switch (scenario) {
            case RELIEF, MILITIA_RELIEF -> OpeningOperationKind.RELIEF.missionId;
            case COUNTERATTACK -> OpeningOperationKind.COUNTERATTACK.missionId;
            case JUNCTION -> SUPPLY_CHAPTERS.get(BrokenSupplyRouteProgress.JUNCTION);
            case LAST_LIFT, CREW_RECOVERY -> SUPPLY_CHAPTERS.get(BrokenSupplyRouteProgress.LIFT);
            case RELAY_NINE -> SUPPLY_CHAPTERS.get(BrokenSupplyRouteProgress.RELAY);
            case THUNDER_RUN -> SUPPLY_CHAPTERS.get(BrokenSupplyRouteProgress.THUNDER);
            case DUST_LEDGER -> "story_corvus_dust_ledger";
            case BLIND_WATCH -> "story_corvus_blind_watch";
            case COLD_ACCOUNT -> "story_corvus_cold_account";
            case SALVAGE_RIGHTS -> "story_shepherd_salvage";
            case COLD_START -> "story_shepherd_cold_start";
            case WORKING_PASSAGE -> "story_shepherd_working_passage";
            case WATER_UNDER_SEAL -> "story_agreus_water_under_seal";
            case LIMITED_WARRANT -> "story_agreus_limited_warrant";
            case YARD_OWNERS -> "story_agreus_yard_owners";
            case TERMS_OF_PROTECTION -> "story_agreus_terms_of_protection";
            case SMALL_WAR -> "story_agreus_small_war";
            case CORDON_ROAD -> WellhollowBloomCampaign.narrationMissionId(WellhollowBloomProgress.Chapter.CORDON_ROAD);
            case THE_BURN -> WellhollowBloomCampaign.narrationMissionId(WellhollowBloomProgress.Chapter.THE_BURN);
            case BROAD_FRONT -> WellhollowBloomCampaign.narrationMissionId(WellhollowBloomProgress.Chapter.BROAD_FRONT);
            default -> null;
        };
    }

    /**
     * The line for one beat: its own entry, else the numbered beat's catch-all ({@code cordon.tide_3} falls back to
     * {@code cordon.tide_N}), so a catalog voices the first instance and the rest without listing every index.
     */
    public Line find(String missionId, String event) {
        Line exact = lines.get(missionId + "/" + event);
        if (exact != null || event == null) return exact;
        Matcher numbered = NUMBERED_EVENT.matcher(event);
        return numbered.find() ? lines.get(missionId + "/" + event.substring(0, numbered.start()) + "_N") : null;
    }
    /** Ordered turns supersede a whole-briefing recording in the pre-mission UI only. */
    public List<Line> briefing(String missionId) {
        var turns = briefings.getOrDefault(missionId, List.of());
        return turns.stream().anyMatch(line -> line.event().startsWith("briefing."))
                ? turns.stream().filter(line -> !line.event().equals("briefing")).toList() : turns;
    }
    public List<Line> lines() { return List.copyOf(lines.values()); }
}
