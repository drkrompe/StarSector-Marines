package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.decision.TacticalNode;

import java.util.List;

/**
 * Relay Nine's power relay works, the authored small territorial profile: a western boat apron, the switch house
 * (Power Yard), the gate depot (Freight Gate) and the relay hall (Relay Hall) inside a ring road, a screened road
 * reserve to the south. Its layout is an owned map, {@code data/world/maps/relay-nine.map.json}
 * ({@code owned-maps.md}), dressed for the world it is fought on. Decoration never adds capture requirements.
 */
public final class RelayNineCompound {
    public static final int WIDTH = 208, HEIGHT = 128;
    /** The map's sites in order: the apron, then the three the defence holds. Names match the owned map's. */
    public static final List<String> SITE_NAMES = List.of("Western apron", "Power Yard", "Freight Gate", "Relay Hall");
    public record Layout(MapResult map, List<TacticalNode> sites) {
        public Layout { sites = List.copyOf(sites); }
    }
    private RelayNineCompound() {}

    /**
     * Relay Nine on its owned map: the layout read from {@code relay-nine.map.json}, dressed for the target world by
     * its {@link BiomeSkin}. Its four sites are the map's, apron first.
     */
    public static Layout generate(long seed, TargetProfile profile) {
        OwnedMapRecipe.Built built = OwnedMapRecipe.build(OwnedMapCatalog.map(OwnedMapCatalog.RELAY_NINE), profile, seed);
        // The compounds' sandbagged doors face the landing: positions the defence and a company that takes them can man.
        CompoundPreparedGround.layForSites(built.context(), built.sites(), built.map().origin().x(), built.map().origin().y());
        return new Layout(OwnedMapRecipe.result(built), built.sites());
    }
}
