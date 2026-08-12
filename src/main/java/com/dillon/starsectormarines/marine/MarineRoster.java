package com.dillon.starsectormarines.marine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.infantry.SoldierAptitude;

/**
 * Thin collection wrapper around the player's captains. Held by {@link MarineRosterScript}
 * which xstream persists with the campaign.
 *
 * <p>Also tracks {@link #completedStoryIds} — the set of one-shot story mission ids the
 * player has already cleared on this save. Lives here (rather than a new top-level
 * script) because xstream already walks the roster graph for the captain list; adding
 * one Set ride-shares for free.
 */
public class MarineRoster implements Serializable {

    /** Hardcoded cap for phase 2. Phase 2.5 will scale this with player level. */
    private static final int DEFAULT_CAPACITY = 10;

    private final List<MarineCaptain> captains = new ArrayList<>();
    // Non-final so xstream's readResolve can backfill on legacy saves that
    // predate this field (xstream bypasses the constructor on deserialization,
    // so the inline initializer doesn't run for old save streams).
    private Set<String> completedStoryIds = new HashSet<>();
    private List<MarineSoldier> soldiers = new ArrayList<>();
    private List<MarineSquad> squads = new ArrayList<>();
    private MarineArmory armory = new MarineArmory();
    private int nextSoldierNumber = 1;
    private int nextSquadNumber = 1;
    private String reserveSquadId;
    private boolean initialComplementIssued;
    private int capacity = DEFAULT_CAPACITY;

    public void add(MarineCaptain captain) {
        captains.add(captain);
    }

    public boolean removeById(String id) {
        return captains.removeIf(c -> c.id().equals(id));
    }

    public MarineCaptain byId(String id) {
        for (MarineCaptain c : captains) {
            if (c.id().equals(id)) return c;
        }
        return null;
    }

    public List<MarineCaptain> all() {
        return Collections.unmodifiableList(captains);
    }

    public List<MarineCaptain> active() {
        List<MarineCaptain> result = new ArrayList<>();
        for (MarineCaptain c : captains) {
            if (c.status() == Status.ACTIVE) result.add(c);
        }
        return result;
    }

    /**
     * Same predicate as {@link #active()} but counts in place without
     * allocating an intermediate list. Used by per-frame readers
     * (e.g. {@code OfficerMoodReader.currentMood}) where the list itself
     * isn't needed.
     */
    public int activeCount() {
        int n = 0;
        for (MarineCaptain c : captains) {
            if (c.status() == Status.ACTIVE) n++;
        }
        return n;
    }

    public int size() {
        return captains.size();
    }

    public int capacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public boolean hasRoom() {
        return captains.size() < capacity;
    }

    public boolean hasCompletedStory(String storyId) {
        return completedStoryIds.contains(storyId);
    }

    public void markStoryComplete(String storyId) {
        if (storyId != null) completedStoryIds.add(storyId);
    }

    public Set<String> completedStoryIds() {
        return Collections.unmodifiableSet(completedStoryIds);
    }

    public MarineArmory armory() { return armory; }

    public List<MarineSoldier> soldiers() {
        return Collections.unmodifiableList(soldiers);
    }

    public List<MarineSquad> squads() { return Collections.unmodifiableList(squads); }

    public MarineSquad squadById(String id) {
        if (id == null) return null;
        for (MarineSquad squad : squads) if (id.equals(squad.id())) return squad;
        return null;
    }

    public MarineSquad squadForSoldier(String soldierId) {
        if (soldierId == null) return null;
        for (MarineSquad squad : squads) {
            if (squad.memberIds().contains(soldierId)) return squad;
        }
        return null;
    }

    public List<MarineSoldier> squadMembers(MarineSquad squad) {
        if (squad == null) return Collections.emptyList();
        List<MarineSoldier> result = new ArrayList<>();
        for (String id : squad.memberIds()) {
            MarineSoldier soldier = soldierById(id);
            if (soldier != null) result.add(soldier);
        }
        return result;
    }

    public int readyCount(MarineSquad squad) {
        int count = 0;
        for (MarineSoldier soldier : squadMembers(squad)) {
            if (soldier.status() == MarineSoldierStatus.ACTIVE) count++;
        }
        return count;
    }

    /** Filled billets include deployable and temporarily wounded personnel. */
    public int manningCount(MarineSquad squad) {
        int count = 0;
        for (MarineSoldier soldier : squadMembers(squad)) {
            if (soldier.status() == MarineSoldierStatus.ACTIVE
                    || soldier.status() == MarineSoldierStatus.WIA) count++;
        }
        return count;
    }

    public int vacancies(MarineSquad squad) {
        if (squad == null || squad.reserve()) return 0;
        return Math.max(0, MarineSquad.CAPACITY - manningCount(squad));
    }

    public MarineSquad reserveSquad() {
        MarineSquad existing = squadById(reserveSquadId);
        if (existing != null) return existing;
        for (MarineSquad squad : squads) {
            if (squad.reserve()) {
                reserveSquadId = squad.id();
                return squad;
            }
        }
        MarineSquad reserve = new MarineSquad(
                java.util.UUID.randomUUID().toString(), "Reserve Pool", true);
        squads.add(reserve);
        reserveSquadId = reserve.id();
        return reserve;
    }

    public MarineSquad createFireteam() {
        MarineSquad squad = new MarineSquad(String.format("Fireteam %02d", nextSquadNumber++));
        int reserveIndex = squads.indexOf(squadById(reserveSquadId));
        if (reserveIndex >= 0) squads.add(reserveIndex, squad);
        else squads.add(squad);
        return squad;
    }

    public boolean renameSquad(String squadId, String name) {
        MarineSquad squad = squadById(squadId);
        if (squad == null || squad.reserve() || name == null || name.trim().isEmpty()) return false;
        squad.setName(name);
        return true;
    }

    /** Hires one replacement into an open line-fireteam billet. */
    public MarineSoldier recruitToSquad(String squadId) {
        MarineSquad squad = squadById(squadId);
        if (squad == null || (!squad.reserve() && vacancies(squad) <= 0)) return null;
        MarineSoldier recruit = createRecruit();
        squad.add(recruit.id());
        armory.ensureBasicIssue(activeSoldierCount());
        return recruit;
    }

    /** One-time free campaign starting complement; later enlistment uses cargo marines. */
    public void bootstrapInitialComplement(int count) {
        if (initialComplementIssued) return;
        initialComplementIssued = true;
        ensureActiveSoldiers(Math.max(0, count));
    }

    /** Demobilizes only a ready reserve marine; callers award the cargo commodity. */
    public boolean releaseReserveSoldier(String soldierId) {
        MarineSoldier soldier = soldierById(soldierId);
        MarineSquad squad = squadForSoldier(soldierId);
        if (soldier == null || soldier.status() != MarineSoldierStatus.ACTIVE
                || squad == null || !squad.reserve()) return false;
        if (!squad.remove(soldierId)) return false;
        if (soldiers.remove(soldier)) return true;
        squad.add(soldierId);
        return false;
    }

    /** Moves a ready marine; casualty history and WIA personnel cannot be reassigned. */
    public boolean transferSoldier(String soldierId, String targetSquadId) {
        MarineSoldier soldier = soldierById(soldierId);
        MarineSquad source = squadForSoldier(soldierId);
        MarineSquad target = squadById(targetSquadId);
        if (soldier == null || soldier.status() != MarineSoldierStatus.ACTIVE
                || source == null || target == null || source == target) return false;
        if (!target.reserve() && manningCount(target) >= MarineSquad.CAPACITY) return false;
        if (!source.remove(soldierId)) return false;
        if (target.add(soldierId)) return true;
        source.add(soldierId);
        return false;
    }

    /** UI helper: next eligible squad in roster order, with reserves as the final stop. */
    public MarineSquad nextTransferTarget(String soldierId) {
        MarineSquad source = squadForSoldier(soldierId);
        if (source == null || squads.size() < 2) return null;
        int start = squads.indexOf(source);
        for (int offset = 1; offset < squads.size(); offset++) {
            MarineSquad candidate = squads.get((start + offset) % squads.size());
            if (candidate.reserve() || manningCount(candidate) < MarineSquad.CAPACITY) {
                return candidate;
            }
        }
        return null;
    }

    public MarineSoldier firstReadyReserve() {
        for (MarineSoldier soldier : squadMembers(reserveSquad())) {
            if (soldier.status() == MarineSoldierStatus.ACTIVE) return soldier;
        }
        return null;
    }

    public boolean fillVacancyFromReserve(String targetSquadId) {
        MarineSoldier reserve = firstReadyReserve();
        return reserve != null && transferSoldier(reserve.id(), targetSquadId);
    }

    public List<MarineSoldier> activeSoldiers() {
        List<MarineSoldier> result = new ArrayList<>();
        for (MarineSoldier soldier : soldiers) {
            if (soldier.status() == MarineSoldierStatus.ACTIVE) result.add(soldier);
        }
        return result;
    }

    /** Ready personnel already assigned to line fireteams; reserves require transfer first. */
    public List<MarineSoldier> lineReadySoldiers() {
        List<MarineSoldier> result = new ArrayList<>();
        for (MarineSquad squad : squads) {
            if (squad.reserve()) continue;
            for (MarineSoldier soldier : squadMembers(squad)) {
                if (soldier.status() == MarineSoldierStatus.ACTIVE) result.add(soldier);
            }
        }
        return result;
    }

    public MarineSoldier soldierById(String id) {
        if (id == null) return null;
        for (MarineSoldier soldier : soldiers) if (id.equals(soldier.id())) return soldier;
        return null;
    }

    /** Recruit enough persistent soldiers to fill the next frozen deployment. */
    public void ensureActiveSoldiers(int count) {
        while (activeSoldierCount() < count) {
            MarineSoldier recruit = createRecruit();
            assignToFireteam(recruit);
        }
        armory.ensureBasicIssue(activeSoldierCount());
    }

    public boolean allocatePrimary(String soldierId, MarineWeapon weapon, EquipmentGrade grade) {
        MarineSoldier soldier = soldierById(soldierId);
        if (soldier == null || soldier.status() != MarineSoldierStatus.ACTIVE
                || !armory.isPrimaryUnlocked(weapon, grade)) return false;
        int allocated = 0;
        for (MarineSoldier other : soldiers) {
            if (other != soldier && holdsAllocatedGear(other)
                    && other.primary() == weapon && other.primaryGrade() == grade) allocated++;
        }
        if (allocated >= armory.ownedPrimary(weapon, grade)) return false;
        soldier.setPrimary(weapon, grade);
        return true;
    }

    public boolean allocateSecondary(String soldierId, MarineSecondary secondary) {
        MarineSoldier soldier = soldierById(soldierId);
        if (soldier == null || soldier.status() != MarineSoldierStatus.ACTIVE) return false;
        if (secondary == null) {
            soldier.setSecondary(null);
            return true;
        }
        if (!armory.isSecondaryUnlocked(secondary)) return false;
        int allocated = 0;
        for (MarineSoldier other : soldiers) {
            if (other != soldier && holdsAllocatedGear(other)
                    && other.secondary() == secondary) allocated++;
        }
        if (allocated >= armory.ownedSecondary(secondary)) return false;
        soldier.setSecondary(secondary);
        return true;
    }

    public boolean allocateArmor(String soldierId, MarineArmorPattern armor) {
        MarineSoldier soldier = soldierById(soldierId);
        if (soldier == null || soldier.status() != MarineSoldierStatus.ACTIVE
                || !armory.isArmorUnlocked(armor)) return false;
        int allocated = 0;
        for (MarineSoldier other : soldiers) {
            if (other != soldier && holdsAllocatedGear(other)
                    && other.armor() == armor) allocated++;
        }
        if (allocated >= armory.ownedArmor(armor)) return false;
        soldier.setArmor(armor);
        return true;
    }

    /** UI helper: walk the owned/unlocked primary catalog, wrapping at the end. */
    public boolean cyclePrimary(String soldierId) {
        MarineSoldier soldier = soldierById(soldierId);
        if (soldier == null) return false;
        MarineWeapon[] weapons = {MarineWeapon.PULSE_RIFLE, MarineWeapon.SMG, MarineWeapon.DMR};
        EquipmentGrade[] grades = EquipmentGrade.values();
        int start = soldier.primary().ordinal() * grades.length + soldier.primaryGrade().ordinal();
        int total = weapons.length * grades.length;
        for (int offset = 1; offset <= total; offset++) {
            int index = (start + offset) % total;
            MarineWeapon weapon = weapons[index / grades.length];
            EquipmentGrade grade = grades[index % grades.length];
            if (allocatePrimary(soldierId, weapon, grade)) return true;
        }
        return false;
    }

    /** UI helper: walk owned/unlocked armor patterns, wrapping at the end. */
    public boolean cycleArmor(String soldierId) {
        MarineSoldier soldier = soldierById(soldierId);
        if (soldier == null) return false;
        MarineArmorPattern[] patterns = MarineArmorPattern.values();
        for (int offset = 1; offset <= patterns.length; offset++) {
            MarineArmorPattern next = patterns[(soldier.armor().ordinal() + offset) % patterns.length];
            if (allocateArmor(soldierId, next)) return true;
        }
        return false;
    }

    /** Applies a whole-fireteam issue pattern only when every ready member can receive it. */
    public SquadPresetResult applySquadPreset(String squadId, SquadEquipmentPreset preset) {
        MarineSquad squad = squadById(squadId);
        if (squad == null || squad.reserve() || preset == null) {
            return SquadPresetResult.NO_READY_PERSONNEL;
        }
        List<MarineSoldier> ready = new ArrayList<>();
        for (MarineSoldier soldier : squadMembers(squad)) {
            if (soldier.status() == MarineSoldierStatus.ACTIVE) ready.add(soldier);
        }
        if (ready.isEmpty()) return SquadPresetResult.NO_READY_PERSONNEL;
        if (!armory.isPrimaryUnlocked(preset.primary, preset.grade)
                || !armory.isArmorUnlocked(preset.armor)) {
            return SquadPresetResult.LOCKED_RECIPE;
        }

        Set<String> targetIds = new HashSet<>();
        for (MarineSoldier soldier : ready) targetIds.add(soldier.id());
        int weaponsUsedElsewhere = 0;
        int armorUsedElsewhere = 0;
        for (MarineSoldier soldier : soldiers) {
            if (targetIds.contains(soldier.id()) || !holdsAllocatedGear(soldier)) continue;
            if (soldier.primary() == preset.primary && soldier.primaryGrade() == preset.grade) {
                weaponsUsedElsewhere++;
            }
            if (soldier.armor() == preset.armor) armorUsedElsewhere++;
        }
        if (armory.ownedPrimary(preset.primary, preset.grade) - weaponsUsedElsewhere
                < ready.size()) return SquadPresetResult.INSUFFICIENT_WEAPONS;
        if (armory.ownedArmor(preset.armor) - armorUsedElsewhere
                < ready.size()) return SquadPresetResult.INSUFFICIENT_ARMOR;

        for (MarineSoldier soldier : ready) {
            soldier.setPrimary(preset.primary, preset.grade);
            soldier.setArmor(preset.armor);
        }
        return SquadPresetResult.APPLIED;
    }

    public void applySoldierOutcome(Set<String> survivors, Set<String> fallen,
                                    int survivorXp) {
        if (survivors != null) {
            for (String id : survivors) {
                MarineSoldier soldier = soldierById(id);
                if (soldier != null && soldier.status() == MarineSoldierStatus.ACTIVE) {
                    soldier.addExperience(survivorXp);
                }
            }
        }
        if (fallen != null) {
            for (String id : fallen) {
                MarineSoldier soldier = soldierById(id);
                if (soldier != null) soldier.setStatus(MarineSoldierStatus.KIA);
            }
        }
    }

    /** Applies the richer personnel report used by the squad debrief. */
    public void applySoldierOutcome(Map<String, MarineSoldierStatus> outcomes,
                                    int survivorXp, float currentDay, float wiaDays) {
        if (outcomes == null) return;
        for (Map.Entry<String, MarineSoldierStatus> entry : outcomes.entrySet()) {
            MarineSoldier soldier = soldierById(entry.getKey());
            if (soldier == null || entry.getValue() == null) continue;
            MarineSoldierStatus status = entry.getValue();
            if (status == MarineSoldierStatus.ACTIVE) {
                soldier.addExperience(survivorXp);
                soldier.setUnavailableUntilDay(0f);
            } else if (status == MarineSoldierStatus.WIA) {
                soldier.setUnavailableUntilDay(currentDay + Math.max(1f, wiaDays));
            } else {
                soldier.setUnavailableUntilDay(0f);
            }
            soldier.setStatus(status);
        }
    }

    /** Returns WIA personnel to duty once their campaign recovery timer expires. */
    public void recoverWounded(float currentDay) {
        for (MarineSoldier soldier : soldiers) {
            if (soldier.status() == MarineSoldierStatus.WIA
                    && currentDay >= soldier.unavailableUntilDay()) {
                soldier.setStatus(MarineSoldierStatus.ACTIVE);
                soldier.setUnavailableUntilDay(0f);
            }
        }
    }

    private int activeSoldierCount() {
        int count = 0;
        for (MarineSoldier soldier : soldiers) {
            if (soldier.status() == MarineSoldierStatus.ACTIVE) count++;
        }
        return count;
    }

    private static boolean holdsAllocatedGear(MarineSoldier soldier) {
        return soldier.status() == MarineSoldierStatus.ACTIVE
                || soldier.status() == MarineSoldierStatus.WIA;
    }

    private static SoldierAptitude aptitudeFor(int number) {
        int roll = Math.floorMod(number * 37, 100);
        if (roll < 5) return SoldierAptitude.EXCEPTIONAL;
        if (roll < 25) return SoldierAptitude.GIFTED;
        if (roll < 90) return SoldierAptitude.STEADY;
        return SoldierAptitude.LIMITED;
    }

    private void assignToFireteam(MarineSoldier recruit) {
        for (MarineSquad squad : squads) {
            if (!squad.reserve() && manningCount(squad) < MarineSquad.CAPACITY) {
                squad.add(recruit.id());
                return;
            }
        }
        MarineSquad reserve = squadById(reserveSquadId);
        if (reserve != null && reserve.reserve()) {
            reserve.add(recruit.id());
            return;
        }
        MarineSquad squad = createFireteam();
        squad.add(recruit.id());
    }

    private MarineSoldier createRecruit() {
        int number = nextSoldierNumber++;
        MarineSoldier recruit = new MarineSoldier(
                String.format("Marine %03d", number), aptitudeFor(number));
        soldiers.add(recruit);
        autoIssueRecruit(recruit, number);
        return recruit;
    }

    /** Gives a new campaign a readable mixed roster before the armory UI lands. */
    private void autoIssueRecruit(MarineSoldier recruit, int number) {
        if (number == 1) {
            allocatePrimary(recruit.id(), MarineWeapon.PULSE_RIFLE, EquipmentGrade.SURPLUS);
        } else if (number % 6 == 2) {
            allocatePrimary(recruit.id(), MarineWeapon.SMG, EquipmentGrade.SERVICE);
        } else if (number % 6 == 4) {
            allocatePrimary(recruit.id(), MarineWeapon.DMR, EquipmentGrade.SERVICE);
        }
        if (number <= 6) {
            allocateArmor(recruit.id(), MarineArmorPattern.CHARCOAL);
        } else if (number <= 10) {
            allocateArmor(recruit.id(), MarineArmorPattern.ARMY_GREEN);
        }
        if (number == 6 || number == 10) {
            allocateSecondary(recruit.id(), MarineSecondary.ROCKET_LAUNCHER);
        }
    }

    /**
     * Backfill for saves created before {@link #completedStoryIds} existed —
     * xstream calls readResolve after building the object graph; an unset
     * Set field arrives as null and would NPE on first use.
     */
    private Object readResolve() {
        if (completedStoryIds == null) completedStoryIds = new HashSet<>();
        if (soldiers == null) soldiers = new ArrayList<>();
        if (squads == null) squads = new ArrayList<>();
        if (armory == null) armory = new MarineArmory();
        if (nextSoldierNumber <= 0) nextSoldierNumber = soldiers.size() + 1;
        if (nextSquadNumber <= 0) nextSquadNumber = squads.size() + 1;
        if (!soldiers.isEmpty()) initialComplementIssued = true;
        for (MarineSoldier soldier : soldiers) {
            if (squadForSoldier(soldier.id()) == null) assignToFireteam(soldier);
        }
        return this;
    }
}
