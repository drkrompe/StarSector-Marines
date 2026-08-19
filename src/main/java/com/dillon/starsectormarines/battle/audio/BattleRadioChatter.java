package com.dillon.starsectormarines.battle.audio;

import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadAlertLevel;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Presentation-side policy for sparse marine infantry radio chatter.
 *
 * <p>The controller samples squad state after a simulation advance and emits
 * at most one cue. It never mutates the simulation and owns a separate random
 * source, so chatter timing cannot perturb deterministic battle outcomes.
 * Squad transitions and combat facts are retained behind one global cooldown,
 * with friendly fire, fallback, and mech contact taking priority over routine
 * calls. When no event is queued, a currently engaged squad can occasionally
 * emit a short acknowledgement or combat remark to keep the tactical net alive
 * without becoming a constant voice track.
 *
 * <p>This class deliberately knows nothing about OpenAL or either battle
 * host's coordinate system. The standalone and hybrid presenters project the
 * returned squad centroid into their own audio frames.
 */
public final class BattleRadioChatter {

    private static final float INITIAL_DELAY_SECONDS = 2.5f;
    private static final float VOICE_GAP_SECONDS = 7f;
    private static final float ACK_MIN_GAP_SECONDS = 16f;
    private static final float ACK_MAX_GAP_SECONDS = 28f;

    private final Random random;
    private final Map<Integer, SquadState> squadStates = new HashMap<>();
    private PendingCue pending;
    private float voiceCooldown;
    private float acknowledgementTimer;
    private boolean enemyMechAnnounced;

    public BattleRadioChatter() {
        this(new Random());
    }

    BattleRadioChatter(Random random) {
        this.random = Objects.requireNonNull(random);
        reset();
    }

    /** Clears per-battle state while preserving the presentation-only RNG. */
    public void reset() {
        squadStates.clear();
        pending = null;
        voiceCooldown = INITIAL_DELAY_SECONDS;
        acknowledgementTimer = nextAcknowledgementGap();
        enemyMechAnnounced = false;
    }

    /** Samples squad transitions plus presentation-safe events exposed by the simulation. */
    public Emission advance(float dt, BattleSimulation sim) {
        return advance(dt, sim.getSquads(), readEvents(sim, !enemyMechAnnounced));
    }

    /**
     * Samples current squad state and returns at most one cue. A zero delta
     * samples transitions without advancing either audio timer, which lets a
     * host freeze chatter during pause.
     */
    public Emission advance(float dt, Iterable<Squad> squads) {
        return advance(dt, squads, FrameEvents.NONE);
    }

    Emission advance(float dt, Iterable<Squad> squads, FrameEvents events) {
        float elapsed = Math.max(0f, dt);
        voiceCooldown = Math.max(0f, voiceCooldown - elapsed);
        acknowledgementTimer = Math.max(0f, acknowledgementTimer - elapsed);

        List<Squad> eligible = new ArrayList<>();
        List<Squad> engaged = new ArrayList<>();
        boolean pendingSourceAlive = pending == null;
        for (Squad squad : squads) {
            if (!isMarineInfantry(squad)) {
                squadStates.remove(squad.id);
                continue;
            }

            if (pending != null && pending.squadId == squad.id) pendingSourceAlive = true;
            eligible.add(squad);
            boolean isEngaged = squad.alertLevel == SquadAlertLevel.ENGAGED;
            if (isEngaged) engaged.add(squad);

            SquadState previous = squadStates.get(squad.id);
            if (previous == null) {
                if (squad.moraleBroken) {
                    queue(Cue.FALLBACK, squad);
                } else if (isEngaged) {
                    queue(Cue.CONTACT, squad);
                }
            } else {
                if (!previous.moraleBroken && squad.moraleBroken) {
                    queue(Cue.FALLBACK, squad);
                } else if (!previous.engaged && isEngaged) {
                    queue(Cue.CONTACT, squad);
                }
            }
            squadStates.put(squad.id, new SquadState(isEngaged, squad.moraleBroken));
        }

        if (!pendingSourceAlive) pending = null;

        for (int squadId : events.friendlyFireSquadIds) {
            Squad source = findSquad(eligible, squadId);
            if (source != null) queue(Cue.CHECK_FIRE, source);
        }
        if (!enemyMechAnnounced && events.enemyMechVisible) {
            Squad source = findSquad(engaged, events.enemyMechSquadId);
            if (source != null && queue(Cue.MECH_SPOTTED, source)) enemyMechAnnounced = true;
        }
        if (events.enemyDown) {
            Squad source = nearestSquad(engaged, events.enemyDownX, events.enemyDownY);
            if (source == null) source = nearestSquad(eligible, events.enemyDownX, events.enemyDownY);
            if (source != null) queue(Cue.ENEMY_DOWN, source);
        }

        if (pending == null && acknowledgementTimer <= 0f && !engaged.isEmpty()) {
            Cue ambient = random.nextBoolean() ? Cue.ACKNOWLEDGE : Cue.COMBAT;
            queue(ambient, engaged.get(random.nextInt(engaged.size())));
            acknowledgementTimer = nextAcknowledgementGap();
        }

        if (pending == null || voiceCooldown > 0f) return null;
        Emission emission = new Emission(
                pending.cue, pending.squadId, pending.cellX, pending.cellY);
        pending = null;
        voiceCooldown = VOICE_GAP_SECONDS;
        return emission;
    }

    private boolean queue(Cue cue, Squad squad) {
        if (pending != null && pending.cue.priority >= cue.priority) return false;
        pending = new PendingCue(cue, squad.id, squad.centroidX, squad.centroidY);
        return true;
    }

    private static FrameEvents readEvents(BattleSimulation sim, boolean findEnemyMech) {
        List<Integer> friendlyFireSquads = new ArrayList<>();
        for (int i = 0; i < sim.getFriendlyFireSquadsThisFrame().size(); i++) {
            friendlyFireSquads.add(sim.getFriendlyFireSquadsThisFrame().getInt(i));
        }

        boolean mechVisible = false;
        int mechSpotterSquadId = Squad.NO_SQUAD;
        FogOfWarService fog = sim.getFogOfWar();
        if (findEnemyMech && fog.isInitialized()) {
            for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
                long unit = sim.liveUnitAt(i);
                if (sim.identity().faction(unit) == Faction.DEFENDER
                        && sim.identity().type(unit).isMech()
                        && fog.getUnitVisibility(i) == FogOfWarService.VIS_VISIBLE) {
                    int unitX = sim.world().cellX(unit);
                    int unitY = sim.world().cellY(unit);
                    for (int j = 0; j < n; j++) {
                        long spotter = sim.liveUnitAt(j);
                        if (sim.identity().faction(spotter) != Faction.MARINE
                                || !sim.squad().hasSquad(spotter)) continue;
                        Squad squad = sim.getSquad(sim.squad().squadId(spotter));
                        if (!isMarineInfantry(squad)
                                || squad.alertLevel != SquadAlertLevel.ENGAGED) continue;
                        float dx = sim.world().x(unit) - sim.world().x(spotter);
                        float dy = sim.world().y(unit) - sim.world().y(spotter);
                        float visionRange = sim.vision().visionRange(spotter);
                        if (dx * dx + dy * dy > visionRange * visionRange) continue;
                        if (!TacticalScoring.canSeePair(sim.getGrid(),
                                sim.world().cellX(spotter), sim.world().cellY(spotter),
                                unitX, unitY, sim.vision().airLosRadius(spotter),
                                sim.vision().airLosRadius(unit))) continue;
                        mechVisible = true;
                        mechSpotterSquadId = squad.id;
                        break;
                    }
                    if (mechVisible) break;
                }
            }
        }

        boolean enemyDown = false;
        float enemyDownX = 0f;
        float enemyDownY = 0f;
        for (int i = 0; i < sim.getDeathsThisFrame().size(); i++) {
            long unit = sim.getDeathsThisFrame().getLong(i);
            UnitType type = sim.identity().type(unit);
            if (sim.identity().faction(unit) == Faction.DEFENDER && type.combatant) {
                enemyDown = true;
                enemyDownX = sim.world().renderX(unit);
                enemyDownY = sim.world().renderY(unit);
                break;
            }
        }
        return new FrameEvents(friendlyFireSquads, mechVisible, mechSpotterSquadId,
                enemyDown, enemyDownX, enemyDownY);
    }

    private static Squad findSquad(List<Squad> squads, int id) {
        for (Squad squad : squads) if (squad.id == id) return squad;
        return null;
    }

    private static Squad nearestSquad(List<Squad> squads, float x, float y) {
        Squad nearest = null;
        float nearestDistanceSq = Float.POSITIVE_INFINITY;
        for (Squad squad : squads) {
            float dx = squad.centroidX - x;
            float dy = squad.centroidY - y;
            float distanceSq = dx * dx + dy * dy;
            if (distanceSq < nearestDistanceSq) {
                nearest = squad;
                nearestDistanceSq = distanceSq;
            }
        }
        return nearest;
    }

    private float nextAcknowledgementGap() {
        return ACK_MIN_GAP_SECONDS
                + random.nextFloat() * (ACK_MAX_GAP_SECONDS - ACK_MIN_GAP_SECONDS);
    }

    private static boolean isMarineInfantry(Squad squad) {
        return squad.faction == Faction.MARINE
                && squad.aliveMembers > 0
                && !squad.isMechSquad()
                && !squad.isDroneSquad();
    }

    public enum Cue {
        ACKNOWLEDGE("marines_radio_ack", 1, 0.32f),
        COMBAT("marines_radio_combat", 1, 0.32f),
        ENEMY_DOWN("marines_radio_enemy_down", 2, 0.32f),
        CONTACT("marines_radio_contact", 3, 0.32f),
        MECH_SPOTTED("marines_radio_mech_spotted", 4, 0.32f),
        FALLBACK("marines_radio_fallback", 5, 0.32f),
        CHECK_FIRE("marines_radio_check_fire", 6, 0.32f);

        private final String soundId;
        private final int priority;
        private final float volume;

        Cue(String soundId, int priority, float volume) {
            this.soundId = soundId;
            this.priority = priority;
            this.volume = volume;
        }

        public String soundId() {
            return soundId;
        }

        public float volume() {
            return volume;
        }
    }

    public record Emission(Cue cue, int squadId, float cellX, float cellY) {}

    private record SquadState(boolean engaged, boolean moraleBroken) {}

    private record PendingCue(Cue cue, int squadId, float cellX, float cellY) {}

    record FrameEvents(List<Integer> friendlyFireSquadIds,
                       boolean enemyMechVisible, int enemyMechSquadId,
                       boolean enemyDown, float enemyDownX, float enemyDownY) {
        private static final FrameEvents NONE = new FrameEvents(
                List.of(), false, Squad.NO_SQUAD, false, 0f, 0f);
    }
}
