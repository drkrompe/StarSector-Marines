package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirBody;

import java.util.ArrayList;
import java.util.List;

/**
 * How a piloted hull moves: the pilot asks for a travel direction, and the hull
 * does what its own table lets it. It accelerates toward the demanded velocity at
 * the hull's acceleration, brakes at its deceleration when nothing is demanded,
 * never exceeds its table speed, and turns at its own turn rate toward where it
 * travels. Nothing teleports; the map's edge stops it.
 */
public final class WarshipHelm {

    /** Below this speed, in cells per second, a hull has no travel direction worth turning to. */
    static final float HEADING_MIN_SPEED = 0.05f;

    private WarshipHelm() {}

    /** Advances the hull's position and velocity by one step of the pilot's demand, keeping its centre on the map. */
    static void drive(Warship ship, float dt, float mapWidth, float mapHeight) {
        float demandX = ship.pilotMoveX();
        float demandY = ship.pilotMoveY();
        boolean demanding = demandX != 0f || demandY != 0f;
        float wantX = demandX * ship.maxSpeed;
        float wantY = demandY * ship.maxSpeed;
        float ax = wantX - ship.velocityX;
        float ay = wantY - ship.velocityY;
        float change = (float) Math.sqrt(ax * ax + ay * ay);
        float allowed = (demanding ? ship.acceleration : ship.deceleration) * dt;
        if (change > allowed && change > 0f) {
            ax *= allowed / change;
            ay *= allowed / change;
        }
        ship.velocityX += ax;
        ship.velocityY += ay;
        ship.x += ship.velocityX * dt;
        ship.y += ship.velocityY * dt;
        if (ship.x < 0f) {
            ship.x = 0f;
            ship.velocityX = Math.max(0f, ship.velocityX);
        } else if (ship.x > mapWidth) {
            ship.x = mapWidth;
            ship.velocityX = Math.min(0f, ship.velocityX);
        }
        if (ship.y < 0f) {
            ship.y = 0f;
            ship.velocityY = Math.max(0f, ship.velocityY);
        } else if (ship.y > mapHeight) {
            ship.y = mapHeight;
            ship.velocityY = Math.min(0f, ship.velocityY);
        }
    }

    /**
     * Chooses the heading a piloted hull turns toward. While the pilot asks for travel
     * it points along it, as a ship flying a route does. Stopped, with areas laid that
     * ask for it, it swings to the heading that brings the most firepower onto them
     * and holds it; with nothing laid it keeps the heading it has.
     */
    static void steer(Warship ship) {
        if (ship.pilotUnderWay()) {
            if (ship.speed() > HEADING_MIN_SPEED) {
                ship.desiredHeading = AirBody.facingToward(ship.velocityX, ship.velocityY);
            }
            // The next stop asks for a fresh choice, from wherever the hull ends up.
            ship.forgetHeadingChoice();
            return;
        }
        if (ship.headingChosen() || ship.speed() > HEADING_MIN_SPEED) return;
        List<WarshipOrder.FireOnArea> bring = new ArrayList<>(2);
        for (Warship.FireGroup g : Warship.FireGroup.values()) {
            WarshipOrder.FireOnArea area = ship.laidArea(g);
            if (area != null && area.bringGunsToBear()) bring.add(area);
        }
        if (bring.isEmpty()) return;
        float[] tx = new float[bring.size()];
        float[] ty = new float[bring.size()];
        float[] weight = new float[bring.size()];
        for (int i = 0; i < tx.length; i++) {
            tx[i] = bring.get(i).x();
            ty[i] = bring.get(i).y();
            weight[i] = 1f;
        }
        ship.desiredHeading = WarshipFacing.bestHeading(ship, tx, ty, weight);
        ship.markHeadingChosen();
    }
}
