package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.command.HeartAssaultCommand;
import com.dillon.starsectormarines.battle.command.HeartLane;
import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.command.objective.BatteryObjective;
import com.dillon.starsectormarines.battle.command.objective.EliminateFactionObjective;
import com.dillon.starsectormarines.battle.command.objective.HeartObjective;
import com.dillon.starsectormarines.battle.evacuation.HeartBores;
import com.dillon.starsectormarines.battle.evacuation.HeartLanes;
import com.dillon.starsectormarines.battle.power.WalkingBarrage;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.ops.RiskLevel;

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
 * <p>The country is {@link HeartCountry}, on a living world. Its places are
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

    private BloomHeartSetup() { }

    /** A living world with no market behind it: the heart is the same kind of place on any green world. */
    public static TargetProfile verdant() {
        return new TargetProfile(0, 0, 0, 0, "", Set.of(), SurfacePalette.VERDANT, SettlementLink.ROAD);
    }

    /** The heart's country, bores and matured bloom, at tick zero. */
    public static Built country(Form form, long seed) {
        TargetProfile profile = verdant();
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            long battleSeed = seed + attempt * 0x9E3779B97F4A7C15L;
            PrecinctPlan plan = HeartCountry.planFor(form, battleSeed);
            MapResult map = MAP_GEN.generate(form.scale.width, form.scale.height, battleSeed, null, profile, plan);
            Precinct workings = HeartCountry.place(plan, HeartCountry.WORKINGS);
            Precinct landing = plan.landingPlace();
            HeartBores heart = HeartBores.plan(map.grid, map.topology, form,
                    new int[]{workings.seedX(), workings.seedY()},
                    new int[]{landing.seedX(), landing.seedY()}, battleSeed ^ BORE_SALT);
            if (heart == null) continue;
            BattleSimulation sim = BattleSetup.buildMap(map, Collections.emptyList(),
                    Collections.emptyList(), battleSeed).sim();
            sim.setSurface(profile.surface());
            heart.open(sim);
            sim.matureCreep();
            return new Built(sim, map, plan, heart, battleSeed);
        }
        throw new IllegalStateException("no heart country of form " + form + " seated its bores in "
                + ATTEMPTS + " attempts from seed " + seed);
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
    public static BattleSimulation mission(Form form, long seed, List<ShuttleAssignment> manifest) {
        if (manifest == null || manifest.isEmpty()) throw new IllegalArgumentException("the heart needs a company to land");
        Built built = country(form, seed);
        BattleSimulation sim = built.sim();
        sim.getBattleResources().disableCompoundProduction();
        // The company is broken when its people are: the Burn's guns standing on do not keep it in the fight.
        sim.addObjective(new EliminateFactionObjective(Faction.DEFENDER, Faction.MARINE).personnelOnly());
        sim.addObjective(new HeartObjective());
        List<LandingPad> pads = LandingPadSelector.select(built.map(), manifest.size(), BattleSetup.LZ_MIN_SEPARATION);
        BattleSetup.recordLzPads(sim, pads);
        BattleSetup.landCompany(sim, pads, manifest, new Random(built.battleSeed()),
                SquadCommandClaim.mission(HeartAssaultCommand.ISSUER, "Company lands for the push on the heart"));
        sim.configureSwarmFromBores(built.battleSeed());
        WalkingBarrage barrage = null;
        if (form == Form.THE_BURN) {
            // Dry country and a steady wind toward the heart: the burn runs before it.
            sim.fireField().setTinder(true);
            sim.fireField().setWind(1f, 0f, BURN_WIND);
            List<Long> guns = burnBattery(sim, form, pads, built.battleSeed());
            barrage = new WalkingBarrage(burnBarrage(), form::cellAt, Faction.MARINE, built.battleSeed() ^ BARRAGE_SALT)
                    .battery(BURN_BATTERY_STRUCTURE, guns);
            sim.addObjective(new BatteryObjective(guns));
        }
        HeartLanes lanes = form == Form.BROAD_FRONT ? alliedLanes(form, built.battleSeed()) : null;
        sim.installHeart(form, barrage, lanes);
        Precinct landing = built.plan().landingPlace();
        int laneAcross = form.advancesAlongY() ? landing.seedX() : landing.seedY();
        int laneHalfWidth = form == Form.BROAD_FRONT
                ? Math.round(BROAD_FRONT_LANE_HALF_WIDTH * form.scale.width)
                : Math.max(form.scale.width, form.scale.height);
        sim.setAutonomousCommander(Faction.MARINE, new HeartAssaultCommand(new HeartLane(form.advancesAlongY(), laneAcross, laneHalfWidth)),
                HeartAssaultCommand.DISCLOSURE);
        sim.setPlayerSupportCommandClaim(SquadCommandClaim.mission(HeartAssaultCommand.ISSUER, "Company support joins the push"));
        return sim;
    }

    /** An allied lane's band: satellites this share of the front's width either side of its line are its own. */
    static final float ALLIED_LANE_HALF_WIDTH = 0.10f;
    /** Where an allied column comes on: this far into the farmland from the start edge. */
    static final float LANE_FOOT_DEPTH = 0.05f;

    /** Broad Front's four allied lanes, each a column at its foot on the start line. */
    static HeartLanes alliedLanes(Form form, long seed) {
        List<HeartLane> grounds = new ArrayList<>();
        List<int[]> entries = new ArrayList<>();
        int halfWidth = Math.round(ALLIED_LANE_HALF_WIDTH * form.scale.width);
        for (float across : HeartCountry.laneAcross()) {
            int[] foot = form.cellAt(LANE_FOOT_DEPTH, across);
            grounds.add(new HeartLane(form.advancesAlongY(), form.advancesAlongY() ? foot[0] : foot[1], halfWidth));
            entries.add(foot);
        }
        return new HeartLanes(grounds, entries, RiskLevel.MEDIUM, seed ^ LANES_SALT);
    }

    private static final long LANES_SALT = 0x4C41_4E45_5348_5254L;

    static final float BURN_WIND = 0.5f;
    /** The Burn's guns: Locust rocket-artillery batteries dug in on the start line, firing a heavier round than a post's Locust. */
    static final String BURN_BATTERY_STRUCTURE = "structure.battery-locust";
    /** Where the guns stand: this far into the farmland, at these shares across it. */
    static final float BURN_BATTERY_DEPTH = 0.02f;
    static final float[] BURN_BATTERY_ACROSS = {0.12f, 0.36f, 0.64f, 0.88f};

    /**
     * Digs The Burn's battery in on the start line, one gun at each share
     * across, each in a sandbagged pit clear of the landing pads with a crew of
     * the company's gunners holding it ({@link BurnBattery}), and returns the
     * guns' ids. The guns fire the barrage and defend themselves, and the swarm
     * can kill them: a battery with none left is overrun, and the battle lost.
     */
    static List<Long> burnBattery(BattleSimulation sim, Form form, List<LandingPad> pads, long seed) {
        return BurnBattery.dig(sim, form, pads, BURN_BATTERY_STRUCTURE, BURN_BATTERY_DEPTH, BURN_BATTERY_ACROSS,
                seed ^ BATTERY_SALT);
    }

    private static final long BATTERY_SALT = 0x4755_4E50_4954_5321L;
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
