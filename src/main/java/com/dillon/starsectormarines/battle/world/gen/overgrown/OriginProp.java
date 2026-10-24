package com.dillon.starsectormarines.battle.world.gen.overgrown;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.PreparedGround;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

/**
 * The props that tell, without words, how a heart's bloom got where it is (`heart-origins.md`): one kit per origin,
 * each with a {@linkplain #telling telling object} the first mother opens at. The art is the {@code origin-*}
 * sheets (`art-source/heart-origins`); a layout places a prop by kind and state here and never names a sprite.
 *
 * <p>{@link State#GROWN} is the prop as the heart finds it, weeks inside the bloom, and the only state with a
 * battle-damage pair ({@code -damaged}, {@code -destroyed}, through {@code PropDamage}). The others are static.
 */
public enum OriginProp {
    // Workings: the reservoir pumps went deep and cut into it.
    SHAFT("origin-workings.shaft", true),
    PUMP_SHED("origin-workings.pump-shed", false),
    PIPE_RUN("origin-workings.pipe-run", false),
    PIPE_ELBOW("origin-workings.pipe-elbow", false),
    /** Where the yard's pipeline goes down into the ground. */
    PIPE_MOUTH("origin-workings.pipe-mouth", false),
    // Freight yard: it rode in on cargo.
    SPLIT_CONTAINER("origin-freight.split-container", true),
    CONTAINER_RUST("origin-freight.container-rust", false),
    CONTAINER_BLUE("origin-freight.container-blue", false),
    /** A pallet of seafood crates; grown, burst and empty. */
    SEAFOOD_CRATES("origin-freight.seafood-crates", false),
    /** The crane's overhead bridge: coverless, laid over the ground between its two {@link #GANTRY_LEG legs}. */
    GANTRY_SPAN("origin-freight.gantry-span", false),
    GANTRY_LEG("origin-freight.gantry-leg", false),
    /** A flatbed stopped mid-unload, cab east, its box half off the back. */
    UNLOADING_TRUCK("origin-freight.unloading-truck", false),
    // Research site: someone was studying it.
    ANNEX("origin-lab.annex", true),
    TANK_RACK("origin-lab.tank-rack", false),
    SAMPLE_FREEZER("origin-lab.sample-freezer", false),
    // Park: nobody's fault, the colony built over it.
    BANDSTAND("origin-park.bandstand", true),
    /** The pond, drained and torn open from below: where the first mother can open instead. */
    POND("origin-park.pond", true),
    SWINGS("origin-park.swings", false),
    SLIDE("origin-park.slide", false),
    CLIMBER("origin-park.climber", false),
    ROUNDABOUT("origin-park.roundabout", false),
    BENCH("origin-park.bench", false);

    /** How far the bloom has the prop. */
    public enum State {
        /** The thing before the bloom. */
        CLEAN("-clean"),
        /** As the heart finds it: what happened to it, and weeks of creep on it. */
        GROWN(""),
        /** A telling object under its thickest growth, half gone. */
        SWALLOWED("-swallowed");

        final String suffix;

        State(String suffix) { this.suffix = suffix; }
    }

    /** The grown state's doodad id; the battle-damage pair hangs off it. */
    public final String id;
    /** The object the story is told by, which has a {@link State#SWALLOWED} state. */
    public final boolean telling;

    OriginProp(String id, boolean telling) {
        this.id = id;
        this.telling = telling;
    }

    public String id(State state) {
        if (state == State.SWALLOWED && !telling) throw new IllegalArgumentException(this + " is not a telling object");
        return id + state.suffix;
    }

    public DoodadDef def(State state) {
        return TileRegistry.installed().doodad(id(state));
    }

    /** Puts the prop down with its low corner at the world cell; see {@link PreparedGround#prop} for what it closes. */
    public boolean place(GenContext ctx, int x, int y, int quarterTurns, State state) {
        return PreparedGround.prop(ctx, x, y, def(state), quarterTurns);
    }
}
