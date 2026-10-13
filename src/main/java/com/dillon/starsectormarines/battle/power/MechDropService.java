package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.AirCorridor;
import com.dillon.starsectormarines.battle.air.MechSupportPayload;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.command.reinforcement.LandingZoneScorer;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechDropAllowance;
import com.dillon.starsectormarines.battle.mech.MechDropDeclaration;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.OrbitalLift;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A commander's own call for a mech lance: the allowance a mission declared, the ledger that
 * spends it, and the delivery that follows.
 *
 * <p><b>This is not the player's power.</b> {@link MechSupport} is paid in command points and
 * charges and is claimed by the player's support claim. A call here is paid from the side's
 * {@link MechDropAllowance} and is claimed for the commander that asked, so the lance lands as an
 * ordinary claimed machine squad that commander plans. The delivery itself is the same one: the
 * same Valkyrie, the same {@link MechSupportPayload}, the same unloading. Nothing is forked.
 *
 * <p><b>Delivery is transport, not ownership.</b> The caller names the claim. A Conquest marine
 * commander passes {@code SquadCommandClaim.mission(strategyId, ...)}; nothing about the claim is
 * decided here.
 *
 * <p><b>The descent follows the side's arrival policy.</b> A marine lance comes down from orbit
 * exactly as the marine lift does ({@link AirCorridor#descent}, {@link OrbitalLift#DESCENT_CELLS},
 * keeping its back to the objective); any other side crosses the map edge nearest the ground, as a
 * planetary delivery does.
 *
 * <p><b>Symmetric.</b> The ledger is keyed by faction; whichever commander holds an allowance may
 * call. Only the declaration decides who does.
 *
 * <p>The call surface is {@link #call}, a serial mutation (the commit stage of a commander, or a
 * debug trigger); {@link #lancesRemaining}, {@link #canCall} and {@link #landingZone} only read.
 */
public final class MechDropService {

    /** A service with no allowance for anybody: calls are refused and nothing is mutated. */
    public static final MechDropService NONE = new MechDropService(MechDropDeclaration.NONE);

    /** The carrier of every call: the one the player's Mech Support flies. */
    public static final ShuttleType CARRIER = ShuttleType.VALKYRIE;
    /** Open neighbours the lance's landing cell needs, as {@link MechSupport} asks. */
    static final int MINIMUM_CLEARANCE = 5;
    /** How far from the hint, in Manhattan cells, a landing zone is looked for. */
    public static final int LZ_SEARCH_RADIUS = 10;

    /** How a call ended. */
    public enum Outcome {
        /** A carrier is in the air with the lance; one lance of allowance is spent. */
        COMMITTED,
        /** The side has no lance left (or never had any); nothing was spent. */
        NO_ALLOWANCE,
        /** No viable landing cell near the hint; nothing was spent. */
        NO_LANDING_ZONE
    }

    /** One committed call, as the ledger keeps it. */
    public record Drop(Faction side, String issuer, int tick, int lzCellX, int lzCellY,
                       long carrier, List<MechVariant> lance) {
        public Drop {
            lance = List.copyOf(lance);
        }
    }

    private final MechDropDeclaration declaration;
    private final Map<Faction, Integer> spent = new EnumMap<>(Faction.class);
    private final List<Drop> drops = new ArrayList<>();

    public MechDropService(MechDropDeclaration declaration) {
        this.declaration = declaration != null ? declaration : MechDropDeclaration.NONE;
    }

    // ---- reads ----

    /** What the mission declared for {@code side}. */
    public MechDropAllowance allowance(Faction side) {
        return declaration.forSide(side);
    }

    /** Lances {@code side} may still call. */
    public int lancesRemaining(Faction side) {
        return Math.max(0, allowance(side).lances() - spent.getOrDefault(side, 0));
    }

    /** Every committed call, oldest first. */
    public List<Drop> drops() {
        return Collections.unmodifiableList(drops);
    }

    /**
     * The landing cell a call at this hint would use, or {@code null} when there is no allowance
     * or no viable ground. Pure: asking spends nothing.
     */
    public int[] landingZone(Faction side, BattleView battle, int hintX, int hintY) {
        if (lancesRemaining(side) <= 0 || battle == null) return null;
        return zoneNear(battle, hintX, hintY);
    }

    /** Whether {@link #call} at this hint would commit. */
    public boolean canCall(Faction side, BattleView battle, int hintX, int hintY) {
        return landingZone(side, battle, hintX, hintY) != null;
    }

    // ---- the call ----

    /**
     * Calls one lance down near {@code (hintX, hintY)} for {@code side}, claimed by {@code claim}.
     *
     * @param issuer the commander's name, for the ledger
     * @param claim  who the landed squad belongs to; the calling commander's mission claim
     * @param hintX  the drop zone's cell column; the landing cell is the nearest viable one
     * @param hintY  the drop zone's cell row
     */
    public Outcome call(BattleControl battle, Faction side, String issuer,
                        SquadCommandClaim claim, int hintX, int hintY) {
        if (lancesRemaining(side) <= 0) return Outcome.NO_ALLOWANCE;
        int[] lz = zoneNear(battle, hintX, hintY);
        if (lz == null) return Outcome.NO_LANDING_ZONE;
        float lzX = lz[0] + 0.5f;
        float lzY = lz[1] + 0.5f;
        float[] route = routeFor(side, battle, lzX, lzY);
        List<MechDeploymentSpec> lance = new ArrayList<>();
        for (MechVariant variant : allowance(side).lance()) {
            lance.add(MechDeploymentSpec.standard(variant));
        }
        long carrier = AirDeliveryPower.dispatchCarrier(battle, CARRIER, side,
                MechSupportPayload.INSTANCE, lzX, lzY, route, claim);
        MechSupport.configureLance(battle.world().mission(carrier), lance);
        spent.merge(side, 1, Integer::sum);
        drops.add(new Drop(side, issuer, battle.getSimTickIndex(), lz[0], lz[1],
                carrier, allowance(side).lance()));
        return Outcome.COMMITTED;
    }

    // ---- the scripted (DEBUG / evidence) caller ----

    /** Who calls on a side's behalf when its allowance states a {@code debugCallTick}. */
    private record ScriptedCaller(SquadCommandClaim claim, Supplier<int[]> dropZone) {}

    private final Map<Faction, ScriptedCaller> scriptedCallers = new EnumMap<>(Faction.class);

    /**
     * Names who a side's scripted call is made for and where the drop zone is. Setup installs it
     * beside the commander so a DEBUG call lands claimed by that commander; it does nothing for a
     * side whose allowance states no {@code debugCallTick}.
     *
     * @param dropZone the cell to hint at, resolved when the call is made; may return {@code null}
     */
    public void installScriptedCaller(Faction side, SquadCommandClaim claim, Supplier<int[]> dropZone) {
        if (allowance(side).isNone()) return; // nothing to call, and the shared NONE must stay inert
        scriptedCallers.put(side, new ScriptedCaller(claim, dropZone));
    }

    /** Makes any scripted call now due. Once per tick, serial. */
    public void advance(BattleControl battle) {
        if (scriptedCallers.isEmpty()) return;
        for (Map.Entry<Faction, ScriptedCaller> e : scriptedCallers.entrySet()) {
            Faction side = e.getKey();
            int due = allowance(side).debugCallTick();
            if (due < 0 || battle.getSimTickIndex() < due) continue;
            if (spent.getOrDefault(side, 0) > 0 || lancesRemaining(side) <= 0) continue;
            int[] zone = e.getValue().dropZone().get();
            if (zone == null) continue;
            call(battle, side, e.getValue().claim().issuer(), e.getValue().claim(), zone[0], zone[1]);
        }
    }

    private static int[] zoneNear(BattleView battle, int hintX, int hintY) {
        if (!battle.getGrid().inBounds(hintX, hintY)) return null;
        return new LandingZoneScorer(battle.getGrid(), battle.getTopology())
                .bestNear(hintX, hintY, LZ_SEARCH_RADIUS, MINIMUM_CLEARANCE);
    }

    /**
     * Where the carrier enters and leaves: the descent for a marine lance, the nearest edge for
     * any other side. {@code {entryX, entryY, exitX, exitY}}.
     */
    private static float[] routeFor(Faction side, BattleView battle, float lzX, float lzY) {
        if (side == Faction.MARINE && OrbitalLift.enabled()) {
            float[] toward = towardHostile(side, battle, lzX, lzY);
            AirCorridor descent = AirCorridor.descent("orbit", lzX, lzY, toward[0], toward[1],
                    OrbitalLift.DESCENT_CELLS);
            if (descent != null) {
                return new float[]{descent.entryX, descent.entryY, descent.exitX, descent.exitY};
            }
        }
        return AirDeliveryPower.routeFromNearestEdge(lzX, lzY, battle.getGrid());
    }

    /**
     * The point the descent keeps its back to: the nearest must-hold ground the other side
     * guards, else the middle of the map.
     */
    static float[] towardHostile(Faction side, BattleView battle, float fromX, float fromY) {
        NavigationGrid grid = battle.getGrid();
        float bestSq = Float.MAX_VALUE;
        float[] best = {grid.getWidth() * 0.5f, grid.getHeight() * 0.5f};
        TacticalMap map = battle.getTacticalMap();
        if (map != null) {
            for (TacticalNode node : map.all()) {
                if (!node.mustHold || node.defaultGuard == null || side.friendlyTo(node.defaultGuard)) continue;
                float x = node.centerX() + 0.5f;
                float y = node.centerY() + 0.5f;
                float sq = (x - fromX) * (x - fromX) + (y - fromY) * (y - fromY);
                if (sq < bestSq) {
                    bestSq = sq;
                    best = new float[]{x, y};
                }
            }
        }
        return best;
    }

    /**
     * The middle of a side's landing pads, in cells, or {@code null} for none: the drop zone a
     * caller with no better hint uses.
     */
    public static int[] centroidOf(List<LandingPad> pads) {
        if (pads == null || pads.isEmpty()) return null;
        long sumX = 0;
        long sumY = 0;
        for (LandingPad pad : pads) {
            sumX += pad.centerX;
            sumY += pad.centerY;
        }
        return new int[]{(int) (sumX / pads.size()), (int) (sumY / pads.size())};
    }
}
