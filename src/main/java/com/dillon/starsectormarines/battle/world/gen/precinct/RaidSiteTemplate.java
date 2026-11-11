package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.MapDistrictTheme;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.StormBank;
import com.dillon.starsectormarines.battle.world.gen.StormWind;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.elevation.OpenPit;
import com.dillon.starsectormarines.battle.world.gen.elevation.RidgePlanner;
import com.dillon.starsectormarines.battle.world.gen.layout.Endpoint;
import com.dillon.starsectormarines.battle.world.gen.layout.MissionLayout;
import com.dillon.starsectormarines.battle.world.gen.layout.Piece;
import com.dillon.starsectormarines.battle.world.gen.layout.Road;
import com.dillon.starsectormarines.battle.world.gen.layout.Side;
import com.dillon.starsectormarines.battle.world.gen.layout.Site;
import com.dillon.starsectormarines.battle.world.gen.layout.SitePurpose;
import com.dillon.starsectormarines.battle.world.gen.raid.RaidIndustries;
import com.dillon.starsectormarines.battle.world.gen.raid.RaidIndustry;
import com.dillon.starsectormarines.battle.world.gen.raid.SiteFrame;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.SplittableRandom;
import java.util.function.UnaryOperator;

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
 * <p>The industry ({@link RaidIndustry}, one class each, {@link RaidIndustries#ALL}) changes what the works holds,
 * where it and its district stand, how thick the industry round it stands, the ground it lays beside the works and
 * how the works is dressed: heavy industry's works is machine halls in a dense industrial quarter; a mine's is its
 * head works, an ore-processing chain beside its pit; and so on. This class is the seam they share: the landing, the
 * homes, the garrison, the roads, the walls and the orientation, the same for every industry.
 *
 * <p>Raid only. Every other mission's map is derived as it was.
 */
public final class RaidSiteTemplate {

    /** The works compound: the raid's objective, listed first so it is the plan's {@link PrecinctPlan#objective()}. */
    public static final String WORKS = "works";
    public static final String GARRISON = "garrison";
    /** The place a works' machine posts are named with ({@code WorksDefences}): its gates and its yard reserve. */
    public static final String WORKS_MACHINES = "works-machines";
    /**
     * The place a fortified works' yard reserve is named with ({@code WorksDefences}): a machine post too, whose
     * machines answer the alarm at the hall under attack ({@code RaidDefenderCommand}).
     */
    public static final String WORKS_RESERVE = "works-reserve";
    public static final String HOMES = "homes";
    /** The industrial quarter the works stands at the back of: yards and sheds between it and the homes. */
    public static final String INDUSTRY = "industry";
    /** The yards beyond the works: the industrial district's far side, so the deep end is built and not empty. */
    public static final String YARDS = "yards";
    public static final String OUTSKIRTS = "outskirts";
    /**
     * A mine's open pit: ground held beside the head works, at the deep end, as a {@link Site} of
     * {@link SitePurpose#GROUND} on the plan's {@link PrecinctPlan#layout() layout}. Nothing grows on it (no road,
     * building, field or works), so {@link OpenPit} finds it clear and cuts the pit there; read it with
     * {@code plan.layout().find(PIT)}.
     */
    public static final String PIT = OpenPit.SITE;
    /** The road from the works' near gate to its industrial district: how the works' output leaves for the town. */
    public static final String HAUL_ROAD = "haul-road";
    /**
     * The feeder lines: roads from the works' far gates off the map's edge, the lines from the planet's other sites
     * that all run in to this one ({@code raid-site-templates.md}, The planet's chokepoint). Named this prefix and
     * the side they leave by.
     */
    public static final String FEEDER = "feeder-";

    /** Width of the haul road and the feeders, in cells. UNTUNED. */
    public static final int LINE_WIDTH = 3;
    /** An endpoint offset beyond any map: the road's far end is clamped to the edge, so it runs off the map. */
    private static final int OFF_MAP = 10_000;

    /** Market size from which the site has an outskirts suburb behind the homes. UNTUNED. */
    static final int OUTSKIRTS_FROM = 6;

    /** Market size from which the homes grow a town's streets rather than a hamlet's. UNTUNED. */
    static final int TOWN_FROM = 5;

    /**
     * How hard a market keeps what it is worth ({@code raid-mission.md}, Security): the wall round its compounds and
     * the open ground before it, and the machines and hulls posted at its works on top of the market's own force
     * ({@code RaidWorksMachines}): a harder market has more to fight, and more to fight through.
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
    public static final MapPlacement LANDING_FROM = new MapPlacement(0f, 0.25f, 0.30f, 0.75f);
    public static final MapPlacement HOMES_AT = new MapPlacement(0.14f, 0.32f, 0.32f, 0.68f);
    public static final MapPlacement INDUSTRY_AT = new MapPlacement(0.48f, 0.50f, 0.62f, 0.80f);
    public static final MapPlacement YARDS_AT = new MapPlacement(0.86f, 0.62f, 0.97f, 0.94f);
    public static final MapPlacement WORKS_AT = new MapPlacement(0.68f, 0.58f, 0.86f, 0.80f);
    public static final MapPlacement GARRISON_AT = new MapPlacement(0.70f, 0.14f, 0.90f, 0.36f);
    public static final MapPlacement OUTSKIRTS_AT = new MapPlacement(0.04f, 0.04f, 0.22f, 0.24f);

    /** Cells of open ground between the head works' claim and the pit's lip: room for the road out of its far gate. UNTUNED. */
    static final int PIT_GAP = 16;
    /** Half the pit's extent across the map, as a share of its height. UNTUNED. */
    static final float PIT_HALF_SPAN = 0.22f;
    /**
     * The pit keeps to the works' side of the map, never nearer the middle than this share: clear of the garrison's
     * ground, whose roads the works can depend on for its own way in. UNTUNED.
     */
    static final float PIT_INNER = 0.56f;
    /** Cells the pit stops short of the map's edges. */
    static final int PIT_MARGIN = 2;

    /**
     * The works' own roads: one junction and short arms, enough to reach the town's streets through its gates and
     * no more, as a civil-defence compound's are.
     */
    private static final GrownTrunkPlan.Profile WORKS_GROWTH =
            new GrownTrunkPlan.Profile(0, 0f, 0.04f, 0.06f, 0f, 4, SettlementLink.NONE);

    /** Share of the map a works may claim before its program is trimmed. UNTUNED. */
    public static final float WORKS_FIT = 0.06f;

    /** Share of the map a wide works may claim: halls standing apart on wide yards, as a mine's do. UNTUNED. */
    public static final float MINE_FIT = 0.08f;

    private final RaidIndustry industry;

    private RaidSiteTemplate(RaidIndustry industry) {
        this.industry = industry;
    }

    /** The template for an industry, by its id, or {@code null} for one that has none yet. */
    public static RaidSiteTemplate forIndustry(String industryId) {
        RaidIndustry industry = RaidIndustries.forId(industryId);
        return industry == null ? null : new RaidSiteTemplate(industry);
    }

    /** The industry this site is laid for. */
    public RaidIndustry industry() { return industry; }

    /** The template's name, for evidence. */
    public String key() { return industry.key(); }

    /** Sites the hub is fed by on the smallest market a Raid names. UNTUNED. */
    static final int FEEDERS_BASE = 3;
    /** Sites added per market size above three. UNTUNED. */
    static final int FEEDERS_PER_SIZE = 2;

    /**
     * How many of the planet's sites feed this one ({@code raid-site-templates.md}, The planet's chokepoint): the
     * digs a concentrator handles, the fields a refinery takes crude from. A bigger market has more of them, all
     * running through the one hub. Never fewer than two: a hub with one feeder is not a hub.
     */
    public static int feedersFor(int marketSize) {
        return Math.max(2, FEEDERS_BASE + FEEDERS_PER_SIZE * (marketSize - 3));
    }

    /**
     * The briefing's string for this site: what the hub is to the planet. Formatted with the planet's name, the
     * feeders ({@link #feedersFor}) and the days its loss costs at full effect.
     */
    public String briefingKey() { return "raidSiteChokepoint_" + key().replace('-', '_'); }

    /**
     * The site laid on a map of this size for this market. The draws are this template's own: one for the
     * orientation, then the places in order.
     *
     * @param machines machines in the works (halls, or a mine's mill lines), one per machine the raid wrecks
     * @param demand   the hardest wall this operation may be asked to face; it caps the works' wall and sets the
     *                 garrison's as a derived plan does
     */
    public PrecinctPlan lay(TargetProfile profile, Fortification.Demand demand, int machines,
                            int width, int height, Random rng) {
        // Mixed through a splittable stream: the first draws of an ordinary Random barely move between
        // neighbouring seeds, so twelve battles in a row would land from the same side.
        SplittableRandom orientation = new SplittableRandom(rng.nextLong());
        boolean drawnX = orientation.nextBoolean();
        boolean mirrorY = orientation.nextBoolean();
        // A desert storm world's town turns its back to the wind and banks earth along the windward edge: the
        // landing is never on that side, and every place is set leeward of the bank's ground.
        boolean banked = StormBank.raises(profile);
        StormWind.Side wind = profile.climate().wind();
        boolean mirrorX = banked ? StormBank.mirrorX(drawnX, wind) : drawnX;
        UnaryOperator<MapPlacement> at = p -> banked
                ? StormBank.leeward(oriented(p, mirrorX, mirrorY), wind, width, height) : oriented(p, mirrorX, mirrorY);
        Security security = Security.of(profile);
        List<PrecinctBrief> briefs = new ArrayList<>();
        briefs.add(PrecinctBrief.garrison(WORKS, at.apply(industry.worksAt()),
                WORKS_GROWTH,
                industry.works(machines).fittedTo(Math.round(industry.worksFit() * width * height)),
                worksWall(security, demand)));
        if (profile.defenseLevel() > 0) {
            briefs.add(PrecinctBrief.garrison(GARRISON, at.apply(GARRISON_AT),
                    PrecinctPlan.raidGarrisonGrowth(),
                    PrecinctPlan.raidGarrison(profile, security.ordinal())
                            .fittedTo(Math.round(PrecinctPlan.FIT * width * height)),
                    PrecinctPlan.raidGarrisonWall(demand.resolve(profile.defenseLevel()), security.buffer())));
        }
        briefs.add(PrecinctBrief.settlement(HOMES, at.apply(HOMES_AT), homesGrowth(profile),
                PrecinctCharacter.TOWN.leaning(MapDistrictTheme.RESIDENTIAL)));
        briefs.add(PrecinctBrief.settlement(INDUSTRY,
                at.apply(industry.industryAt()), industry.industryGrowth(), industry.industryCharacter()));
        if (industry.yards()) {
            briefs.add(PrecinctBrief.settlement(YARDS, at.apply(YARDS_AT),
                    industry.industryGrowth(), industry.industryCharacter()));
        }
        if (profile.marketSize() >= OUTSKIRTS_FROM) {
            briefs.add(PrecinctBrief.settlement(OUTSKIRTS, at.apply(OUTSKIRTS_AT),
                    GrownTrunkPlan.Profile.hamlet(), PrecinctCharacter.SUBURB));
        }
        PrecinctPlan plan = PrecinctPlan.laidOut(briefs, at.apply(LANDING_FROM), width, height, rng)
                .withStandoff(Standoff.INSET);
        List<Piece> pieces = new ArrayList<>();
        industry.sitePieces(new SiteFrame(plan, mirrorX, mirrorY, width, height, security, banked ? wind : null),
                pieces);
        boolean deepFeeder = industry.deepFeeder();
        pieces.addAll(lines(mirrorX, mirrorY, !deepFeeder));
        if (banked) plan = plan.withLandscape(stormBank(plan, wind, mirrorX, mirrorY, !deepFeeder, width, height));
        return plan.withLayout(new MissionLayout(pieces)).withRaidIndustry(industry.key()).withRaidSecurity(security);
    }

    /**
     * The works' roads ({@code raid-site-templates.md}, The planet's chokepoint): the haul road from its near gate
     * to the industrial district, and a feeder off the map's edge from each far gate, the deep end's and the outer
     * flank's. Where the deep end is the industry's own ground ({@code noDeepFeeder}: a mine's pit) the only feeder is
     * the flank's.
     */
    static List<Road> lines(boolean mirrorX, boolean mirrorY, boolean noDeepFeeder) {
        Side deep = mirrorX ? Side.WEST : Side.EAST;
        Side outer = mirrorY ? Side.SOUTH : Side.NORTH;
        List<Road> out = new ArrayList<>();
        // Each runs to the works' gate, where its wall opens onto it and the road carries on to the works' streets.
        // Each is laid through the districts on its course, as their street, so the line shows across them.
        out.add(new Road(HAUL_ROAD, Endpoint.beside(INDUSTRY, deep, 0, 0), Endpoint.gate(WORKS, deep.opposite()),
                LINE_WIDTH, false).laidThrough());
        out.add(new Road(FEEDER + outer.name().toLowerCase(Locale.ROOT),
                Endpoint.beside(WORKS, outer, 0, outer.dy * OFF_MAP), Endpoint.gate(WORKS, outer), LINE_WIDTH, false)
                .laidThrough());
        if (!noDeepFeeder) {
            out.add(new Road(FEEDER + deep.name().toLowerCase(Locale.ROOT),
                    Endpoint.beside(WORKS, deep, deep.dx * OFF_MAP, 0), Endpoint.gate(WORKS, deep), LINE_WIDTH, false)
                    .laidThrough());
        }
        return out;
    }

    /** The pit's ground stopped short of the storm bank's, so the pit and the bank never contend for the same cells. */
    public static Site clearOfBank(Site pit, StormWind.Side wind, int width, int height) {
        int depth = StormBank.depth(wind, width, height);
        int x0 = pit.x0(), y0 = pit.y0(), x1 = pit.x1(), y1 = pit.y1();
        switch (wind) {
            case WEST -> x0 = Math.max(x0, depth);
            case EAST -> x1 = Math.min(x1, width - 1 - depth);
            case SOUTH -> y0 = Math.max(y0, depth);
            case NORTH -> y1 = Math.min(y1, height - 1 - depth);
        }
        if (x1 < x0) x1 = x0;
        if (y1 < y0) y1 = y0;
        return new Site(pit.name(), x0, y0, x1, y1, pit.purpose(), pit.required());
    }

    /**
     * The plan's landscape with the storm bank along the windward edge ({@link StormBank}): its bands held from
     * growth and the strip between them and the edge kept open, with a pass planned where each of the works' feeders
     * leaves the map on that side. A feeder runs off the map from beside the works' claim, so across the edge it
     * stands level with the works' seed.
     */
    static Landscape stormBank(PrecinctPlan plan, StormWind.Side wind, boolean mirrorX, boolean mirrorY,
                               boolean noDeepFeeder, int width, int height) {
        Side deep = mirrorX ? Side.WEST : Side.EAST;
        Side outer = mirrorY ? Side.SOUTH : Side.NORTH;
        Precinct works = null;
        for (Precinct p : plan.precincts()) if (WORKS.equals(p.name())) works = p;
        List<Integer> passes = new ArrayList<>();
        if (works != null) {
            for (Side feeder : noDeepFeeder ? List.of(outer) : List.of(outer, deep)) {
                if (feeder.name().equals(wind.name())) passes.add(wind.alongX() ? works.seedX() : works.seedY());
            }
        }
        Landscape landscape = plan.landscape();
        List<RidgePlanner.Band> held = new ArrayList<>(landscape.held());
        held.addAll(StormBank.bands(wind, width, height, passes));
        List<Landscape.Track> kept = new ArrayList<>(landscape.kept());
        kept.addAll(StormBank.windward(wind, width, height, passes));
        return landscape.withHeld(held).withKept(kept);
    }

    /**
     * The most a works' claim reaches from its seed: half the side of the square a works of this fit and security
     * can claim, at its largest. Its program is fitted to {@code fit} of the map, so its claim is never wider.
     */
    public static int worksReach(float fit, Security security, int width, int height) {
        int side = (int) Math.ceil(Math.sqrt(fit * width * height)) + 2 * security.buffer();
        return (side + 1) / 2;
    }

    /**
     * The pit beside a head works seeded at {@code (seedX, seedY)}: from {@link #PIT_GAP} beyond the works' reach to
     * the deep edge, and across the map {@link #PIT_HALF_SPAN} either side of the works, kept to the works' half
     * (never nearer the middle than {@link #PIT_INNER}) so it stays off the garrison's flank.
     */
    public static Site pitSite(int seedX, int seedY, int reach, boolean mirrorX, boolean mirrorY, int width,
                               int height) {
        int x0, x1;
        if (mirrorX) {
            x0 = PIT_MARGIN;
            x1 = seedX - reach - PIT_GAP;
        } else {
            x0 = seedX + reach + PIT_GAP;
            x1 = width - 1 - PIT_MARGIN;
        }
        int span = Math.round(PIT_HALF_SPAN * height);
        int y0 = seedY - span, y1 = seedY + span;
        if (mirrorY) {
            y0 = Math.max(PIT_MARGIN, y0);
            y1 = Math.min(Math.round((1f - PIT_INNER) * height), y1);
        } else {
            y0 = Math.max(Math.round(PIT_INNER * height), y0);
            y1 = Math.min(height - 1 - PIT_MARGIN, y1);
        }
        if (x1 < x0) x1 = x0;
        if (y1 < y0) y1 = y0;
        return new Site(PIT, x0, y0, x1, y1, SitePurpose.GROUND, true);
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
    public static final GrownTrunkPlan.Profile INDUSTRY_GROWTH = shortArms(GrownTrunkPlan.Profile.town());

    /**
     * A sparse district round the works: a hamlet's few roads, sheds and yards with open ground between, as a mine's
     * ore yards and a base's depots are.
     */
    public static final GrownTrunkPlan.Profile SPARSE_GROWTH = shortArms(GrownTrunkPlan.Profile.hamlet());

    /** The kind of district round the works, for evidence. */
    PrecinctCharacter industryCharacter() { return industry.industryCharacter(); }

    /** How many junctions the industry round the works grows, for evidence. */
    int industryJunctions() { return industry.industryGrowth().junctionBudget; }

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
