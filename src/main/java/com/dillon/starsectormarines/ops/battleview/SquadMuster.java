package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.infantry.SquadMedicine;
import com.dillon.starsectormarines.marine.FireTeamBillet;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.IntegralSystemEffect;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.OfficerSpecialty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Current twelve-billet formation, detached from the campaign roster. A marine lost
 * to MIA or KIA leaves the seat empty where it stood until a replacement takes it.
 */
public record SquadMuster(String squadId, String squadName, boolean stationed,
                          List<Billet> billets, int readyCount,
                          int vacantCount, int missingCount, int killedCount) {

    public enum State { READY, VACANT }

    /**
     * One current place, with plain value labels for ordinary retained hover UI.
     * {@code medic} marks a billet that is a squad medic in battle: every
     * ready marine whose suit carries a field-aid kit, or, in a squad with
     * none, the one billet Field Medicine promotes. {@code kit} is what its marine
     * wears and carries, null for a vacant place or one standing in for no marine.
     */
    public record Billet(int index, String marineId, String name, String rankLabel,
                         State state, boolean leader, boolean medic,
                         FireTeamBillet kit) {
        public Billet {
            Objects.requireNonNull(state, "billet state");
        }

        public Billet(int index, String marineId, String name, String rankLabel,
                      State state, boolean leader, boolean medic) {
            this(index, marineId, name, rankLabel, state, leader, medic, null);
        }

        public int teamIndex() { return index / MarineSquad.TEAM_SIZE; }

        public String hoverText() {
            if (state == State.VACANT) return Strings.get("battleMusterVacantBillet");
            String identity = Strings.format("battleMusterIdentity", rankLabel, name);
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
     */
    public static SquadMuster from(MarineRoster roster, String squadId) {
        Objects.requireNonNull(roster, "roster");
        MarineSquad squad = roster.squadById(squadId);
        if (squad == null || squad.reserve()) return null;

        List<String> members = roster.seatedMemberIds(squad);
        List<Billet> billets = new ArrayList<>(MarineSquad.CAPACITY);
        int ready = 0;
        for (int index = 0; index < MarineSquad.CAPACITY; index++) {
            MarineSoldier marine = index < members.size()
                    ? roster.soldierById(members.get(index)) : null;
            if (marine == null) {
                billets.add(new Billet(index, null, Strings.get("battleMusterVacant"), "", State.VACANT, false, false));
                continue;
            }
            ready++;
            billets.add(new Billet(index, marine.id(), marine.name(),
                    marine.enlistedRank().abbreviation(),
                    State.READY,
                    marine.id().equals(squad.leaderSoldierId()), false,
                    new FireTeamBillet(marine.name(), marine.primaryId(), marine.primaryGrade(),
                            marine.specialEquipmentId(), marine.armorDef().id())));
        }
        markMedic(roster, squad, billets);

        int missing = 0;
        int killed = 0;
        for (MarineSoldier marine : roster.squadMembers(squad)) {
            if (marine.status() == MarineSoldierStatus.MIA) missing++;
            else if (marine.status() == MarineSoldierStatus.KIA) killed++;
        }
        return new SquadMuster(squad.id(), squad.name(), squad.stationed(), billets,
                ready, MarineSquad.CAPACITY - ready, missing, killed);
    }

    /**
     * Marks the squad's medics: every ready marine whose suit carries a
     * field-aid kit, the NCO included. Only when the squad has none does the
     * home officer's Field Medicine promote a single billet, the one
     * {@link SquadMedicine#medicBillet} picks: ready marines other than the NCO,
     * the fire team derived from billet order. A vacant billet deploys nobody, so
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

    /** Marks every ready billet whose marine {@code carriesKit}; a vacant billet deploys nobody. */
    static void markKitMedics(List<Billet> billets, Predicate<Billet> carriesKit) {
        for (int i = 0; i < billets.size(); i++) {
            Billet billet = billets.get(i);
            if (billet.state() == State.READY && carriesKit.test(billet)) markAsMedic(billets, i);
        }
    }

    private static void markAsMedic(List<Billet> billets, int index) {
        Billet b = billets.get(index);
        billets.set(index, new Billet(b.index(), b.marineId(), b.name(), b.rankLabel(),
                b.state(), b.leader(), true, b.kit()));
    }
}
