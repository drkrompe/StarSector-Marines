package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.sim.MovementService;
import com.dillon.starsectormarines.battle.unit.BodyCarrier;
import com.dillon.starsectormarines.battle.unit.BodyService;

/**
 * A body's ground velocity in cells per second, whatever is carrying it: the carrier's own
 * motion for an embarked or vehicle-borne body, the movement pass's applied velocity for one
 * on foot, and zero for a body that does not move. One answer shared by every shooter that
 * leads a target, so a led direct round and a led mortar bomb extrapolate the same motion.
 *
 * <p>The movement velocity is rewritten during the same tick a shooter may read it, so a
 * read is an extrapolation hint off by at most one tick's travel, never an invariant. Reads
 * only.
 */
public final class TargetMotion {

    private TargetMotion() {}

    public static float velocityX(BodyService bodies, MovementService movement, long id) {
        BodyCarrier carrier = bodies.carrierOf(id);
        if (carrier != null) return carrier.velocityX(id);
        return movement.has(id) ? movement.velX(id) : 0f;
    }

    public static float velocityY(BodyService bodies, MovementService movement, long id) {
        BodyCarrier carrier = bodies.carrierOf(id);
        if (carrier != null) return carrier.velocityY(id);
        return movement.has(id) ? movement.velY(id) : 0f;
    }
}
