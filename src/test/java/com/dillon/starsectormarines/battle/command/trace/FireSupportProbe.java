package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.airsupport.AirPoolService;
import com.dillon.starsectormarines.battle.deployable.FieldMortarService;
import com.dillon.starsectormarines.battle.deployable.MortarSections;
import com.dillon.starsectormarines.battle.fort.Fort;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the marines' fire support did over a replayed Conquest: the tubes' rounds and how many were fired on an assigned
 * mission, tubes lost or re-crewed, the air pool's calls, launches and losses, and which fort nests died and to what. The
 * attribution is a heuristic read off the simulation (a tube aimed at the nest, or a pool sortie over it, in the 90 ticks
 * before it fell; otherwise the ground), not a damage ledger.
 */
final class FireSupportProbe {

    private static final int ATTRIBUTION_WINDOW_TICKS = 90;
    private static final float AIR_ATTRIBUTION_CELLS = 12f;

    private final Map<Long, int[]> tubeRounds = new HashMap<>();
    private final Map<Long, Integer> lastMortarAim = new HashMap<>();
    private final Map<Long, Integer> lastAirNear = new HashMap<>();
    private final Set<Long> seenDead = new HashSet<>();
    private final Map<AirPoolService.EventType, Integer> pool = new EnumMap<>(AirPoolService.EventType.class);
    private int poolSeen;
    private int nests;
    private int killedByMortar;
    private int killedByAir;
    private int killedByGround;

    void sample(BattleSimulation sim) {
        World w = sim.world();
        int tick = sim.getSimTickIndex();
        for (FieldMortarService.Tube tube : sim.fieldMortars().tubes()) {
            if (tube.faction != Faction.MARINE) continue;
            tubeRounds.put(tube.entityId() != 0L ? tube.entityId() : -tube.carrierId,
                    new int[] {tube.roundsFired(), tube.missionRoundsFired()});
            if (tube.missionTarget() != 0L) lastMortarAim.put(tube.missionTarget(), tick);
        }
        List<Fort> forts = sim.forts().forts();
        if (!forts.isEmpty()) {
            for (long craft : sim.getAirEntityIds()) {
                ShuttleMission mission = w.mission(craft);
                if (mission == null || !mission.poolSortie) continue;
                for (Fort fort : forts) for (long nest : fort.nestIds()) {
                    if (Math.hypot(w.x(craft) - w.x(nest), w.y(craft) - w.y(nest)) <= AIR_ATTRIBUTION_CELLS) lastAirNear.put(nest, tick);
                }
            }
            nests = 0;
            for (Fort fort : forts) for (long nest : fort.nestIds()) {
                nests++;
                if (w.isAlive(nest) || !seenDead.add(nest)) continue;
                if (tick - lastMortarAim.getOrDefault(nest, -100000) <= ATTRIBUTION_WINDOW_TICKS) killedByMortar++;
                else if (tick - lastAirNear.getOrDefault(nest, -100000) <= ATTRIBUTION_WINDOW_TICKS) killedByAir++;
                else killedByGround++;
            }
        }
        List<AirPoolService.Event> events = sim.airPool().events();
        while (poolSeen < events.size()) {
            AirPoolService.Event e = events.get(poolSeen++);
            if (e.side() == Faction.MARINE) pool.merge(e.type(), 1, Integer::sum);
        }
    }

    /** Rounds the marines' tubes fired, and how many of them on an assigned mission, as numbers for a summary. */
    int[] marineRounds() {
        int rounds = 0;
        int mission = 0;
        for (int[] r : tubeRounds.values()) {
            rounds += r[0];
            mission += r[1];
        }
        return new int[] {rounds, mission};
    }

    /** Fort nests killed, by mortar, by air and by the ground (the 90-tick heuristic), as numbers for a summary. */
    int[] nestKills() {
        return new int[] {seenDead.size(), killedByMortar, killedByAir, killedByGround};
    }

    String describe(BattleSimulation sim) {
        int rounds = 0;
        int mission = 0;
        for (int[] r : tubeRounds.values()) {
            rounds += r[0];
            mission += r[1];
        }
        FieldMortarService mortars = sim.fieldMortars();
        StringBuilder out = new StringBuilder();
        out.append(String.format("- fire support: tubes, both sides (set down %d, lifted %d, lost with gunner %d, shot out %d, re-crewed %d); marine tubes: "
                        + "rounds fired %d (%d on an assigned mission)%n",
                mortars.setUps(), mortars.packs(), mortars.lostWithGunner(), mortars.destroyedStanding(), mortars.recrewed(),
                rounds, mission));
        MortarSections sections = mortars.sections();
        out.append(String.format("- mortar sections: opened %d, ended %d (window expired %d, under fire %d, never committed %d), "
                        + "engagement held a window past its plain length %d times, held %.0fs in all; rounds fired from them %d (%d on a mission)%n",
                sections.opened(), sections.ended(), sections.expired(), sections.underFire(), sections.abandoned(),
                sections.extended(), sections.secondsHeld(), sections.roundsFired(), sections.missionRoundsFired()));
        out.append(String.format("- marine air pool: called %d, launched %d, returned %d, lost %d, aborted %d, dropped %d, replaced %d%n",
                pool.getOrDefault(AirPoolService.EventType.CALLED, 0), pool.getOrDefault(AirPoolService.EventType.LAUNCHED, 0),
                pool.getOrDefault(AirPoolService.EventType.RETURNED, 0), pool.getOrDefault(AirPoolService.EventType.LOST, 0),
                pool.getOrDefault(AirPoolService.EventType.ABORTED, 0), pool.getOrDefault(AirPoolService.EventType.CALL_DROPPED, 0),
                pool.getOrDefault(AirPoolService.EventType.REPLACED, 0)));
        out.append(String.format("- fort nests killed: %d of %d (by mortar %d, by air %d, by the ground %d; attribution is a 90-tick heuristic)%n",
                seenDead.size(), nests, killedByMortar, killedByAir, killedByGround));
        return out.toString();
    }
}
