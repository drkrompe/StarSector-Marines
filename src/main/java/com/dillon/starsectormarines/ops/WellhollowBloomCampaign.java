package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.command.HivePosture;
import com.dillon.starsectormarines.battle.cordon.CordonPlan;
import com.dillon.starsectormarines.battle.cordon.CordonVariant;
import com.dillon.starsectormarines.battle.evacuation.SwarmDefenseRoster;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BloomHeartBattleFixture;
import com.dillon.starsectormarines.battle.fixture.CivilianRescueBattleFixture;
import com.dillon.starsectormarines.battle.fixture.CordonBattleFixture;
import com.dillon.starsectormarines.battle.setup.BloomHeartSetup;
import com.dillon.starsectormarines.battle.setup.CordonSetup;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMap;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPool;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPools;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.WellhollowBloomProgress;
import com.dillon.starsectormarines.campaign.WellhollowBloomProgress.Chapter;
import com.dillon.starsectormarines.i18n.Strings;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.RepLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * Campaign boundary for *The Khettara Bloom*, the authored Rav Bloom arc on Asharu
 * ({@code wellhollow-bloom-arc.md}). Colonel Bradley Orens, the Asharu Planetary Defense Force's field commander for the Khettara district, offers on his board the current chapter and a replay of
 * every chapter won. Projection never starts an attempt; launch begins one and only its own receipt
 * settles it. Independent of {@code RavBloomSystem}: no bloom, no campaign event, no clock.
 */
public final class WellhollowBloomCampaign {
    /** Orens's synthetic client: his board carries only this arc's work. The id predates the APDF framing and stays for saves. */
    public static final String CLIENT_FACTION_ID = "marines_wellhollow_compact_client";
    static final String SPEAKER = "@wellhollowSpeakerWarden";
    private static final String CASTLE = "@dialogueSpeakerCastle";
    private static final long SEED_BASE = 16100L;
    /**
     * Each chapter's first-briefing lines by number: Orens speaks the odd ones, Castle the even. Colony Breach
     * has nine (Orens naming the staging post, owner 2026-10-28); The Burn opens on Castle's question and Broad
     * Front ends on the spearhead lane (owner cuts, 2026-10-30). A cut number stays out so every recording keeps its id.
     */
    private static int[] briefingLines(Chapter chapter) {
        return switch (chapter) {
            case COLONY_BREACH -> IntStream.rangeClosed(1, 9).toArray();
            case CORDON_ROAD -> IntStream.rangeClosed(1, 7).toArray();
            case THE_BURN -> IntStream.rangeClosed(2, 7).toArray();
            case BROAD_FRONT -> IntStream.rangeClosed(1, 5).toArray();
        };
    }
    /** The end-of-alpha challenge force (owner, 2026-10-27): a lieutenant colonel's company with its machines. */
    public static final int RECOMMENDED_SQUADS = 7, RECOMMENDED_MACHINES = 4;

    private WellhollowBloomCampaign() { }

    /**
     * The ledger, or null until Morrow has departed: the arc is the alpha's end-game challenge
     * (owner, 2026-10-27). Old saves enter at chapter one.
     */
    public static WellhollowBloomProgress current() {
        if (Global.getSector() == null) return null;
        var script = CampaignStateScript.getInstance();
        if (script == null || script.state().shepherdRecovery == null || !script.state().shepherdRecovery.departed()) return null;
        return script.state().wellhollowBloom == null ? new WellhollowBloomProgress() : script.state().wellhollowBloom;
    }
    public static boolean owns(String id) { return WellhollowBloomProgress.owns(id); }
    public static boolean isBattle(Mission m) { return m != null && m.source == MissionSource.STORY && owns(m.id); }
    /** The Colony Breach chapter, launched through the rescue path. */
    public static boolean isColonyBreach(Mission m) {
        return isBattle(m) && WellhollowBloomProgress.chapterOf(m.id) == Chapter.COLONY_BREACH;
    }

    public static PlanetAPI site() {
        if (Global.getSector() == null) return null;
        var corvus = Global.getSector().getStarSystem("Corvus");
        if (corvus == null) return null;
        for (var planet : corvus.getPlanets()) if ("asharu".equals(planet.getId()) && !planet.isStar()) return planet;
        return null;
    }
    public static boolean atSite(PlanetAPI planet) {
        var site = site();
        return planet != null && site != null && planet.getId().equals(site.getId());
    }
    /** Whether Orens is on this planet's board. */
    public static boolean contactAt(PlanetAPI planet) { return atSite(planet) && current() != null; }
    public static Client client() {
        return new Client(CLIENT_FACTION_ID, Strings.get("wellhollowContactName"), OpeningBroker.CREST,
                RepLevel.NEUTRAL, false, null);
    }

    public static List<Mission> offers(PlanetAPI planet) {
        var p = current();
        if (p == null || p.active() || !atSite(planet)) return List.of();
        return offers(p, planet.getName());
    }
    /** The current chapter first, then a replay of each chapter won, latest first. */
    static List<Mission> offers(WellhollowBloomProgress p, String planetName) {
        List<Mission> out = new ArrayList<>();
        if (p.current() != null) out.add(offer(p, p.current(), false, planetName));
        for (int i = p.won() - 1; i >= 0; i--) out.add(offer(p, Chapter.values()[i], true, planetName));
        return List.copyOf(out);
    }

    static Mission offer(WellhollowBloomProgress p, Chapter chapter, boolean replay, String planetName) {
        String id = p.missionId(chapter, replay);
        String name = Strings.get("wellhollowChapter" + key(chapter));
        int fee = WellhollowBloomProgress.fee(id);
        boolean breach = chapter == Chapter.COLONY_BREACH;
        return Mission.builder().id(id).name(replay ? Strings.format("wellhollowReplayName", name) : name)
                .source(MissionSource.STORY)
                .type(breach ? MissionType.EXTRACTION : MissionType.ASSAULT)
                .tier(OperationTier.ESTABLISHED).risk(RiskLevel.HIGH)
                .payout(fee).targetPlanetName(planetName)
                .flavor(Strings.get("wellhollowFlavor" + key(chapter)))
                .dialogue(briefing(p, chapter, replay))
                .requirements(Strings.format("wellhollowPlace", chapter.number())
                        + Strings.format("wellhollowStatus", p.won(), p.replaysWon())
                        + (replay ? Strings.format("wellhollowReplayTerms", fee) : Strings.format("wellhollowTerms", fee))
                        + Strings.get("wellhollowForce"))
                .requiredDrops(breach ? 4 : 6).employerShuttles(0)
                .marineArrivalPolicy(MarineArrivalPolicy.PAIRED_HALF_SQUAD)
                .fieldPresencePolicy(FieldPresencePolicy.UNRESTRICTED)
                .battleSeed(seed(chapter, p.attempt())).build();
    }

    static String key(Chapter chapter) {
        return switch (chapter) {
            case COLONY_BREACH -> "ColonyBreach";
            case CORDON_ROAD -> "CordonRoad";
            case THE_BURN -> "TheBurn";
            case BROAD_FRONT -> "BroadFront";
        };
    }

    /** The chapter's recordings in {@code wellhollow_narration.json}: a replay hears its chapter's, as a debug run does. */
    public static String narrationMissionId(Chapter chapter) {
        return "story_wellhollow_" + chapter.name().toLowerCase(Locale.ROOT);
    }

    /**
     * First briefing, one retry line after a loss on this chapter, or one replay line. Each turn names
     * its recording, so a line whose string changed after recording plays as text until it is re-recorded.
     */
    static List<MissionDialogue.Turn> briefing(WellhollowBloomProgress p, Chapter chapter, boolean replay) {
        if (replay) return List.of(warden("@wellhollowReplay", "wellhollow_replay"));
        String stem = switch (chapter) {
            case COLONY_BREACH -> "Breach";
            case CORDON_ROAD -> "Road";
            case THE_BURN -> "Burn";
            case BROAD_FRONT -> "Front";
        };
        String sound = "wellhollow_" + stem.toLowerCase(Locale.ROOT) + "_";
        if (p.lastLost(chapter)) return List.of(warden("@wellhollow" + stem + "Retry", sound + "retry"));
        int[] lines = briefingLines(chapter);
        List<MissionDialogue.Turn> turns = new ArrayList<>(lines.length);
        for (int i : lines) {
            String text = "@wellhollow" + stem + i, id = sound + String.format(Locale.ROOT, "%02d", i);
            turns.add(i % 2 == 1 ? warden(text, id) : new MissionDialogue.Turn("castle", CASTLE, text, id));
        }
        return List.copyOf(turns);
    }
    private static MissionDialogue.Turn warden(String text, String sound) { return new MissionDialogue.Turn("warden", SPEAKER, text, sound); }

    /** The story map each chapter plays from on Asharu. */
    static StoryMap storyMap(Chapter chapter) {
        return switch (chapter) {
            case COLONY_BREACH -> StoryMap.COLONY_BREACH;
            case CORDON_ROAD -> CordonSetup.storyMap(CordonVariant.ROAD, density(chapter));
            case THE_BURN -> BloomHeartSetup.storyMap(HeartCountry.Form.THE_BURN, heartDensity());
            case BROAD_FRONT -> BloomHeartSetup.storyMap(HeartCountry.Form.BROAD_FRONT, heartDensity());
        };
    }
    /** Ain Tafrant is a town; the rest is open basin country. */
    private static CordonPlan.Density density(Chapter chapter) {
        return chapter == Chapter.CORDON_ROAD ? CordonPlan.Density.TOWN : CordonPlan.Density.RURAL;
    }
    private static HeartCountry.Density heartDensity() { return HeartCountry.Density.RURAL; }

    /**
     * A vetted ARID map for this attempt, cycling the chapter's pool so a replay is the same place on a
     * different day. Colony Breach picks its own pooled map from the roll at launch.
     */
    static long seed(Chapter chapter, int attempt) {
        long roll = SEED_BASE + 7919L * chapter.ordinal() + attempt;
        if (chapter == Chapter.COLONY_BREACH || !StoryMapPools.installed()) return roll;
        List<StoryMapPool.Entry> entries = StoryMapPools.pool(storyMap(chapter)).entries(SurfacePalette.ARID);
        return entries == null || entries.isEmpty() ? roll : entries.get(Math.floorMod(attempt, entries.size())).seed();
    }

    static WellhollowBloomProgress requireCurrent(Mission m) {
        var p = current();
        if (!isBattle(m) || p == null || !p.matches(m.id) || m.payout != WellhollowBloomProgress.fee(m.id)
                || !m.targetPlanetName.equals(site() == null ? null : site().getName()))
            throw new IllegalStateException("This Khettara contract is no longer current.");
        return p;
    }
    /** Asharu as the Rav maps read it: a desert world, the basin carried by each map's own setup. */
    static TargetProfile profile(Mission m) {
        requireCurrent(m);
        return BloomHeartSetup.world(SurfacePalette.ARID);
    }
    /** The Cordon or heart fixture for a chapter; Colony Breach goes through the rescue path instead. */
    static BattleFixture fixture(Mission m, long seed, List<ShuttleAssignment> manifest) {
        requireCurrent(m);
        Chapter chapter = WellhollowBloomProgress.chapterOf(m.id);
        if (chapter == Chapter.COLONY_BREACH) throw new IllegalStateException("Colony Breach launches through the rescue path");
        return fixture(chapter, seed, manifest);
    }
    /**
     * A chapter's battle on Asharu outside the campaign, for the debug player. Colony Breach builds the rescue a
     * campaign launch builds for this chapter, on the seed given rather than a pool pick from a roll.
     */
    static BattleFixture fixture(Chapter chapter, long seed, List<ShuttleAssignment> manifest) {
        return switch (chapter) {
            case CORDON_ROAD -> new CordonBattleFixture(seed, CordonVariant.ROAD, SurfacePalette.ARID, density(chapter), manifest);
            case THE_BURN -> new BloomHeartBattleFixture(seed, HeartCountry.Form.THE_BURN, SurfacePalette.ARID, heartDensity(), manifest);
            case BROAD_FRONT -> new BloomHeartBattleFixture(seed, HeartCountry.Form.BROAD_FRONT, SurfacePalette.ARID, heartDensity(), manifest);
            case COLONY_BREACH -> CivilianRescueBattleFixture.fromFactoryInputs(seed, manifest, false, RiskLevel.HIGH,
                    SwarmDefenseRoster.countFor(RiskLevel.HIGH), BloomHeartSetup.world(SurfacePalette.ARID), false, HivePosture.DEFAULT);
        };
    }
    /** The first vetted ARID map of the chapter's pool: the one map the debug player always plays. */
    static long fixedSeed(Chapter chapter) {
        long fallback = SEED_BASE + 7919L * chapter.ordinal();
        if (!StoryMapPools.installed()) return fallback;
        List<StoryMapPool.Entry> entries = StoryMapPools.pool(storyMap(chapter)).entries(SurfacePalette.ARID);
        return entries == null || entries.isEmpty() ? fallback : entries.get(0).seed();
    }
    static void begin(Mission m) {
        var p = requireCurrent(m);
        if (!p.begin(m.id)) throw new IllegalStateException("This Khettara contract has already launched.");
        CampaignStateScript.getInstance().state().wellhollowBloom = p;
    }
    static void abandon(Mission m) {
        var p = isBattle(m) ? current() : null;
        if (p != null) p.abandon(m.id);
    }
    static int settle(MissionOutcome o) {
        var script = Global.getSector() == null ? null : CampaignStateScript.getInstance();
        var p = script == null ? null : script.state().wellhollowBloom;
        if (p == null || o == null || o.missionSource != MissionSource.STORY || !owns(o.missionId)) return -1;
        return p.settle(o.missionId, o.victory, o.payoutEarned);
    }

    /** Where this result leaves the arc, read from the outcome alone: the chapter, and whether it is won. */
    static String progress(MissionOutcome o) {
        Chapter chapter = WellhollowBloomProgress.chapterOf(o.missionId);
        return Strings.format(o.victory && !WellhollowBloomProgress.replay(o.missionId)
                ? "wellhollowResultWon" : "wellhollowResultOpen", chapter.number());
    }

    /** Results-screen line from Orens: what changed, or what still stands. */
    static String debrief(MissionOutcome o) {
        Chapter chapter = WellhollowBloomProgress.chapterOf(o.missionId);
        if (!o.victory) return Strings.get("wellhollowDebriefLost");
        if (WellhollowBloomProgress.replay(o.missionId)) return Strings.get("wellhollowDebriefWonReplay");
        return Strings.get("wellhollowDebriefWon" + key(chapter));
    }
}
