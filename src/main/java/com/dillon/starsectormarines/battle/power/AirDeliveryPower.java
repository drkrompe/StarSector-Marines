package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.AirDeliveryPayload;
import com.dillon.starsectormarines.battle.air.AirLoss;
import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.LiftAdmission;
import com.dillon.starsectormarines.battle.air.LiftClass;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.command.reinforcement.LandingZoneScorer;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.List;

/**
 * Shared target-validation and physical-carrier lifecycle for delivered powers.
 *
 * <p><b>Who flies a call.</b> A power whose lift is supplied by a mission, an
 * employer or a fleet hull flies the synthetic {@code carrier} pattern. A power
 * may also be given a committed company boat ({@link #companyCarrier}): when that
 * boat's lift admits everything on the call, the boat itself flies it, and a boat
 * shot down is the company's loss through ordinary settlement. When it cannot
 * (too heavy a machine, or the boat is lost or still in the air) the synthetic
 * lift flies the call if the power has one ({@link #syntheticLift}).
 */
abstract class AirDeliveryPower extends CommandPower {

    private static final int LZ_SEARCH_RADIUS = 4;
    private static final float OFFMAP_PAD = 8f;

    private final ShuttleType carrier;
    private final AirDeliveryPayload payload;
    private final int minimumClearance;
    private final FittedBoat companyCarrier;
    private final boolean syntheticLift;
    /** The last sortie the company boat flew, or 0 before its first. */
    private long companySortie;

    protected AirDeliveryPower(String id, String displayName, float cpCost, int maxCharges,
                               ShuttleType carrier, AirDeliveryPayload payload,
                               int minimumClearance) {
        this(id, displayName, cpCost, maxCharges, 0, carrier, payload, minimumClearance);
    }

    protected AirDeliveryPower(String id, String displayName, float cpCost, int maxCharges,
                               int supplyCost, ShuttleType carrier,
                               AirDeliveryPayload payload, int minimumClearance) {
        this(id, displayName, cpCost, maxCharges, supplyCost, 1,
                carrier, payload, minimumClearance);
    }

    protected AirDeliveryPower(String id, String displayName, float cpCost, int maxCharges,
                               int supplyCost, int slotWeight, ShuttleType carrier,
                               AirDeliveryPayload payload, int minimumClearance) {
        this(id, displayName, cpCost, maxCharges, supplyCost, slotWeight, carrier,
                payload, minimumClearance, null, true);
    }

    /**
     * @param companyCarrier a committed cradled company boat that may fly the
     *     calls, or {@code null}
     * @param syntheticLift whether a mission, employer, hull or ship supplies a
     *     synthetic {@code carrier} for calls the company boat cannot fly
     */
    protected AirDeliveryPower(String id, String displayName, float cpCost, int maxCharges,
                               int supplyCost, int slotWeight, ShuttleType carrier,
                               AirDeliveryPayload payload, int minimumClearance,
                               FittedBoat companyCarrier, boolean syntheticLift) {
        super(id, displayName, cpCost, 0f, maxCharges, supplyCost, slotWeight);
        if (companyCarrier == null && !syntheticLift) {
            throw new IllegalArgumentException(id + " needs a company carrier or synthetic lift");
        }
        this.carrier = carrier;
        this.payload = payload;
        this.minimumClearance = minimumClearance;
        this.companyCarrier = companyCarrier;
        this.syntheticLift = syntheticLift;
    }

    /** The committed company boat that may fly this power's calls, or {@code null}. */
    public FittedBoat companyCarrier() { return companyCarrier; }

    /** Whether a synthetic carrier stands in for lift the company does not own. */
    public boolean syntheticLift() { return syntheticLift; }

    /**
     * What the next call would unload, by lift class. Empty means the payload
     * asks nothing of a carrier's lift.
     */
    protected List<LiftClass> nextLoad(CommandPowerService service) {
        return List.of();
    }

    /**
     * Everything the power can ever unload, by lift class. A power with no
     * synthetic lift refuses its target outright when its company boat cannot
     * carry all of it, so a charge is never spent on a call nobody can fly.
     */
    protected List<LiftClass> liftDemand() {
        return nextLoad(null);
    }

    /**
     * Who flies one call: the company boat when it is home, unlost and lifts
     * all of {@code load}; otherwise the synthetic carrier when the power has
     * one; otherwise nobody ({@code null}).
     */
    static Airframe chooseCarrier(List<LiftClass> load, FittedBoat company,
                                  boolean companyAvailable, boolean syntheticLift,
                                  ShuttleType synthetic) {
        if (company != null && companyAvailable && liftsAll(load, company)) return company;
        return syntheticLift ? synthetic : null;
    }

    /** Whether {@code carrier} admits every machine in {@code load}. */
    static boolean liftsAll(List<LiftClass> load, Airframe carrier) {
        for (LiftClass payloadClass : load) {
            if (payloadClass != null && !LiftAdmission.check(payloadClass, carrier).admitted()) {
                return false;
            }
        }
        return true;
    }

    private boolean companyAvailable(BattleView battle) {
        if (companyCarrier == null) return false;
        if (companySortie != 0L && battle.world().isAlive(companySortie)) return false;
        for (AirLoss loss : battle.getAirLosses()) {
            if (companyCarrier.equals(loss.frame())) return false;
        }
        return true;
    }

    private Airframe carrierFor(CommandPowerService service, BattleView battle) {
        return chooseCarrier(nextLoad(service), companyCarrier,
                companyAvailable(battle), syntheticLift, carrier);
    }

    @Override
    public float previewRadiusCells() { return 1.5f; }

    @Override
    public boolean canTarget(int cellX, int cellY, BattleView battle) {
        if (battle != null && !syntheticLift
                && (!companyAvailable(battle) || !liftsAll(liftDemand(), companyCarrier))) {
            return false;
        }
        return landingZoneNear(cellX, cellY, battle) != null;
    }

    @Override
    public final void resolve(int cellX, int cellY, CommandPowerService service,
                              BattleControl battle) {
        int[] lz = landingZoneNear(cellX, cellY, battle);
        if (lz == null) return;
        float lzX = lz[0] + 0.5f;
        float lzY = lz[1] + 0.5f;
        float[] route = routeFromNearestEdge(lzX, lzY, battle.getGrid());
        Airframe frame = carrierFor(service, battle);
        if (frame == null) return;
        long shuttle;
        ShuttleType pattern;
        if (frame instanceof FittedBoat boat) {
            pattern = boat.pattern();
            shuttle = battle.spawnShuttle(pattern, boat, Faction.MARINE,
                    lzX, lzY, route[0], route[1], route[2], route[3], 0f, 1);
            loadCarrier(battle.world().mission(shuttle), pattern, payload,
                    battle.playerSupportCommandClaim());
            companySortie = shuttle;
        } else {
            pattern = carrier;
            shuttle = dispatchCarrier(battle, carrier, Faction.MARINE, payload,
                    lzX, lzY, route, battle.playerSupportCommandClaim());
        }
        configureMission(battle.world().mission(shuttle), frame, pattern, service);
    }

    /**
     * Puts one single-sortie carrier carrying {@code payload} in the air and returns its id. The one place the
     * physical delivery is stood up, shared by the player's powers and a commander's call, so
     * the two cannot drift into separate deliveries.
     *
     * @param route {@code {entryX, entryY, exitX, exitY}}
     * @param claim who the delivered squad belongs to; {@code null} leaves it unowned
     */
    static long dispatchCarrier(BattleControl battle, ShuttleType carrier,
                                Faction faction, AirDeliveryPayload payload,
                                float lzX, float lzY, float[] route,
                                SquadCommandClaim claim) {
        long shuttle = battle.spawnShuttle(carrier, faction,
                lzX, lzY, route[0], route[1], route[2], route[3], 0f);
        loadCarrier(battle.world().mission(shuttle), carrier, payload, claim);
        return shuttle;
    }

    private static void loadCarrier(ShuttleMission mission, ShuttleType carrier,
                                    AirDeliveryPayload payload, SquadCommandClaim claim) {
        mission.payload = payload;
        mission.marinesRemaining = payload.unitsPerSortie(carrier);
        mission.totalCycles = 1;
        mission.commandClaim = claim;
    }

    /**
     * Setup that also knows what is flying: the synthetic pattern, or the
     * company's own fitted boat.
     */
    protected void configureMission(ShuttleMission mission, Airframe frame,
                                    ShuttleType carrier, CommandPowerService service) {
        configureMission(mission, carrier, service);
    }

    /** Payload-specific setup that may inspect the post-commit power state. */
    protected void configureMission(ShuttleMission mission, ShuttleType carrier,
                                    CommandPowerService service) {
        configureMission(mission, carrier);
    }

    /** Payload-specific mission setup after the physical carrier is spawned. */
    protected void configureMission(ShuttleMission mission, ShuttleType carrier) {}

    private int[] landingZoneNear(int cellX, int cellY, BattleView battle) {
        if (battle == null || !battle.getGrid().inBounds(cellX, cellY)) return null;
        return new LandingZoneScorer(battle.getGrid(), battle.getTopology())
                .bestNear(cellX, cellY, LZ_SEARCH_RADIUS, minimumClearance);
    }

    /** Entry and exit points just off the map edge nearest the landing point: {@code {entryX, entryY, exitX, exitY}}. */
    static float[] routeFromNearestEdge(float lzX, float lzY, NavigationGrid grid) {
        float left = lzX;
        float right = grid.getWidth() - lzX;
        float bottom = lzY;
        float top = grid.getHeight() - lzY;
        float nearest = Math.min(Math.min(left, right), Math.min(bottom, top));
        if (nearest == left) return new float[]{-OFFMAP_PAD, lzY, -OFFMAP_PAD - 4f, lzY};
        if (nearest == right) return new float[]{grid.getWidth() + OFFMAP_PAD, lzY,
                grid.getWidth() + OFFMAP_PAD + 4f, lzY};
        if (nearest == bottom) return new float[]{lzX, -OFFMAP_PAD, lzX, -OFFMAP_PAD - 4f};
        return new float[]{lzX, grid.getHeight() + OFFMAP_PAD, lzX,
                grid.getHeight() + OFFMAP_PAD + 4f};
    }
}
