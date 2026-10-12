package com.dillon.starsectormarines.battle.story;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.OpeningOperationCommand;
import com.dillon.starsectormarines.battle.mech.MechSpawnPlacement;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.WaylineHound;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

/** Securing the maintenance bay commissions one ordinary combat walker. */
public final class WaylineHoundRecovery {
    public static final String FOUND = "opening.counterattack.hound_found";
    public static final String READY = "opening.counterattack.hound_ready";
    public static final String LOST = "opening.counterattack.hound_disabled";
    public static final String CONTROL = "opening.counterattack.hound_control";
    public static final String RETURN = "opening.counterattack.hound_return";
    public static final float STARTUP_SECONDS = 5f;
    public record Report(boolean activated, boolean disabled) {}

    private final float x, y;
    private final StoryBeatLog log;
    private final long frame;
    private long mech;
    private float startup;
    private boolean found;
    private boolean controlled;

    public WaylineHoundRecovery(BattleSimulation sim, int cellX, int cellY, StoryBeatLog log) {
        x = cellX + .5f;
        y = cellY + .5f;
        this.log = log;
        if (!MechSpawnPlacement.canPlace(sim.getGrid(), MechVariant.HOUND.radius, x, y, (px, py) -> true))
            throw new IllegalArgumentException("Wayline maintenance berth cannot hold a Hound");
        // Unclaimed equipment is neutral, inert and cannot count as a company
        // arrival, grant vision, take orders, or keep a defeated company alive.
        frame = sim.spawn(new EntitySpec(WaylineHound.NAME + " / offline", Faction.CIVILIAN,
                UnitType.MACHINE_FRAME, cellX, cellY).mechVariant(MechVariant.HOUND).role(UnitRole.STRUCTURE));
    }

    public long frameId() { return frame; }
    public long mechId() { return mech; }
    public boolean found() { return found; }
    public float startupFraction() { return Math.min(1f, startup / STARTUP_SECONDS); }
    public boolean objectiveVisible(BattleSimulation sim) {
        return found && mech == 0L && !report(sim).disabled() && !sim.isComplete();
    }
    public float x() { return x; }
    public float y() { return y; }
    public Report report(BattleSimulation sim) {
        return new Report(mech != 0L, !sim.world().isAlive(mech != 0L ? mech : frame));
    }

    public void tick(BattleSimulation sim) {
        if (sim.isComplete()) return;
        if (mech != 0L) {
            if (!sim.world().isAlive(mech)) emit(sim, LOST);
            else if (sim.directControl().isControlling(mech)) {
                controlled = true;
                emit(sim, CONTROL);
            } else if (controlled) emit(sim, RETURN);
            return;
        }
        boolean companyPresent = false, sighted = false, contested = false;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            if (!sim.identity().type(id).combatant) continue;
            float dx = sim.world().x(id) - x, dy = sim.world().y(id) - y;
            float distanceSq = dx * dx + dy * dy;
            Faction faction = sim.identity().faction(id);
            if (faction == Faction.MARINE && sim.identity().type(id).usesInfantryTraining()) {
                if (distanceSq <= 400f && sim.getGrid().hasLineOfFire(sim.world().x(id), sim.world().y(id), x, y)) {
                    sighted = true;
                    if (distanceSq <= 36f) companyPresent = true;
                }
            }
            if (Faction.MARINE.hostileTo(faction) && distanceSq <= 100f) contested = true;
        }
        if (sighted && !found) { found = true; emit(sim, FOUND); }
        if (!sim.world().isAlive(frame)) {
            if (found) emit(sim, LOST);
            return;
        }
        if (!companyPresent || contested) { startup = 0f; return; }
        startup += BattleSimulation.TICK_DT;
        if (startup + .0001f < STARTUP_SECONDS) return;
        if (!MechSpawnPlacement.canPlace(sim.getGrid(), MechVariant.HOUND.radius, x, y, (px, py) -> true)
                || !MechSpawnPlacement.unoccupied(sim.getRoster(), x, y, MechVariant.HOUND.radius, frame)) return;
        float hp = sim.world().hp(frame);
        float armor = sim.world().armor(frame);
        sim.takeOffTheField(frame);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        mech = sim.spawn(new EntitySpec(WaylineHound.NAME, Faction.MARINE, UnitType.HEAVY_MECH,
                (int) x, (int) y).atPosition(x, y).mechVariant(MechVariant.HOUND).hp(hp).squad(squadId));
        sim.world().setArmor(mech, armor);
        var fit = WaylineHound.deployment();
        sim.world().attachMechLoadout(mech, new MechLoadoutComponent(fit.variant(),
                fit.arms(), fit.leftShoulder(), fit.rightShoulder(), fit.role()));
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = mech;
        squad.originalSize = 1;
        sim.claimSquadCommand(squadId, CommandAuthority.MISSION_COMMAND,
                OpeningOperationCommand.issuer(Faction.MARINE), "Recovered Wayline security walker");
        emit(sim, READY);
    }

    private void emit(BattleSimulation sim, String key) {
        log.emitOnce(sim.getSimTickIndex(), key, StoryBeatLog.Audience.PLAYER);
    }
}
