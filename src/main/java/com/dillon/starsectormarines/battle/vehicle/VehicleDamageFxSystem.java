package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.fx.EffectsService;
import com.dillon.starsectormarines.battle.combat.fx.FxHash;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

/**
 * What located damage looks like on a living ground hull ({@code convoy-nouns.md}, a hull holds its damage where it
 * lands): the vehicle counterpart of {@code MechSectionFxSystem}. A hull whose own structure is badly spent, or whose
 * plates are worn nearly through, trails thin smoke off its engine deck, heavier once it is close to going; a drive
 * that goes bursts at the running gear and smokes there for as long as the hull stands; a turret section that goes
 * bursts where it sat and smoulders on its ring. A wreck has its own smoke ({@code EffectsService#spawnTankWreck}) and
 * takes none of this.
 *
 * <p>Presentation only. It reads {@link HullLocations} and the structure column and never writes the simulation, and
 * it draws nothing from the simulation's random stream: cadence and jitter are hashed from the hull's id and the tick
 * ({@link FxHash}), so a frame is still a pure function of simulation state. It emits into the effects service's
 * per-frame smoke and fire queues, which the live screen and the headless review feed both drain. The one thing it
 * keeps is which losses it has already burst, so a loss bursts once.
 */
public final class VehicleDamageFxSystem {

    /** The hull's own structure share at or under which its engine deck smokes thinly. */
    static final float WORN_STRUCTURE = 0.5f;
    /** The mean plate wear at or over which the engine deck smokes thinly, whatever the structure. */
    static final float WORN_PLATES = 0.75f;
    /** The hull's own structure share at or under which the smoke thickens. */
    static final float FAILING_STRUCTURE = 0.25f;

    /** Ticks between engine-deck puffs on a worn hull, and on a failing one. */
    static final int WORN_SMOKE_PERIOD_TICKS = 22;
    static final int FAILING_SMOKE_PERIOD_TICKS = 11;
    /** Ticks between puffs at a crippled hull's running gear. */
    static final int DRIVE_SMOKE_PERIOD_TICKS = 14;
    /** Ticks between smoulder puffs on a lost turret's ring. */
    static final int TURRET_SMOKE_PERIOD_TICKS = 18;

    /** The emitters {@link #emitters} can raise. */
    static final int WORN = 1;
    static final int FAILING = 1 << 1;
    static final int DRIVE = 1 << 2;
    static final int TURRET = 1 << 3;

    private static final int SALT_DECK = 0x41;
    private static final int SALT_DRIVE = 0x5d;
    private static final int SALT_TURRET = 0x67;
    private static final int SALT_BURST = 0x19;

    /** Losses already burst, as {@link #DRIVE} and {@link #TURRET} bits, by hull id. */
    private final Long2IntOpenHashMap burst = new Long2IntOpenHashMap();

    public void tick(int tickIndex, ConvoyService convoy, EffectsService fx) {
        for (int i = 0, n = convoy.vehicleCount(); i < n; i++) {
            long id = convoy.vehicleAt(i);
            VehicleType type = convoy.vehicleType(id);
            VehicleMission mission = convoy.mission(id);
            if (type == null || mission == null || !mission.isVisible() || mission.state == VehicleState.WRECKED
                    || !convoy.isPresent(id) || convoy.isAirborne(id)) continue;
            HullLocations locations = convoy.locations(id);
            GroundBody body = convoy.body(id);
            float structure = convoy.structure(id);
            if (locations == null || body == null || structure <= 0f) continue;
            int raised = emitters(hullShare(locations, structure, convoy.maxStructure(id)),
                    meanPlateWear(locations), locations.crippled(),
                    locations.turret() != null && locations.turret().destroyed());
            if (raised != 0) emit(id, tickIndex, type, body, raised, fx);
        }
    }

    /**
     * Which emitters a hull raises from its state: {@link #WORN} (with {@link #FAILING} under it) from its own
     * structure share and its plates' mean wear, {@link #DRIVE} once crippled, {@link #TURRET} once the turret
     * section is gone.
     */
    static int emitters(float hullShare, float plateWear, boolean crippled, boolean turretLost) {
        int raised = 0;
        if (hullShare <= WORN_STRUCTURE || plateWear >= WORN_PLATES) raised |= WORN;
        if (hullShare <= FAILING_STRUCTURE) raised |= WORN | FAILING;
        if (crippled) raised |= DRIVE;
        if (turretLost) raised |= TURRET;
        return raised;
    }

    /** The hull's own structure (neither the turret's nor the drive's) as a share of its own maximum. */
    static float hullShare(HullLocations locations, float totalStructure, float maxTotalStructure) {
        float turretMax = locations.turret() != null ? locations.turret().maxStructure() : 0f;
        float max = maxTotalStructure - turretMax - locations.maxDrive();
        if (max <= 0f) return 1f;
        return Math.min(1f, locations.hullStructure(totalStructure) / max);
    }

    /** The four plates' mean wear: 0 whole, 1 stripped all round. */
    static float meanPlateWear(HullLocations locations) {
        float sum = 0f;
        int counted = 0;
        for (HullLocations.Plate plate : HullLocations.Plate.values()) {
            float max = locations.maxArmor(plate);
            if (max <= 0f) continue;
            sum += Math.max(0f, 1f - locations.armor(plate) / max);
            counted++;
        }
        return counted == 0 ? 0f : sum / counted;
    }

    private void emit(long id, int tick, VehicleType type, GroundBody body, int raised, EffectsService fx) {
        double a = Math.toRadians(body.facingDegrees);
        // Hull-local +Y is the nose: forward is (-sin, cos), the hull's right is (cos, sin).
        float fwdX = (float) -Math.sin(a), fwdY = (float) Math.cos(a);
        float rightX = (float) Math.cos(a), rightY = (float) Math.sin(a);
        float length = type.visualLengthCells;
        float width = type.visualWidthCells;
        float size = Math.min(length, width);

        if ((raised & WORN) != 0) {
            boolean failing = (raised & FAILING) != 0;
            int period = failing ? FAILING_SMOKE_PERIOD_TICKS : WORN_SMOKE_PERIOD_TICKS;
            if (FxHash.onCadence(id, tick, period, SALT_DECK)) {
                float aft = 0.32f * length;
                float j = FxHash.jitter(id, tick, SALT_DECK);
                fx.emitSmokePuff(body.x - fwdX * aft + rightX * 0.18f * width * j,
                        body.y - fwdY * aft + rightY * 0.18f * width * j,
                        (failing ? 0.36f : 0.24f) * size * (1f + 0.15f * FxHash.jitter(id, tick, SALT_DECK + 1)));
            }
        }

        int seen = burst.get(id);
        if ((raised & DRIVE) != 0) {
            // On the tracks or wheel rows themselves, over the rear drive and along their run.
            RunningGear gear = type.runningGear();
            float aft = gear.grounded() ? 0.75f * gear.rearCells() : 0.28f * length;
            float side = gear.grounded() ? gear.halfGaugeCells() : 0.46f * width;
            float forward = gear.grounded() ? gear.frontCells() : 0.35f * length;
            float back = gear.grounded() ? gear.rearCells() : 0.35f * length;
            if ((seen & DRIVE) == 0) {
                seen |= DRIVE;
                for (int s = -1; s <= 1; s += 2) {
                    fx.emitFireBurst(body.x - fwdX * aft + rightX * side * s, body.y - fwdY * aft + rightY * side * s,
                            0.22f * size);
                }
                fx.emitSmokePuff(body.x - fwdX * aft, body.y - fwdY * aft, 0.4f * size);
            } else if (FxHash.onCadence(id, tick, DRIVE_SMOKE_PERIOD_TICKS, SALT_DRIVE)) {
                // Low along the running gear, one side and then the other, sliding fore and aft.
                float s = FxHash.jitter(id, tick, SALT_DRIVE) < 0f ? -1f : 1f;
                float j = FxHash.jitter(id, tick, SALT_DRIVE + 1);
                float along = j >= 0f ? forward * j : back * j;
                fx.emitSmokePuff(body.x + fwdX * along + rightX * side * s, body.y + fwdY * along + rightY * side * s,
                        (0.2f + 0.05f * FxHash.jitter(id, tick, SALT_DRIVE + 2)) * size);
            }
        }
        if ((raised & TURRET) != 0) {
            float mx = body.x + type.turretMountX * rightX + type.turretMountY * fwdX;
            float my = body.y + type.turretMountX * rightY + type.turretMountY * fwdY;
            if ((seen & TURRET) == 0) {
                seen |= TURRET;
                fx.emitFireBurst(mx, my, 0.42f * size);
                fx.emitFireBurst(mx + 0.12f * size * FxHash.jitter(id, tick, SALT_BURST),
                        my + 0.12f * size * FxHash.jitter(id, tick, SALT_BURST + 1), 0.3f * size);
                fx.emitSmokePuff(mx, my, 0.4f * size);
            } else if (FxHash.onCadence(id, tick, TURRET_SMOKE_PERIOD_TICKS, SALT_TURRET)) {
                fx.emitSmokePuff(mx + 0.08f * size * FxHash.jitter(id, tick, SALT_TURRET),
                        my + 0.08f * size * FxHash.jitter(id, tick, SALT_TURRET + 1),
                        (0.28f + 0.05f * FxHash.jitter(id, tick, SALT_TURRET + 2)) * size);
            }
        }
        if (seen != 0) burst.put(id, seen);
    }
}
