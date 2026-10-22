package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.evacuation.HeartBores;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;

import java.util.Collections;
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

    /** What setup made: the battle, its map and plan, the heart's sites and the seed the attempt used. */
    public record Built(BattleSimulation sim, MapResult map, PrecinctPlan plan, HeartBores heart, long battleSeed) { }
}
