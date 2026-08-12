package com.dillon.starsectormarines.marine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private MarineArmory armory = new MarineArmory();
    private int nextSoldierNumber = 1;
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

    public List<MarineSoldier> activeSoldiers() {
        List<MarineSoldier> result = new ArrayList<>();
        for (MarineSoldier soldier : soldiers) {
            if (soldier.status() == MarineSoldierStatus.ACTIVE) result.add(soldier);
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
            int number = nextSoldierNumber++;
            MarineSoldier recruit = new MarineSoldier(
                    String.format("Marine %03d", number), aptitudeFor(number));
            soldiers.add(recruit);
            autoIssueRecruit(recruit, number);
        }
        armory.ensureBasicIssue(activeSoldierCount());
    }

    public boolean allocatePrimary(String soldierId, MarineWeapon weapon, EquipmentGrade grade) {
        MarineSoldier soldier = soldierById(soldierId);
        if (soldier == null || soldier.status() != MarineSoldierStatus.ACTIVE
                || !armory.isPrimaryUnlocked(weapon, grade)) return false;
        int allocated = 0;
        for (MarineSoldier other : soldiers) {
            if (other != soldier && other.status() == MarineSoldierStatus.ACTIVE
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
            if (other != soldier && other.status() == MarineSoldierStatus.ACTIVE
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
            if (other != soldier && other.status() == MarineSoldierStatus.ACTIVE
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

    private int activeSoldierCount() {
        int count = 0;
        for (MarineSoldier soldier : soldiers) {
            if (soldier.status() == MarineSoldierStatus.ACTIVE) count++;
        }
        return count;
    }

    private static SoldierAptitude aptitudeFor(int number) {
        int roll = Math.floorMod(number * 37, 100);
        if (roll < 5) return SoldierAptitude.EXCEPTIONAL;
        if (roll < 25) return SoldierAptitude.GIFTED;
        if (roll < 90) return SoldierAptitude.STEADY;
        return SoldierAptitude.LIMITED;
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
        if (armory == null) armory = new MarineArmory();
        if (nextSoldierNumber <= 0) nextSoldierNumber = soldiers.size() + 1;
        return this;
    }
}
