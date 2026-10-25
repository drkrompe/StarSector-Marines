package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * The forward observers a mission posts with a side's formations (`air-nouns.md`, Forward observer): one radio to each
 * <b>post</b> (a redoubt's garrison, a lane's column), carried by a soldier of the first of the post's squads that is
 * on the map with a hand free. The radio is what lets the side's {@link AirStrikeDirector} put its pool onto ground that
 * observer has in sight, and nothing else does.
 *
 * <p>A post is issued once. An observer killed or a post never manned leaves that formation with no eyes for the air
 * for the rest of the battle: losing one is part of the fight, never something the mission quietly repairs.
 *
 * <p>The support kit's radio for squads the battle makes itself (garrisons, columns), which no lift or defender roll
 * counts: those issue by squad ordinal ({@code SquadSupportKit}), this by formation.
 */
public final class ForwardObservers {

    /** Ticks between looks for a post still waiting for its observer. */
    static final int PERIOD_TICKS = 30;

    /** A formation that carries one observer: the squads it may be issued into, in preference order. */
    @FunctionalInterface
    public interface Post {
        List<Integer> squads(BattleSimulation sim);
    }

    private final Faction side;
    private final List<? extends Post> posts;
    private final long[] observers;

    public ForwardObservers(Faction side, List<? extends Post> posts) {
        this.side = side;
        this.posts = List.copyOf(posts);
        this.observers = new long[this.posts.size()];
    }

    public Faction side() {
        return side;
    }

    /** How many posts the mission named. */
    public int posts() {
        return observers.length;
    }

    /** The unit post {@code index} issued its radio to, or {@code 0} while it waits for one. */
    public long observer(int index) {
        return observers[index];
    }

    /** Posts that have issued their radio. */
    public int posted() {
        int n = 0;
        for (long o : observers) if (o != 0L) n++;
        return n;
    }

    /** Issues each waiting post's radio, if one of its squads is now on the map with a hand to carry it. */
    void tick(BattleSimulation sim) {
        if (sim.getSimTickIndex() % PERIOD_TICKS != 0) return;
        for (int i = 0; i < observers.length; i++) {
            if (observers[i] == 0L) observers[i] = issue(sim, side, posts.get(i).squads(sim));
        }
    }

    /**
     * Gives a comms radio to a soldier of the first of {@code squads} that is {@code side}'s, on the map, and has a
     * soldier on foot carrying no special item: a member other than the leader when there is one, so the squad keeps
     * its leader's own kit. Returns the observer, or {@code 0} when no squad had one to give.
     */
    static long issue(BattleSimulation sim, Faction side, List<Integer> squads) {
        World world = sim.world();
        for (int id : squads) {
            Squad squad = sim.getSquad(id);
            if (squad == null || squad.faction != side) continue;
            long chosen = 0L;
            for (int i = 0, n = sim.squadMemberCount(id); i < n; i++) {
                long member = sim.squadMemberAt(id, i);
                if (!world.isAlive(member) || !onFoot(sim.identity().type(member))) continue;
                if (world.hasSecondaryWeapon(member) && world.specialEquipment(member) != null) continue;
                chosen = member;
                if (member != squad.leaderId) break;
            }
            if (chosen == 0L) continue;
            SpecialEquipmentDef radio = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.COMMS_RADIO_ID);
            world.attachSpecialEquipment(chosen, radio, radio.startingAmmo());
            return chosen;
        }
        return 0L;
    }

    /**
     * A post for a formation that has no roster of its own but a band of the front: {@code side}'s squads whose leader
     * stands within {@code halfWidth} of {@code across} (along x when the front advances along y), earliest made first,
     * so the radio goes to the first team to step off on that ground.
     */
    public static Post band(Faction side, boolean advancesAlongY, int across, int halfWidth) {
        return sim -> {
            List<Integer> out = new ArrayList<>();
            for (Squad squad : sim.getSquads()) {
                if (squad.faction != side || !sim.world().isAlive(squad.leaderId)) continue;
                float at = advancesAlongY ? sim.world().x(squad.leaderId) : sim.world().y(squad.leaderId);
                if (Math.abs(at - across) <= halfWidth) out.add(squad.id);
            }
            out.sort(null);
            return out;
        };
    }

    /** Whether a unit of this type is a soldier on foot, who can carry a radio. */
    static boolean onFoot(UnitType type) {
        return type.combatant && !type.isMech() && !type.isMachineFrame() && !type.isGroundVehicle()
                && !type.isStatic() && !type.isSwarm() && !type.isDrone();
    }
}
