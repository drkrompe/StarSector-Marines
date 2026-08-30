package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.Runway;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

/**
 * Decides when a garrison field puts an armed aircraft over the battle, and
 * sends it down the strip to do it.
 *
 * <p>This is what the sheds are for. Everything under it — the lot, the strip,
 * the berths, the ground procedure — has existed without anything ever asking
 * for a sortie, so a station's fighters sat in their hangars for the whole
 * battle. Stateless in the usual sense except for its own cadence: the berths
 * are {@link AirfieldService}'s and the aircraft are the world's.
 *
 * <p><b>One strike at a time.</b> A garrison does not launch its whole air arm
 * at the first contact it sees, and a field that did would spend itself in the
 * opening minute and have nothing left for the assault it exists to answer.
 * The interval and the single-sortie rule together are what make the air arm a
 * recurring threat rather than one event.
 *
 * <p>The target is the densest enemy concentration; {@link EnemyConcentration}
 * owns that choice and why it is the right one, and the corridor dispatcher
 * asks it the same question.
 */
public final class AirStrikeSystem {

    private static final Logger LOG = Global.getLogger(AirStrikeSystem.class);

    /** Sim-seconds before the first strike, and between one ending and the next. */
    private static final float STRIKE_INTERVAL_SEC = 45f;

    /**
     * How long a retry waits when the field wanted to fly and could not — no
     * concentration worth attacking, or nothing airworthy in the sheds.
     *
     * <p>Short, because the reason is usually about to change, and re-asking
     * costs a scan rather than a sortie.
     */
    private static final float RETRY_SEC = 5f;

    /** Sim-seconds an aircraft works its target before turning for home. */

    private final Faction side;
    private final Faction enemy;
    private float nextStrikeIn = STRIKE_INTERVAL_SEC;
    private boolean warnedUnarmed;

    public AirStrikeSystem(Faction side, Faction enemy) {
        this.side = side;
        this.enemy = enemy;
    }

    /** Advance the cadence and launch when everything a sortie needs is true. */
    public void tick(float dt, BattleSimulation sim) {
        nextStrikeIn -= dt;
        if (nextStrikeIn > 0f) return;

        AirfieldService field = sim.getAirfieldService();
        Runway strip = field.runway();
        // A strike rolls, so a field with no strip flies none however many
        // aircraft are in its sheds.
        if (strip == null) {
            nextStrikeIn = STRIKE_INTERVAL_SEC;
            return;
        }
        if (strikeAlreadyOut(sim)) {
            nextStrikeIn = RETRY_SEC;
            return;
        }
        long target = EnemyConcentration.densest(sim, enemy);
        if (target == 0L) {
            nextStrikeIn = RETRY_SEC;
            return;
        }
        World world = sim.world();
        float targetX = world.x(target);
        float targetY = world.y(target);
        AirfieldService.Berth shed = field.nearestAirworthy(
                targetX, targetY, AirfieldService.Kind.SHELTER);
        if (shed == null) {
            nextStrikeIn = RETRY_SEC;
            return;
        }
        launch(sim, field, strip, shed, targetX, targetY);
        nextStrikeIn = STRIKE_INTERVAL_SEC;
    }

    /** Sends one aircraft out of {@code shed}, down the strip, at the target. */
    private void launch(BattleSimulation sim, AirfieldService field, Runway strip,
                        AirfieldService.Berth shed, float targetX, float targetY) {
        float shelterX = shed.centerX + 0.5f;
        float shelterY = shed.centerY + 0.5f;
        long craft = sim.spawnSortie(shed.airframe, side,
                targetX, targetY, shelterX, shelterY, shelterX, shelterY, 0f);
        ShuttleMission mission = sim.world().mission(craft);
        // The hull that leaves is the hull that was in the shed, damage and
        // all, and it owes itself back to that shed.
        mission.hp = field.launch(shed);
        mission.homeBerth = shed;
        mission.strikeSortie = true;
        sim.world().kinematics(craft).teleport(shelterX, shelterY, shed.facingDegrees);
        // No turrets. The aircraft is the weapon: it carries its ordnance on
        // its nose and aims it by flying, so what it needs is a load and not a
        // mount. A hull with neither still flies the sortie and says
        // so once — it rolls out, crosses the map and can be shot at, which is
        // most of what a sortie is.
        if (shed.airframe.ordnance() == null && !warnedUnarmed) {
            warnedUnarmed = true;
            LOG.warn("air: " + shed.airframe + " flew a strike carrying nothing to drop");
        }
        mission.departFromRunway(strip, shelterX, shelterY, targetX, targetY);
    }

    /**
     * Whether this field already has one of its own aircraft out.
     *
     * <p>Its own: a sortie still holding a berth. Air cover flown in from off
     * the map is somebody else's aircraft on the same side, and counting it
     * here would let a carrier overhead keep the garrison's sheds shut for the
     * whole battle.
     */
    private boolean strikeAlreadyOut(BattleSimulation sim) {
        for (long id : sim.getAirEntityIds()) {
            ShuttleMission mission = sim.world().mission(id);
            if (mission == null || !mission.strikeSortie) continue;
            if (mission.homeBerth == null) continue;
            if (sim.world().airFaction(id) == side) return true;
        }
        return false;
    }

}
