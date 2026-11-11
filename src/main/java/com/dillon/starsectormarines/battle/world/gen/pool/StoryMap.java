package com.dillon.starsectormarines.battle.world.gen.pool;

/** A story map that plays from a pool rather than from any seed ({@code story-map-pools.md}). */
public enum StoryMap {
    CORDON_LINE("cordon-line"),
    CORDON_ROAD("cordon-road"),
    CORDON_LINE_TOWN("cordon-line-town"),
    CORDON_ROAD_TOWN("cordon-road-town"),
    CORDON_LINE_URBAN("cordon-line-urban"),
    CORDON_ROAD_URBAN("cordon-road-urban"),
    HEART_BURN("heart-burn"),
    HEART_FRONT("heart-front"),
    HEART_BURN_TOWN("heart-burn-town"),
    HEART_FRONT_TOWN("heart-front-town"),
    HEART_BURN_CITY("heart-burn-city"),
    HEART_FRONT_CITY("heart-front-city"),
    /** Colony Breach's colony, one map per biome while the mission is tuned (owner, 2026-10-26). */
    COLONY_BREACH("colony-breach"),
    /**
     * The generic Raid's map ({@code raid-mission.md}, The owned map): a raid for valuables, and a raid on an industry with no site
     * of its own. Every Raid map below is laid on the reference market, not the raided one.
     */
    RAID("raid"),
    /** Each industry's Raid site ({@code RaidSiteTemplate}), one owned map per site. */
    RAID_HEAVY_INDUSTRY("raid-heavy-industry"),
    RAID_MINING("raid-mining"),
    RAID_REFINING("raid-refining"),
    RAID_LIGHT_INDUSTRY("raid-light-industry"),
    RAID_FARMING("raid-farming"),
    RAID_SPACEPORT("raid-spaceport"),
    RAID_MILITARY("raid-military"),
    /**
     * The Long Survey's chapters ({@code long-survey-arc.md}, Seeds): each a shipped mission's ground on an owned seed
     * with a fallen hull laid over it by its patch. A chapter's map names the story map whose ground it is built on,
     * or none where it owns ground of its own (Stakes, a Sabotage country).
     */
    LONG_SURVEY_FALL("long-survey-fall", COLONY_BREACH),
    LONG_SURVEY_STAKES("long-survey-stakes", null),
    LONG_SURVEY_LINE("long-survey-line", HEART_FRONT_CITY),
    LONG_SURVEY_SURVEYOR("long-survey-surveyor", HEART_FRONT);

    /** The pool file's name and the {@code map} it declares. */
    public final String id;
    /** The story map whose ground a Long Survey chapter is laid on, or null. */
    public final StoryMap base;

    StoryMap(String id) { this(id, null); }

    StoryMap(String id, StoryMap base) {
        this.id = id;
        this.base = base;
    }

    /**
     * Whether this map's battles may be fought at night ({@code TownNight}): a Raid's town may be raided after dark;
     * the other story maps keep to the day until their story says otherwise.
     */
    public boolean nightFalls() {
        return id.equals("raid") || id.startsWith("raid-");
    }

    /** Whether this is one of the Long Survey's chapters, a fallen hull laid over its ground. */
    public boolean longSurvey() { return id.startsWith("long-survey-"); }

    /** The pool file, relative to the mod folder. */
    public String path() { return "data/world/pools/" + id + ".pool.json"; }
}
