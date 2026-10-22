package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.command.HeartAssaultCommand;
import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.command.objective.EliminateFactionObjective;
import com.dillon.starsectormarines.battle.command.objective.HeartObjective;
import com.dillon.starsectormarines.battle.evacuation.HeartBores;
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
     * every mother has collapsed and lost when the company is broken.
     */
    public static BattleSimulation mission(Form form, long seed, List<ShuttleAssignment> manifest) {
        if (manifest == null || manifest.isEmpty()) throw new IllegalArgumentException("the heart needs a company to land");
        Built built = country(form, seed);
        BattleSimulation sim = built.sim();
        sim.getBattleResources().disableCompoundProduction();
        sim.addObjective(new EliminateFactionObjective(Faction.DEFENDER, Faction.MARINE));
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
            barrage = new WalkingBarrage(burnBarrage(), form::cellAt, Faction.MARINE, built.battleSeed() ^ BARRAGE_SALT);
        }
        sim.installHeart(form, barrage);
        Precinct landing = built.plan().landingPlace();
        int laneAcross = form.advancesAlongY() ? landing.seedX() : landing.seedY();
        int laneHalfWidth = form == Form.BROAD_FRONT
                ? Math.round(BROAD_FRONT_LANE_HALF_WIDTH * form.scale.width)
                : Math.max(form.scale.width, form.scale.height);
        sim.setAutonomousCommander(Faction.MARINE, new HeartAssaultCommand(form.advancesAlongY(), laneAcross, laneHalfWidth),
                HeartAssaultCommand.DISCLOSURE);
        sim.setPlayerSupportCommandClaim(SquadCommandClaim.mission(HeartAssaultCommand.ISSUER, "Company support joins the push"));
        return sim;
    }

    static final float BURN_WIND = 0.5f;
    private static final long BARRAGE_SALT = 0x4255_524E_4241_5252L;
    /** The first line opens once the company is down and formed, on the near ride. */
    static final float BURN_FIRST_OPEN_SECONDS = 40f;
    static final float BURN_FIRST_FIRE_SECONDS = 50f;
    static final float BURN_LINE_FIRE_SECONDS = 40f;
    static final float BURN_FIRST_DEPTH = 0.30f, BURN_LAST_DEPTH = 0.66f, BURN_LINE_STEP = 0.06f;
    /** The opening shoot is incendiary (owner's pick): it lights the line. */
    static final int BURN_OPENING_HEAT = 420;

    /**
     * The Burn's barrage plan (`heart-nouns.md`, Barrage plan): an incendiary
     * opening shoot on the near ride, then high explosive lifting a line at a
     * time across the approach to a last line short of the heart.
     */
    public static WalkingBarrage.Plan burnBarrage() {
        List<WalkingBarrage.Line> lines = new ArrayList<>();
        float open = BURN_FIRST_OPEN_SECONDS;
        int count = Math.round((BURN_LAST_DEPTH - BURN_FIRST_DEPTH) / BURN_LINE_STEP) + 1;
        for (int i = 0; i < count; i++) {
            float fire = i == 0 ? BURN_FIRST_FIRE_SECONDS : BURN_LINE_FIRE_SECONDS;
            lines.add(new WalkingBarrage.Line(BURN_FIRST_DEPTH + i * BURN_LINE_STEP, open, fire,
                    i == 0 ? BURN_OPENING_HEAT : 0));
            open += fire;
        }
        return new WalkingBarrage.Plan(lines, new WalkingBarrage.Shell(3f, 70f, 20f, 60, 3f), 2.5f, 0.015f);
    }

    /** What setup made: the battle, its map and plan, the heart's sites and the seed the attempt used. */
    public record Built(BattleSimulation sim, MapResult map, PrecinctPlan plan, HeartBores heart, long battleSeed) { }
}
