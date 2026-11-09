package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.MapDistrictTheme;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.fortress.RaidWorksPrograms;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.IntFunction;

/**
 * An authored Raid site for one industry ({@code raid-mission.md}, Site templates; owner pick {@code raid-mapgen}:
 * site templates): where the works, the garrison, the homes and the landing stand relative to each other, written
 * once and laid at every map size.
 *
 * <p>The template is drawn with the company arriving from the west. The landing band is the west edge; the homes
 * are the near town the landing comes down in, between the landing and everything worth raiding; an industrial
 * quarter of yards and sheds stands between the homes and the works, so the homes never reach the compound; the
 * works compound stands at the back of that industrial district, at the deep end, with the district's yards
 * beyond it, walled or fenced by the market's {@link Security} with a buffer of open
 * ground round it; the garrison stands at the deep end too, on the works' other flank, so the alarm comes from
 * beside the works and not from behind the landing. Districting is the point: nothing worth stealing or breaking
 * stands among the homes.
 *
 * <p>The seed varies it: the whole site mirrored east to west (the company arriving from the east) and north to
 * south (the works north or south of the garrison), and each place seeded anywhere in its region. The market
 * varies it by size: a bigger market has a town's streets rather than a hamlet's and, from {@link #OUTSKIRTS_FROM},
 * an outskirts suburb on the landing side, behind the homes and away from everything worth raiding.
 *
 * <p>The industry changes what the works holds and how thick the industry round it stands: heavy industry's works is
 * machine halls in a dense industrial quarter; a mine's is its head works, a winding house over each shaft and the
 * ore mill they feed, in sparse ore yards; a refinery's is its stills; light industry's its shop floors; a farm's
 * its silos and packing shed among farmsteads; a port's its cargo terminal; a base's its motor pool and magazine
 * among depots, with the garrison beside it as on every site.
 *
 * <p>Raid only. Every other mission's map is derived as it was.
 */
public final class RaidSiteTemplate {

    /** The works compound: the raid's objective, listed first so it is the plan's {@link PrecinctPlan#objective()}. */
    public static final String WORKS = "works";
    public static final String GARRISON = "garrison";
    public static final String HOMES = "homes";
    /** The industrial quarter the works stands at the back of: yards and sheds between it and the homes. */
    public static final String INDUSTRY = "industry";
    /** The yards beyond the works: the industrial district's far side, so the deep end is built and not empty. */
    public static final String YARDS = "yards";
    public static final String OUTSKIRTS = "outskirts";

    /** Market size from which the site has an outskirts suburb behind the homes. UNTUNED. */
    static final int OUTSKIRTS_FROM = 6;

    /** Market size from which the homes grow a town's streets rather than a hamlet's. UNTUNED. */
    static final int TOWN_FROM = 5;

    /**
     * How hard a market keeps what it is worth ({@code raid-mission.md}, Security): the wall round its compounds and
     * the open ground before it. It never changes how many defenders there are, only what they stand behind.
     */
    public enum Security {
        /** A fence and wide gates, a narrow strip of cleared ground. */
        OPEN(Fortification.Strength.PICKET, 3),
        /** A wall with gatehouses and posts on the gates, a wider strip. */
        GUARDED(Fortification.Strength.GARRISON, 5),
        /** A thick wall with emplacements, and a wide cleared field before it. */
        FORTIFIED(Fortification.Strength.STRONGHOLD, 8);

        private final Fortification.Strength wall;
        private final int buffer;

        Security(Fortification.Strength wall, int buffer) {
            this.wall = wall;
            this.buffer = buffer;
        }

        /** The rung of wall a compound at this security is built to, before the operation's ceiling. */
        public Fortification.Strength wall() { return wall; }

        /** Cells of open ground round a compound at this security. UNTUNED. */
        public int buffer() { return buffer; }

        /**
         * The market's security: fortified with heavy batteries or a defence rating of five or more, guarded with
         * ground defences or a rating of two or more, otherwise open.
         */
        public static Security of(TargetProfile profile) {
            if (profile.heavyBatteries() || profile.defenseLevel() >= 5) return FORTIFIED;
            if (profile.groundDefences() || profile.defenseLevel() >= 2) return GUARDED;
            return OPEN;
        }
    }

    // Regions as fractions of the map, with the company landing in the west and the works to the north
    // (MapPlacement's y is up). UNTUNED.
    static final MapPlacement LANDING_FROM = new MapPlacement(0f, 0.25f, 0.30f, 0.75f);
    static final MapPlacement HOMES_AT = new MapPlacement(0.14f, 0.32f, 0.32f, 0.68f);
    static final MapPlacement INDUSTRY_AT = new MapPlacement(0.48f, 0.50f, 0.62f, 0.80f);
    static final MapPlacement YARDS_AT = new MapPlacement(0.86f, 0.62f, 0.97f, 0.94f);
    static final MapPlacement WORKS_AT = new MapPlacement(0.68f, 0.58f, 0.86f, 0.80f);
    static final MapPlacement GARRISON_AT = new MapPlacement(0.70f, 0.14f, 0.90f, 0.36f);
    static final MapPlacement OUTSKIRTS_AT = new MapPlacement(0.04f, 0.04f, 0.22f, 0.24f);

    /**
     * The works' own roads: one junction and short arms, enough to reach the town's streets through its gates and
     * no more, as a civil-defence compound's are.
     */
    private static final GrownTrunkPlan.Profile WORKS_GROWTH =
            new GrownTrunkPlan.Profile(0, 0f, 0.04f, 0.06f, 0f, 4, SettlementLink.NONE);

    /** Share of the map a works may claim before its program is trimmed. UNTUNED. */
    static final float WORKS_FIT = 0.06f;

    /** Share of the map a mine's head works may claim: its shafts stand apart on wide ore yards. UNTUNED. */
    static final float MINE_FIT = 0.08f;

    private static final Set<String> HEAVY_INDUSTRY = Set.of("heavyindustry", "orbitalworks");
    private static final Set<String> MINING = Set.of("mining", "techmining");
    private static final Set<String> REFINING = Set.of("refining", "fuelprod");
    private static final Set<String> LIGHT_INDUSTRY = Set.of("lightindustry");
    private static final Set<String> FARMING = Set.of("farming", "aquaculture");
    private static final Set<String> PORT = Set.of("spaceport", "megaport");
    private static final Set<String> MILITARY = Set.of("militarybase", "patrolhq", "highcommand");

    private final String key;
    private final IntFunction<FortressProgram> works;
    private final float worksFit;
    private final GrownTrunkPlan.Profile industryGrowth;
    private final PrecinctCharacter industryCharacter;

    private RaidSiteTemplate(String key, IntFunction<FortressProgram> works, float worksFit,
                             GrownTrunkPlan.Profile industryGrowth) {
        this(key, works, worksFit, industryGrowth, PrecinctCharacter.INDUSTRY);
    }

    private RaidSiteTemplate(String key, IntFunction<FortressProgram> works, float worksFit,
                             GrownTrunkPlan.Profile industryGrowth, PrecinctCharacter industryCharacter) {
        this.key = key;
        this.works = works;
        this.worksFit = worksFit;
        this.industryGrowth = industryGrowth;
        this.industryCharacter = industryCharacter;
    }

    /** The template for an industry, by its id, or {@code null} for one that has none yet. */
    public static RaidSiteTemplate forIndustry(String industryId) {
        if (industryId == null) return null;
        String id = industryId.toLowerCase(Locale.ROOT);
        if (HEAVY_INDUSTRY.contains(id)) {
            return new RaidSiteTemplate("heavy-industry", FortressProgram::works, WORKS_FIT, INDUSTRY_GROWTH);
        }
        if (MINING.contains(id)) return new RaidSiteTemplate("mining", RaidWorksPrograms::mine, MINE_FIT, ORE_YARDS);
        if (REFINING.contains(id)) {
            return new RaidSiteTemplate("refining", RaidWorksPrograms::refinery, MINE_FIT, INDUSTRY_GROWTH);
        }
        if (LIGHT_INDUSTRY.contains(id)) {
            return new RaidSiteTemplate("light-industry", RaidWorksPrograms::shops, WORKS_FIT, INDUSTRY_GROWTH);
        }
        if (FARMING.contains(id)) {
            return new RaidSiteTemplate("farming", RaidWorksPrograms::farmPlant, WORKS_FIT, ORE_YARDS,
                    PrecinctCharacter.FARMSTEAD);
        }
        if (PORT.contains(id)) {
            return new RaidSiteTemplate("spaceport", RaidWorksPrograms::cargoTerminal, MINE_FIT, INDUSTRY_GROWTH);
        }
        if (MILITARY.contains(id)) {
            return new RaidSiteTemplate("military", RaidWorksPrograms::motorPool, MINE_FIT, ORE_YARDS,
                    PrecinctCharacter.DEPOT);
        }
        return null;
    }

    /** The template's name, for evidence. */
    public String key() { return key; }

    /**
     * The site laid on a map of this size for this market. The draws are this template's own: one for the
     * orientation, then the places in order.
     *
     * @param machines machines in the works (halls, or a mine's winding houses), one per machine the raid wrecks
     * @param demand   the hardest wall this operation may be asked to face; it caps the works' wall and sets the
     *                 garrison's as a derived plan does
     */
    public PrecinctPlan lay(TargetProfile profile, Fortification.Demand demand, int machines,
                            int width, int height, Random rng) {
        // Mixed through a splittable stream: the first draws of an ordinary Random barely move between
        // neighbouring seeds, so twelve battles in a row would land from the same side.
        SplittableRandom orientation = new SplittableRandom(rng.nextLong());
        boolean mirrorX = orientation.nextBoolean();
        boolean mirrorY = orientation.nextBoolean();
        Security security = Security.of(profile);
        List<PrecinctBrief> briefs = new ArrayList<>();
        briefs.add(PrecinctBrief.garrison(WORKS, oriented(WORKS_AT, mirrorX, mirrorY), WORKS_GROWTH,
                works.apply(machines).fittedTo(Math.round(worksFit * width * height)),
                worksWall(security, demand)));
        if (profile.defenseLevel() > 0) {
            briefs.add(PrecinctBrief.garrison(GARRISON, oriented(GARRISON_AT, mirrorX, mirrorY),
                    GrownTrunkPlan.Profile.hamlet(),
                    PrecinctPlan.garrisonFor(profile).fittedTo(Math.round(PrecinctPlan.FIT * width * height)),
                    demand.resolve(profile.defenseLevel())));
        }
        briefs.add(PrecinctBrief.settlement(HOMES, oriented(HOMES_AT, mirrorX, mirrorY), homesGrowth(profile),
                PrecinctCharacter.TOWN.leaning(MapDistrictTheme.RESIDENTIAL)));
        briefs.add(PrecinctBrief.settlement(INDUSTRY, oriented(INDUSTRY_AT, mirrorX, mirrorY),
                industryGrowth, industryCharacter));
        briefs.add(PrecinctBrief.settlement(YARDS, oriented(YARDS_AT, mirrorX, mirrorY),
                industryGrowth, industryCharacter));
        if (profile.marketSize() >= OUTSKIRTS_FROM) {
            briefs.add(PrecinctBrief.settlement(OUTSKIRTS, oriented(OUTSKIRTS_AT, mirrorX, mirrorY),
                    GrownTrunkPlan.Profile.hamlet(), PrecinctCharacter.SUBURB));
        }
        return PrecinctPlan.laidOut(briefs, oriented(LANDING_FROM, mirrorX, mirrorY), width, height, rng)
                .withStandoff(Standoff.INSET);
    }

    /**
     * The works' wall: the security's rung, no harder than the operation may face, laid square with the security's
     * buffer of open ground round it.
     */
    static Fortification worksWall(Security security, Fortification.Demand demand) {
        Fortification.Strength rung = security.wall().noHarderThan(demand.ceiling());
        return rung.fortification().laidSquare().withGlacis(security.buffer());
    }

    /**
     * The homes grow with the market: a hamlet's streets for a small market, a town's from {@link #TOWN_FROM}. Their
     * arms are short, {@link #HOMES_ARM_LO} to {@link #HOMES_ARM_HI} of the map rather than a settlement's usual
     * reach, so the town fills its own district and never runs round to the works.
     */
    static GrownTrunkPlan.Profile homesGrowth(TargetProfile profile) {
        return shortArms(profile.marketSize() >= TOWN_FROM
                ? GrownTrunkPlan.Profile.town() : GrownTrunkPlan.Profile.hamlet());
    }

    /** Shortest and longest arm of the homes' streets, as fractions of the map. UNTUNED. */
    static final float HOMES_ARM_LO = 0.07f;
    static final float HOMES_ARM_HI = 0.15f;

    /** The industrial quarter and the yards: a town's density of yards and sheds on the homes' short arms. */
    private static final GrownTrunkPlan.Profile INDUSTRY_GROWTH = shortArms(GrownTrunkPlan.Profile.town());

    /** A mine's ore yards and railhead: a hamlet's few roads, sheds and stockpiles with open ground between. */
    private static final GrownTrunkPlan.Profile ORE_YARDS = shortArms(GrownTrunkPlan.Profile.hamlet());

    /** The kind of district round the works, for evidence. */
    PrecinctCharacter industryCharacter() { return industryCharacter; }

    /** How many junctions the industry round the works grows, for evidence. */
    int industryJunctions() { return industryGrowth.junctionBudget; }

    private static GrownTrunkPlan.Profile shortArms(GrownTrunkPlan.Profile p) {
        return new GrownTrunkPlan.Profile(p.junctionBudget, p.branchChance, HOMES_ARM_LO, HOMES_ARM_HI,
                p.fourWayChance, p.frontageDepth, p.link);
    }

    /** A region of the canonical template, mirrored as the seed chose. */
    static MapPlacement oriented(MapPlacement at, boolean mirrorX, boolean mirrorY) {
        float x0 = mirrorX ? 1f - at.x1() : at.x0();
        float x1 = mirrorX ? 1f - at.x0() : at.x1();
        float y0 = mirrorY ? 1f - at.y1() : at.y0();
        float y1 = mirrorY ? 1f - at.y0() : at.y1();
        return new MapPlacement(x0, y0, x1, y1);
    }
}
