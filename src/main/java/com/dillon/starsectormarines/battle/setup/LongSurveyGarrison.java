package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.Armament;
import com.dillon.starsectormarines.battle.command.CapabilityProfile;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.CommandDirective;
import com.dillon.starsectormarines.battle.derelict.DerelictSecurityDirector;
import com.dillon.starsectormarines.battle.derelict.FallenHull;
import com.dillon.starsectormarines.battle.derelict.GarrisonTasking;
import com.dillon.starsectormarines.battle.derelict.StakeTargets;
import com.dillon.starsectormarines.battle.drone.Flock;
import com.dillon.starsectormarines.battle.evacuation.HeartLanes;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The garrison against the watch in a Long Survey battle ({@code fallen-hull-nouns.md}, The garrison against the
 * watch): once a second it reads the allied squads, the watch's walkers and flocks the side sees, and the claim's
 * standing stakes, and issues {@link GarrisonTasking}'s orders.
 *
 * <ul>
 *   <li>An answer to a walker or a flock is leased over whatever holds the squad, the lanes included, for
 *   {@link #LEASE_SECONDS}, and renewed while the threat stands; when it lapses the squad's own command takes it back.</li>
 *   <li>A stake order goes to a squad the lanes do not hold. A lane squad's stake is its lane's target, which reads
 *   the same {@link StakeTargets} ({@link LongSurveyBattle#stakeTargets}), so the lanes keep their formations.</li>
 * </ul>
 *
 * The player's squads are never ordered: the company is the player's. Ticked after the lanes
 * ({@link BattleSimulation#installAlliedOrders}).
 */
final class LongSurveyGarrison implements Consumer<BattleSimulation> {

    static final String ISSUER = "long-survey-garrison";
    /** How often the garrison reads the field and issues its orders. */
    static final float PULSE_SECONDS = 1f;
    /** How long an answer to a threat holds a squad over its own command, unless renewed. UNTUNED. */
    static final float LEASE_SECONDS = 3f;

    private final FallenHull hull;
    private final int pulseTicks = Math.max(1, Math.round(PULSE_SECONDS / BattleSimulation.TICK_DT));
    private final int leaseTicks = Math.max(1, Math.round(LEASE_SECONDS / BattleSimulation.TICK_DT));

    LongSurveyGarrison(FallenHull hull) {
        this.hull = hull;
    }

    @Override
    public void accept(BattleSimulation sim) {
        int tick = sim.getSimTickIndex();
        if (tick % pulseTicks != 0) return;
        DerelictSecurityDirector watch = sim.derelictSecurity();
        if (watch == null) return;
        List<GarrisonTasking.Squad> squads = new ArrayList<>();
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != Faction.ALLY || sim.squadMemberCount(squad.id) == 0) continue;
            CommandDirective held = sim.getSquadCommandDirective(squad.id);
            if (held != null && held.authority() == CommandAuthority.PLAYER_INTERVENTION) continue;
            int[] at = centroid(sim, squad.id);
            if (at != null) squads.add(new GarrisonTasking.Squad(squad.id, at[0], at[1], antiArmour(sim, squad)));
        }
        if (squads.isEmpty()) return;
        List<int[]> walkers = new ArrayList<>();
        for (DerelictSecurityDirector.Walker w : watch.walkers()) {
            int[] at = centroid(sim, w.squadId());
            if (at != null && sim.playerSeesCell(at[0], at[1])) walkers.add(at);
        }
        List<int[]> flocks = new ArrayList<>();
        for (Flock flock : sim.flocks().flocks()) {
            if (flock.side() != Faction.DEFENDER || flock.size() == 0) continue;
            int[] at = centroid(sim, flock.memberIds());
            if (at != null && sim.playerSeesCell(at[0], at[1])) flocks.add(at);
        }
        List<StakeTargets.Target> stakes = hull.stakeTargets(watch);
        for (GarrisonTasking.Order order : GarrisonTasking.orders(squads, walkers, flocks, stakes)) {
            issue(sim, order, tick);
        }
    }

    private void issue(BattleSimulation sim, GarrisonTasking.Order order, int tick) {
        Squad squad = sim.getSquad(order.squadId());
        if (squad == null) return;
        if (order.duty() == GarrisonTasking.Duty.STAKE) {
            CommandDirective held = sim.getSquadCommandDirective(order.squadId());
            if (held != null && HeartLanes.ISSUER.equals(held.issuer())) return;
        }
        sim.getCommanderService().assignments().assignExternal(squad, order.assignment(), CommandAuthority.SCRIPTED,
                ISSUER, "garrison " + order.duty().name().toLowerCase() + " against the watch", tick, tick + leaseTicks);
    }

    /** Whether the squad's kit hurts a frame, read as the command frame reads its own force's kit. */
    private static boolean antiArmour(BattleSimulation sim, Squad squad) {
        int count = sim.squadMemberCount(squad.id);
        boolean machine = squad.isMechSquad() || squad.isDroneSquad();
        List<Armament> arms = new ArrayList<>();
        int walkers = 0;
        for (int i = 0; i < count; i++) {
            long member = sim.squadMemberAt(squad.id, i);
            if (sim.world().hasMechLoadout(member)) {
                for (MechWeaponMount mount : sim.world().mechLoadout(member).mounts()) {
                    if (mount != null) arms.add(Armament.ofMount(mount));
                }
            } else if (!machine) {
                walkers++;
                arms.add(Armament.ofPrimary(sim.combat().primaryWeaponDef(member)));
                if (sim.world().hasSecondaryWeapon(member)) {
                    arms.add(Armament.ofSpecial(sim.world().specialEquipment(member), sim.world().secondaryAmmo(member)));
                }
            }
        }
        return GarrisonTasking.antiArmour(CapabilityProfile.of(arms, walkers, count));
    }

    private static int[] centroid(BattleSimulation sim, int squadId) {
        int count = sim.squadMemberCount(squadId);
        List<Long> members = new ArrayList<>(count);
        for (int i = 0; i < count; i++) members.add(sim.squadMemberAt(squadId, i));
        return centroid(sim, members);
    }

    private static int[] centroid(BattleSimulation sim, List<Long> members) {
        float x = 0f, y = 0f;
        int n = 0;
        for (long m : members) {
            if (m == 0L || !sim.world().isAlive(m)) continue;
            x += sim.world().x(m);
            y += sim.world().y(m);
            n++;
        }
        return n == 0 ? null : new int[] {(int) (x / n), (int) (y / n)};
    }
}
