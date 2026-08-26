package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenRecipe;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FinalizeStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.InitSolidStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.TacticalLinkStage;
import com.dillon.starsectormarines.battle.world.gen.ship.stage.DeckEndSpawnStage;
import com.dillon.starsectormarines.battle.world.gen.ship.stage.HullProfileStage;
import com.dillon.starsectormarines.battle.world.gen.ship.stage.SpineStage;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.List;
import java.util.Random;

/**
 * The longitudinal ship-deck family — the third interior premise beside the
 * city and the station.
 *
 * <p>A station organizes space around a core: concentric rings, converging
 * ports, radial depth, mirrored geometry. A ship organizes it around an
 * <b>axis</b>. The deck runs bow to stern along +x with a beam that varies by
 * frame, a fore-aft spine every compartment hangs off, and an assault gradient
 * that runs along the length rather than inward from a perimeter.
 *
 * <p>Like the station entries this is not yet on the {@code MapGenerator}
 * interface — no production caller selects ship decks, and the tests drive it
 * directly. See {@code ship-interiors-nouns.md} and {@code ship-deck-family.md}.
 */
public final class ShipDeckGenerator {

    /** Walkable width of the spine corridor. Four cells lets a squad pass a stalled one without the corridor becoming a room. */
    public static final int SPINE_WIDTH = 4;

    private final GenRecipe deckRecipe = buildDeckRecipe();
    private DeckProfile lastDeckProfile;

    /**
     * The ship-deck recipe. It shares the station's solid-default inversion and
     * its generic tail, and replaces the core-organized middle with the
     * axis-organized one.
     */
    private GenRecipe buildDeckRecipe() {
        return new GenRecipe("ShipDeck", List.of(
                new InitSolidStage(),                    // solid hull
                new HullProfileStage(SPINE_WIDTH),       // beam per frame + zones; publishes the profile
                new SpineStage(),                        // carve the fore-aft corridor
                new DeckEndSpawnStage(),                 // bow / stern anchors
                new TacticalLinkStage(),                 // (no nodes yet -> empty map)
                new FinalizeStage()));                   // wall HP / cover / wall tags / buildings
    }

    /** Generate one deck. Identical inputs produce an identical deck. */
    public MapResult generateDeck(int width, int height, long seed) {
        Random rng = new Random(seed);
        NavigationGrid grid = new NavigationGrid(width, height);
        CellTopology topology = new CellTopology(width, height);

        GenContext ctx = new GenContext(grid, topology, rng, width, height, seed);
        ctx.put(BspKeys.MARKET_PROFILE, TargetProfile.NEUTRAL);

        deckRecipe.run(ctx);

        this.lastDeckProfile = ctx.get(ShipKeys.DECK_PROFILE);

        Buildings buildings = ctx.get(BspKeys.BUILDINGS);
        TacticalMap tacticalMap = ctx.get(BspKeys.TACTICAL_MAP);
        int[] marine = ctx.get(BspKeys.MARINE_SPAWN);
        int[] defender = ctx.get(BspKeys.DEFENDER_SPAWN);

        return new MapResult(grid, topology,
                marine[0], marine[1], defender[0], defender[1],
                ctx.pois, ctx.doodads, tacticalMap, buildings);
    }

    /** The profile behind the most recent {@link #generateDeck} run; null before the first. */
    public DeckProfile getLastDeckProfile() {
        return lastDeckProfile;
    }
}
