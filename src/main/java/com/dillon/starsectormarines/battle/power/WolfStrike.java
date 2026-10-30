package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.warship.LowPass;
import com.dillon.starsectormarines.battle.warship.ShipSupportSource;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipAtmosphere;
import com.dillon.starsectormarines.battle.warship.WarshipVariants;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A Wolf frigate's fly-by along a short line the player draws: it breaks out of the cloud deck behind the line's start
 * on the line's heading, flies a {@link LowPass} down it with its turrets working whatever is in the corridor, and at
 * the line's end climbs straight back up through the deck. No station is kept and nothing is left behind. The body is
 * the Onslaught's reduced one ({@link SpawnInOnslaught#PRESENCE}).
 *
 * <p>A committed Wolf of any mark is the <em>source</em> (it makes the card exist), but the ship flown is always the
 * Wolf (H) Assault refitted as a gunship ({@link #source}), so the guns over the battle are the ones this power
 * chooses, not whatever the member was fitted with, and nothing is written back to the fleet. The run is a strafe
 * ({@link LowPass#strafe}): the guns work the ground {@link #LEAD_CELLS} ahead of the hull, and the rocket pods walk
 * their ripples over that lead point while anything hostile is near it.
 */
public final class WolfStrike extends CommandPower {

    private static final Logger LOG = Global.getLogger(WolfStrike.class);

    public static final String ID = "wolf_strike";
    /** The hull and stock variant every strike flies, whichever Wolf brought the card. */
    public static final String HULL_ID = "wolf_hegemony";
    public static final String VARIANT_ID = "wolf_hegemony_Assault";
    /** The nose hardpoint, the Assault's pulse laser, left empty: the gunship fights with its turret and rockets. */
    static final String NOSE_SLOT = "WS 004";
    /** The two small missile hardpoints, which carry rocket pods in place of the Assault's sabots. */
    static final String[] ROCKET_SLOTS = {"WS 005", "WS 006"};
    /** The rocket pod: unguided high-explosive rockets fired in rippling bursts (a rocket rack). */
    static final String ROCKET_POD = "annihilator";

    /** How far ahead of the hull, along the line, the guns' circle is centred, in cells. */
    public static final float LEAD_CELLS = 16f;
    /** The guns' circle's radius, in cells: from about six cells ahead of the hull to twenty-six. */
    public static final float FIRE_RADIUS_CELLS = 10f;

    /** The longest line the player draws, in cells: a short, sharp run. The shortest is {@link LowPass#MIN_LENGTH_CELLS}. */
    public static final float MAX_LINE_CELLS = 60f;

    /** Draft tuning: a light hull's quick run, available twice. */
    private static final float CP_COST = 3f;
    private static final float COOLDOWN_SECONDS = 20f;
    private static final int MAX_CHARGES = 2;
    private static final int SUPPLY_COST = 2;
    private static final int SLOT_WEIGHT = 2;

    public WolfStrike() {
        super(ID, "@battlePowerWolfStrike", CP_COST, COOLDOWN_SECONDS, MAX_CHARGES, SUPPLY_COST, SLOT_WEIGHT);
    }

    /** The one ship every strike fits: the Wolf (H) Assault, its nose laser out and rocket pods for its sabots. */
    public static ShipSupportSource source() {
        Map<String, String> weapons = new LinkedHashMap<>(WarshipVariants.weaponsBySlot(HULL_ID, VARIANT_ID));
        weapons.remove(NOSE_SLOT);
        for (String slot : ROCKET_SLOTS) weapons.put(slot, ROCKET_POD);
        return new ShipSupportSource(HULL_ID, "Wolf (H)", false, weapons);
    }

    @Override public float lineMaxCells() { return MAX_LINE_CELLS; }

    /** The corridor the guns work, so the preview shows the ground either side of the line. */
    @Override public float previewRadiusCells() { return FIRE_RADIUS_CELLS; }

    @Override
    public boolean canTarget(int cellX, int cellY, BattleView battle) {
        return super.canTarget(cellX, cellY, battle) && battle.playerSeesCell(cellX, cellY);
    }

    /** A line from seen ground, long enough for a pass and no longer than a short run. */
    @Override
    public boolean canTargetLine(int ax, int ay, int bx, int by, BattleView battle) {
        if (!super.canTargetLine(ax, ay, bx, by, battle)) return false;
        double length = Math.hypot(bx - ax, by - ay);
        return length >= LowPass.MIN_LENGTH_CELLS && length <= MAX_LINE_CELLS + 1f;
    }

    /** Aimed at one cell (a caller with no line): a run of the longest line centred on it, across the map's ship axis. */
    @Override
    public void resolve(int cellX, int cellY, CommandPowerService service, BattleControl battle) {
        float half = MAX_LINE_CELLS * 0.5f;
        fly(cellX + 0.5f - half, cellY + 0.5f, cellX + 0.5f + half, cellY + 0.5f, battle);
    }

    @Override
    public void resolveLine(int ax, int ay, int bx, int by, CommandPowerService service, BattleControl battle) {
        fly(ax + 0.5f, ay + 0.5f, bx + 0.5f, by + 0.5f, battle);
    }

    /** Fits the Wolf behind A on the line's heading, sends it down the line, and has it climb out at B. */
    public static Warship fly(float ax, float ay, float bx, float by, BattleControl battle) {
        int w = battle.getGrid().getWidth();
        int h = battle.getGrid().getHeight();
        try {
            float dx = bx - ax;
            float dy = by - ay;
            float len = (float) Math.hypot(dx, dy);
            if (len < 1e-3f) return null;
            float heading = AirBody.facingToward(dx, dy);
            Warship probe = Warship.fit(source(), Faction.MARINE, 0f, 0f, heading, SpawnInOnslaught.PRESENCE);
            // It comes down through the deck while running in, so its guns go live about as it reaches A.
            float runUp = runUpCells(probe.maxSpeed, probe.acceleration);
            float sx = clamp(ax - dx / len * runUp, 0f, w);
            float sy = clamp(ay - dy / len * runUp, 0f, h);
            Warship wolf = Warship.fit(source(), Faction.MARINE, sx, sy, heading, SpawnInOnslaught.PRESENCE);
            wolf.calledFrom(source());
            wolf.arriveFromAboveOnHeading(heading);
            wolf.useDoctrine();
            wolf.fluxScale(Warship.BRAWLER_FLUX_SCALE);
            wolf.limitFlux();
            LowPass pass = LowPass.plan(sx, sy, ax, ay, bx, by, w, h);
            if (pass != null) pass.strafe(LEAD_CELLS, FIRE_RADIUS_CELLS);
            if (pass == null || !wolf.beginLowPass(pass)) {
                LOG.warn("WolfStrike: no pass along " + ax + "," + ay + " -> " + bx + "," + by);
                return null;
            }
            wolf.climbAfterPass();
            battle.addWarship(wolf);
            return wolf;
        } catch (Exception e) {
            LOG.warn("WolfStrike: the Wolf (H) could not be fitted", e);
            return null;
        }
    }

    /** Cells a hull flies from rest at the pass speed over the descent: how far behind A it breaks out of the deck. */
    static float runUpCells(float maxSpeed, float acceleration) {
        float v = LowPass.passSpeed(maxSpeed);
        float a = LowPass.passAcceleration(acceleration);
        float t = WarshipAtmosphere.DESCENT_SECONDS;
        if (!(v > 0f) || !(a > 0f)) return 0f;
        float rampSeconds = v / a;
        if (t <= rampSeconds) return 0.5f * a * t * t;
        return v * v / (2f * a) + v * (t - rampSeconds);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
