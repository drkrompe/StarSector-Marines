package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip.BoatBayState;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Hulls for tests and visual evidence to be drawn on.
 *
 * <p>In a campaign the company lives aboard a ship out of the player's own
 * fleet, so there is no production hull to point a test at. These stand in:
 * ordinary ships with the proportions and complements of the vanilla hulls they
 * are named for, so a deck generated on one is a deck the game could really
 * produce.
 */
public final class TestHulls {

    private TestHulls() { }

    /** A light troop transport: few hands to fly her, most of the hull is lift. */
    public static CompanyShip transport() {
        return new CompanyShip(HullClass.CRUISER, HullRole.TROOP_TRANSPORT,
                10, 250, 50, 0.28f);
    }

    /**
     * Second Wind, the company's own Cerberus, with every compartment she has
     * open: the converted frigate the campaign starts aboard, drawn on the
     * authored arrangement rather than a generic program.
     *
     * <p>Small enough to lay out in a fraction of a transport's time, which is
     * what a screen photographed over a ship wants. Her outline is vanilla's
     * collision polygon, read the way the campaign resolver reads it.
     *
     * @param starsectorCore the game's {@code starsector-core}, for the hull file
     */
    public static CompanyShip secondWind(Path starsectorCore) throws IOException, JSONException {
        var spec = new JSONObject(Files.readString(starsectorCore.resolve("data/hulls/warhound.ship")));
        var bounds = spec.getJSONArray("bounds");
        float[] polygon = new float[bounds.length()];
        for (int i = 0; i < polygon.length; i++) polygon[i] = (float) bounds.getDouble(i);
        var outline = HullOutline.fromBounds(polygon, "cerberus");
        return new CompanyShip(HullClass.FRIGATE, HullRole.FREIGHTER, 20, 68, 75,
                outline.aspect(), outline, spec.getString("spriteName"), true, BoatBayState.RESTORED);
    }
}
