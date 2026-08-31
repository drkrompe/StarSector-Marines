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
 * <p><b>A field commits half its sheds and keeps half back.</b> A garrison
 * does not launch its whole air arm at the first contact it sees — a field
 * that did would spend itself in the opening minute and have nothing left for
 * the assault it exists to answer. But nor does it fly one aircraft at a time
 * while four more sit in their sheds, which is what the limit used to say and
 * what a playtester saw: a station with five airframes putting exactly one over
 * the battle for a whole engagement. The limit is a share of the field's own
 * surviving establishment, so a strip with one shed and a station with six are
 * different propositions, and half of it is always in reserve or in the shop.
 *
 * <p><b>The interval paces launches, not sorties.</b> It used to mean "this
 * long after the last one ended", and it was the whole reason a limit of one
 * looked like a doctrine rather than an accident: a sortie at the current
 * atmosphere calibration is about forty seconds from shed to shed, so a
 * forty-five second gap after each one guaranteed the field was empty before
 * the next aircraft moved, whatever any cap said. What paces a launch now is
 * how fast the base can push one departure through its own taxiway and strip,
 * which is measured in seconds; what limits the field is the cap. That is the
 * right division — the pacing is physical and the limit is doctrine.
 *
 * <p>The target is the densest enemy concentration; {@link EnemyConcentration}
 * owns that choice and why it is the right one, and the corridor dispatcher
 * asks it the same question. With more than one aircraft up the field asks for
 * the densest concentration <em>nobody is already working</em>, falling back to
 * the outright densest when the map holds only one worth attacking — two
 * aircraft on one platoon is a tactic, and it should be a decision rather than
 * the only sentence the code can say.
 */
public final class AirStrikeSystem {

    private static final Logger LOG = Global.getLogger(AirStrikeSystem.class);

    /**
     * Sim-seconds before the field's first strike of the battle.
     *
     * <p>Separate from the launch stagger below. A garrison that put an
     * aircraft over the map in the opening seconds would be answering an
     * assault that has not arrived, so the opening quiet is its own number and
     * outlives any re-dial of how quickly the field then builds up.
     */
    private static final float FIRST_STRIKE_SEC = 45f;

    /**
     * Sim-seconds between one aircraft starting out of its shed and the next.
     *
     * <p>Roughly what a departure costs the base: out of the shed, down the
     * taxiway, and off the strip is a handful of seconds, and one aircraft at
     * a time can be doing it. It is deliberately shorter than a sortie, which
     * is what lets a field have several up at once — a stagger longer than the
     * round trip is a limit of one wearing a cadence's clothes.
     */
    private static final float LAUNCH_INTERVAL_SEC = 12f;

    /**
     * The share of a field's surviving sheds it will have in the air at once.
     *
     * <p>Half, so half is always back — refitting, or standing ready for the
     * push the field exists to answer. Rounded up, because a one-shed field
     * that kept its half back would never fly at all.
     */
    private static final float COMMITTED_SHARE = 0.5f;

    /**
     * Hard ceiling on simultaneous sorties from one field, whatever its size.
     *
     * <p>A backstop for a pathological lot rather than a balance dial, the same
     * role the corridor dispatcher's ceiling plays. Four aircraft over one
     * ground battle is already a great deal of air.
     */
    private static final int MAX_CONCURRENT = 4;

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
    private float nextStrikeIn = FIRST_STRIKE_SEC;
    private boolean warnedUnarmed;
    /**
     * Interleaved x, y of what this field's airborne strikes are working, so
     * the next one can be pointed somewhere else. Rebuilt on each dispatch
     * rather than tracked, because a sortie's target belongs to the sortie and
     * this only ever needs it at the moment it is choosing another.
     */
    private final float[] engagedXy = new float[MAX_CONCURRENT * 2];

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
            nextStrikeIn = LAUNCH_INTERVAL_SEC;
            return;
        }
        if (committed(field) >= concurrentLimit(field)) {
            nextStrikeIn = RETRY_SEC;
            return;
        }
        long target = pickTarget(sim);
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
        nextStrikeIn = LAUNCH_INTERVAL_SEC;
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
        sim.world().setHp(craft, field.launch(shed));
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
     * How many of this field's own aircraft are currently committed.
     *
     * <p>Asked of the berths rather than of the aircraft. A shed whose airframe
     * is up is {@code AWAY} and stays that way until the sortie hands it back
     * or writes it off, which makes the count a fact about the field rather
     * than about how many air entities happen to exist this tick — and a berth
     * is what a sortie actually spends.
     *
     * <p>Air cover flown in from off the map holds no berth, so it does not
     * appear here at all. Counting it would let a friendly carrier overhead
     * keep the garrison's sheds shut for the whole battle.
     */
    private static int committed(AirfieldService field) {
        int out = 0;
        for (AirfieldService.Berth berth : field.berths()) {
            if (berth.kind == AirfieldService.Kind.SHELTER
                    && berth.state == AirfieldService.BerthState.AWAY) {
                out++;
            }
        }
        return out;
    }

    /**
     * How many sorties this field will have up at once, given what is left of
     * it.
     *
     * <p>Read off the sheds that still exist rather than the ones that happen
     * to be airworthy this instant: a berth in the middle of a turnaround is
     * still part of the field's establishment, and a limit that shrank while
     * aircraft were being serviced would throttle a field for being busy. A
     * shed burned on the ground is gone, so an attacker who works through a
     * field's hangars narrows what it can put up now as well as what it can put
     * up ever.
     */
    private static int concurrentLimit(AirfieldService field) {
        int surviving = 0;
        for (AirfieldService.Berth berth : field.berths()) {
            if (berth.kind == AirfieldService.Kind.SHELTER
                    && berth.state != AirfieldService.BerthState.DESTROYED) {
                surviving++;
            }
        }
        if (surviving <= 0) return 0;
        int share = (int) Math.ceil(surviving * COMMITTED_SHARE);
        return Math.min(MAX_CONCURRENT, Math.max(1, share));
    }

    /**
     * What the next sortie is sent at: the densest concentration this field is
     * not already working, or the outright densest when that is all there is.
     */
    private long pickTarget(BattleSimulation sim) {
        int engaged = collectEngagedTargets(sim);
        if (engaged > 0) {
            long fresh = EnemyConcentration.densestAwayFrom(sim, enemy, engagedXy, engaged,
                    EnemyConcentration.SEPARATE_TARGET_DIST);
            if (fresh != 0L) return fresh;
        }
        return EnemyConcentration.densest(sim, enemy);
    }

    /**
     * Fills {@link #engagedXy} with what this field's airborne strikes are
     * working, and answers how many it wrote.
     */
    private int collectEngagedTargets(BattleSimulation sim) {
        World world = sim.world();
        int found = 0;
        for (long id : sim.getAirEntityIds()) {
            if (found * 2 >= engagedXy.length) break;
            ShuttleMission mission = world.mission(id);
            if (mission == null || !mission.strikeSortie) continue;
            if (mission.homeBerth == null) continue;
            if (world.airFaction(id) != side) continue;
            engagedXy[found * 2] = mission.lzX;
            engagedXy[found * 2 + 1] = mission.lzY;
            found++;
        }
        return found;
    }

}
