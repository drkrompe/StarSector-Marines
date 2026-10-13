package com.dillon.starsectormarines.battle.fort;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * How a fort's gatehouse is drawn: a cap on each flanking tower and the leaves of the gate itself. Authored on the
 * structure that is the fort's platform, so a fort kind names its own gatehouse. Pieces are authored in the layout's
 * west-gate frame, two cells across and three along the wall, and turned with the fort.
 *
 * <p>A tower cap is drawn only while the tower's walls stand: a breached tower is rubble, and a cap over rubble would
 * be a lie. A hurt tower (any of its walls under {@link #DAMAGED_BELOW} of its full strength) takes the damaged cap.
 */
public final class FortGatehouseArt {

    /** Fraction of a tower wall's full strength below which the tower is drawn damaged. */
    public static final float DAMAGED_BELOW = 0.6f;

    private final String tower;
    private final String towerDamaged;
    private final String gate;

    private FortGatehouseArt(String tower, String towerDamaged, String gate) {
        this.tower = tower;
        this.towerDamaged = towerDamaged;
        this.gate = gate;
    }

    public static FortGatehouseArt parse(String structureId, JSONObject json) throws JSONException {
        return new FortGatehouseArt(require(structureId, json, "tower"), require(structureId, json, "towerDamaged"),
                require(structureId, json, "gate"));
    }

    private static String require(String structureId, JSONObject json, String key) throws JSONException {
        String value = json.optString(key, "").trim();
        if (value.isEmpty()) {
            throw new JSONException("Structure '" + structureId + "' gatehouse art is missing '" + key + "'");
        }
        return value;
    }

    /** The tower cap for a tower whose weakest wall has {@code wallFraction} of its strength left. */
    public String towerFor(float wallFraction) {
        return wallFraction < DAMAGED_BELOW ? towerDamaged : tower;
    }

    /** The gate leaves, drawn over the open passage. */
    public String gate() {
        return gate;
    }

    public List<String> spritePaths() {
        return List.of(tower, towerDamaged, gate);
    }
}
