package com.dillon.starsectormarines.marine;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

/**
 * The sensor sweep's authored numbers: how far the suit reads while the system
 * is running, and how far into the walls in front of it that read carries
 * ({@code progression-nouns.md}).
 *
 * <p>Both numbers are bounds, not authorities. A sweep contributes a temporary
 * observer to the player's reveal on exactly the terms a shuttle or a recon
 * ping already does ({@code fog-of-war-nouns.md}): the same shadowcast, the same
 * reference count, the same expiry. It reveals; it does not target, mark, or
 * follow anything, and nothing downstream of it is allowed to.
 *
 * <p><b>The wall read is bounded below the range on purpose.</b> A read that
 * carried through walls as far as it carried through air would not be a sweep,
 * it would be an x-ray: the rim of the disc would show the far side of the
 * block and there would be nothing left for a wall to do. Keeping the tolerant
 * core strictly inside the range means the sweep opens the room the wearer is
 * standing against and the next one over, and then walls start working again.
 */
public record PerceptionSweepSpec(float revealRangeCells, float wallReadRadiusCells)
        implements Serializable {

    static PerceptionSweepSpec parse(JSONObject json, String armorId, String systemId)
            throws JSONException {
        float range = IntegralSystemDef.positive(json, "revealRangeCells", armorId);
        if (!json.has("wallReadRadiusCells")) {
            throw new JSONException("Sensor sweep '" + systemId + "' on armor '" + armorId
                    + "' must author wallReadRadiusCells: how far a read carries into the walls"
                    + " in front of it is a judgement about the suit's sensors, not a constant."
                    + " Author 0 for a sweep that only sees further and wider.");
        }
        float wallRead = (float) json.getDouble("wallReadRadiusCells");
        if (!Float.isFinite(wallRead) || wallRead < 0f) {
            throw new JSONException("Sensor sweep '" + systemId + "' on armor '" + armorId
                    + "' field 'wallReadRadiusCells' must be finite and not negative");
        }
        if (wallRead >= range) {
            throw new JSONException("Sensor sweep '" + systemId + "' on armor '" + armorId
                    + "' would read through every wall it can reach: wallReadRadiusCells must"
                    + " stay below revealRangeCells, or the sweep is an x-ray rather than a"
                    + " sweep and cover has stopped meaning anything at its rim.");
        }
        return new PerceptionSweepSpec(range, wallRead);
    }
}
