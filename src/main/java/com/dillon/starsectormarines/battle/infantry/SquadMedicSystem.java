package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.IntegralSystemEffect;
import com.dillon.starsectormarines.marine.OfficerCommandProfile;

import java.util.Arrays;

/**
 * A squad's medics ({@code squad-medic.md}). Every marine whose suit carries a
 * field-aid kit is a medic of their own, with the kit's medicine, so a loadout
 * with two kits has two medics. Only a squad deploying with no kit at all is
 * medicated by its officer: one that trains Field Medicine promotes a single
 * marine, designated by billet, to medic.
 *
 * <p>Two halves, split by who may write what. {@link #tick} is serial and runs
 * ahead of the unit dispatch: it designates the medic, regenerates their energy,
 * chooses a patient, and lands the treatment on the patient's health and armor.
 * {@link #serve} is the per-marine reflex that runs inside the dispatch and
 * moves or silences only the marine it is updating. No worker ever writes
 * another marine's health.
 *
 * <p>The medic holds fire while treating and walks with the squad: the patient
 * carries on with whatever the squad is doing, and the medic keeps at their
 * side. A medic who is their own worst-hurt patient stops where they stand and
 * dresses their own wounds. Treatment is paid for out of an energy pool
 * ({@link MedicEnergy}).
 */
public final class SquadMedicSystem {

    /** Medic-to-patient pairs treating this tick, for presentation to read by unit. */
    private long[] treatingMedics = new long[8];
    private long[] treatingPatients = new long[8];
    private int treatingCount;

    /**
     * The patient {@code unit} is treating as of the last serial pass, or zero.
     * For presentation, which runs after the dispatch and reads by unit.
     */
    public long treatingPatientOf(long unit) {
        for (int i = 0; i < treatingCount; i++) {
            if (treatingMedics[i] == unit) return treatingPatients[i];
        }
        return 0L;
    }

    /** Serial pass: one step of every medic in every squad. */
    public void tick(float dt, BattleSimulation sim) {
        long controlled = sim.directControl().activeUnitId();
        treatingCount = 0;
        for (Squad squad : sim.getSquads()) {
            if (squad.aliveMembers <= 0) continue;
            if (!squad.kitMedicsIssued) {
                issueKitMedics(squad, sim);
                // A kit anywhere in the squad leaves the medicine to the kits.
                int rank = rankOf(squad);
                if (squad.kitMedics.isEmpty() && rank > 0) squad.medic = new SquadMedic(SquadMedicine.forRank(rank));
            }
            if (squad.medic == null && squad.kitMedics.isEmpty()) continue;
            SimWard ward = new SimWard(squad, sim, controlled);
            if (squad.medic != null) {
                step(squad.medic, ward, dt);
                publishIfTreating(squad.medic);
            }
            for (int i = 0, n = squad.kitMedics.size(); i < n; i++) {
                SquadMedic kit = squad.kitMedics.get(i);
                step(kit, ward, dt);
                publishIfTreating(kit);
            }
        }
    }

    /** One medic of the kit's own medicine for every living kit wearer. */
    private static void issueKitMedics(Squad squad, BattleSimulation sim) {
        squad.kitMedicsIssued = true;
        for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
            long member = sim.squadMemberAt(squad.id, i);
            if (!sim.world().isAlive(member)) continue;
            String kit = kitOf(sim, member);
            if (kit != null) squad.kitMedics.add(new SquadMedic(SquadMedicine.forKit(kit), member));
        }
    }

    /** The field-aid kit {@code member}'s suit carries, or null. */
    private static String kitOf(BattleSimulation sim, long member) {
        IntegralSystemDef system = sim.integralSystems().spec(member);
        return system != null && system.effect() == IntegralSystemEffect.FIELD_AID ? system.id() : null;
    }

    private void publishIfTreating(SquadMedic medic) {
        if (medic.phase == SquadMedic.Phase.TREATING) publishTreating(medic.medicId, medic.patientId);
    }

    private void publishTreating(long medic, long patient) {
        if (treatingCount == treatingMedics.length) {
            treatingMedics = Arrays.copyOf(treatingMedics, treatingCount * 2);
            treatingPatients = Arrays.copyOf(treatingPatients, treatingCount * 2);
        }
        treatingMedics[treatingCount] = medic;
        treatingPatients[treatingCount] = patient;
        treatingCount++;
    }

    /** The Field Medicine rank of the officer commanding {@code squad}, or zero. */
    static int rankOf(Squad squad) {
        if (squad.commandGroupIdentity == null) return 0;
        OfficerCommandProfile profile = squad.commandGroupIdentity.profile();
        return profile == null ? 0 : profile.medicine();
    }

    /**
     * One tick of one squad's medic against the squad as {@code ward} presents
     * it. Package-visible so the treatment rules can be tested on a ward of
     * plain numbers.
     */
    static void step(SquadMedic medic, Ward ward, float dt) {
        SquadMedicine m = medic.medicine;
        medic.energy = MedicEnergy.regenerate(medic.energy, m.energyMax(), m.energyRegenPerSecond(), dt);
        if (medic.retryTimer > 0f) medic.retryTimer = Math.max(0f, medic.retryTimer - dt);

        if (medic.medicId == 0L || !ward.alive(medic.medicId)) {
            if (medic.phase == SquadMedic.Phase.TREATING) interrupt(medic);
            else drop(medic);
            // A kit is its wearer's; only the officer's billet passes to the next marine.
            if (medic.kit) return;
            medic.medicId = designate(ward);
            if (medic.medicId == 0L) return;
        }

        switch (medic.phase) {
            case TREATING -> treat(medic, ward, dt);
            case APPROACHING -> approach(medic, ward, dt);
            case IDLE -> choose(medic, ward);
        }
    }

    /**
     * The squad's medic billet: a living squadmate other than the leader,
     * preferring the middle fire team so the medic stands near everybody, then
     * seat order. Fixed until that marine dies.
     */
    static long designate(Ward ward) {
        int index = SquadMedicine.medicBillet(ward.memberCount(),
                i -> ward.alive(ward.memberAt(i)) && ward.memberAt(i) != ward.leader(),
                i -> ward.fireTeamIndex(ward.memberAt(i)));
        return index < 0 ? 0L : ward.memberAt(index);
    }

    private static void choose(SquadMedic medic, Ward ward) {
        SquadMedicine m = medic.medicine;
        long carrier = medic.medicId;
        if (medic.retryTimer > 0f || MedicEnergy.empty(medic.energy)) return;
        if (!ward.mayAct(carrier)) return;
        if (ward.incomingPressure(carrier) >= m.heavyFirePressure()) return;
        long patient = worstHurt(medic, ward);
        if (patient == 0L || !mayStart(medic, ward, patient)) return;
        medic.patientId = patient;
        medic.phase = SquadMedic.Phase.APPROACHING;
        medic.approachTimer = 0f;
        if (ward.distance(carrier, patient) <= m.reachCells()) begin(medic);
    }

    /** The start gate and wound floor of {@link MedicEnergy#mayStart}, against one patient. */
    static boolean mayStart(SquadMedic medic, Ward ward, long patient) {
        SquadMedicine m = medic.medicine;
        float maxHp = ward.maxHp(patient);
        float maxArmor = ward.maxArmor(patient);
        float missing = Math.max(0f, maxHp - ward.hp(patient))
                + (maxArmor > 0f ? Math.max(0f, maxArmor - ward.armor(patient)) : 0f);
        return MedicEnergy.mayStart(medic.energy, missing, maxHp + Math.max(0f, maxArmor),
                m.energyPerPoint(), m.minStartEnergy(), m.woundFloorFraction());
    }

    /**
     * The squadmate most in need within the leash: lowest health fraction
     * among those below the health threshold, and only when nobody is, the
     * lowest armor fraction among those below the armor threshold. The medic
     * counts as a squadmate: they are already in reach of themselves, so they
     * treat themselves without an approach. Never a squadmate another medic has
     * already set out for, and never one a nearer medic is free to take
     * ({@link #nearerFreeMedic}).
     */
    static long worstHurt(SquadMedic medic, Ward ward) {
        SquadMedicine m = medic.medicine;
        long carrier = medic.medicId;
        long byHealth = 0L;
        float lowestHealth = m.treatBelowHealth();
        long byArmor = 0L;
        float lowestArmor = m.treatBelowArmor();
        for (int i = 0, n = ward.memberCount(); i < n; i++) {
            long member = ward.memberAt(i);
            if (!ward.alive(member) || ward.maxHp(member) <= 0f) continue;
            if (inOtherHands(medic, ward, member)) continue;
            if (ward.distance(carrier, member) > m.leashCells()) continue;
            float health = ward.hp(member) / ward.maxHp(member);
            float maxArmor = ward.maxArmor(member);
            float armor = maxArmor > 0f ? ward.armor(member) / maxArmor : 1f;
            if (health >= lowestHealth && armor >= lowestArmor) continue;
            if (nearerFreeMedic(medic, ward, member)) continue;
            if (health < lowestHealth) {
                lowestHealth = health;
                byHealth = member;
            }
            if (armor < lowestArmor) {
                lowestArmor = armor;
                byArmor = member;
            }
        }
        return byHealth != 0L ? byHealth : byArmor;
    }

    /** Whether a medic other than {@code self} has {@code member} as patient. */
    static boolean inOtherHands(SquadMedic self, Ward ward, long member) {
        for (int i = 0, n = ward.medicCount(); i < n; i++) {
            SquadMedic other = ward.medicAt(i);
            if (other != self && other.patientId == member) return true;
        }
        return false;
    }

    /**
     * Whether another medic, free to set out for {@code patient} this tick, is
     * nearer to them than {@code medic}: the nearest takes the patient, so a
     * medic beside a wounded squadmate is not passed over for one across the
     * squad because of seat order. Equidistant medics fall back to seat order.
     * A medic busy with a patient, with an empty pool, under heavy fire, or to
     * whom the patient does not qualify is not free and does not hold anyone back.
     */
    static boolean nearerFreeMedic(SquadMedic medic, Ward ward, long patient) {
        float mine = ward.distance(medic.medicId, patient);
        boolean reachedSelf = false;
        for (int i = 0, n = ward.medicCount(); i < n; i++) {
            SquadMedic other = ward.medicAt(i);
            if (other == medic) {
                reachedSelf = true;
                continue;
            }
            if (!freeToTreat(other, ward, patient)) continue;
            float theirs = ward.distance(other.medicId, patient);
            if (theirs < mine || (theirs == mine && !reachedSelf)) return true;
        }
        return false;
    }

    /** Whether {@code medic} would set out for {@code patient} if they chose now. */
    private static boolean freeToTreat(SquadMedic medic, Ward ward, long patient) {
        long carrier = medic.medicId;
        if (carrier == 0L || medic.phase != SquadMedic.Phase.IDLE || medic.retryTimer > 0f
                || MedicEnergy.empty(medic.energy) || !ward.alive(carrier) || !ward.mayAct(carrier)) {
            return false;
        }
        SquadMedicine m = medic.medicine;
        return ward.incomingPressure(carrier) < m.heavyFirePressure()
                && ward.distance(carrier, patient) <= m.leashCells()
                && needsTreatment(medic, ward, patient)
                && mayStart(medic, ward, patient);
    }

    private static void approach(SquadMedic medic, Ward ward, float dt) {
        SquadMedicine m = medic.medicine;
        long patient = medic.patientId;
        medic.approachTimer += dt;
        if (!ward.alive(patient) || !ward.mayAct(medic.medicId)
                || medic.approachTimer > m.approachSeconds()
                || ward.distance(medic.medicId, patient) > m.leashCells()
                || !needsTreatment(medic, ward, patient)) {
            // Nothing spent, but the medic waits a moment before setting out
            // again, so a patient they cannot reach is not chased afresh on the
            // very next tick.
            medic.retryTimer = m.retrySeconds();
            drop(medic);
            return;
        }
        if (ward.distance(medic.medicId, patient) <= m.reachCells()) begin(medic);
    }

    private static void begin(SquadMedic medic) {
        medic.started++;
        medic.phase = SquadMedic.Phase.TREATING;
        medic.treatingSeconds = 0f;
    }

    /**
     * One tick of treatment. Both restorations run at their own rates, clamped
     * to the patient's maxima, and both are paid out of the pool at the same
     * price per point; when the pool cannot pay for the whole tick, both are
     * scaled down together. Ends when the patient is whole, the pool is empty,
     * or reach is broken; the start gate is not checked again.
     */
    static void treat(SquadMedic medic, Ward ward, float dt) {
        SquadMedicine m = medic.medicine;
        long patient = medic.patientId;
        if (!ward.alive(patient) || !ward.mayAct(medic.medicId)
                || ward.distance(medic.medicId, patient) > m.reachCells()) {
            interrupt(medic);
            return;
        }
        float maxHp = ward.maxHp(patient);
        float hp = ward.hp(patient);
        float maxArmor = ward.maxArmor(patient);
        float armor = ward.armor(patient);
        float wantHealth = hp < maxHp ? Math.min(maxHp - hp, m.healthPerSecond() * dt) : 0f;
        float wantArmor = maxArmor > 0f && armor < maxArmor
                ? Math.min(maxArmor - armor, m.armorPerSecond() * dt) : 0f;
        float wanted = wantHealth + wantArmor;
        float paid = MedicEnergy.affordable(medic.energy, wanted, m.energyPerPoint());
        float share = wanted > 0f ? paid / wanted : 0f;
        if (wantHealth > 0f) {
            float healed = Math.min(maxHp, hp + wantHealth * share);
            ward.setHp(patient, healed);
            medic.healthRestored += healed - hp;
        }
        if (wantArmor > 0f) {
            float plated = Math.min(maxArmor, armor + wantArmor * share);
            ward.setArmor(patient, plated);
            medic.armorRestored += plated - armor;
            // Re-plating is the one way armor rises in battle: a broken plate
            // made good again, and able to break a second time.
            if (armor <= 0f && plated > 0f) medic.armorReplated++;
        }
        medic.energy = MedicEnergy.spend(medic.energy, paid, m.energyPerPoint());
        medic.treatingSeconds += dt;
        boolean whole = ward.hp(patient) >= maxHp
                && (maxArmor <= 0f || ward.armor(patient) >= maxArmor);
        if (whole) {
            medic.completed++;
            // A finished treatment dresses the limb wounds too: the slowed walk and the
            // shaken aim end with it. An interrupted or exhausted one leaves them.
            ward.dressWounds(patient);
            drop(medic);
        } else if (MedicEnergy.empty(medic.energy)) {
            medic.exhausted++;
            drop(medic);
        }
    }

    private static boolean needsTreatment(SquadMedic medic, Ward ward, long patient) {
        SquadMedicine m = medic.medicine;
        if (ward.hp(patient) / ward.maxHp(patient) < m.treatBelowHealth()) return true;
        float maxArmor = ward.maxArmor(patient);
        return maxArmor > 0f && ward.armor(patient) / maxArmor < m.treatBelowArmor();
    }

    /** Ends a treatment early; what landed stays and nothing further is spent. */
    private static void interrupt(SquadMedic medic) {
        medic.interrupted++;
        drop(medic);
    }

    private static void drop(SquadMedic medic) {
        medic.patientId = 0L;
        medic.phase = SquadMedic.Phase.IDLE;
        medic.approachTimer = 0f;
        medic.treatingSeconds = 0f;
    }

    /**
     * The medic's part, run as a reflex inside the unit dispatch. Returns
     * whether the marine spent the tick.
     *
     * <ul>
     *   <li>The medic walking to a patient walks, and may answer what is
     *       already in range; it starts nothing.</li>
     *   <li>The medic treating holds fire and stays at the patient's side,
     *       following them when they move on.</li>
     *   <li>The patient is never held: they carry on with the squad.</li>
     * </ul>
     *
     * A medic whose fire team has broken is never held: the morale law comes
     * first, and the serial pass ends the treatment on the next tick.
     */
    public static boolean serve(long unit, Squad squad, BattleControl sim) {
        SquadMedic medic = squad == null ? null : squad.medicOf(unit);
        if (medic == null || medic.phase == SquadMedic.Phase.IDLE) return false;
        if (squad.fireTeamBroken(sim.squad().fireTeamIndex(unit))) return false;
        if (medic.phase == SquadMedic.Phase.APPROACHING) {
            walkTo(unit, medic.patientId, sim);
            InfantryUnitPrep.tryOpportunityPrimary(unit, sim);
            return true;
        }
        // Hands on the patient: a burst already in the air stops with them.
        World world = sim.world();
        if (world.burstRemaining(unit) > 0) {
            world.setBurstRemaining(unit, 0);
            world.setBurstTargetId(unit, 0L);
        }
        if (world.isAlive(medic.patientId)
                && distance(world, unit, medic.patientId) > medic.medicine.reachCells() * 0.5f) {
            walkTo(unit, medic.patientId, sim);
        } else if (!sim.movement().settled(unit)) {
            sim.clearPath(unit);
        }
        return true;
    }

    private static void walkTo(long unit, long target, BattleControl sim) {
        World world = sim.world();
        if (sim.movement().mayRepath(unit)) {
            sim.setPath(unit, GridPathfinder.findPath(sim.getGrid(), world.cellX(unit),
                    world.cellY(unit), world.cellX(target), world.cellY(target),
                    sim.getOccupancyMap()));
        }
        sim.advanceMovement(unit);
    }

    private static float distance(World world, long a, long b) {
        float dx = world.x(a) - world.x(b);
        float dy = world.y(a) - world.y(b);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** The squad as the treatment rules see it. */
    interface Ward {
        int memberCount();
        long memberAt(int index);
        long leader();
        int fireTeamIndex(long member);
        boolean alive(long member);
        /** Free to set out or keep treating: not broken, not rejoining, not under the player's hand. */
        boolean mayAct(long member);
        float incomingPressure(long member);
        float distance(long a, long b);
        float hp(long member);
        float maxHp(long member);
        void setHp(long member, float hp);
        /** Armor capacity remaining; zero for an armorless marine. */
        float armor(long member);
        /** Armor capacity when whole; zero for an armorless marine. */
        float maxArmor(long member);
        void setArmor(long member, float armor);
        /** Dresses the limb wounds {@code member} carries. */
        void dressWounds(long member);
        /** The squad's medics, the officer's promoted one first and then each kit's, in seat order. */
        int medicCount();
        SquadMedic medicAt(int index);
    }

    private record SimWard(Squad squad, BattleSimulation sim, long controlled) implements Ward {
        @Override public int memberCount() { return sim.squadMemberCount(squad.id); }
        @Override public long memberAt(int index) { return sim.squadMemberAt(squad.id, index); }
        @Override public long leader() { return squad.leaderId; }
        @Override public int fireTeamIndex(long member) { return sim.squad().fireTeamIndex(member); }
        /** A squadmate on a carrier's blades is out of reach: medics never chase a carrier. */
        @Override public boolean alive(long member) {
            return sim.world().isAlive(member) && !sim.swarmHarvest().isCarried(member) && !sim.isRiding(member);
        }
        @Override public boolean mayAct(long member) {
            return member != controlled && !sim.swarmHarvest().holdsStill(member) && !squad.isRejoining(member)
                    && !squad.fireTeamBroken(sim.squad().fireTeamIndex(member));
        }
        @Override public float incomingPressure(long member) {
            return sim.combat().incomingPressure(member, sim.getSimTickIndex());
        }
        @Override public float distance(long a, long b) {
            return SquadMedicSystem.distance(sim.world(), a, b);
        }
        @Override public float hp(long member) { return sim.world().hp(member); }
        @Override public float maxHp(long member) { return sim.world().maxHp(member); }
        @Override public void setHp(long member, float hp) { sim.world().setHp(member, hp); }
        @Override public float armor(long member) {
            return sim.world().hasArmor(member) ? sim.world().armor(member) : 0f;
        }
        @Override public float maxArmor(long member) {
            return sim.world().hasArmor(member) ? sim.world().maxArmor(member) : 0f;
        }
        @Override public void setArmor(long member, float armor) { sim.world().setArmor(member, armor); }
        @Override public void dressWounds(long member) { sim.wounds().dress(member); }
        @Override public int medicCount() { return (squad.medic != null ? 1 : 0) + squad.kitMedics.size(); }
        @Override public SquadMedic medicAt(int index) {
            if (squad.medic == null) return squad.kitMedics.get(index);
            return index == 0 ? squad.medic : squad.kitMedics.get(index - 1);
        }
    }
}
