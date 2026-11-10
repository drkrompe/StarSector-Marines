package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.SurfaceField;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

/**
 * Weathers a dead site ({@link RuinsWeather}): the last skin on a ruins ground, after the streets are finished, so a
 * drift never takes a carriageway's finish. Inert on a map without a delve.
 */
public final class RuinsWeatherStage implements GenStage {

    /**
     * Control-run switch for the weathering A/B (on by default). Temporary: remove it, and the off arm, once the owner
     * decides the pick {@code ruins-weather}.
     */
    public static final String PROPERTY = "battle.ruins.weather";

    @Override
    public void run(GenContext ctx) {
        RuinsDelve delve = ctx.get(RuinsKeys.DELVE);
        if (delve == null || !Boolean.parseBoolean(System.getProperty(PROPERTY, "true"))) return;
        RuinsWeather.apply(ctx.topology, ctx.grid, delve, SurfaceField.of(ctx), TileRegistry.installed(), ctx.doodads);
    }
}
