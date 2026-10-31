package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.evacuation.SwarmCues;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.fs.starfarer.api.Global;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The background voice of the people in a fight (`colony-breach-nouns.md`, Sound): shouts, cries and
 * grunts heard in the mix under the fire, never hero lines, so nothing is shown and nothing waits on them. Three
 * {@link Cast casts} speak, each in its own voices and words: the marines (the player's and allied company infantry),
 * the colony militia, and the colonists, who make no soldier's calls, only fear, pain and grief. Read off what the
 * battle already reports, as {@link SwarmAudio} is, so the simulation stays silent:
 * <ul>
 *   <li><b>Contact</b> when Rav close on a squad that was clear of them, called by its marine nearest them.</li>
 *   <li><b>Reload</b> when a marine starts a reload with Rav near.</li>
 *   <li><b>Grabbed</b> from a marine a Runner seizes or impales ({@link SwarmCues.Kind#DRAG}, {@code IMPALE}), and a
 *       cry from one <b>thrown</b> ({@code THROWN}, posted by the body that lands).</li>
 *   <li>A warning from the marine nearest a <b>Brute</b> telling its charge, or the <b>Matriarch</b> telling her run.</li>
 *   <li>Relief when a Rav dies near the line (<b>kill</b>, or <b>big kill</b> for a Brute or the Matriarch), and a
 *       curse from a squadmate when a marine dies with Rav near (<b>man down</b>).</li>
 *   <li>A <b>strain</b> grunt and a <b>scuffle</b> of boots and gear when a marine is hurt with a Rav at arm's length.</li>
 * </ul>
 * Against people and machines (the enemy's own fighters, seen by the player), the same voices call:
 * <ul>
 *   <li>A <b>contact</b> of its own ({@code contact_hostile}) when enemy infantry close on a squad that was clear of
 *       them, and <b>drones</b> the same way.</li>
 *   <li><b>Taking fire</b> when enemy rounds land on a squad after a quiet spell, and a <b>turret</b> warning when an
 *       emplacement fired them.</li>
 *   <li><b>Frag out</b> from a marine throwing a grenade, and <b>grenade</b> from the one nearest where an enemy's
 *       will land.</li>
 *   <li><b>Armor</b> from the one nearest a mech, machine frame or vehicle the first time it is seen near them.</li>
 *   <li>A pained <b>hit</b> (the thrown takes) when one is hurt with no Rav at hand; reload and man down with the
 *       enemy near as with the Rav; and a <b>kill</b> from a marine (not the militia, whose kill lines are for bugs)
 *       when an enemy fighter dies near the line.</li>
 * </ul>
 * Each speaker keeps one of its cast's voices for the battle, dealt round its squad so squadmates differ, and a
 * little pitched from anyone sharing it; the lines of a kind take turns, so the same line is not heard twice running.
 * People outside any squad, as most colonists are, share a channel with those of their cast on their patch of ground.
 * A squad has one voice at a time ({@link Channels}):
 * nobody in it starts while another is speaking (save the cry of one being seized or thrown), a marine does not bark
 * again inside its own gap, a squad does not
 * repeat a call soon after, and the whole battle has at most {@link #VOICES} barks sounding at once, the most urgent
 * first. Each kind also layers through a {@link VoicePool}. Heard only on ground the player has seen. Levels live in
 * {@code sounds.json}, set per take from its measured loudness by {@code tools/generate_marine_barks.py}.
 */
public final class MarineBarks {

    static final String PREFIX = "marines_bark_";
    /** Lines recorded of every kind, in every voice. */
    static final int LINES = 3;
    /** Barks sounding at once across the battle, at most. */
    static final int VOICES = 3;
    /** A squad's breath between one voice ending and the next starting. */
    static final float SQUAD_PAUSE_SECONDS = 0.6f;
    /** A Rav this close to a squad that was clear is contact; it is clear again past {@link #CLEAR_CELLS}. */
    static final float CONTACT_CELLS = 9f;
    static final float CLEAR_CELLS = 15f;
    /** A Rav this close makes a reload worth calling, a kill or a death worth a word. */
    static final float NEAR_CELLS = 12f;
    /** A Brute or the Matriarch telling within this of a marine is warned of. */
    static final float WARN_CELLS = 16f;
    /** A Rav this close to a hurt marine is a fight hand to hand. */
    static final float MELEE_CELLS = 2.2f;
    /** Health lost below this is a graze and draws no grunt. */
    static final float HURT_THRESHOLD = 1f;
    /** Contact is looked for every this many ticks. */
    static final int CONTACT_EVERY = 6;
    /** Enemy infantry this close to a squad that was clear is contact; it is clear again past the second. Gunfire range, not claw range. */
    static final float HOSTILE_CONTACT_CELLS = 14f;
    static final float HOSTILE_CLEAR_CELLS = 22f;
    /** Enemy drones this close to a squad that was clear are called; it is clear of them again past the second. */
    static final float DRONE_CONTACT_CELLS = 10f;
    static final float DRONE_CLEAR_CELLS = 16f;
    /** An enemy round ending this close to a speaker was fired at them. */
    static final float FIRE_CELLS = 2f;
    /** A squad not fired on for this long is taking fire again when the next round lands. */
    static final float QUIET_SECONDS = 8f;
    /** An enemy machine first seen this close to a speaker is warned of. */
    static final float ARMOR_CELLS = 18f;
    /** A grenade leaving this close to a speaker is theirs to call. */
    static final float THROWER_CELLS = 1.5f;
    /** A speaker this far beyond an enemy grenade's blast is still close enough to shout it. */
    static final float GRENADE_MARGIN_CELLS = 2f;
    /** An enemy this close makes a speaker's wound a hit by fire worth a cry. */
    static final float HIT_RANGE_CELLS = 30f;

    /**
     * One kind of bark: its priority (lower first), whether it is a voice (the squad's one channel) or an effort sound,
     * how long a line holds the squad's channel, a marine's own gap after it and its squad's before the same kind
     * again, and how many of it may sound at once. A line's length is its longest takes' less their trailing breath
     * ({@code marine-barks/levels.json}); the kinds for people and machines are estimated from their words until
     * their takes are recorded and measured.
     */
    enum Bark {
        GRABBED("grabbed", 0, true, 2.4f, 3f, 0f, 2),
        THROWN("thrown", 1, true, 1.9f, 3f, 0f, 2),
        GRENADE("grenade", 2, true, 1.6f, 3f, 4f, 2),
        MATRIARCH("matriarch", 3, true, 2.4f, 6f, 8f, 1),
        BRUTE("brute", 4, true, 2.1f, 6f, 6f, 1),
        ARMOR("armor", 5, true, 2.2f, 6f, 20f, 1),
        MAN_DOWN("man_down", 6, true, 1.6f, 6f, 8f, 1),
        // Hurt by fire: the thrown takes, but held for the squad like any voice and spaced, or a firefight is all cries.
        HIT("thrown", 7, true, 1.9f, 4f, 5f, 1),
        STRAIN("strain", 8, true, 2.0f, 2.5f, 4f, 2),
        TAKING_FIRE("taking_fire", 9, true, 1.8f, 6f, 15f, 1),
        CONTACT("contact", 10, true, 1.8f, 6f, 15f, 2),
        CONTACT_HOSTILE("contact_hostile", 10, true, 2.0f, 6f, 20f, 2),
        TURRET("turret", 11, true, 2.4f, 6f, 20f, 1),
        DRONES("drones", 11, true, 2.0f, 6f, 20f, 1),
        FRAG_OUT("frag_out", 12, true, 1.4f, 4f, 6f, 1),
        BIG_KILL("big_kill", 13, true, 2.4f, 6f, 6f, 1),
        KILL("kill", 14, true, 1.3f, 6f, 7f, 1),
        RELOAD("reload", 15, true, 2.0f, 6f, 10f, 2),
        // An effort sound holds no channel; its length here spaces the scuffles across the whole fight.
        SCUFFLE("scuffle", 16, false, 2.5f, 4f, 0f, 1);

        final String clip;
        final int priority;
        final boolean voiced;
        final float lineSeconds;
        final float marineGapSeconds;
        final float squadGapSeconds;
        final VoicePool.Layering layering;

        Bark(String clip, int priority, boolean voiced, float lineSeconds, float marineGapSeconds,
             float squadGapSeconds, int voices) {
            this.clip = clip;
            this.priority = priority;
            this.voiced = voiced;
            this.lineSeconds = lineSeconds;
            this.marineGapSeconds = marineGapSeconds;
            this.squadGapSeconds = squadGapSeconds;
            layering = new VoicePool.Layering(voices, lineSeconds, marineGapSeconds);
        }
    }

    /** Who speaks: the take names' prefix, the barks the cast has, and its voices (the take names' voice keys). */
    enum Cast {
        /** The operation radio's cast heard dry: the player's marines and allied company infantry. */
        MARINE("", EnumSet.allOf(Bark.class), "kestrel", "hammer", "viper", "jackal", "ox", "rivet"),
        /** The colony militia: ravs and bugs, grabbers and breakers; against people, frightened civilians with rifles. */
        MILITIA("militia_", EnumSet.allOf(Bark.class), "a", "b", "c", "d"),
        /** Colonists: no calls of their own to make, only fear, pain and grief. */
        CIVILIAN("civilian_", EnumSet.of(Bark.CONTACT, Bark.GRABBED, Bark.THROWN, Bark.HIT, Bark.MAN_DOWN,
                Bark.STRAIN, Bark.SCUFFLE), "a", "b", "c", "d");

        final String prefix;
        final Set<Bark> barks;
        final String[] voices;

        Cast(String prefix, Set<Bark> barks, String... voices) {
            this.prefix = prefix;
            this.barks = barks;
            this.voices = voices;
        }
    }

    /** Cells a side of the patch of ground over which squadless people of one cast share a channel. */
    static final int PATCH_CELLS = 12;

    /** A bark to offer: who says it, of which cast, their channel, and where. */
    record Spot(Bark bark, long speaker, Cast cast, long squad, float x, float y) { }

    /** A bark offered this tick, and the ticks it has already been held. */
    private record Offer(Spot spot, int heldFor) { }

    /** A bark to start, where, and at what pitch and level. */
    public record Play(String soundId, float x, float y, float pitch, float volume) { }

    /**
     * The squads' channels and the battle's voice budget, on simulation seconds. Pure bookkeeping: {@link #admits}
     * asks, {@link #start} records.
     */
    static final class Channels {
        private final Long2DoubleOpenHashMap squadFreeAt = new Long2DoubleOpenHashMap();
        private final Long2DoubleOpenHashMap squadKindAt = new Long2DoubleOpenHashMap();
        private final Long2DoubleOpenHashMap marineFreeAt = new Long2DoubleOpenHashMap();
        private final List<Float> sounding = new ArrayList<>();

        /** Whether {@code marine} of {@code squad} may start {@code bark} at {@code now}. */
        boolean admits(Bark bark, long marine, long squad, float now) {
            if (!bark.voiced) return true;
            sounding.removeIf(end -> end <= now);
            if (sounding.size() >= VOICES) return false;
            // A cry of one's own body being seized or thrown is not held for the squad, nor for one's last word.
            if (!cries(bark) && now < squadFreeAt.getOrDefault(squad, Float.NEGATIVE_INFINITY)) return false;
            if (!cries(bark) && now < marineFreeAt.getOrDefault(marine, Float.NEGATIVE_INFINITY)) return false;
            return now >= squadKindAt.getOrDefault(squadKind(bark, squad), Float.NEGATIVE_INFINITY);
        }

        /** {@code marine} of {@code squad} started {@code bark} at {@code now}. */
        void start(Bark bark, long marine, long squad, float now) {
            if (!bark.voiced) return;
            sounding.add(now + bark.lineSeconds);
            squadFreeAt.put(squad, now + bark.lineSeconds + SQUAD_PAUSE_SECONDS);
            // Every voice holds the marine through its gap, whatever it said: one marine is not a stream of barks.
            marineFreeAt.put(marine, now + Math.max(bark.lineSeconds, Math.min(bark.marineGapSeconds, 3f)));
            if (bark.squadGapSeconds > 0f) squadKindAt.put(squadKind(bark, squad), now + bark.squadGapSeconds);
        }

        void clear() {
            squadFreeAt.clear();
            squadKindAt.clear();
            marineFreeAt.clear();
            sounding.clear();
        }

        private static long squadKind(Bark bark, long squad) { return (long) (bark.ordinal() + 1) << 48 ^ squad; }
    }

    // Presentation-only: per battle, the channels, the readings and whose turn each kind's lines are on.
    private static final Map<BattleSimulation, Mix> MIXES = new WeakHashMap<>();

    /** Per battle: the last tick heard, the channels, each marine's health and reload last seen. */
    public static final class Mix {
        private int tick = -1;
        final Channels channels = new Channels();
        final VoicePool<Bark, Spot> voices = new VoicePool<>(Bark.class, bark -> bark.layering);
        private final EnumMap<Bark, Integer> turns = new EnumMap<>(Bark.class);
        private final Long2DoubleOpenHashMap health = new Long2DoubleOpenHashMap();
        private final Long2DoubleOpenHashMap reload = new Long2DoubleOpenHashMap();
        /** Squads in contact with the Rav, with enemy infantry and with enemy drones, by channel; absent is clear. */
        private final Long2IntOpenHashMap contact = new Long2IntOpenHashMap();
        private final Long2IntOpenHashMap hostileContact = new Long2IntOpenHashMap();
        private final Long2IntOpenHashMap droneContact = new Long2IntOpenHashMap();
        /** When each channel was last fired on, in simulation seconds. */
        private final Long2DoubleOpenHashMap firedOnAt = new Long2DoubleOpenHashMap();
        /** Enemy machines already warned of. */
        private final LongOpenHashSet announcedArmor = new LongOpenHashSet();
        /** Rounds in flight last tick, so a grenade is called once, as it leaves. */
        private Set<Projectile> projectiles = Collections.newSetFromMap(new IdentityHashMap<>());
        /** Each speaker's voice, as an index into its cast's, once it has spoken; how many each squad has dealt. */
        private final Long2IntOpenHashMap voiceOf = new Long2IntOpenHashMap();
        private final Long2IntOpenHashMap dealt = new Long2IntOpenHashMap();

        public Mix() {
            voiceOf.defaultReturnValue(-1);
        }

        /** {@code speaker}'s voice for the battle: the next of its cast's round its squad, from a place set by the squad. */
        int voice(long speaker, long squad, Cast cast) {
            int v = voiceOf.get(speaker);
            if (v < 0) {
                v = (int) Math.floorMod(mix(squad) + dealt.addTo(squad, 1), (long) cast.voices.length);
                voiceOf.put(speaker, v);
            }
            return v;
        }

        /** What the people in the fight say this tick, once per tick (empty on a tick already heard). */
        public List<Play> hear(BattleSimulation sim) {
            List<Play> out = new ArrayList<>();
            int now = sim.getSimTickIndex();
            if (now == tick) return out;
            if (now < tick) clear();
            tick = now;
            Field field = Field.of(sim);
            List<Spot> fresh = new ArrayList<>();
            cues(sim, field, fresh);
            deaths(sim, field, fresh);
            marines(sim, field, fresh);
            shots(sim, field, fresh, now * BattleSimulation.TICK_DT);
            grenades(sim, field, fresh);
            armor(field, fresh);
            if (now % CONTACT_EVERY == 0) {
                contacts(field, field.ravs, contact, Bark.CONTACT, CONTACT_CELLS, CLEAR_CELLS, fresh);
                contacts(field, field.infantry, hostileContact, Bark.CONTACT_HOSTILE,
                        HOSTILE_CONTACT_CELLS, HOSTILE_CLEAR_CELLS, fresh);
                contacts(field, field.drones, droneContact, Bark.DRONES, DRONE_CONTACT_CELLS, DRONE_CLEAR_CELLS, fresh);
            }
            List<Offer> offers = new ArrayList<>();
            for (VoicePool.Held<Bark, Spot> h : voices.due(now)) offers.add(new Offer(h.payload(), h.heldFor()));
            for (Spot spot : fresh) offers.add(new Offer(spot, 0));
            offers.sort(Comparator.comparingInt(o -> o.spot().bark().priority));
            for (Offer o : offers) offer(sim, o.spot(), o.heldFor(), now, out);
            return out;
        }

        private void offer(BattleSimulation sim, Spot spot, int heldFor, int now, List<Play> out) {
            if (!sim.getFogOfWar().isCellRevealed((int) Math.floor(spot.x()), (int) Math.floor(spot.y()))) return;
            float seconds = now * BattleSimulation.TICK_DT;
            Bark bark = spot.bark();
            if (!spot.cast().barks.contains(bark)) return;
            if (!channels.admits(bark, spot.speaker(), spot.squad(), seconds)) return;
            if (voices.offer(bark, spot.speaker(), now, spot, heldFor) != VoicePool.Admit.START) return;
            channels.start(bark, spot.speaker(), spot.squad(), seconds);
            int voice = voice(spot.speaker(), spot.squad(), spot.cast());
            out.add(new Play(soundId(spot.cast(), bark, voice, turn(bark)), spot.x(), spot.y(),
                    bark.voiced ? pitch(spot.speaker()) : 1f, voices.gain(bark)));
        }

        /** The next line of {@code bark}: lines take turns, so one is never heard twice running. */
        int turn(Bark bark) {
            int n = turns.merge(bark, 1, Integer::sum);
            return (n - 1) % LINES + 1;
        }

        private void cues(BattleSimulation sim, Field field, List<Spot> offers) {
            for (SwarmCues.Cue cue : sim.swarmCues().cuesThisFrame()) {
                switch (cue.kind()) {
                    case DRAG, IMPALE -> {
                        long victim = sim.swarmHarvest().loadOf(cue.body());
                        if (victim != 0L && field.speaks(sim, victim))
                            offers.add(field.spot(sim, Bark.GRABBED, victim));
                    }
                    case THROWN -> {
                        if (field.speaks(sim, cue.body())) offers.add(field.spot(sim, Bark.THROWN, cue.body()));
                    }
                    case CHARGE_TELL -> {
                        Bark warning = warning(cue.strain());
                        int i = warning == null ? -1 : field.nearest(cue.x(), cue.y(), WARN_CELLS, warning, null);
                        if (warning != null && i >= 0) offers.add(field.spot(warning, i));
                    }
                    default -> { }
                }
            }
        }

        private void deaths(BattleSimulation sim, Field field, List<Spot> offers) {
            LongList deaths = sim.getDeathsThisFrame();
            for (int i = 0; i < deaths.size(); i++) {
                long body = deaths.getLong(i);
                UnitType type = sim.identity().type(body);
                float x = sim.world().renderX(body), y = sim.world().renderY(body);
                if (type.isSwarm()) {
                    int by = field.nearest(x, y, NEAR_CELLS, kill(type), null);
                    if (by >= 0) offers.add(field.spot(kill(type), by));
                } else if (Field.hostile(sim.identity().faction(body)) && type.combatant) {
                    int by = field.nearest(x, y, NEAR_CELLS, Bark.KILL, Cast.MARINE);
                    if (by >= 0) offers.add(field.spot(Bark.KILL, by));
                } else if (Field.cast(sim, body) != null && field.nearestThreat(x, y) <= NEAR_CELLS) {
                    int mate = field.nearestSquadmate(sim, body, x, y, NEAR_CELLS);
                    if (mate >= 0) offers.add(field.spot(Bark.MAN_DOWN, mate));
                }
            }
        }

        /** Grunts off each speaker's health, reload calls off its magazine; readings of the dead are forgotten. */
        private void marines(BattleSimulation sim, Field field, List<Spot> offers) {
            Long2DoubleOpenHashMap seenHealth = new Long2DoubleOpenHashMap();
            Long2DoubleOpenHashMap seenReload = new Long2DoubleOpenHashMap();
            for (int i = 0; i < field.speakers.size(); i++) {
                long marine = field.speakers.getLong(i);
                float hp = sim.world().hp(marine);
                float prior = (float) health.getOrDefault(marine, hp);
                seenHealth.put(marine, hp);
                float timer = sim.combat().has(marine) ? sim.combat().reloadTimer(marine) : 0f;
                float priorTimer = (float) reload.getOrDefault(marine, timer);
                seenReload.put(marine, timer);
                // The enemy is looked for only round a marine something happened to.
                if (hp >= prior && timer <= priorTimer) continue;
                float rav = field.ravs.nearest(field.x[i], field.y[i]);
                float enemy = field.hostiles.nearest(field.x[i], field.y[i]);
                if (hurt(prior, hp, rav)) {
                    offers.add(field.spot(Bark.STRAIN, i));
                    offers.add(field.spot(Bark.SCUFFLE, i));
                } else if (hit(prior, hp, enemy)) {
                    offers.add(field.spot(Bark.HIT, i));
                }
                if (reloading(priorTimer, timer, Math.min(rav, enemy))) offers.add(field.spot(Bark.RELOAD, i));
            }
            health.clear();
            health.putAll(seenHealth);
            reload.clear();
            reload.putAll(seenReload);
        }

        /**
         * Each channel's contact edge against {@code foes}: the one of it nearest them calls {@code bark} as they close
         * inside {@code close}, and the channel is clear of them again past {@code clear}.
         */
        private void contacts(Field field, Points foes, Long2IntOpenHashMap state, Bark bark, float close, float clear,
                              List<Spot> offers) {
            Long2IntOpenHashMap nearestBySquad = new Long2IntOpenHashMap();
            nearestBySquad.defaultReturnValue(-1);
            float[] distance = new float[field.speakers.size()];
            for (int i = 0; i < field.speakers.size(); i++) {
                distance[i] = foes.nearest(field.x[i], field.y[i]);
                int best = nearestBySquad.get(field.squad[i]);
                if (best < 0 || distance[i] < distance[best]) nearestBySquad.put(field.squad[i], i);
            }
            for (Long2IntOpenHashMap.Entry e : nearestBySquad.long2IntEntrySet()) {
                long squad = e.getLongKey();
                int i = e.getIntValue();
                boolean was = state.containsKey(squad);
                boolean is = inContact(was, distance[i], close, clear);
                if (is && !was) offers.add(field.spot(bark, i));
                if (is) state.put(squad, 1);
                else state.remove(squad);
            }
        }

        /**
         * Enemy rounds landing on the line: the speaker nearest where one ended calls taking fire if its squad had a
         * quiet spell first, and a turret warning if an emplacement fired it.
         */
        private void shots(BattleSimulation sim, Field field, List<Spot> offers, float seconds) {
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                if (!Field.hostile(shot.shooterFaction)) continue;
                int i = field.nearest(shot.toX, shot.toY, FIRE_CELLS, null, null);
                if (i < 0) continue;
                if (shot.turretStructureDef != null) offers.add(field.spot(Bark.TURRET, i));
                long squad = field.squad[i];
                if (takingFire(firedOnAt.getOrDefault(squad, Double.NEGATIVE_INFINITY), seconds))
                    offers.add(field.spot(Bark.TAKING_FIRE, i));
                firedOnAt.put(squad, seconds);
            }
        }

        /** Grenades as they leave: frag out from the friendly thrower, a warning from the one nearest an enemy's landing. */
        private void grenades(BattleSimulation sim, Field field, List<Spot> offers) {
            Set<Projectile> now = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Projectile p : sim.snapshotActiveProjectiles()) {
                now.add(p);
                if (projectiles.contains(p) || !thrownGrenade(p.sourceWeaponId)) continue;
                if (Field.hostile(p.shooterFaction)) {
                    float reach = (p.onArrival != null ? p.onArrival.aoeRadius : 0f) + GRENADE_MARGIN_CELLS;
                    int i = field.nearest(p.toX, p.toY, reach, Bark.GRENADE, null);
                    if (i >= 0) offers.add(field.spot(Bark.GRENADE, i));
                } else {
                    int i = field.nearest(p.fromX, p.fromY, THROWER_CELLS, Bark.FRAG_OUT, null);
                    if (i >= 0) offers.add(field.spot(Bark.FRAG_OUT, i));
                }
            }
            projectiles = now;
        }

        /** Each enemy machine, once: the speaker nearest it the first time it is seen near the line. */
        private void armor(Field field, List<Spot> offers) {
            for (int m = 0; m < field.armorIds.size(); m++) {
                long machine = field.armorIds.getLong(m);
                if (announcedArmor.contains(machine)) continue;
                int i = field.nearest(field.armor.x[m], field.armor.y[m], ARMOR_CELLS, Bark.ARMOR, null);
                if (i < 0) continue;
                offers.add(field.spot(Bark.ARMOR, i));
                announcedArmor.add(machine);
            }
        }

        void clear() {
            channels.clear();
            voices.clear();
            health.clear();
            reload.clear();
            contact.clear();
            hostileContact.clear();
            droneContact.clear();
            firedOnAt.clear();
            announcedArmor.clear();
            projectiles.clear();
            voiceOf.clear();
            dealt.clear();
        }
    }

    /** Points on the ground, and the distance to the nearest of them. */
    static final class Points {
        float[] x = new float[16], y = new float[16];
        int size;

        void add(float px, float py) {
            if (size == x.length) {
                x = Arrays.copyOf(x, size * 2);
                y = Arrays.copyOf(y, size * 2);
            }
            x[size] = px;
            y[size++] = py;
        }

        /** Cells from {@code (px, py)} to the nearest point, or infinity. */
        float nearest(float px, float py) {
            float best = Float.POSITIVE_INFINITY;
            for (int i = 0; i < size; i++) {
                float dx = x[i] - px, dy = y[i] - py;
                best = Math.min(best, dx * dx + dy * dy);
            }
            return (float) Math.sqrt(best);
        }
    }

    /**
     * This tick's speakers and the enemy they face, gathered once: positions, casts and channels; the Rav; and the
     * enemy's people and machines the player can see, by the call each draws.
     */
    private static final class Field {
        final LongArrayList speakers = new LongArrayList();
        float[] x = new float[16], y = new float[16];
        long[] squad = new long[16];
        Cast[] cast = new Cast[16];
        final Points ravs = new Points();
        /** Every seen enemy fighter that is not a Rav, and its infantry, drones and machines apart. */
        final Points hostiles = new Points();
        final Points infantry = new Points();
        final Points drones = new Points();
        final Points armor = new Points();
        final LongArrayList armorIds = new LongArrayList();

        static Field of(BattleSimulation sim) {
            Field f = new Field();
            FogOfWarService fog = sim.getFogOfWar();
            for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
                long unit = sim.liveUnitAt(i);
                if (!sim.world().isAlive(unit)) continue;
                UnitType type = sim.identity().type(unit);
                float ux = sim.world().renderX(unit), uy = sim.world().renderY(unit);
                if (type.isSwarm()) {
                    f.ravs.add(ux, uy);
                    continue;
                }
                if (hostile(sim.identity().faction(unit))) {
                    if (type.combatant && fog.getUnitVisibility(i) == FogOfWarService.VIS_VISIBLE) f.hostile(unit, type, ux, uy);
                    continue;
                }
                Cast c = cast(sim, unit);
                if (c != null) f.speaker(unit, c, channel(sim, unit, c), ux, uy);
            }
            return f;
        }

        /** Whether {@code faction} is the enemy every speaker faces. */
        static boolean hostile(Faction faction) {
            return Faction.MARINE.hostileTo(faction);
        }

        private void hostile(long unit, UnitType type, float hx, float hy) {
            hostiles.add(hx, hy);
            switch (threat(type)) {
                case INFANTRY -> infantry.add(hx, hy);
                case DRONE -> drones.add(hx, hy);
                case ARMOR -> {
                    armor.add(hx, hy);
                    armorIds.add(unit);
                }
                case OTHER -> { }
            }
        }

        /** Who {@code unit} speaks as, or null for one with no voice. */
        static Cast cast(BattleSimulation sim, long unit) {
            return MarineBarks.cast(sim.identity().faction(unit), sim.identity().type(unit));
        }

        boolean speaks(BattleSimulation sim, long unit) {
            return sim.world().isAlive(unit) && cast(sim, unit) != null;
        }

        /** A squad's channel; people outside any squad share one with their cast on their patch of ground. */
        static long channel(BattleSimulation sim, long unit, Cast cast) {
            if (sim.squad().hasSquad(unit)) return sim.squad().squadId(unit);
            return patch(cast, sim.world().x(unit), sim.world().y(unit));
        }

        private void speaker(long unit, Cast c, long channel, float mx, float my) {
            int n = speakers.size();
            if (n == x.length) {
                x = Arrays.copyOf(x, n * 2);
                y = Arrays.copyOf(y, n * 2);
                squad = Arrays.copyOf(squad, n * 2);
                cast = Arrays.copyOf(cast, n * 2);
            }
            speakers.add(unit);
            x[n] = mx;
            y[n] = my;
            squad[n] = channel;
            cast[n] = c;
        }

        /** Cells from {@code (px, py)} to the nearest living Rav or seen enemy fighter, or infinity. */
        float nearestThreat(float px, float py) {
            return Math.min(ravs.nearest(px, py), hostiles.nearest(px, py));
        }

        /**
         * The speaker nearest {@code (px, py)} within {@code range} whose cast has {@code bark} (any speaker when that
         * is null), and is {@code only} when that is not null, as an index, or -1.
         */
        int nearest(float px, float py, float range, Bark bark, Cast only) {
            int best = -1;
            float bestD2 = range * range;
            for (int i = 0; i < speakers.size(); i++) {
                if (bark != null && !cast[i].barks.contains(bark) || only != null && cast[i] != only) continue;
                float dx = x[i] - px, dy = y[i] - py, d2 = dx * dx + dy * dy;
                if (d2 <= bestD2) {
                    bestD2 = d2;
                    best = i;
                }
            }
            return best;
        }

        /** The living squadmate of {@code dead} nearest it within {@code range}, else any of its cast that near, or -1. */
        int nearestSquadmate(BattleSimulation sim, long dead, float px, float py, float range) {
            Cast own = cast(sim, dead);
            long channel = channel(sim, dead, own);
            int best = -1;
            float bestD2 = range * range;
            for (int i = 0; i < speakers.size(); i++) {
                if (squad[i] != channel) continue;
                float dx = x[i] - px, dy = y[i] - py, d2 = dx * dx + dy * dy;
                if (d2 <= bestD2) {
                    bestD2 = d2;
                    best = i;
                }
            }
            return best >= 0 ? best : nearest(px, py, range, Bark.MAN_DOWN, own);
        }

        Spot spot(Bark bark, int i) {
            return new Spot(bark, speakers.getLong(i), cast[i], squad[i], x[i], y[i]);
        }

        Spot spot(BattleSimulation sim, Bark bark, long speaker) {
            Cast c = cast(sim, speaker);
            return new Spot(bark, speaker, c, channel(sim, speaker, c), sim.world().renderX(speaker),
                    sim.world().renderY(speaker));
        }
    }

    private MarineBarks() { }

    /** Called every rendered frame; plays each tick's barks once. */
    public static void play(BattleSimulation sim) {
        for (Play p : MIXES.computeIfAbsent(sim, ignored -> new Mix()).hear(sim)) {
            if (UNPLAYABLE.contains(p.soundId())) continue;
            try {
                Global.getSoundPlayer().playSound(p.soundId(), p.pitch(), p.volume(),
                        new Vector2f(p.x() * BattleShotAudio.WORLD_UNITS_PER_CELL, p.y() * BattleShotAudio.WORLD_UNITS_PER_CELL),
                        new Vector2f(0f, 0f));
            } catch (RuntimeException e) {
                // A take not yet recorded and registered: skip it for the session, and say so once.
                UNPLAYABLE.add(p.soundId());
                LOG.warn("Bark " + p.soundId() + " is not registered; skipped: " + e.getMessage());
            }
        }
    }

    private static final Logger LOG = Global.getLogger(MarineBarks.class);
    private static final Set<String> UNPLAYABLE = new HashSet<>();
    /** The weapon ids of thrown grenades, read from the installed equipment registry when it changes. */
    private static Set<String> grenadeWeapons = Set.of();
    private static SpecialEquipmentRegistry grenadeRegistry;

    /** Whether a round in flight was fired by a thrown grenade's weapon. */
    static boolean thrownGrenade(String weaponId) {
        SpecialEquipmentRegistry registry = SpecialEquipmentRegistry.installed();
        if (weaponId == null || registry == null) return false;
        if (registry != grenadeRegistry) {
            Set<String> ids = new HashSet<>();
            for (SpecialEquipmentDef def : registry.all()) {
                if (def.isThrownGrenade() && def.weaponId() != null) ids.add(def.weaponId());
            }
            grenadeWeapons = ids;
            grenadeRegistry = registry;
        }
        return grenadeWeapons.contains(weaponId);
    }

    /** The call a seen enemy fighter draws by its kind. */
    enum Threat { INFANTRY, DRONE, ARMOR, OTHER }

    static Threat threat(UnitType type) {
        if (type.isDrone()) return Threat.DRONE;
        if (type.isMech() || type.isMachineFrame() || type.isGroundVehicle()) return Threat.ARMOR;
        if (type.drawnAsLayers() && !type.isStatic() && type != UnitType.ALIEN) return Threat.INFANTRY;
        return Threat.OTHER;
    }

    /** A take's sound id: a voiced bark's line in one of its cast's voices, an effort sound's one pool for everyone. */
    static String soundId(Cast cast, Bark bark, int voice, int line) {
        if (!bark.voiced) return PREFIX + bark.clip;
        return PREFIX + cast.prefix + bark.clip + "_" + cast.voices[voice] + "_" + line;
    }

    /**
     * Who a unit of {@code faction} and {@code type} speaks as: marines and allied company infantry as marines, the
     * colony militia as militia, colonists as colonists; null for the enemy, the swarm, machines and the alien.
     */
    static Cast cast(Faction faction, UnitType type) {
        if (type.isSwarm() || type == UnitType.ALIEN || !type.drawnAsLayers()) return null;
        return switch (faction) {
            case MARINE -> type.drawsLayeredWeapon() ? Cast.MARINE : null;
            case ALLY -> type == UnitType.MILITIA ? Cast.MILITIA : type.drawsLayeredWeapon() ? Cast.MARINE : null;
            case CIVILIAN -> Cast.CIVILIAN;
            default -> null;
        };
    }

    /** The channel squadless people of {@code cast} standing at {@code (x, y)} share: their patch of ground's. */
    static long patch(Cast cast, float x, float y) {
        long px = Math.floorDiv((int) Math.floor(x), PATCH_CELLS) & 0xFFFFL;
        long py = Math.floorDiv((int) Math.floor(y), PATCH_CELLS) & 0xFFFFL;
        return 1L << 40 | (long) cast.ordinal() << 32 | px << 16 | py;
    }

    /** A marine's own pitch, within four percent of its voice's, so two marines sharing a voice are two people. */
    static float pitch(long marine) {
        return 0.96f + (Math.floorMod(mix(marine) >>> 8, 81L)) / 1000f;
    }

    private static long mix(long v) {
        v = (v ^ (v >>> 33)) * 0xff51afd7ed558ccdL;
        v = (v ^ (v >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return v ^ (v >>> 33);
    }

    /** A squad's contact state from the Rav nearest it: it closes inside {@link #CONTACT_CELLS} and clears past {@link #CLEAR_CELLS}. */
    static boolean inContact(boolean was, float nearestRav) {
        return inContact(was, nearestRav, CONTACT_CELLS, CLEAR_CELLS);
    }

    /** A squad's contact state from the foe nearest it: it closes inside {@code close} and clears past {@code clear}. */
    static boolean inContact(boolean was, float nearest, float close, float clear) {
        return was ? nearest <= clear : nearest <= close;
    }

    /** Whether a round landing at {@code now} finds a squad last fired on at {@code lastFiredOn} after a quiet spell. */
    static boolean takingFire(double lastFiredOn, float now) {
        return now - lastFiredOn >= QUIET_SECONDS;
    }

    /** Whether one with no Rav at hand was hurt by fire: health lost past a graze, alive, an enemy in range. */
    static boolean hit(float priorHp, float hp, float nearestEnemy) {
        return hp > 0f && priorHp - hp >= HURT_THRESHOLD && nearestEnemy <= HIT_RANGE_CELLS;
    }

    /** Whether a marine was hurt hand to hand: health lost past a graze, alive, a Rav at arm's length. */
    static boolean hurt(float priorHp, float hp, float nearestRav) {
        return hp > 0f && priorHp - hp >= HURT_THRESHOLD && nearestRav <= MELEE_CELLS;
    }

    /** Whether a marine just began a reload with the enemy near enough to call it. */
    static boolean reloading(float priorTimer, float timer, float nearestRav) {
        return priorTimer <= 0f && timer > 0f && nearestRav <= NEAR_CELLS;
    }

    /** Whether {@code bark} is a cry torn out of a body seized or thrown, which waits on no one. */
    static boolean cries(Bark bark) {
        return bark == Bark.GRABBED || bark == Bark.THROWN;
    }

    /** The warning a charge tell draws: the Brute's charge, the Matriarch's run. */
    static Bark warning(UnitType strain) {
        if (strain == UnitType.SWARM_MATRIARCH) return Bark.MATRIARCH;
        if (strain == UnitType.SWARM_BRUTE) return Bark.BRUTE;
        return null;
    }

    /** The relief a Rav's death draws. */
    static Bark kill(UnitType strain) {
        return strain == UnitType.SWARM_BRUTE || strain == UnitType.SWARM_MATRIARCH ? Bark.BIG_KILL : Bark.KILL;
    }
}
