package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.drone.FlockLivery;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.HullFallBattleFixture;
import com.dillon.starsectormarines.battle.setup.BloomHeartSetup;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMap;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPools;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.EndGameBranches;
import com.dillon.starsectormarines.campaign.LongSurveyProgress;
import com.dillon.starsectormarines.campaign.LongSurveyProgress.Chapter;
import com.dillon.starsectormarines.campaign.backend.CampaignBackend;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;
import com.dillon.starsectormarines.i18n.Strings;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.RepLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Campaign boundary for *The Long Survey*, the authored fallen-hull arc ({@code long-survey-arc.md}). Garnir's
 * free-port defence chief offers on the port's board the current chapter and a replay of every chapter won, in the
 * Khettara's pattern ({@code wellhollow-bloom-arc.md}): a story source with its own ledger, no event gate, no clock.
 * Projection never starts an attempt; launch begins one and only its own receipt settles it. A chapter whose battle
 * is not built yet ({@link Chapter#built}) is never offered.
 */
public final class LongSurveyCampaign {
    /** The defence chief's synthetic client: the board carries only this arc's work. */
    public static final String CLIENT_FACTION_ID = "marines_long_survey_client";
    static final String SPEAKER = "@longSurveySpeakerCommander";
    private static final String CASTLE = "@dialogueSpeakerCastle";
    /**
     * The arc's world (owner, 2026-11-11): Garnir in Corvus, the pirate free port, beside Asharu's Khettara Bloom.
     * The one place the world is named.
     */
    static final String SYSTEM = "Corvus", PLANET_ID = "corvus_IIIa";
    /**
     * The world the chapters are fought on: Garnir, airless and cold, as A Cold Account plays it. Each owned map is
     * laid out on its pool's home palette and skinned for this one.
     */
    static final SurfacePalette SURFACE = SurfacePalette.FROZEN;
    /**
     * The Fall's map: the Colony Breach colony's seed with the Surveyor down beside the town (`long-survey-arc.md`,
     * Seeds; the {@code long-survey-fall} pool). The pool is read for its seed; this is the seed when no pool is installed.
     */
    static final StoryMap THE_FALL_MAP = StoryMap.LONG_SURVEY_FALL;
    static final long THE_FALL_SEED = 38L;
    /**
     * The hull's livery, and the grade a site network is laid at where the ground carries no hull (no pool installed):
     * the fallback the battle keeps (UNTUNED).
     */
    static final RuinsGrade THE_FALL_GRADE = RuinsGrade.EXTENSIVE;
    static final FlockLivery LIVERY = FlockLivery.DOMAIN;
    /** The end-game challenge force, as the Khettara's: seven squads and four machines. */
    public static final int RECOMMENDED_SQUADS = 7, RECOMMENDED_MACHINES = 4;

    private LongSurveyCampaign() { }

    /**
     * The ledger, or null until the end-game branches open beside the Khettara ({@link EndGameBranches}). Old saves
     * enter at chapter one.
     */
    public static LongSurveyProgress current() {
        var script = CampaignStateScript.getInstance();
        if (script == null || !EndGameBranches.open(script.state())) return null;
        return script.state().longSurvey == null ? new LongSurveyProgress() : script.state().longSurvey;
    }
    public static boolean owns(String id) { return LongSurveyProgress.owns(id); }
    public static boolean isBattle(Mission m) { return m != null && m.source == MissionSource.STORY && owns(m.id); }

    public static PlanetAPI site() {
        var system = CampaignBackend.starSystem(SYSTEM);
        if (system == null) return null;
        for (var planet : system.getPlanets()) if (PLANET_ID.equals(planet.getId()) && !planet.isStar()) return planet;
        return null;
    }
    public static boolean atSite(PlanetAPI planet) {
        var site = site();
        return planet != null && site != null && planet.getId().equals(site.getId());
    }
    /** Whether the defence chief is on this planet's board. */
    public static boolean contactAt(PlanetAPI planet) { return atSite(planet) && current() != null; }
    public static Client client() {
        return new Client(CLIENT_FACTION_ID, Strings.get("longSurveyContactName"), OpeningBroker.CREST,
                RepLevel.NEUTRAL, false, null);
    }

    public static List<Mission> offers(PlanetAPI planet) {
        var p = current();
        if (p == null || p.active() || !atSite(planet)) return List.of();
        return offers(p, planet.getName());
    }
    /** The current chapter first where its battle is built, then a replay of each chapter won, latest first. */
    static List<Mission> offers(LongSurveyProgress p, String planetName) {
        List<Mission> out = new ArrayList<>();
        if (p.current() != null && p.current().built) out.add(offer(p, p.current(), false, planetName));
        for (int i = p.won() - 1; i >= 0; i--) {
            Chapter chapter = Chapter.values()[i];
            if (chapter.built) out.add(offer(p, chapter, true, planetName));
        }
        return List.copyOf(out);
    }

    static Mission offer(LongSurveyProgress p, Chapter chapter, boolean replay, String planetName) {
        String id = p.missionId(chapter, replay);
        String name = Strings.get("longSurveyChapter" + key(chapter));
        int fee = LongSurveyProgress.fee(id);
        return Mission.builder().id(id).name(replay ? Strings.format("longSurveyReplayName", name) : name)
                .source(MissionSource.STORY)
                .type(type(chapter))
                .tier(OperationTier.ESTABLISHED).risk(RiskLevel.HIGH)
                .payout(fee).targetPlanetName(planetName)
                .flavor(Strings.get("longSurveyFlavor" + key(chapter)))
                .dialogue(briefing(p, chapter, replay))
                .requirements(Strings.format("longSurveyPlace", chapter.number())
                        + Strings.format("longSurveyStatus", p.won(), p.replaysWon())
                        + (replay ? Strings.format("longSurveyReplayTerms", fee) : Strings.format("longSurveyTerms", fee))
                        + Strings.get("longSurveyForce"))
                .requiredDrops(chapter == Chapter.THE_FALL ? 4 : 6).employerShuttles(0)
                .marineArrivalPolicy(MarineArrivalPolicy.PAIRED_HALF_SQUAD)
                .fieldPresencePolicy(FieldPresencePolicy.UNRESTRICTED)
                .battleSeed(fixedSeed(chapter)).build();
    }

    /** The mission family each chapter is fought as (`long-survey-arc.md`, Chapters). */
    static MissionType type(Chapter chapter) {
        return switch (chapter) {
            case THE_FALL -> MissionType.EXTRACTION;
            case STAKES -> MissionType.SABOTAGE;
            case THE_LINE, THE_SURVEYOR -> MissionType.ASSAULT;
        };
    }

    static String key(Chapter chapter) {
        return switch (chapter) {
            case THE_FALL -> "TheFall";
            case STAKES -> "Stakes";
            case THE_LINE -> "TheLine";
            case THE_SURVEYOR -> "TheSurveyor";
        };
    }

    /** Each chapter's first-briefing lines by number: the commander speaks the odd ones, Castle the even. */
    private static int[] briefingLines(Chapter chapter) {
        return switch (chapter) {
            case THE_FALL -> IntStream.rangeClosed(1, 7).toArray();
            case STAKES, THE_LINE, THE_SURVEYOR -> IntStream.rangeClosed(1, 3).toArray();
        };
    }

    /** First briefing, one retry line after a loss on this chapter, or one replay line. Unvoiced yet. */
    static List<MissionDialogue.Turn> briefing(LongSurveyProgress p, Chapter chapter, boolean replay) {
        if (replay) return List.of(commander("@longSurveyReplay"));
        String stem = "@longSurvey" + key(chapter);
        if (p.lastLost(chapter)) return List.of(commander(stem + "Retry"));
        int[] lines = briefingLines(chapter);
        List<MissionDialogue.Turn> turns = new ArrayList<>(lines.length);
        for (int i : lines) {
            String text = stem + i;
            turns.add(i % 2 == 1 ? commander(text) : new MissionDialogue.Turn("castle", CASTLE, text));
        }
        return List.copyOf(turns);
    }
    private static MissionDialogue.Turn commander(String text) {
        return new MissionDialogue.Turn("garrison", SPEAKER, text);
    }

    /**
     * The one map a chapter plays, on every attempt, retry, replay and debug run: its owned seed. The Fall's is its
     * pool's owned map; the later chapters' seeds are the ground session's, and a chapter not built has none yet.
     */
    static long fixedSeed(Chapter chapter) {
        if (chapter != Chapter.THE_FALL) return THE_FALL_SEED + 7919L * chapter.ordinal();
        return StoryMapPools.installed() ? StoryMapPools.pool(THE_FALL_MAP).story(SURFACE).seed() : THE_FALL_SEED;
    }

    static LongSurveyProgress requireCurrent(Mission m) {
        var p = current();
        if (!isBattle(m) || p == null || !p.matches(m.id) || m.payout != LongSurveyProgress.fee(m.id)
                || !m.targetPlanetName.equals(site() == null ? null : site().getName()))
            throw new IllegalStateException("This Long Survey contract is no longer current.");
        return p;
    }
    /** The world as the chapters read it. */
    static TargetProfile profile(Mission m) {
        requireCurrent(m);
        return world();
    }
    static TargetProfile world() { return BloomHeartSetup.world(SURFACE); }

    /** A campaign chapter's battle, on its own seed whatever the launch rolled. */
    static BattleFixture fixture(Mission m, List<ShuttleAssignment> manifest) {
        requireCurrent(m);
        return fixture(LongSurveyProgress.chapterOf(m.id), manifest);
    }
    /** A chapter's battle outside the campaign, for the debug player: the same fixture a launch builds. */
    static BattleFixture fixture(Chapter chapter, List<ShuttleAssignment> manifest) {
        if (!chapter.built) throw new IllegalStateException(chapter + " is not built yet");
        return switch (chapter) {
            case THE_FALL -> new HullFallBattleFixture(fixedSeed(chapter), manifest, world(), THE_FALL_MAP,
                    THE_FALL_GRADE, LIVERY);
            case STAKES, THE_LINE, THE_SURVEYOR -> throw new IllegalStateException(chapter + " is not built yet");
        };
    }

    static void begin(Mission m) {
        var p = requireCurrent(m);
        if (!p.begin(m.id)) throw new IllegalStateException("This Long Survey contract has already launched.");
        CampaignStateScript.getInstance().state().longSurvey = p;
    }
    static void abandon(Mission m) {
        var p = isBattle(m) ? current() : null;
        if (p != null) p.abandon(m.id);
    }
    static int settle(MissionOutcome o) {
        var script = CampaignStateScript.getInstance();
        var p = script == null ? null : script.state().longSurvey;
        if (p == null || o == null || o.missionSource != MissionSource.STORY || !owns(o.missionId)) return -1;
        return p.settle(o.missionId, o.victory, o.payoutEarned);
    }

    /** Where this result leaves the arc, read from the outcome alone: the chapter, and whether it is won. */
    static String progress(MissionOutcome o) {
        Chapter chapter = LongSurveyProgress.chapterOf(o.missionId);
        return Strings.format(o.victory && !LongSurveyProgress.replay(o.missionId)
                ? "longSurveyResultWon" : "longSurveyResultOpen", chapter.number());
    }

    /** Results-screen line from the commander: what changed, or what still stands. */
    static String debrief(MissionOutcome o) {
        Chapter chapter = LongSurveyProgress.chapterOf(o.missionId);
        if (!o.victory) return Strings.get("longSurveyDebriefLost");
        if (LongSurveyProgress.replay(o.missionId)) return Strings.get("longSurveyDebriefWonReplay");
        return Strings.get("longSurveyDebriefWon" + key(chapter));
    }
}
