package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.airsupport.AlliedAirWing;
import com.dillon.starsectormarines.battle.airsupport.ForwardObservers;
import com.dillon.starsectormarines.battle.airsupport.HardstandAirBase;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartOrigin;
import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan;
import com.dillon.starsectormarines.battle.vehicle.VehicleRoutePlanner;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.TerrainCostField;
import com.dillon.starsectormarines.battle.cordon.CordonSupplyStreets;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.command.BarrageScreen;
import com.dillon.starsectormarines.battle.command.HeartAssaultCommand;
import com.dillon.starsectormarines.battle.command.HeartLane;
import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.command.objective.BatteryObjective;
import com.dillon.starsectormarines.battle.command.objective.EliminateFactionObjective;
import com.dillon.starsectormarines.battle.command.objective.HeartObjective;
import com.dillon.starsectormarines.battle.evacuation.HeartBoreGrowth;
import com.dillon.starsectormarines.battle.evacuation.HeartBores;
import com.dillon.starsectormarines.battle.evacuation.HeartLanes;
import com.dillon.starsectormarines.battle.evacuation.HeartObjectives;
import com.dillon.starsectormarines.battle.command.allied.AlliedTool;
import com.dillon.starsectormarines.battle.command.allied.AlliedToolShed;
import com.dillon.starsectormarines.battle.evacuation.HeartGuns;
import com.dillon.starsectormarines.battle.evacuation.HeartVehiclePark;
import com.dillon.starsectormarines.battle.evacuation.HeartSequence;
import com.dillon.starsectormarines.battle.evacuation.HeartSupplyRoad;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveBoard;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveContext;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRoll;
import com.dillon.starsectormarines.battle.objective.optional.OptionalObjective;
import com.dillon.starsectormarines.battle.power.WalkingBarrage;
import com.dillon.starsectormarines.battle.power.MechDropService;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.mech.MechDropDeclaration;
import com.dillon.starsectormarines.battle.vehicle.VehicleStockFit;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.AirbaseLot;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.Runway;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.Basin;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Density;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartLayout;
import com.dillon.starsectormarines.battle.world.gen.overgrown.OriginProp;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.ops.RiskLevel;

import com.dillon.starsectormarines.battle.world.gen.pool.MapPatch;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMap;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPool;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The heart of the infection (`bloom-mission-arc.md`, Mission 3): the country,
 * the heart's bores and the bloom they have grown over it in the weeks before
 * the battle.
 *
 * <p>The country is {@link HeartCountry}, on a world of the event's biome. Its places are
 * laid out, the map generated, and the heart's sites chosen on ground the start
 * line can walk to ({@link HeartBores}); a map that cannot seat them re-rolls
 * the seed. The bores open at tick zero already fed to maw and their creep is
 * matured as creep weeks old ({@link BattleSimulation#matureCreep}), so the
 * overgrowth, the dead woods and the fallen roofs are those bores' work.
 */
public final class BloomHeartSetup {

    private static final int ATTEMPTS = 8;
    private static final BspCityGenerator MAP_GEN = new BspCityGenerator();
    private static final long BORE_SALT = 0x4845_4152_5453_4954L;
    /**
     * The heart's basin on a desert world ({@link Basin#ravBloom}): wider than the map, so the whole valley is its
     * green floor and the desert shows only in the corners.
     */
    static final float BASIN_REACH_X = 1.12f, BASIN_REACH_Y = 1.15f;

    private BloomHeartSetup() { }

    /** A world of this biome with no market behind it: the heart is the same kind of place on any such world. */
    public static TargetProfile world(SurfacePalette biome) {
        return new TargetProfile(0, 0, 0, 0, "", Set.of(), biome, SettlementLink.ROAD);
    }

    /** The story map a form plays from in open country ({@code story-map-pools.md}). */
    public static StoryMap storyMap(Form form) {
        return storyMap(form, Density.RURAL);
    }

    /** The story map a form plays from in country as built-up as {@code density}: one pool each. */
    public static StoryMap storyMap(Form form, Density density) {
        boolean burn = form == Form.THE_BURN;
        return switch (density) {
            case RURAL -> burn ? StoryMap.HEART_BURN : StoryMap.HEART_FRONT;
            case TOWN -> burn ? StoryMap.HEART_BURN_TOWN : StoryMap.HEART_FRONT_TOWN;
            case CITY -> burn ? StoryMap.HEART_BURN_CITY : StoryMap.HEART_FRONT_CITY;
        };
    }

    /** The form a heart story map is, or null for another mission's map. */
    public static Form formOf(StoryMap map) {
        for (Form form : Form.values()) for (Density density : Density.values()) {
            if (storyMap(form, density) == map) return form;
        }
        return null;
    }

    /** The density a heart story map is laid out at; rural for any other map. */
    public static Density densityOf(StoryMap map) {
        for (Form form : Form.values()) for (Density density : Density.values()) {
            if (storyMap(form, density) == map) return density;
        }
        return Density.RURAL;
    }

    /**
     * The heart's country, bores and matured bloom on a world of this biome, at
     * tick zero: the pooled map when the form's pool holds {@code seed} for the
     * biome, else the first country from it whose bores seat. The second is how
     * a candidate is tried before it is pooled; the campaign reaches only the first.
     */
    public static Built country(Form form, SurfacePalette biome, long seed) {
        return country(form, biome, Density.RURAL, seed);
    }

    /**
     * As {@link #country(Form, SurfacePalette, long)} in country as built-up as
     * {@code density}, from that density's pool.
     */
    public static Built country(Form form, SurfacePalette biome, Density density, long seed) {
        StoryMapPool.Entry entry = StoryMapPools.installed()
                ? StoryMapPools.pool(storyMap(form, density)).entry(biome, seed) : null;
        if (entry != null) return country(form, biome, density, entry);
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            Built built = build(form, biome, density, seed + attempt * 0x9E3779B97F4A7C15L, MapPatch.NONE);
            if (built != null) return built;
        }
        throw new IllegalStateException("no " + biome + " " + density + " heart country of form " + form
                + " seated its bores in " + ATTEMPTS + " attempts from seed " + seed);
    }

    /** A pooled heart country: never re-rolled, so a pooled seed whose bores no longer seat is a broken pool. */
    public static Built country(Form form, SurfacePalette biome, StoryMapPool.Entry entry) {
        return country(form, biome, Density.RURAL, entry);
    }

    /** As {@link #country(Form, SurfacePalette, StoryMapPool.Entry)}, from the pool of a density. */
    public static Built country(Form form, SurfacePalette biome, Density density, StoryMapPool.Entry entry) {
        Built built = build(form, biome, density, entry.seed(), entry.patch());
        if (built == null) {
            throw new IllegalStateException(storyMap(form, density).id + " " + biome + " seed " + entry.seed()
                    + " does not seat its bores");
        }
        return built;
    }

    private static HeartBores bores(Form form, MapResult map, PrecinctPlan plan, long battleSeed) {
        Precinct workings = HeartCountry.place(plan, HeartCountry.WORKINGS);
        Precinct landing = plan.landingPlace();
        return HeartBores.plan(map.grid, map.topology, form,
                new int[]{workings.seedX(), workings.seedY()}, originCore(map, plan),
                new int[]{landing.seedX(), landing.seedY()}, supplyRoad(map, plan), battleSeed ^ BORE_SALT);
    }

    /**
     * The supply road's cells as the map laid them; where none could be laid (a city's ground, every pooled city map),
     * the streets are the supply road, as the Cordon's are (`cordon-nouns.md`, Logistics base): the way a truck drives
     * from the landing to the settlement, over the ground as the vehicle router prices it, a street cheapest, widened
     * to a laid road's width. None where a truck has no way at all.
     */
    public static List<int[]> supplyRoad(MapResult map, PrecinctPlan plan) {
        Laid road = map.layout.find(HeartLayout.SUPPLY_ROAD);
        if (road != null && road.isLaid()) return road.cells();
        Precinct landing = plan.landingPlace();
        Precinct settlement = HeartCountry.place(plan, HeartCountry.SETTLEMENT);
        if (settlement == null) return List.of();
        VehicleClearance truck = VehicleClearance.erode(map.grid,
                VehicleClearance.radiusForWidth(VehicleType.SUPPLY_TRUCK.visualWidthCells));
        int[] a = VehicleRoutePlanner.snapToMask(truck, landing.seedX(), landing.seedY(), STREET_SNAP);
        int[] b = VehicleRoutePlanner.snapToMask(truck, settlement.seedX(), settlement.seedY(), STREET_SNAP);
        if (a == null || b == null) return List.of();
        float[][] route = VehicleRoutePlanner.route(a[0], a[1], b[0], b[1], map.grid, TerrainCostField.from(map.topology), truck);
        if (route == null) return List.of();
        return widened(CordonSupplyStreets.cells(route[0], route[1]), STREET_ROAD_REACH,
                map.grid.getWidth(), map.grid.getHeight());
    }

    /** Cells round a way's ends a truck's footprint is looked for. */
    static final int STREET_SNAP = 8;
    /** Cells either side of a street way the supply road takes in: a laid road's half width. */
    static final int STREET_ROAD_REACH = 2;

    /** Every in-bounds cell within {@code reach} (Chebyshev) of a cell of {@code way}, each once, in way order. */
    static List<int[]> widened(List<int[]> way, int reach, int width, int height) {
        boolean[] seen = new boolean[width * height];
        List<int[]> out = new ArrayList<>();
        for (int[] c : way) {
            for (int dy = -reach; dy <= reach; dy++) for (int dx = -reach; dx <= reach; dx++) {
                int x = c[0] + dx, y = c[1] + dy;
                if (x < 0 || y < 0 || x >= width || y >= height || seen[y * width + x]) continue;
                seen[y * width + x] = true;
                out.add(new int[]{x, y});
            }
        }
        return out;
    }

    /**
     * Where the origin's first mother came up: the middle of its core building, or for an origin that opens at a prop
     * (the park's pond) the middle of that prop as the yard dressing laid it; null when the far place packed neither.
     */
    public static int[] originCore(MapResult map, PrecinctPlan plan) {
        Precinct workings = HeartCountry.place(plan, HeartCountry.WORKINGS);
        HeartOrigin origin = HeartOrigin.of(workings);
        if (origin == null) return null;
        Laid dressing = map.layout == null ? null : map.layout.find(HeartLayout.ORIGIN_DRESSING);
        if (origin.opensAt != null && dressing != null && dressing.isLaid()) {
            // The pond is the dressing's first prop.
            int[] at = dressing.cells().get(0);
            DoodadDef def = origin.opensAt.def(OriginProp.State.CLEAN);
            return def == null ? at : new int[]{at[0] + def.footprintCellsX / 2, at[1] + def.footprintCellsY / 2};
        }
        return HeartOrigin.core(map.topology, origin, workings.seedX(), workings.seedY());
    }

    /**
     * The map carrying the origin's damage as one more initial-damage layer (`heart-origins.md`): its core building
     * broken open from the first mother's side, the facility battered round it.
     */
    private static MapResult originDamage(MapResult map, PrecinctPlan plan, HeartBores heart, long battleSeed) {
        int[] core = originCore(map, plan);
        if (core == null || heart.mothers().isEmpty()) return map;
        HeartBores.Site first = heart.mothers().get(0);
        Precinct workings = HeartCountry.place(plan, HeartCountry.WORKINGS);
        List<DamagePlan.Layer> layers = new ArrayList<>();
        if (map.initialDamage != null) layers.addAll(map.initialDamage.layers());
        layers.add(new DamagePlan.Layer(HeartOrigin.DAMAGE_LAYER, true,
                HeartOrigin.damage(map.topology, core, new int[]{first.x(), first.y()}, workings.seedX(), workings.seedY(),
                        battleSeed)));
        long seed = map.initialDamage != null ? map.initialDamage.seed() : battleSeed;
        return map.withInitialDamage(new DamagePlan(map.grid.getWidth(), map.grid.getHeight(), seed, layers));
    }

    /** The country from one seed with its patch, or {@code null} when its bores do not seat. */
    private static Built build(Form form, SurfacePalette biome, Density density, long battleSeed, MapPatch patch) {
        TargetProfile profile = world(biome).withBasin(Basin.ravBloom(biome, BASIN_REACH_X, BASIN_REACH_Y));
        PrecinctPlan plan = patch.pinned(HeartCountry.planFor(form, density, battleSeed));
        MapResult map = MAP_GEN.generate(form.scale.width, form.scale.height, battleSeed, null, profile, plan);
        HeartBores heart = bores(form, map, plan, battleSeed);
        if (heart == null) return null;
        map = originDamage(patch.ruined(map, battleSeed), plan, heart, battleSeed);
        BattleSimulation sim = BattleSetup.buildMap(map, Collections.emptyList(),
                Collections.emptyList(), battleSeed).sim();
        sim.setSurface(profile.surface());
        heart.open(sim);
        sim.matureCreep();
        return new Built(sim, map, plan, heart, battleSeed);
    }

    /**
     * The heart as a campaign battle: the pooled map of the world's biome and market size for a roll its event
     * carries, a frontier world's farmland, a settled world's towns or a core world's city.
     */
    public static BattleSimulation pooledMission(Form form, SurfacePalette biome, int marketSize, long roll,
                                                 List<ShuttleAssignment> manifest) {
        Density density = Density.of(marketSize);
        return mission(form, biome, density, StoryMapPools.pool(storyMap(form, density)).pick(biome, roll).seed(), manifest);
    }

    /** Broad Front's company lane: satellites this share of the map's width either side of its landing are its own. */
    static final float BROAD_FRONT_LANE_HALF_WIDTH = 0.14f;

    /**
     * The heart as a mission: the country, bores and bloom of {@link #country},
     * the company coming down by shuttle on the cordon's farmland under its
     * Purge commander, the bores' director and the heart's wake rules, won when
     * every mother has collapsed and lost when the company is broken (in The
     * Burn, also when its battery is overrun).
     */
    public static BattleSimulation mission(Form form, SurfacePalette biome, long seed,
                                           List<ShuttleAssignment> manifest) {
        return mission(form, biome, Density.RURAL, seed, manifest);
    }

    /** As {@link #mission(Form, SurfacePalette, long, List)} in country as built-up as {@code density}. */
    public static BattleSimulation mission(Form form, SurfacePalette biome, Density density, long seed,
                                           List<ShuttleAssignment> manifest) {
        return mission(form, biome, density, seed, manifest, List.of());
    }

    /**
     * As {@link #mission(Form, SurfacePalette, Density, long, List)}, running the named optional objectives instead
     * of the draw when {@code objectives} is not empty; each still runs only where its place was laid.
     */
    public static BattleSimulation mission(Form form, SurfacePalette biome, Density density, long seed,
                                           List<ShuttleAssignment> manifest, List<String> objectives) {
        return mission(form, biome, density, seed, manifest, objectives, MechDropDeclaration.NONE);
    }

    /**
     * As {@link #mission(Form, SurfacePalette, Density, long, List, List)}, with the lances {@code mechDrops} declares.
     * The campaign's heart declares none (the player's own machines come down through Mech Support); a declared
     * Marine lance with a {@code debugCallTick} lands at the company's pads claimed by its command, which is how debug
     * launches and evidence field a well-found company's machines.
     */
    public static BattleSimulation mission(Form form, SurfacePalette biome, Density density, long seed,
                                           List<ShuttleAssignment> manifest, List<String> objectives,
                                           MechDropDeclaration mechDrops) {
        if (manifest == null || manifest.isEmpty()) throw new IllegalArgumentException("the heart needs a company to land");
        Built built = country(form, biome, density, seed);
        BattleSimulation sim = built.sim();
        sim.getBattleResources().disableCompoundProduction();
        // The company is broken when its people are: the Burn's guns standing on do not keep it in the fight.
        sim.addObjective(new EliminateFactionObjective(Faction.DEFENDER, Faction.MARINE).personnelOnly());
        sim.addObjective(new HeartObjective());
        List<LandingPad> pads = LandingPadSelector.select(built.map(), manifest.size(), BattleSetup.LZ_MIN_SEPARATION);
        BattleSetup.recordLzPads(sim, pads);
        // A heart is fought late in the campaign: the company it stands up is a well-found one.
        SquadCommandClaim companyClaim = SquadCommandClaim.mission(HeartAssaultCommand.ISSUER, "Company lands for the push on the heart");
        BattleSetup.landCompany(sim, pads, manifest, new Random(built.battleSeed()), companyClaim,
                InfantryLoadoutRolls::lateCampaignSquad);
        sim.setMechDrops(new MechDropService(mechDrops));
        sim.mechDrops().installScriptedCaller(Faction.MARINE,
                SquadCommandClaim.mission(HeartAssaultCommand.ISSUER, "Company's mech lance"),
                () -> MechDropService.centroidOf(sim.getMarineLandingPads()));
        sim.configureSwarmFromBores(built.battleSeed());
        WalkingBarrage barrage = null;
        // The guns' rounds and their trucks (owner, 2026-10-23): trucks come from the vehicle park where it was laid.
        HeartGuns heartGuns = new HeartGuns();
        int[] battery = null;
        if (form == Form.THE_BURN) {
            // Dry country and a steady wind toward the heart: the burn runs before it.
            sim.fireField().setTinder(true);
            sim.fireField().setWind(1f, 0f, BURN_WIND);
            List<Long> guns = burnBattery(sim, form, pads, built.map().layout, built.battleSeed());
            barrage = new WalkingBarrage(burnBarrage(), form::cellAt, Faction.MARINE, built.battleSeed() ^ BARRAGE_SALT)
                    .battery(BURN_BATTERY_STRUCTURE, guns);
            sim.addObjective(new BatteryObjective(guns));
            battery = centreOf(sim, guns);
            if (battery != null) {
                heartGuns.place(HeartGuns.BATTERY, HeartGuns.BATTERY_SHELLS, battery);
                for (long gun : guns) heartGuns.add(HeartGuns.BATTERY, gun);
                // Every barrage shell is a round off its gun's stock: a battery the trucks cannot reach thins out.
                barrage.magazine(heartGuns.stock(HeartGuns.BATTERY)::spend);
            }
        }
        HeartLanes lanes = form == Form.BROAD_FRONT ? alliedLanes(sim, form, built.battleSeed()) : null;
        // The armour waves come up the supply road from the vehicle park the layout laid, while it stands.
        HeartVehiclePark park = HeartVehiclePark.of(built.map().layout);
        if (lanes != null && park != null) {
            lanes.sentFrom(park);
            lanes.road(supplyRoad(built.map(), built.plan()));
            // The park is the lanes' base: a pair a lane ready at the opening, restocked at the waves' pace.
            int n = lanes.grounds().size();
            // The base offers every tool (owner, 2026-10-23); the lanes' commander picks by need.
            AlliedToolShed shed = new AlliedToolShed(park)
                    .stock(AlliedTool.ARMOUR_PAIR, lanes.armourMaker(), n, 2 * n,
                            Math.round(ARMOUR_WAVE_SECONDS / (float) n / BattleSimulation.TICK_DT))
                    .stock(AlliedTool.GUNSHIP, lanes.gunship().maker(), 1, 1, Math.round(GUNSHIP_RESTOCK_SECONDS / BattleSimulation.TICK_DT));
            if (density != Density.RURAL) shed.stock(AlliedTool.MECH_LANCE, lanes.mechMaker(), 1, n,
                    Math.round(2 * ARMOUR_WAVE_SECONDS / (float) n / BattleSimulation.TICK_DT));
            lanes.calledFrom(shed).mechsFirst(density != Density.RURAL);
            shed.stock(AlliedTool.AMMO_TRUCK, heartGuns.truckMaker(), AMMO_TRUCKS, AMMO_TRUCKS, AMMO_TRUCK_RESTOCK_TICKS);
            // Indirect fire for a lane whose ring cannot hurt its bore (`heart-allied-tools-next.md`): one section ready,
            // at most two, restocked at the mech lances' pace.
            shed.stock(AlliedTool.FIRE_SECTION, lanes.sectionMaker(), 1, FIRE_SECTIONS,
                    Math.round(2 * ARMOUR_WAVE_SECONDS / (float) n / BattleSimulation.TICK_DT));
            // Fresh hulls and ammunition come up the supply road: while its satellite cuts it, none reaches the park (F6).
            HeartSupplyRoad road = lanes.road();
            for (AlliedTool upTheRoad : new AlliedTool[]{AlliedTool.ARMOUR_PAIR, AlliedTool.MECH_LANCE, AlliedTool.AMMO_TRUCK,
                    AlliedTool.FIRE_SECTION}) {
                shed.suppliedWhile(upTheRoad, s -> road.open());
            }
            heartGuns.calledFrom(shed, !lanes.callsByNeed());
        } else if (park != null) {
            heartGuns.calledFrom(new AlliedToolShed(park)
                    .stock(AlliedTool.AMMO_TRUCK, heartGuns.truckMaker(), AMMO_TRUCKS, AMMO_TRUCKS, AMMO_TRUCK_RESTOCK_TICKS), true);
        }
        if (lanes != null) {
            // The military's air wing over Broad Front, called through the observer it attaches to each lane (`heart-nouns.md`).
            Precinct home = built.plan().landingPlace();
            AlliedAirWing.install(sim, home.seedX(), home.seedY(), lanes.grounds().stream()
                    .map(g -> ForwardObservers.band(Faction.ALLY, g.advancesAlongY(), g.across(), g.halfWidth())).toList());
        }
        boolean front = barrage != null && burnFront();
        HardstandAirBase airBase = null;
        if (front) {
            lanes = burnFront(sim, form, burnScreen(form, barrage.plan()), built.battleSeed());
            // The military's airbase on station behind the start line: its wing flies off the lot, called through
            // the observer each battlegroup carries (`heart-nouns.md`, Airbase and Forward observer).
            Laid lot = built.map().layout.find(HeartLayout.AIRBASE);
            int[] home = lot != null && lot.isLaid() ? middleOf(lot) : form.cellAt(0f, 0.5f);
            AlliedAirWing.install(sim, home[0], home[1], lanes.grounds().stream()
                    .map(g -> ForwardObservers.band(Faction.ALLY, g.advancesAlongY(), g.across(), g.halfWidth())).toList());
            if (lot != null && lot.isLaid()) airBase = baseWing(sim, built.map(), lot);
        }
        sim.installHeart(form, barrage, lanes, built.map().layout);
        sim.getHeart().guns(heartGuns);
        if (airBase != null) sim.getHeart().airBase(airBase);
        if (form == Form.THE_BURN) {
            Precinct start = built.plan().landingPlace();
            sim.getHeart().growBores(new HeartBoreGrowth(form, new int[]{start.seedX(), start.seedY()}, built.battleSeed()));
        }
        // Flak at the base, so the flier law can be met: the old bloom's spires and broods fly only under it.
        HeartObjectives.airDefence(sim, built.map().layout, heartGuns, battery);
        // The beats this battle runs: drawn from what its map laid, the same draw the briefing shows.
        int[] count = HeartObjectives.count(density);
        List<OptionalObjective> pool = new ArrayList<>(HeartObjectives.pool(form));
        if (!objectives.isEmpty()) {
            pool.removeIf(o -> !objectives.contains(o.key()));
            count = new int[]{pool.size(), pool.size()};
        }
        ObjectiveBoard board = new ObjectiveBoard(ObjectiveRoll.pick(pool,
                built.map().layout, built.battleSeed(), count[0], count[1]),
                ObjectiveContext.forBattle(sim, built.map().layout, built.battleSeed(), HeartSequence.BEAT_PREFIX));
        sim.installOptionalObjectives(board);
        board.start(0);
        Precinct landing = built.plan().landingPlace();
        int laneAcross = form.advancesAlongY() ? landing.seedX() : landing.seedY();
        int laneHalfWidth = form == Form.BROAD_FRONT
                ? Math.round(BROAD_FRONT_LANE_HALF_WIDTH * form.scale.width)
                : front ? Math.round(COMPANY_SECTOR_HALF_WIDTH * acrossExtent(form))
                : Math.max(form.scale.width, form.scale.height);
        // -Dbattle.heart.followBarrage=false is the control: the company goes straight for its bore.
        BarrageScreen screen = barrage == null || !Boolean.parseBoolean(System.getProperty("battle.heart.followBarrage", "true"))
                ? null : burnScreen(form, barrage.plan());
        if (form == Form.BROAD_FRONT && BROAD_FORM_UP_SECONDS > 0) screen = broadFormUp(form, landing);
        HeartLane companyLane = new HeartLane(form.advancesAlongY(), laneAcross, laneHalfWidth);
        if (form == Form.BROAD_FRONT && lanes != null && BROAD_COMPANY_ARMOUR) supportCompany(sim, form, lanes, companyLane, landing);
        if (front) lanes.companySector(companyLane);
        HeartAssaultCommand company = new HeartAssaultCommand(companyLane, screen);
        if (form == Form.BROAD_FRONT && lanes != null) company.alongside(lanes.waveGrounds());
        if (form == Form.BROAD_FRONT && BROAD_WAIT_FOR_LANES) {
            if (BROAD_FALL_BACK) company.waitingForSatellites(landing.seedX(), landing.seedY());
            else company.waitingForSatellites();
        }
        sim.setAutonomousCommander(Faction.MARINE, company, HeartAssaultCommand.DISCLOSURE);
        sim.setPlayerSupportCommandClaim(SquadCommandClaim.mission(HeartAssaultCommand.ISSUER, "Company support joins the push"));
        return sim;
    }

    /** An allied lane's band: satellites this share of the front's width either side of its line are its own. */
    static final float ALLIED_LANE_HALF_WIDTH = 0.10f;
    /** Where an allied column comes on: this far into the farmland from the start edge. */
    static final float LANE_FOOT_DEPTH = 0.05f;

    /** Broad Front's four allied lanes, each a column at its foot on the start line. */
    static HeartLanes alliedLanes(BattleSimulation sim, Form form, long seed) {
        List<HeartLane> grounds = new ArrayList<>();
        List<int[]> entries = new ArrayList<>();
        int halfWidth = Math.round(ALLIED_LANE_HALF_WIDTH * form.scale.width);
        float[] across = HeartCountry.laneAcross();
        for (float a : across) {
            int[] foot = form.cellAt(LANE_FOOT_DEPTH, a);
            grounds.add(new HeartLane(form.advancesAlongY(), form.advancesAlongY() ? foot[0] : foot[1], halfWidth));
            entries.add(foot);
        }
        if (!broadStrength()) return new HeartLanes(grounds, entries, RiskLevel.MEDIUM, seed ^ LANES_SALT);
        // The militia in strength, on its own clock: no barrage to follow, so the waves come at a steady cadence.
        int[] waves = new int[1 + BROAD_WAVES];
        waves[0] = HeartLanes.STEP_OFF_SECONDS;
        for (int w = 1; w < waves.length; w++) waves[w] = HeartLanes.STEP_OFF_SECONDS + w * LATE_WAVE_SECONDS;
        int axis = form.advancesAlongY() ? 1 : 0;
        int sign = Integer.signum(form.cellAt(1f, 0.5f)[axis] - form.cellAt(0f, 0.5f)[axis]);
        float facing = HeartLanes.advanceFacing(form.advancesAlongY(), sign);
        HeartLanes lanes = new HeartLanes(grounds, entries, BATTLEGROUP_TEAMS, waves, RiskLevel.MEDIUM, seed ^ LANES_SALT)
                .advancing(sign).armourBehind(BROAD_ARMOUR_BEHIND);
        for (int i = 0; i < across.length; i++) {
            int[] base = form.cellAt(BROAD_ARMOUR_DEPTH, across[i]);
            int[] escort = offset(base, axis, 0);
            lanes.addFittedHull(sim, i, VehicleStockFit.CONVOY_ESCORT, escort[0], escort[1], facing);
            int slot = 1;
            for (VehicleType type : List.of(VehicleType.MILITIA_TECHNICAL, VehicleType.BREAKTHROUGH_TANK)) {
                int[] at = offset(base, axis, slot++);
                lanes.addArmour(sim, i, type, at[0], at[1], facing);
            }
            for (int h = 0; h < 2; h++) {
                int[] at = offset(base, axis, slot++);
                lanes.addArmour(sim, i, null, at[0], at[1], facing);
            }
            for (int w = 1; w <= BROAD_ARMOUR_WAVES; w++) {
                int tick = Math.round(w * ARMOUR_WAVE_SECONDS / BattleSimulation.TICK_DT);
                int[] tank = offset(base, axis, 0), technical = offset(base, axis, 1);
                lanes.scheduleArmour(tick, i, VehicleType.BREAKTHROUGH_TANK, tank[0], tank[1], facing);
                lanes.scheduleArmour(tick, i, VehicleType.MILITIA_TECHNICAL, technical[0], technical[1], facing);
            }
        }
        return lanes;
    }

    /**
     * Broad Front's lanes in strength: militia waves, armour and armour waves as The Burn's battlegroups have,
     * without the barrage. {@code -Dbattle.heart.broadStrength=false} is the control, one small column a lane.
     */
    static boolean broadStrength() {
        return Boolean.parseBoolean(System.getProperty("battle.heart.broadStrength", "true"));
    }

    /**
     * How long Broad Front's company holds its landing before it goes for its own satellite, so the lanes'
     * first waves and armour are up beside it rather than behind it ({@code -Dbattle.heart.broadFormUp}, 0 for none).
     */
    static final int BROAD_FORM_UP_SECONDS = Integer.getInteger("battle.heart.broadFormUp", 120);

    /** Broad Front's company on its landing until {@link #BROAD_FORM_UP_SECONDS}: a screen with form-up ground and no guns. */
    static BarrageScreen broadFormUp(Form form, Precinct landing) {
        int axis = form.advancesAlongY() ? 1 : 0;
        int sign = Integer.signum(form.cellAt(1f, 0.5f)[axis] - form.cellAt(0f, 0.5f)[axis]);
        int along = form.advancesAlongY() ? landing.seedY() : landing.seedX();
        return new BarrageScreen(form.advancesAlongY(), sign, new int[0], new int[0], new int[0], 0, along,
                Math.round(BROAD_FORM_UP_SECONDS / BattleSimulation.TICK_DT));
    }

    /**
     * Broad Front's company gets armour in its own sector, a breakthrough tank and a Hound pair stationed on its
     * target and a fresh tank every two minutes, as the lanes either side have ({@code -Dbattle.heart.broadCompanyArmour}).
     */
    static final boolean BROAD_COMPANY_ARMOUR =
            Boolean.parseBoolean(System.getProperty("battle.heart.broadCompanyArmour", "true"));

    static void supportCompany(BattleSimulation sim, Form form, HeartLanes lanes, HeartLane companyLane, Precinct landing) {
        int axis = form.advancesAlongY() ? 1 : 0;
        int sign = Integer.signum(form.cellAt(1f, 0.5f)[axis] - form.cellAt(0f, 0.5f)[axis]);
        float facing = HeartLanes.advanceFacing(form.advancesAlongY(), sign);
        int[] base = {landing.seedX(), landing.seedY()};
        int support = lanes.addSupport(companyLane, base, sim.getSimTickIndex() ^ LANES_SALT);
        int slot = 0;
        int[] tank = offset(base, axis, slot++);
        lanes.addArmour(sim, support, VehicleType.BREAKTHROUGH_TANK, tank[0], tank[1], facing);
        for (int h = 0; h < 2; h++) {
            int[] at = offset(base, axis, slot++);
            lanes.addArmour(sim, support, null, at[0], at[1], facing);
        }
        for (int w = 1; w <= BROAD_ARMOUR_WAVES; w++)
            lanes.scheduleArmour(Math.round(w * ARMOUR_WAVE_SECONDS / BattleSimulation.TICK_DT), support,
                    VehicleType.BREAKTHROUGH_TANK, tank[0], tank[1], facing);
    }

    /** Broad Front's company holds its taken ground until every satellite is down ({@code -Dbattle.heart.broadWaitForLanes}). */
    /** Whether it waits back on its landing rather than on the ground it took ({@code -Dbattle.heart.broadFallBack}). */
    static final boolean BROAD_FALL_BACK = Boolean.parseBoolean(System.getProperty("battle.heart.broadFallBack", "true"));
    static final boolean BROAD_WAIT_FOR_LANES =
            Boolean.parseBoolean(System.getProperty("battle.heart.broadWaitForLanes", "true"));

    /** Broad Front's waves after the first, {@link #LATE_WAVE_SECONDS} apart ({@code -Dbattle.heart.broadWaves}). */
    static final int BROAD_WAVES = Integer.getInteger("battle.heart.broadWaves", 12);
    /** Where a Broad Front lane's armour is set down: on its start line, beside the column's foot. */
    static final float BROAD_ARMOUR_DEPTH = 0.03f;

    /**
     * The Burn as an allied front (`heart-nouns.md`, Allied front); {@code -Dbattle.heart.burnFront=false} is the
     * control, the company alone behind the barrage.
     */
    static boolean burnFront() {
        return Boolean.parseBoolean(System.getProperty("battle.heart.burnFront", "true"));
    }

    /** The company's sector on The Burn's front: this share of the front's width either side of its landing. */
    static final float COMPANY_SECTOR_HALF_WIDTH =
            Float.parseFloat(System.getProperty("battle.heart.companySector", "0.08"));
    /**
     * Every battlegroup gets a tank and a Hound pair as well (the measured better mix, pending the owner's pick);
     * {@code -Dbattle.heart.burnFrontArmour=standard} keeps tanks beside the company and Hounds on the flanks only.
     */
    static final boolean HEAVY_ARMOUR = !"standard".equals(System.getProperty("battle.heart.burnFrontArmour", "heavy"));
    /** A battlegroup's sector: this share of the front's width either side of its lane. */
    static final float BATTLEGROUP_HALF_WIDTH = 0.09f;
    /**
     * The planetary militia committed in strength (owner, 2026-10-22: "no need to be stingy with infantry and
     * vehicles"): teams in each wave of each battlegroup, and how many waves follow the first, one stepping off as
     * each of the barrage's first lines lifts. {@code -Dbattle.heart.burnFrontTeams} and
     * {@code -Dbattle.heart.burnFrontWaves} tune them.
     */
    static final int BATTLEGROUP_TEAMS = Integer.getInteger("battle.heart.burnFrontTeams", 4);
    static final int BATTLEGROUP_WAVES = Integer.getInteger("battle.heart.burnFrontWaves", 3);
    /**
     * Once the barrage has ended the militia keeps feeding the front against the mothers: this many more waves,
     * one every {@link #LATE_WAVE_SECONDS} ({@code -Dbattle.heart.burnFrontLateWaves}).
     */
    static final int BATTLEGROUP_LATE_WAVES = Integer.getInteger("battle.heart.burnFrontLateWaves", 8);
    static final int LATE_WAVE_SECONDS = 60;
    /**
     * How far behind each lane's aim its armour stands, per form; negative stands it ahead of its infantry.
     * Allied guns hold fire while a friend is in their line, so armour behind its own militia waits on it
     * ({@code -Dbattle.heart.burnArmourBehind}, {@code -Dbattle.heart.broadArmourBehind}).
     */
    /** Most fire sections Broad Front's base fields at once (an owner decision, `heart-allied-tools-next.md`). */
    static final int FIRE_SECTIONS = 2;
    static final int BURN_ARMOUR_BEHIND = Integer.getInteger("battle.heart.burnArmourBehind", -4);
    static final int BROAD_ARMOUR_BEHIND = Integer.getInteger("battle.heart.broadArmourBehind", 4);
    /**
     * Fresh armour for every battlegroup after the barrage, a tank and a technical at a time, this many times,
     * {@link #ARMOUR_WAVE_SECONDS} apart from the barrage's end ({@code -Dbattle.heart.burnFrontArmourWaves}); eight, since
     * allied guns hold fire through their own friends and the front needs the extra weight.
     */
    static final int BURN_ARMOUR_WAVES = Integer.getInteger("battle.heart.burnFrontArmourWaves", 8);
    /** Broad Front's armour waves, the same pairs from the opening, eight for the same reason ({@code -Dbattle.heart.broadArmourWaves}). */
    static final int BROAD_ARMOUR_WAVES = Integer.getInteger("battle.heart.broadArmourWaves", 8);
    static final int ARMOUR_WAVE_SECONDS = 120;
    /** Ammunition trucks the vehicle park holds ready, and the ticks it takes to load another. */
    static final int AMMO_TRUCKS = 1, AMMO_TRUCK_RESTOCK_TICKS = Math.round(90f / BattleSimulation.TICK_DT);

    /** The middle of the living guns' cells, or {@code null} with none. */
    static int[] centreOf(BattleSimulation sim, List<Long> guns) {
        float x = 0f, y = 0f;
        int n = 0;
        for (long gun : guns) {
            if (sim.resolveUnit(gun) == 0L) continue;
            x += sim.world().x(gun);
            y += sim.world().y(gun);
            n++;
        }
        return n == 0 ? null : new int[]{Math.round(x / n), Math.round(y / n)};
    }

    /** Seconds after a gunship sortie leaves the shed before the next is ready. */
    static final int GUNSHIP_RESTOCK_SECONDS = 240;
    /** Where a battlegroup's armour is set down: at the farmland's open front, short of where the front forms up. */
    static final float ARMOUR_DEPTH = 0.12f;

    static int acrossExtent(Form form) {
        return form.advancesAlongY() ? form.scale.width : form.scale.height;
    }

    /**
     * The Burn's allied front: a battlegroup on each lane across the approach, militia columns with their
     * armour (an escort APC, a technical, a breakthrough tank and a Hound pair in each; the standard mix keeps
     * the tanks beside the company and the Hounds on the outer flanks), all following the barrage on {@code screen}.
     */
    static HeartLanes burnFront(BattleSimulation sim, Form form, BarrageScreen screen, long seed) {
        List<HeartLane> grounds = new ArrayList<>();
        List<int[]> entries = new ArrayList<>();
        int halfWidth = Math.round(BATTLEGROUP_HALF_WIDTH * acrossExtent(form));
        float[] across = HeartCountry.laneAcross();
        for (float a : across) {
            int[] foot = form.cellAt(LANE_FOOT_DEPTH, a);
            grounds.add(new HeartLane(form.advancesAlongY(), form.advancesAlongY() ? foot[0] : foot[1], halfWidth));
            entries.add(foot);
        }
        int[] lifts = screen.liftTick();
        int early = Math.min(BATTLEGROUP_WAVES, lifts.length);
        int[] waves = new int[1 + early + BATTLEGROUP_LATE_WAVES];
        waves[0] = HeartLanes.STEP_OFF_SECONDS;
        for (int w = 1; w <= early; w++) waves[w] = Math.round(lifts[w - 1] * BattleSimulation.TICK_DT);
        int ended = Math.round(lifts[lifts.length - 1] * BattleSimulation.TICK_DT);
        for (int w = 0; w < BATTLEGROUP_LATE_WAVES; w++) waves[1 + early + w] = ended + w * LATE_WAVE_SECONDS;
        HeartLanes lanes = new HeartLanes(grounds, entries, BATTLEGROUP_TEAMS, waves, RiskLevel.MEDIUM, seed ^ LANES_SALT)
                .following(screen).armourBehind(BURN_ARMOUR_BEHIND);
        int axis = form.advancesAlongY() ? 1 : 0;
        float facing = HeartLanes.advanceFacing(form.advancesAlongY(), screen.sign());
        for (int i = 0; i < across.length; i++) {
            List<VehicleType> hulls = new ArrayList<>(List.of(VehicleType.MILITIA_TECHNICAL));
            boolean inner = i == 1 || i == across.length - 2;
            if (inner || HEAVY_ARMOUR) hulls.add(VehicleType.BREAKTHROUGH_TANK);
            // The heavy front brings a second tank and a second technical to every battlegroup.
            if (HEAVY_ARMOUR) hulls.addAll(List.of(VehicleType.BREAKTHROUGH_TANK, VehicleType.MILITIA_TECHNICAL));
            int[] base = form.cellAt(ARMOUR_DEPTH, across[i]);
            int[] escort = offset(base, axis, 0);
            lanes.addFittedHull(sim, i, VehicleStockFit.CONVOY_ESCORT, escort[0], escort[1], facing);
            int slot = 1;
            for (VehicleType type : hulls) {
                int[] at = offset(base, axis, slot++);
                lanes.addArmour(sim, i, type, at[0], at[1], facing);
            }
            for (int h = 0; (!inner || HEAVY_ARMOUR) && h < 2; h++) {
                int[] at = offset(base, axis, slot++);
                lanes.addArmour(sim, i, null, at[0], at[1], facing);
            }
            for (int w = 0; w < BURN_ARMOUR_WAVES; w++) {
                int tick = Math.round((ended + w * ARMOUR_WAVE_SECONDS) / BattleSimulation.TICK_DT);
                int[] tank = offset(base, axis, 0), technical = offset(base, axis, 1);
                lanes.scheduleArmour(tick, i, VehicleType.BREAKTHROUGH_TANK, tank[0], tank[1], facing);
                lanes.scheduleArmour(tick, i, VehicleType.MILITIA_TECHNICAL, technical[0], technical[1], facing);
            }
        }
        return lanes;
    }

    /** {@code base} moved {@code slot} places across the advance, five cells apart, alternating sides. */
    private static int[] offset(int[] base, int axis, int slot) {
        int shift = (slot % 2 == 0 ? 1 : -1) * ((slot + 1) / 2) * 5;
        return axis == 0 ? new int[]{base[0], base[1] + shift} : new int[]{base[0] + shift, base[1]};
    }

    private static final long LANES_SALT = 0x4C41_4E45_5348_5254L;

    static final float BURN_WIND = 0.5f;

    /** The middle of a laid lot reported by its two corners. */
    static int[] middleOf(Laid lot) {
        return middleOf(lot.cells());
    }

    /** The allied wing on the airbase lot: its strip, its sheds, and its apron berths, each inside the lot's fence. */
    static HardstandAirBase baseWing(BattleSimulation sim, MapResult map, Laid lot) {
        int[] lo = lot.cells().get(0), hi = lot.cells().get(1);
        Runway strip = null;
        for (Runway r : map.runways) if (inside(lo, hi, Math.round(r.centreX()), Math.round(r.centreY()))) strip = r;
        List<Gantry> sheds = new ArrayList<>();
        for (Gantry g : map.shelters) if (inside(lo, hi, g.centerX, g.centerY)) sheds.add(g);
        List<LandingPad> berths = new ArrayList<>();
        for (LandingPad p : map.landingPads) {
            if (p.purpose == LandingPad.Purpose.ALLIED_AIRFIELD && inside(lo, hi, p.centerX, p.centerY)) berths.add(p);
        }
        return AlliedAirWing.baseOnLot(sim, strip, sheds, berths);
    }

    private static boolean inside(int[] lo, int[] hi, int x, int y) {
        return x >= lo[0] && x <= hi[0] && y >= lo[1] && y <= hi[1];
    }
    /** The Burn's guns: Locust rocket-artillery batteries dug in on the start line, firing a heavier round than a post's Locust. */
    static final String BURN_BATTERY_STRUCTURE = "structure.battery-locust";
    /** Where a gun stands when its base was not laid: this far into the farmland, at its share across it. */
    static final float BURN_BATTERY_DEPTH = 0.02f;
    static final float[] BURN_BATTERY_ACROSS = {0.12f, 0.36f, 0.64f, 0.88f};
    /** Cells ahead of an ammunition dump's middle, toward the heart, its gun is dug in. */
    static final int GUN_BY_DUMP = 9;
    /** Cells behind the airbase's back fence its gun line stands, past the lot's clearance. */
    static final int GUN_BEHIND_AIRBASE = 5;

    /**
     * Digs The Burn's battery in at the bases behind the start line (owner, 2026-10-31): a gun beside each of the
     * logistics base's ammunition dumps, by its rounds, and two in a line behind the airbase's back fence, each in a
     * sandbagged pit clear of the landing pads and the airbase with a crew of the company's gunners holding it
     * ({@link BurnBattery}), and returns the guns' ids. A base the map did not lay leaves its guns on the start line at
     * their old shares. The guns fire the barrage and defend themselves, and the swarm can kill them: a battery with
     * none left is overrun, and the battle lost.
     */
    static List<Long> burnBattery(BattleSimulation sim, Form form, List<LandingPad> pads, LaidLayout layout, long seed) {
        int axis = form.advancesAlongY() ? 1 : 0;
        int sign = Integer.signum(form.cellAt(1f, 0.5f)[axis] - form.cellAt(0f, 0.5f)[axis]);
        List<int[]> wants = new ArrayList<>();
        List<int[]> keepOff = new ArrayList<>();
        Laid airbase = layout == null ? null : layout.find(HeartLayout.AIRBASE);
        for (int i = 0; i < 2; i++) {
            Laid dump = layout == null ? null : layout.find(HeartLayout.AMMO_DUMP + (i + 1));
            int[] at = dump == null || !dump.isLaid() ? null : middleOf(dump.cells());
            if (at != null) at[axis] += sign * GUN_BY_DUMP;
            wants.add(at != null ? at : form.cellAt(BURN_BATTERY_DEPTH, BURN_BATTERY_ACROSS[1 + i]));
        }
        if (airbase != null && airbase.isLaid()) {
            int[] lo = airbase.cells().get(0), hi = airbase.cells().get(1);
            int clear = AirbaseLot.Size.STATION.clearance();
            keepOff.add(new int[]{lo[0] - clear, lo[1] - clear, hi[0] + clear, hi[1] + clear});
            int back = (sign > 0 ? hi[axis] + clear : lo[axis] - clear) + sign * GUN_BEHIND_AIRBASE;
            int acrossLo = lo[1 - axis], acrossHi = hi[1 - axis];
            for (int q : new int[]{1, 3}) {
                int across = acrossLo + (acrossHi - acrossLo) * q / 4;
                wants.add(axis == 0 ? new int[]{back, across} : new int[]{across, back});
            }
        } else {
            wants.add(form.cellAt(BURN_BATTERY_DEPTH, BURN_BATTERY_ACROSS[0]));
            wants.add(form.cellAt(BURN_BATTERY_DEPTH, BURN_BATTERY_ACROSS[3]));
        }
        return BurnBattery.dig(sim, form, pads, keepOff, BURN_BATTERY_STRUCTURE, wants, seed ^ BATTERY_SALT);
    }

    /** The middle of the cells a piece was reported by, or null with none. */
    static int[] middleOf(List<int[]> cells) {
        if (cells.isEmpty()) return null;
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
        for (int[] c : cells) {
            x0 = Math.min(x0, c[0]);
            y0 = Math.min(y0, c[1]);
            x1 = Math.max(x1, c[0]);
            y1 = Math.max(y1, c[1]);
        }
        return new int[]{(x0 + x1) / 2, (y0 + y1) / 2};
    }

    private static final long BATTERY_SALT = 0x4755_4E50_4954_5321L;
    /**
     * How far short of the firing line The Burn's company stands: the shell's
     * own danger-close veto (blast, margin and the walk a body makes while a round
     * flies across the map) plus the line's spread, so the company is never what
     * stops the guns.
     */
    static final int BURN_FOLLOW_CELLS = 18;
    /** Where The Burn's company forms up before the guns have walked off the first line: on its own farmland. */
    static final float BURN_FORM_UP_DEPTH = 0.15f;

    /** The Burn's company following its barrage: each line's ground along the advance and its timetable. */
    static BarrageScreen burnScreen(Form form, WalkingBarrage.Plan plan) {
        int axis = form.advancesAlongY() ? 1 : 0;
        int sign = Integer.signum(form.cellAt(1f, 0.5f)[axis] - form.cellAt(0f, 0.5f)[axis]);
        List<WalkingBarrage.Line> lines = plan.lines();
        int[] along = new int[lines.size()], open = new int[lines.size()], lift = new int[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            WalkingBarrage.Line line = lines.get(i);
            along[i] = form.cellAt(line.depth(), 0.5f)[axis];
            open[i] = Math.round(line.openSeconds() / BattleSimulation.TICK_DT);
            lift[i] = Math.round((line.openSeconds() + line.fireSeconds()) / BattleSimulation.TICK_DT);
        }
        if (!Boolean.parseBoolean(System.getProperty("battle.heart.holdBack", "true")) || lines.isEmpty())
            return new BarrageScreen(form.advancesAlongY(), sign, along, open, lift, BURN_FOLLOW_CELLS);
        // The company forms on the cordon's open farmland, out of the surges' path, until the first line lifts.
        return new BarrageScreen(form.advancesAlongY(), sign, along, open, lift, BURN_FOLLOW_CELLS,
                form.cellAt(BURN_FORM_UP_DEPTH, 0.5f)[axis], lift[0]);
    }

    private static final long BARRAGE_SALT = 0x4255_524E_4241_5252L;
    /** The first line opens once the company is down and formed, just past the near ride. */
    static final float BURN_FIRST_OPEN_SECONDS = 40f;
    static final float BURN_FIRST_FIRE_SECONDS = 50f;
    static final float BURN_LINE_FIRE_SECONDS = 40f;
    /** The barrage's last line: short of the mothers, round the workings past three quarters of the way in. */
    static final float BURN_LAST_DEPTH = 0.70f;
    /** Lines lift about this far at a time between the firebreaks. */
    static final float BURN_LINE_STEP = 0.06f;
    /** A line this close to a firebreak's own line gives way to it. */
    static final float BURN_MERGE = 0.025f;
    /** How far past a firebreak's far edge its relighting line falls: clear of the shells' scatter, so the near side never catches. */
    static final float BURN_RELIGHT_PAST = 0.02f;
    /** The shells that relight the burn past a firebreak. */
    static final int BURN_RELIGHT_HEAT = 420;

    /**
     * The Burn's barrage plan (`heart-nouns.md`, Barrage plan): the burn is
     * lit and relit just past each firebreak with incendiary shells (the
     * fire holds at each break, the owner's pick), with high explosive lifting
     * a line at a time between them to a last line short of the heart.
     */
    public static WalkingBarrage.Plan burnBarrage() {
        float[] breaks = HeartCountry.burnFirebreaks();
        List<float[]> depths = new ArrayList<>();
        for (float b : breaks) if (b + BURN_RELIGHT_PAST <= BURN_LAST_DEPTH) depths.add(new float[]{b + BURN_RELIGHT_PAST, 1f});
        for (float d = breaks[0] + BURN_RELIGHT_PAST + BURN_LINE_STEP; d <= BURN_LAST_DEPTH + 1e-4f; d += BURN_LINE_STEP) {
            boolean near = false;
            for (float[] line : depths) if (Math.abs(line[0] - d) < BURN_MERGE) near = true;
            if (!near) depths.add(new float[]{d, 0f});
        }
        depths.sort((a, b) -> Float.compare(a[0], b[0]));
        List<WalkingBarrage.Line> lines = new ArrayList<>();
        float open = BURN_FIRST_OPEN_SECONDS;
        for (int i = 0; i < depths.size(); i++) {
            float fire = i == 0 ? BURN_FIRST_FIRE_SECONDS : BURN_LINE_FIRE_SECONDS;
            lines.add(new WalkingBarrage.Line(depths.get(i)[0], open, fire, depths.get(i)[1] > 0f ? BURN_RELIGHT_HEAT : 0));
            open += fire;
        }
        return new WalkingBarrage.Plan(lines, new WalkingBarrage.Shell(3f, 70f, 20f, 60, 3f), 2.5f, 0.012f);
    }

    /** What setup made: the battle, its map and plan, the heart's sites and the seed the attempt used. */
    public record Built(BattleSimulation sim, MapResult map, PrecinctPlan plan, HeartBores heart, long battleSeed) { }
}
