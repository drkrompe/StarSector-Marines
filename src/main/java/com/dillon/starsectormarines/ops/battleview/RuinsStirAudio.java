package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.ruins.RuinsPressure;
import com.dillon.starsectormarines.battle.ruins.RuinsPressure.Aim;
import com.dillon.starsectormarines.battle.ruins.RuinsPressure.Push;
import com.dillon.starsectormarines.battle.ruins.RuinsPushes.Voice;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.fs.starfarer.api.Global;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The step sting ({@code ruins-pressure.md}, Cues): each push the site sends is heard once, in the occupant's sound,
 * as its comms line goes up. Derelict security is a relay's alert pitched down the halls, the scavengers their radio,
 * the Rav a seismic shudder (a bore's collapse, slowed). The site is everywhere below, so the sting is not
 * positional and not gated by what the company has seen; it names no unit, as the line does not.
 *
 * <p>Every sting is an existing take reused (UNTUNED: pitch and level are not yet heard against the delve's mix). A
 * way-back push plays a little louder and lower, as its line takes the danger tone. Pushes landing in one frame play
 * one sting.
 */
public final class RuinsStirAudio {

    static final String SECURITY = "marines_tender_alert";
    static final String CREW = "c5_yard_radio_chatter";
    static final String SLEEPERS = "marines_breach_bore_collapse";

    /** A sting to play: which sound, at what pitch and level. */
    record Sting(String soundId, float pitch, float volume) { }

    private static final Map<BattleSimulation, Mix> MIXES = new WeakHashMap<>();

    private RuinsStirAudio() { }

    /** Per battle: the pressure heard and how many of its pushes have been. */
    static final class Mix {
        private Object current;
        private int heard;

        /**
         * The sting for the pushes {@code pressure} has sent since the last look, or null: one sting however many
         * arrived. A new pressure (a new battle's) starts unheard.
         */
        Sting hear(Object pressure, Voice voice, List<Push> pushes) {
            if (pressure != current) {
                current = pressure;
                heard = 0;
            }
            if (pressure == null || voice == null || heard >= pushes.size()) return null;
            boolean wayBack = false;
            for (int i = heard; i < pushes.size(); i++) wayBack |= pushes.get(i).aim() == Aim.WAY_BACK;
            heard = pushes.size();
            return sting(voice, wayBack);
        }
    }

    /** The occupant's sting; a way-back push's is lower and louder. */
    static Sting sting(Voice voice, boolean wayBack) {
        float pitch = switch (voice) {
            case SECURITY -> 0.8f;
            case CREW -> 1f;
            case SLEEPERS -> 0.7f;
        };
        float volume = switch (voice) {
            case SECURITY -> 0.5f;
            case CREW -> 0.45f;
            case SLEEPERS -> 0.7f;
        };
        String id = switch (voice) {
            case SECURITY -> SECURITY;
            case CREW -> CREW;
            case SLEEPERS -> SLEEPERS;
        };
        return wayBack ? new Sting(id, pitch * 0.9f, Math.min(1f, volume * 1.25f)) : new Sting(id, pitch, volume);
    }

    public static void play(BattleSimulation sim) {
        if (sim.isComplete()) return;
        RuinsPressure pressure = sim.ruinsPressure();
        Mix mix = MIXES.computeIfAbsent(sim, ignored -> new Mix());
        if (pressure == null || pressure.fielded() == null) {
            mix.hear(null, null, List.of());
            return;
        }
        Sting sting = mix.hear(pressure, pressure.fielded().voice(), pressure.pushes());
        if (sting != null) Global.getSoundPlayer().playUISound(sting.soundId(), sting.pitch(), sting.volume());
    }
}
