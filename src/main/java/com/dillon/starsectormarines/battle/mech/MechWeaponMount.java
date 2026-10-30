package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.combat.PointFireAim;
import com.dillon.starsectormarines.battle.combat.SalvoChain;

/** Mutable firing state for one installed {@link MechWeaponComponent}. */
public final class MechWeaponMount {

    public final MechMountSlot slot;
    public final MechWeaponComponent component;
    public int ammo;
    /** Mechanical loader/cycler time remaining; never the chassis flux reservoir. */
    public float cycleRemaining;
    public int burstRemaining;
    public float burstTimer;
    public long burstTargetId;
    /** Accepted manual trigger point; marks cursor-driven continuation instead of an AI target. */
    public PointFireAim burstPointAim;
    /** Accumulated loader work toward the rack's recharge (the next trigger pack, under the control). */
    public float replenishmentProgressSeconds;
    /** The recharge time the loader last ran this mount at; NaN before it has run or for a mount it never refills. */
    public float rechargeSeconds = Float.NaN;
    /**
     * Further salvos this rack's trigger has committed to without waiting out its cycle ({@link SalvoChain}):
     * the rest of an autonomous carrier's chosen count, or unbounded while a pilot holds the button.
     */
    public int chainSalvos;
    /** Seconds until the salvo in flight has gone and a chained one may follow it. */
    public float chainGapRemaining;
    /** The body the autonomous chain was committed against; a chained salvo goes to no other. */
    public long chainTargetId;
    /** Salvos a pilot's current hold has committed, for the HUD; zero when the button is up. */
    public int holdSalvos;
    /**
     * A rotary gun's spin and belt, or null for a mount fired a trigger pull at a time. A rotary mount
     * fires while its trigger is held and never uses the burst fields or the trigger-pack ammunition.
     */
    public final RotaryFeed rotary;
    /**
     * Rounds this mount has put in the air, wrapping past the int range. Presentation only: it numbers each round
     * for its weapon's tracer cadence ({@code ShotEvent.roundIndex}) and the simulation never reads it.
     */
    private int roundsReleased;

    public MechWeaponMount(MechMountSlot slot, MechWeaponComponent component) {
        if (slot == null || component == null || !component.accepts(slot)) {
            throw new IllegalArgumentException("Incompatible mech hardpoint component");
        }
        this.slot = slot;
        this.component = component;
        this.ammo = component.ammoCapacity;
        this.rotary = component.rotary() != null ? new RotaryFeed(component.rotary()) : null;
    }

    /** Whether this mount fires while its trigger is held, off a belt, rather than a pull at a time. */
    public boolean isRotary() { return rotary != null; }

    /**
     * Whether the gun is firing now, for the arm's pose: a burst under way, or a rotary gun that released a
     * round within the last few ticks.
     */
    public boolean firing() {
        return burstRemaining > 0 || rotary != null && rotary.sinceRound() <= ROTARY_FIRING_SECONDS;
    }

    private static final float ROTARY_FIRING_SECONDS = 0.2f;

    /** Numbers the round leaving now: zero-based, counted over every round this mount has released. */
    public int releaseRound() {
        int index = roundsReleased;
        roundsReleased = (roundsReleased + 1) & Integer.MAX_VALUE;
        return index;
    }

    public String weaponId() { return component.weaponId; }

    public WeaponDef weaponDef() { return component.weaponDef(); }

    public boolean hasAmmo() {
        return component.ammoCapacity < 0 || ammo > 0;
    }

    public boolean needsSupply() {
        return component.ammoCapacity >= 0 && ammo < component.ammoCapacity;
    }

    /** True when every authored trigger slot on this mount is filled. */
    public boolean full() {
        return component.ammoCapacity < 0 || ammo >= component.ammoCapacity;
    }

    /**
     * Advances unlimited onboard replenishment at the installed subsystem's cadence. Capacity limits ready
     * ammunition, not lifetime reload count. A rack recharges whole: once the loader's time has run from its last
     * salvo ({@link #commitTrigger} restarts it), every spent salvo is back at once, so an LRM-5 waits out its
     * recharge before it can fire again and an LRM-15 fires its three salvos and then recharges
     * (`mechs-nouns.md`, Missile racks). Under the control ({@link MechWeaponComponent#missileBuffers}) each pack
     * comes back on its own, one cadence apiece.
     */
    public void advanceReplenishment(float dt,
                                     MissileReplenisherComponent replenisher) {
        float cadence = replenisher.replenishmentSeconds(weaponId());
        rechargeSeconds = Float.isFinite(cadence) ? cadence : Float.NaN;
        if (full() || !Float.isFinite(cadence)) {
            replenishmentProgressSeconds = 0f;
            return;
        }
        replenishmentProgressSeconds += Math.max(0f, dt);
        while (replenishmentProgressSeconds >= cadence && !full()) {
            ammo = MechWeaponComponent.missileBuffers() ? component.ammoCapacity : ammo + 1;
            replenishmentProgressSeconds -= cadence;
        }
        if (full()) replenishmentProgressSeconds = 0f;
    }

    /**
     * Seconds until the loader has this rack full again, or zero for a full rack, a mount the loader does not refill
     * and a mount it has not yet run on.
     */
    public float rechargeRemaining() {
        if (full() || !Float.isFinite(rechargeSeconds)) return 0f;
        return Math.max(0f, rechargeSeconds - replenishmentProgressSeconds);
    }

    public boolean resupplyOne() {
        if (!needsSupply()) return false;
        ammo++;
        return true;
    }

    /** Whether this mount chains its salvos under a held trigger. */
    public boolean chains() {
        return component.chainsSalvos();
    }

    /**
     * Whether the next trigger would be a chained salvo: the cycle is still running, but a chain is pending.
     * A trigger after the cycle has run out is a fresh one.
     */
    public boolean chainedTrigger() {
        return cycleRemaining > 0f && chainSalvos > 0 && chains();
    }

    /**
     * Whether the mechanical clocks admit a trigger now: no burst in flight, a pack loaded, and either the cycle
     * done or a chained salvo whose predecessor has gone. Flux, aim and the barrel are the caller's gates.
     */
    public boolean triggerReady() {
        if (burstRemaining > 0 || !hasAmmo()) return false;
        if (cycleRemaining <= 0f) return true;
        return chainedTrigger() && chainGapRemaining <= 0f;
    }

    /** The flux the next trigger reserves: in full, or {@link SalvoChain#CHAINED_FLUX_SHARE} of it when chained. */
    public float triggerFlux() {
        return SalvoChain.fluxCost(component.fluxPerTrigger(), chainedTrigger());
    }

    /**
     * A pilot holds the rack's button. Once this hold has fired a salvo, or while the cycle of the last salvo still
     * runs, every loaded salvo chains as soon as the one before it has gone, until release: three clicks on an
     * LRM-15 are its three groups, as a hold is. Only a press after the cycle has run out is a fresh trigger.
     */
    public void holdChain() {
        if (chains() && (holdSalvos > 0 || cycleRemaining > 0f)) chainSalvos = Integer.MAX_VALUE;
    }

    /** The button is up, or the autonomous chain is over: the rack's cycle runs from its last salvo. */
    public void releaseChain() {
        chainSalvos = 0;
        chainTargetId = 0L;
        holdSalvos = 0;
    }

    /** Ticks the chain gap down with the cycle. */
    public void advanceClocks(float dt) {
        cycleRemaining = Math.max(0f, cycleRemaining - dt);
        chainGapRemaining = Math.max(0f, chainGapRemaining - dt);
    }

    /** Commits one emitted trigger; ammunition counts trigger packs, not individual rounds. */
    public void commitTrigger(long target, PointFireAim point) {
        if (chainedTrigger() && chainSalvos != Integer.MAX_VALUE) chainSalvos--;
        consumeTrigger();
        // The rack's recharge runs from its last salvo.
        if (MechWeaponComponent.missileBuffers()) replenishmentProgressSeconds = 0f;
        chainGapRemaining = component.projectilesPerTrigger * weaponDef().burstSpacing;
        cycleRemaining = component.cycleSeconds();
        burstRemaining = Math.max(0, component.projectilesPerTrigger - 1);
        burstTimer = burstRemaining > 0 ? weaponDef().burstSpacing : 0f;
        burstTargetId = burstRemaining > 0 && point == null ? target : 0L;
        burstPointAim = burstRemaining > 0 ? point : null;
    }

    /** Cancels queued rounds without refunding ammunition or resetting clocks. */
    public void clearBurst() {
        burstRemaining = 0;
        burstTimer = 0f;
        burstTargetId = 0L;
        burstPointAim = null;
    }

    public void consumeTrigger() {
        if (component.ammoCapacity >= 0) ammo--;
    }
}
