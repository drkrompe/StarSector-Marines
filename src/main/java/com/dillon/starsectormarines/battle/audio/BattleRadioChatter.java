package com.dillon.starsectormarines.battle.audio;

import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadAlertLevel;
import com.dillon.starsectormarines.battle.unit.Faction;

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
 * Contact and morale-break transitions are retained while another voice owns
 * the global cooldown; fallback has priority over contact. When no event is
 * queued, a currently engaged squad can occasionally emit a short
 * acknowledgement to keep the tactical net alive without becoming a constant
 * voice track.
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
    }

    /**
     * Samples current squad state and returns at most one cue. A zero delta
     * samples transitions without advancing either audio timer, which lets a
     * host freeze chatter during pause.
     */
    public Emission advance(float dt, Iterable<Squad> squads) {
        float elapsed = Math.max(0f, dt);
        voiceCooldown = Math.max(0f, voiceCooldown - elapsed);
        acknowledgementTimer = Math.max(0f, acknowledgementTimer - elapsed);

        List<Squad> engaged = new ArrayList<>();
        boolean pendingSourceAlive = pending == null;
        for (Squad squad : squads) {
            if (!isMarineInfantry(squad)) {
                squadStates.remove(squad.id);
                continue;
            }

            if (pending != null && pending.squadId == squad.id) pendingSourceAlive = true;
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
        if (pending == null && acknowledgementTimer <= 0f && !engaged.isEmpty()) {
            queue(Cue.ACKNOWLEDGE, engaged.get(random.nextInt(engaged.size())));
            acknowledgementTimer = nextAcknowledgementGap();
        }

        if (pending == null || voiceCooldown > 0f) return null;
        Emission emission = new Emission(
                pending.cue, pending.squadId, pending.cellX, pending.cellY);
        pending = null;
        voiceCooldown = VOICE_GAP_SECONDS;
        return emission;
    }

    private void queue(Cue cue, Squad squad) {
        if (pending != null && pending.cue.priority >= cue.priority) return;
        pending = new PendingCue(cue, squad.id, squad.centroidX, squad.centroidY);
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
        CONTACT("marines_radio_contact", 2, 0.32f),
        FALLBACK("marines_radio_fallback", 3, 0.32f);

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
}
