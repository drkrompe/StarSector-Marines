package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.infantry.SquadMedicine;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.IntegralSystemEffect;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.OfficerSpecialty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Current twelve-billet formation, detached from the campaign roster. WIA
 * personnel retain their places; the historical MIA/KIA roll is counted beside
 * the formation rather than occupying a current fire-team position.
 */
public record SquadMuster(String squadId, String squadName, boolean stationed,
                          List<Billet> billets, int readyCount, int woundedCount,
                          int vacantCount, int missingCount, int killedCount,
                          float earliestRecoveryDays) {

    public enum State { READY, WOUNDED, VACANT }

    /**
     * One current place, with plain value labels for ordinary retained hover UI.
     * {@code medic} marks a billet that is a squad medic in battle: every
     * ready marine whose suit carries a field-aid kit, or, in a squad with
     * none, the one billet Field Medicine promotes.
     */
    public record Billet(int index, String marineId, String name, String rankLabel,
                         State state, float recoveryDays, boolean leader, boolean medic) {
        public Billet {
            Objects.requireNonNull(state, "billet state");
        }

        public int teamIndex() { return index / MarineSquad.TEAM_SIZE; }

        public String hoverText() {
            if (state == State.VACANT) return Strings.get("battleMusterVacantBillet");
            String identity = Strings.format("battleMusterIdentity", rankLabel, name);
            if (state == State.WOUNDED) {
                return Strings.join("battleMusterSeparator", identity, Strings.get("battleMusterWounded"),
                        Strings.format("battleMusterReturnIn", recoveryDays));
            }
            return Strings.join("battleMusterSeparator", identity, Strings.get("battleMusterReady"),
                    leader ? Strings.get("battleMusterNco") : null,
                    medic ? Strings.get("battleMusterMedic") : null);
        }
    }

    public SquadMuster {
        Objects.requireNonNull(squadId, "squad identity");
        Objects.requireNonNull(squadName, "squad name");
        billets = List.copyOf(billets);
        if (billets.size() != MarineSquad.CAPACITY) {
            throw new IllegalArgumentException("a squad muster requires twelve billets");
        }
    }

    /**
     * Pure read projection using the roster's current billet order. A missing
     * identity or the reserve holding pool has no formation and returns null.
     * Recovery values are remaining campaign days; -1 means no recovery clock.
     */
    public static SquadMuster from(MarineRoster roster, String squadId, float currentDay) {
        Objects.requireNonNull(roster, "roster");
        if (!Float.isFinite(currentDay)) {
            throw new IllegalArgumentException("campaign day must be finite");
        }
        MarineSquad squad = roster.squadById(squadId);
        if (squad == null || squad.reserve()) return null;

        List<String> members = roster.manningMemberIds(squad);
        List<Billet> billets = new ArrayList<>(MarineSquad.CAPACITY);
        int ready = 0;
        int wounded = 0;
        float earliest = -1f;
        for (int index = 0; index < MarineSquad.CAPACITY; index++) {
            MarineSoldier marine = index < members.size()
                    ? roster.soldierById(members.get(index)) : null;
            if (marine == null) {
                billets.add(new Billet(index, null, Strings.get("battleMusterVacant"), "", State.VACANT, -1f, false, false));
                continue;
            }
            boolean recovering = marine.status() == MarineSoldierStatus.WIA;
            float days = recovering ? Math.max(0f, marine.unavailableUntilDay() - currentDay) : -1f;
            if (recovering) {
                wounded++;
                earliest = earliest < 0f ? days : Math.min(earliest, days);
            } else {
                ready++;
            }
            billets.add(new Billet(index, marine.id(), marine.name(),
                    marine.enlistedRank().abbreviation(),
                    recovering ? State.WOUNDED : State.READY, days,
                    marine.id().equals(squad.leaderSoldierId()), false));
        }
        markMedic(roster, squad, billets);

        int missing = 0;
        int killed = 0;
        for (MarineSoldier marine : roster.squadMembers(squad)) {
            if (marine.status() == MarineSoldierStatus.MIA) missing++;
            else if (marine.status() == MarineSoldierStatus.KIA) killed++;
        }
        return new SquadMuster(squad.id(), squad.name(), squad.stationed(), billets,
                ready, wounded, MarineSquad.CAPACITY - ready - wounded,
                missing, killed, earliest);
    }

    /**
     * Whole campaign hours until every wounded billet across {@code musters} is back
     * on duty, rounded up so a timer never reads zero while somebody is still WIA;
     * zero when nobody is wounded.
     */
    public static int hoursUntilFullHeal(Collection<SquadMuster> musters) {
        float latest = 0f;
        for (SquadMuster muster : musters) {
            if (muster == null) continue;
            for (Billet billet : muster.billets()) {
                if (billet.state() == State.WOUNDED) latest = Math.max(latest, billet.recoveryDays());
            }
        }
        return latest <= 0f ? 0 : Math.max(1, (int) Math.ceil(latest * 24f - 1e-3f));
    }

    /**
     * Marks the squad's medics: every ready marine whose suit carries a
     * field-aid kit, the NCO included. Only when the squad has none does the
     * home officer's Field Medicine promote a single billet, the one
     * {@link SquadMedicine#medicBillet} picks: ready marines other than the NCO,
     * the fire team derived from billet order. A WIA marine does not deploy, so
     * cannot carry either.
     */
    private static void markMedic(MarineRoster roster, MarineSquad squad, List<Billet> billets) {
        markKitMedics(billets, billet -> {
            MarineSoldier marine = roster.soldierById(billet.marineId());
            IntegralSystemDef system = marine == null ? null : marine.armorDef().integralSystem();
            return system != null && system.effect() == IntegralSystemEffect.FIELD_AID;
        });
        if (billets.stream().anyMatch(Billet::medic)) return;
        MarineCaptain officer = roster.captainForSquad(squad.id());
        if (officer == null || officer.career().level(OfficerSpecialty.FIELD_MEDICINE) <= 0) return;
        int index = SquadMedicine.medicBillet(billets.size(),
                i -> billets.get(i).state() == State.READY && !billets.get(i).leader(),
                i -> billets.get(i).teamIndex());
        if (index >= 0) markAsMedic(billets, index);
    }

    /** Marks every ready billet whose marine {@code carriesKit}; a wounded or vacant billet deploys nobody. */
    static void markKitMedics(List<Billet> billets, Predicate<Billet> carriesKit) {
        for (int i = 0; i < billets.size(); i++) {
            Billet billet = billets.get(i);
            if (billet.state() == State.READY && carriesKit.test(billet)) markAsMedic(billets, i);
        }
    }

    private static void markAsMedic(List<Billet> billets, int index) {
        Billet b = billets.get(index);
        billets.set(index, new Billet(b.index(), b.marineId(), b.name(), b.rankLabel(),
                b.state(), b.recoveryDays(), b.leader(), true));
    }
}
