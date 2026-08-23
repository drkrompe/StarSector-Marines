package com.dillon.starsectormarines.marine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.ExperienceTier;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.infantry.SoldierAptitude;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryRow;

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
    private List<CaptainCandidate> captainCandidates = new ArrayList<>();
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
        MarineCaptain captain = byId(id);
        if (captain == null || captain.status() == Status.GARRISONED) return false;
        for (MarineSquad squad : squads) {
            if (squad.stationed() && id.equals(squad.homeCaptainId())) return false;
        }
        for (MarineSquad squad : squads) {
            if (id.equals(squad.homeCaptainId())) squad.setHomeCaptainId(null);
        }
        return captains.remove(captain);
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

    /**
     * Records one immutable candidate for a stable campaign source. Replays return the
     * original offer without allowing later callers to rewrite its authored details.
     */
    public CaptainCandidate discoverCaptainCandidate(
            String sourceKey, String name, String portraitSprite,
            Rank startingRank, Trait startingTrait, float currentDay) {
        String normalizedSource = normalizeSourceKey(sourceKey);
        if (normalizedSource == null) return null;
        CaptainCandidate existing = captainCandidateBySource(normalizedSource);
        if (existing != null) return existing;

        CaptainCandidate candidate = new CaptainCandidate(
                normalizedSource, name, portraitSprite,
                startingRank, startingTrait, currentDay);
        if (!candidate.valid()) return null;
        captainCandidates.add(candidate);
        return candidate;
    }

    public CaptainCandidate captainCandidateBySource(String sourceKey) {
        String normalizedSource = normalizeSourceKey(sourceKey);
        if (normalizedSource == null) return null;
        for (CaptainCandidate candidate : captainCandidates) {
            if (normalizedSource.equals(candidate.sourceKey())) return candidate;
        }
        return null;
    }

    public List<CaptainCandidate> captainCandidates() {
        return Collections.unmodifiableList(captainCandidates);
    }

    public List<CaptainCandidate> availableCaptainCandidates() {
        List<CaptainCandidate> available = new ArrayList<>();
        for (CaptainCandidate candidate : captainCandidates) {
            if (candidate.state() == CaptainCandidateState.AVAILABLE) {
                available.add(candidate);
            }
        }
        return Collections.unmodifiableList(available);
    }

    /** Atomically admits an available candidate, or returns the prior admission on replay. */
    public MarineCaptain acceptCaptainCandidate(String sourceKey) {
        CaptainCandidate candidate = captainCandidateBySource(sourceKey);
        if (candidate == null || !candidate.valid()
                || candidate.state() == CaptainCandidateState.DECLINED) {
            return null;
        }

        MarineCaptain existing = byId(candidate.id());
        if (candidate.state() == CaptainCandidateState.ACCEPTED) return existing;
        if (existing != null) {
            candidate.markAccepted();
            return existing;
        }
        if (!hasRoom()) return null;

        MarineCaptain captain = candidate.createCaptain();
        captains.add(captain);
        candidate.markAccepted();
        return captain;
    }

    public boolean declineCaptainCandidate(String sourceKey) {
        CaptainCandidate candidate = captainCandidateBySource(sourceKey);
        if (candidate == null || candidate.state() != CaptainCandidateState.AVAILABLE) {
            return false;
        }
        candidate.markDeclined();
        return true;
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

    /** Whether any line or reserve squad still points at this reusable template. */
    public boolean isFireTeamTemplateAssigned(String cardId) {
        if (cardId == null) return false;
        for (MarineSquad squad : squads) {
            for (int team = 0; team < MarineSquad.TEAMS_PER_SQUAD; team++) {
                if (cardId.equals(squad.teamTemplateCardId(team))) return true;
            }
        }
        return false;
    }

    /** Whether a fielded team or saved squad arrangement still references this template. */
    public boolean isFireTeamTemplateReferenced(String templateId) {
        return isFireTeamTemplateAssigned(templateId)
                || armory.isTemplateReferencedByArrangement(templateId);
    }

    /** Custom templates may be deleted only after every live reference is removed. */
    public boolean deleteFireTeamTemplate(String cardId) {
        return !isFireTeamTemplateReferenced(cardId) && armory.deleteTemplateCard(cardId);
    }

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

    public MarineCaptain captainForSquad(String squadId) {
        MarineSquad squad = squadById(squadId);
        return squad != null ? byId(squad.homeCaptainId()) : null;
    }

    public List<MarineSquad> squadsCommandedBy(String captainId) {
        if (captainId == null) return Collections.emptyList();
        List<MarineSquad> result = new ArrayList<>();
        for (MarineSquad squad : squads) {
            if (!squad.reserve() && captainId.equals(squad.homeCaptainId())) {
                result.add(squad);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public boolean isSquadAvailable(String squadId) {
        MarineSquad squad = squadById(squadId);
        return squad != null && !squad.reserve() && !squad.stationed();
    }

    public List<MarineSquad> squadsStationedOn(long contractId) {
        if (contractId <= 0L) return Collections.emptyList();
        List<MarineSquad> result = new ArrayList<>();
        for (MarineSquad squad : squads) {
            if (!squad.reserve() && squad.stationingContractId() == contractId) {
                result.add(squad);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** Atomically binds a rank-bounded mission formation to one stationing contract. */
    public boolean bindStationing(long contractId, String captainId,
                                  Iterable<String> squadIds) {
        MarineCaptain captain = byId(captainId);
        if (contractId <= 0L || captain == null || captain.status() != Status.ACTIVE
                || squadIds == null || !squadsStationedOn(contractId).isEmpty()) {
            return false;
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String squadId : squadIds) {
            if (squadId == null || !unique.add(squadId)) return false;
        }
        if (unique.isEmpty() || unique.size() > captain.rank().squadCommandCap()) return false;
        boolean hasReadyMember = false;
        for (String squadId : unique) {
            MarineSquad squad = squadById(squadId);
            if (squad == null || squad.reserve() || squad.stationed()) return false;
            if (readyCount(squad) > 0) hasReadyMember = true;
        }
        if (!hasReadyMember) return false;
        for (String squadId : unique) {
            squadById(squadId).setStationingContractId(contractId);
        }
        return true;
    }

    /** Clears every binding owned by the contract. Replay-safe; returns teams released. */
    public int releaseStationing(long contractId) {
        if (contractId <= 0L) return 0;
        int released = 0;
        for (MarineSquad squad : squads) {
            if (squad.stationingContractId() != contractId) continue;
            squad.setStationingContractId(-1L);
            released++;
        }
        return released;
    }

    /** Marks every recoverable member MIA before releasing an overrun assignment. */
    public int failStationingExtraction(long contractId) {
        List<MarineSquad> stationed = squadsStationedOn(contractId);
        if (stationed.isEmpty()) return 0;
        for (MarineSquad squad : stationed) {
            for (MarineSoldier soldier : squadMembers(squad)) {
                if (soldier.status() == MarineSoldierStatus.ACTIVE
                        || soldier.status() == MarineSoldierStatus.WIA) {
                    soldier.setStatus(MarineSoldierStatus.MIA);
                    soldier.setUnavailableUntilDay(0f);
                }
            }
        }
        return releaseStationing(contractId);
    }

    /** Assigns or atomically reassigns one line squad to an active captain. */
    public boolean assignCaptainToSquad(String captainId, String squadId) {
        MarineCaptain captain = byId(captainId);
        MarineSquad squad = squadById(squadId);
        if (captain == null || captain.status() != Status.ACTIVE
                || squad == null || squad.reserve()) return false;
        if (captainId.equals(squad.homeCaptainId())) return true;
        if (squad.stationed()) return false;
        if (squadsCommandedBy(captainId).size() >= captain.rank().squadCommandCap()) {
            return false;
        }
        squad.setHomeCaptainId(captainId);
        return true;
    }

    public boolean clearSquadCaptain(String squadId) {
        MarineSquad squad = squadById(squadId);
        if (squad == null || squad.reserve() || squad.stationed()
                || squad.homeCaptainId() == null) return false;
        squad.setHomeCaptainId(null);
        return true;
    }

    /** Next roster-order captain who can receive this team; excludes its current captain. */
    public MarineCaptain nextAssignableCaptain(String squadId) {
        MarineSquad squad = squadById(squadId);
        if (squad == null || squad.reserve() || squad.stationed() || captains.isEmpty()) {
            return null;
        }
        String currentId = squad.homeCaptainId();
        int start = -1;
        for (int i = 0; i < captains.size(); i++) {
            if (captains.get(i).id().equals(currentId)) {
                start = i;
                break;
            }
        }
        for (int offset = 1; offset <= captains.size(); offset++) {
            MarineCaptain candidate = captains.get((start + offset) % captains.size());
            if (candidate.id().equals(currentId) || candidate.status() != Status.ACTIVE) continue;
            if (squadsCommandedBy(candidate.id()).size() < candidate.rank().squadCommandCap()) {
                return candidate;
            }
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

    /** ACTIVE and WIA personnel remain living strength for a named assignment. */
    public int livingCount(Iterable<String> squadIds) {
        if (squadIds == null) return 0;
        Set<String> unique = new HashSet<>();
        int count = 0;
        for (String squadId : squadIds) {
            if (squadId == null || !unique.add(squadId)) continue;
            for (MarineSoldier soldier : squadMembers(squadById(squadId))) {
                if (soldier.status() == MarineSoldierStatus.ACTIVE
                        || soldier.status() == MarineSoldierStatus.WIA) {
                    count++;
                }
            }
        }
        return count;
    }

    public int stationedLivingCount(long contractId) {
        List<String> squadIds = new ArrayList<>();
        for (MarineSquad squad : squadsStationedOn(contractId)) squadIds.add(squad.id());
        return livingCount(squadIds);
    }

    public int stationedActiveCount(long contractId) {
        int count = 0;
        for (MarineSquad squad : squadsStationedOn(contractId)) count += readyCount(squad);
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

    public MarineSquad createSquad() {
        MarineSquad squad = new MarineSquad(String.format("Squad %02d", nextSquadNumber++));
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

    /** Hires one replacement into an open line-squad billet. */
    public MarineSoldier recruitToSquad(String squadId) {
        MarineSquad squad = squadById(squadId);
        if (squad == null || squad.stationed()
                || (!squad.reserve() && vacancies(squad) <= 0)) return null;
        MarineSoldier recruit = createRecruit();
        squad.add(recruit.id());
        armory.ensureBasicIssue(activeSoldierCount());
        refreshLeadership();
        return recruit;
    }

    /** Enlists into the first vacant line billet, creating a squad when needed. */
    MarineSoldier enlistLineRecruit() {
        MarineSquad target = null;
        for (MarineSquad squad : squads) {
            if (!squad.reserve() && !squad.stationed() && vacancies(squad) > 0) {
                target = squad;
                break;
            }
        }
        if (target == null) target = createSquad();
        MarineSoldier recruit = createRecruit();
        target.add(recruit.id());
        armory.ensureBasicIssue(activeSoldierCount());
        refreshLeadership();
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
        if (soldiers.remove(soldier)) {
            refreshLeadership();
            return true;
        }
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
        if (source.stationed() || target.stationed()) return false;
        if (!target.reserve() && manningCount(target) >= MarineSquad.CAPACITY) return false;
        if (!source.remove(soldierId)) return false;
        if (target.add(soldierId)) {
            refreshLeadership();
            return true;
        }
        source.add(soldierId);
        return false;
    }

    /** UI helper: next eligible squad in roster order, with reserves as the final stop. */
    public MarineSquad nextTransferTarget(String soldierId) {
        MarineSquad source = squadForSoldier(soldierId);
        if (source == null || source.stationed() || squads.size() < 2) return null;
        int start = squads.indexOf(source);
        for (int offset = 1; offset < squads.size(); offset++) {
            MarineSquad candidate = squads.get((start + offset) % squads.size());
            if (candidate.stationed()) continue;
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

    /** Ready personnel already assigned to line squads; reserves require transfer first. */
    public List<MarineSoldier> lineReadySoldiers() {
        List<MarineSoldier> result = new ArrayList<>();
        for (MarineSquad squad : squads) {
            if (squad.reserve() || squad.stationed()) continue;
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
            assignToSquad(recruit);
        }
        armory.ensureBasicIssue(activeSoldierCount());
        refreshLeadership();
    }

    public boolean allocatePrimary(String soldierId, MarineWeapon weapon, EquipmentGrade grade) {
        MarineSoldier soldier = soldierById(soldierId);
        if (!canAllocatePrimary(soldierId, weapon, grade)) return false;
        soldier.setPrimary(weapon, grade);
        return true;
    }

    public boolean canAllocatePrimary(String soldierId, MarineWeapon weapon, EquipmentGrade grade) {
        MarineSoldier soldier = soldierById(soldierId);
        if (soldier == null || soldier.status() != MarineSoldierStatus.ACTIVE
                || isStationed(soldierId)
                || !armory.isPrimaryUnlocked(weapon, grade)) return false;
        if (weapon == MarineWeapon.FIELD_RIFLE && grade == EquipmentGrade.SERVICE) return true;
        int allocated = 0;
        for (MarineSoldier other : soldiers) {
            if (other != soldier && holdsAllocatedGear(other)
                    && other.primary() == weapon && other.primaryGrade() == grade) allocated++;
        }
        return allocated < armory.ownedPrimary(weapon, grade);
    }

    public boolean allocateSecondary(String soldierId, MarineSecondary secondary) {
        MarineSoldier soldier = soldierById(soldierId);
        if (!canAllocateSecondary(soldierId, secondary)) return false;
        if (secondary == null) {
            soldier.setSecondary(null);
            return true;
        }
        soldier.setSecondary(secondary);
        return true;
    }

    public boolean canAllocateSecondary(String soldierId, MarineSecondary secondary) {
        MarineSoldier soldier = soldierById(soldierId);
        if (soldier == null || soldier.status() != MarineSoldierStatus.ACTIVE
                || isStationed(soldierId)) return false;
        if (secondary == null) return true;
        if (!armory.isSecondaryUnlocked(secondary)) return false;
        int allocated = 0;
        for (MarineSoldier other : soldiers) {
            if (other != soldier && holdsAllocatedGear(other)
                    && other.secondary() == secondary) allocated++;
        }
        return allocated < armory.ownedSecondary(secondary);
    }

    public boolean allocateArmor(String soldierId, MarineArmorPattern armor) {
        MarineSoldier soldier = soldierById(soldierId);
        if (!canAllocateArmor(soldierId, armor)) return false;
        soldier.setArmor(armor);
        return true;
    }

    public boolean canAllocateArmor(String soldierId, MarineArmorPattern armor) {
        MarineSoldier soldier = soldierById(soldierId);
        if (soldier == null || soldier.status() != MarineSoldierStatus.ACTIVE
                || isStationed(soldierId)
                || !armory.isArmorUnlocked(armor)) return false;
        int allocated = 0;
        for (MarineSoldier other : soldiers) {
            if (other != soldier && holdsAllocatedGear(other)
                    && other.armor() == armor) allocated++;
        }
        return allocated < armory.ownedArmor(armor);
    }

    /** UI helper: walk the owned/unlocked primary catalog, wrapping at the end. */
    public boolean cyclePrimary(String soldierId) {
        MarineSoldier soldier = soldierById(soldierId);
        if (soldier == null) return false;
        MarineWeapon[] weapons = {MarineWeapon.PULSE_RIFLE, MarineWeapon.SMG, MarineWeapon.DMR};
        EquipmentGrade[] grades = EquipmentGrade.values();
        int familyIndex = java.util.Arrays.asList(weapons).indexOf(soldier.primary());
        int start = familyIndex < 0 ? 0
                : 1 + familyIndex * grades.length + soldier.primaryGrade().ordinal();
        int total = 1 + weapons.length * grades.length;
        for (int offset = 1; offset <= total; offset++) {
            int index = (start + offset) % total;
            MarineWeapon weapon = index == 0 ? MarineWeapon.FIELD_RIFLE
                    : weapons[(index - 1) / grades.length];
            EquipmentGrade grade = index == 0 ? EquipmentGrade.SERVICE
                    : grades[(index - 1) % grades.length];
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

    /** Company-wide use and free-stock capacity for one reusable design. */
    public FireTeamTemplateAvailability fireTeamTemplateAvailability(String cardId) {
        FireTeamTemplateCard card = armory.templateCardById(cardId);
        if (card == null) return new FireTeamTemplateAvailability(0, 0, false);

        int fielded = 0;
        for (MarineSquad squad : squads) {
            for (int team = 0; team < MarineSquad.TEAMS_PER_SQUAD; team++) {
                if (card.id().equals(squad.teamTemplateCardId(team))) fielded++;
            }
        }
        boolean unlocked = recipesUnlocked(card);
        if (!unlocked) return new FireTeamTemplateAvailability(fielded, 0, false);

        TemplateRequirements required = requirements(card);
        int ready = Integer.MAX_VALUE;
        for (Map.Entry<PrimaryIssue, Integer> entry : required.primaries.entrySet()) {
            int free = freePrimary(entry.getKey());
            if (free != FireTeamGearDelta.UNLIMITED) {
                ready = Math.min(ready, free / entry.getValue());
            }
        }
        for (Map.Entry<MarineArmorPattern, Integer> entry : required.armor.entrySet()) {
            ready = Math.min(ready, freeArmor(entry.getKey()) / entry.getValue());
        }
        for (Map.Entry<MarineSecondary, Integer> entry : required.secondaries.entrySet()) {
            ready = Math.min(ready, freeSecondary(entry.getKey()) / entry.getValue());
        }
        if (ready == Integer.MAX_VALUE) ready = 0;
        return new FireTeamTemplateAvailability(fielded, Math.max(0, ready), true);
    }

    /**
     * Previews the exact transaction used by {@link #applyFireTeamTemplate}.
     * Free stock is reported before the target team returns its current issue.
     */
    public FireTeamRefitPreview previewFireTeamTemplate(String squadId, int teamIndex,
                                                        String templateCardId) {
        return previewRefits(List.of(new RefitRequest(squadId, teamIndex, templateCardId)));
    }

    /**
     * Assigns one reusable template to one complete, ready fire team.
     * The preview and mutation share one inventory calculation, and mutation
     * begins only after that entire transaction succeeds.
     */
    public FireTeamTemplateResult applyFireTeamTemplate(String squadId, int teamIndex,
                                                        String templateCardId) {
        RefitRequest request = new RefitRequest(squadId, teamIndex, templateCardId);
        FireTeamRefitPreview preview = previewRefits(List.of(request));
        if (!preview.canApply()) return preview.result();
        materializeRefits(List.of(request));
        return FireTeamTemplateResult.APPLIED;
    }

    /** Evaluates two teams exchanging their assigned templates as one net transaction. */
    public FireTeamRefitPreview previewFireTeamTemplateSwap(
            String firstSquadId, int firstTeamIndex,
            String secondSquadId, int secondTeamIndex) {
        if (firstSquadId != null && firstSquadId.equals(secondSquadId)
                && firstTeamIndex == secondTeamIndex) {
            return new FireTeamRefitPreview(
                    FireTeamTemplateResult.INVALID_FIRE_TEAM, Collections.emptyList());
        }
        MarineSquad first = squadById(firstSquadId);
        MarineSquad second = squadById(secondSquadId);
        if (first == null || second == null) {
            return new FireTeamRefitPreview(
                    FireTeamTemplateResult.INVALID_FIRE_TEAM, Collections.emptyList());
        }
        String firstCardId = first.teamTemplateCardId(firstTeamIndex);
        String secondCardId = second.teamTemplateCardId(secondTeamIndex);
        if (firstCardId == null || secondCardId == null) {
            return new FireTeamRefitPreview(
                    FireTeamTemplateResult.UNKNOWN_TEMPLATE, Collections.emptyList());
        }
        return previewRefits(List.of(
                new RefitRequest(firstSquadId, firstTeamIndex, secondCardId),
                new RefitRequest(secondSquadId, secondTeamIndex, firstCardId)));
    }

    /** Atomically exchanges the assigned designs and materialized issue of two teams. */
    public FireTeamTemplateResult swapFireTeamTemplates(
            String firstSquadId, int firstTeamIndex,
            String secondSquadId, int secondTeamIndex) {
        MarineSquad first = squadById(firstSquadId);
        MarineSquad second = squadById(secondSquadId);
        if (first == null || second == null) return FireTeamTemplateResult.INVALID_FIRE_TEAM;
        String firstCardId = first.teamTemplateCardId(firstTeamIndex);
        String secondCardId = second.teamTemplateCardId(secondTeamIndex);
        List<RefitRequest> requests = List.of(
                new RefitRequest(firstSquadId, firstTeamIndex, secondCardId),
                new RefitRequest(secondSquadId, secondTeamIndex, firstCardId));
        FireTeamRefitPreview preview = previewFireTeamTemplateSwap(
                firstSquadId, firstTeamIndex, secondSquadId, secondTeamIndex);
        if (!preview.canApply()) return preview.result();
        materializeRefits(requests);
        return FireTeamTemplateResult.APPLIED;
    }

    /** Number of squads whose current Alpha/Bravo/Charlie assignments match this plan. */
    public int squadArrangementFieldedCount(String arrangementId) {
        SquadArrangement arrangement = armory.squadArrangementById(arrangementId);
        if (arrangement == null) return 0;
        int fielded = 0;
        for (MarineSquad squad : squads) {
            boolean matches = true;
            for (int team = 0; team < MarineSquad.TEAMS_PER_SQUAD; team++) {
                if (!arrangement.templateId(team).equals(squad.teamTemplateCardId(team))) {
                    matches = false;
                    break;
                }
            }
            if (matches) fielded++;
        }
        return fielded;
    }

    /** Previews all three template assignments as one squad-wide inventory transaction. */
    public SquadArrangementPreview previewSquadArrangement(String squadId,
                                                            String arrangementId) {
        SquadArrangement arrangement = armory.squadArrangementById(arrangementId);
        if (arrangement == null) {
            return new SquadArrangementPreview(
                    FireTeamTemplateResult.UNKNOWN_ARRANGEMENT, Collections.emptyList());
        }
        FireTeamRefitPreview preview = previewRefits(arrangementRequests(squadId, arrangement));
        return new SquadArrangementPreview(preview.result(), preview.gear());
    }

    /** Applies Alpha, Bravo and Charlie together, or leaves the whole squad untouched. */
    public FireTeamTemplateResult applySquadArrangement(String squadId,
                                                        String arrangementId) {
        SquadArrangement arrangement = armory.squadArrangementById(arrangementId);
        if (arrangement == null) return FireTeamTemplateResult.UNKNOWN_ARRANGEMENT;
        List<RefitRequest> requests = arrangementRequests(squadId, arrangement);
        FireTeamRefitPreview preview = previewRefits(requests);
        if (!preview.canApply()) return preview.result();
        materializeRefits(requests);
        return FireTeamTemplateResult.APPLIED;
    }

    private List<RefitRequest> arrangementRequests(String squadId,
                                                   SquadArrangement arrangement) {
        List<RefitRequest> requests = new ArrayList<>();
        for (int team = 0; team < MarineSquad.TEAMS_PER_SQUAD; team++) {
            requests.add(new RefitRequest(squadId, team, arrangement.templateId(team)));
        }
        return requests;
    }

    private FireTeamRefitPreview previewRefits(List<RefitRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return new FireTeamRefitPreview(
                    FireTeamTemplateResult.INVALID_FIRE_TEAM, Collections.emptyList());
        }

        List<RefitPlan> plans = new ArrayList<>();
        Set<String> targetIds = new HashSet<>();
        for (RefitRequest request : requests) {
            MarineSquad squad = squadById(request.squadId);
            if (squad == null || squad.reserve() || request.teamIndex < 0
                    || request.teamIndex >= MarineSquad.TEAMS_PER_SQUAD) {
                return new FireTeamRefitPreview(
                        FireTeamTemplateResult.INVALID_FIRE_TEAM, Collections.emptyList());
            }
            if (squad.stationed()) {
                return new FireTeamRefitPreview(
                        FireTeamTemplateResult.STATIONED, Collections.emptyList());
            }
            FireTeamTemplateCard card = armory.templateCardById(request.cardId);
            if (card == null) {
                return new FireTeamRefitPreview(
                        FireTeamTemplateResult.UNKNOWN_TEMPLATE, Collections.emptyList());
            }
            List<String> memberIds = squad.teamMembers(request.teamIndex);
            if (memberIds.size() != MarineSquad.TEAM_SIZE) {
                return new FireTeamRefitPreview(
                        FireTeamTemplateResult.TEAM_NOT_READY, Collections.emptyList());
            }
            List<MarineSoldier> team = new ArrayList<>();
            for (String memberId : memberIds) {
                MarineSoldier soldier = soldierById(memberId);
                if (soldier == null || soldier.status() != MarineSoldierStatus.ACTIVE
                        || !targetIds.add(memberId)) {
                    return new FireTeamRefitPreview(
                            FireTeamTemplateResult.TEAM_NOT_READY, Collections.emptyList());
                }
                team.add(soldier);
            }
            plans.add(new RefitPlan(squad, request.teamIndex, team, card));
        }

        Map<PrimaryIssue, Integer> requiredPrimaries = new HashMap<>();
        Map<MarineArmorPattern, Integer> requiredArmor = new HashMap<>();
        Map<MarineSecondary, Integer> requiredSecondaries = new HashMap<>();
        Map<PrimaryIssue, Integer> returnedPrimaries = new HashMap<>();
        Map<MarineArmorPattern, Integer> returnedArmor = new HashMap<>();
        Map<MarineSecondary, Integer> returnedSecondaries = new HashMap<>();
        boolean unlocked = true;

        for (RefitPlan plan : plans) {
            TemplateRequirements required = requirements(plan.card);
            mergeCounts(requiredPrimaries, required.primaries);
            mergeCounts(requiredArmor, required.armor);
            mergeCounts(requiredSecondaries, required.secondaries);
            unlocked &= recipesUnlocked(plan.card);
            for (MarineSoldier soldier : plan.team) {
                returnedPrimaries.merge(new PrimaryIssue(
                        soldier.primary(), soldier.primaryGrade()), 1, Integer::sum);
                returnedArmor.merge(soldier.armor(), 1, Integer::sum);
                if (soldier.secondary() != null) {
                    returnedSecondaries.merge(soldier.secondary(), 1, Integer::sum);
                }
            }
        }

        List<FireTeamGearDelta> gear = new ArrayList<>();
        for (MarineWeapon weapon : MarineWeapon.values()) {
            for (EquipmentGrade grade : EquipmentGrade.values()) {
                PrimaryIssue issue = new PrimaryIssue(weapon, grade);
                int returned = returnedPrimaries.getOrDefault(issue, 0);
                int required = requiredPrimaries.getOrDefault(issue, 0);
                if (returned == 0 && required == 0) continue;
                gear.add(new FireTeamGearDelta(FireTeamGearDelta.Kind.PRIMARY,
                        weapon.catalogName(grade), freePrimary(issue), returned, required));
            }
        }
        for (MarineArmorPattern armor : MarineArmorPattern.values()) {
            int returned = returnedArmor.getOrDefault(armor, 0);
            int required = requiredArmor.getOrDefault(armor, 0);
            if (returned == 0 && required == 0) continue;
            gear.add(new FireTeamGearDelta(FireTeamGearDelta.Kind.ARMOR,
                    armor.displayName, freeArmor(armor), returned, required));
        }
        for (MarineSecondary secondary : MarineSecondary.values()) {
            int returned = returnedSecondaries.getOrDefault(secondary, 0);
            int required = requiredSecondaries.getOrDefault(secondary, 0);
            if (returned == 0 && required == 0) continue;
            gear.add(new FireTeamGearDelta(FireTeamGearDelta.Kind.SPECIAL,
                    secondary.displayName, freeSecondary(secondary), returned, required));
        }

        FireTeamTemplateResult result = unlocked ? insufficiency(gear)
                : FireTeamTemplateResult.LOCKED_RECIPE;
        return new FireTeamRefitPreview(result, gear);
    }

    private FireTeamTemplateResult insufficiency(List<FireTeamGearDelta> gear) {
        for (FireTeamGearDelta delta : gear) {
            if (delta.kind() == FireTeamGearDelta.Kind.PRIMARY && !delta.sufficient()) {
                return FireTeamTemplateResult.INSUFFICIENT_PRIMARIES;
            }
        }
        for (FireTeamGearDelta delta : gear) {
            if (delta.kind() == FireTeamGearDelta.Kind.ARMOR && !delta.sufficient()) {
                return FireTeamTemplateResult.INSUFFICIENT_ARMOR;
            }
        }
        for (FireTeamGearDelta delta : gear) {
            if (delta.kind() == FireTeamGearDelta.Kind.SPECIAL && !delta.sufficient()) {
                return FireTeamTemplateResult.INSUFFICIENT_SECONDARIES;
            }
        }
        return FireTeamTemplateResult.APPLIED;
    }

    private void materializeRefits(List<RefitRequest> requests) {
        for (RefitRequest request : requests) {
            MarineSquad squad = squadById(request.squadId);
            FireTeamTemplateCard card = armory.templateCardById(request.cardId);
            List<String> members = squad.teamMembers(request.teamIndex);
            for (int i = 0; i < members.size(); i++) {
                MarineSoldier soldier = soldierById(members.get(i));
                FireTeamBillet billet = card.billet(i);
                soldier.setPrimary(billet.primary(), billet.grade());
                soldier.setSecondary(billet.secondary());
                soldier.setArmor(billet.armor());
            }
            squad.setTeamTemplateCardId(request.teamIndex, card.id());
        }
    }

    private boolean recipesUnlocked(FireTeamTemplateCard card) {
        for (FireTeamBillet billet : card.billets()) {
            if (!armory.isPrimaryUnlocked(billet.primary(), billet.grade())
                    || !armory.isArmorUnlocked(billet.armor())
                    || billet.secondary() != null
                    && !armory.isSecondaryUnlocked(billet.secondary())) return false;
        }
        return true;
    }

    private TemplateRequirements requirements(FireTeamTemplateCard card) {
        Map<PrimaryIssue, Integer> primaries = new HashMap<>();
        Map<MarineArmorPattern, Integer> armor = new HashMap<>();
        Map<MarineSecondary, Integer> secondaries = new HashMap<>();
        for (FireTeamBillet billet : card.billets()) {
            primaries.merge(new PrimaryIssue(
                    billet.primary(), billet.grade()), 1, Integer::sum);
            armor.merge(billet.armor(), 1, Integer::sum);
            if (billet.secondary() != null) {
                secondaries.merge(billet.secondary(), 1, Integer::sum);
            }
        }
        return new TemplateRequirements(primaries, armor, secondaries);
    }

    private int freePrimary(PrimaryIssue issue) {
        if (issue.weapon == MarineWeapon.FIELD_RIFLE
                && issue.grade == EquipmentGrade.SERVICE) {
            return FireTeamGearDelta.UNLIMITED;
        }
        int allocated = 0;
        for (MarineSoldier soldier : soldiers) {
            if (holdsAllocatedGear(soldier) && soldier.primary() == issue.weapon
                    && soldier.primaryGrade() == issue.grade) allocated++;
        }
        return Math.max(0, armory.ownedPrimary(issue.weapon, issue.grade) - allocated);
    }

    private int freeArmor(MarineArmorPattern armor) {
        int allocated = 0;
        for (MarineSoldier soldier : soldiers) {
            if (holdsAllocatedGear(soldier) && soldier.armor() == armor) allocated++;
        }
        return Math.max(0, armory.ownedArmor(armor) - allocated);
    }

    private int freeSecondary(MarineSecondary secondary) {
        int allocated = 0;
        for (MarineSoldier soldier : soldiers) {
            if (holdsAllocatedGear(soldier) && soldier.secondary() == secondary) allocated++;
        }
        return Math.max(0, armory.ownedSecondary(secondary) - allocated);
    }

    private static <K> void mergeCounts(Map<K, Integer> target, Map<K, Integer> source) {
        for (Map.Entry<K, Integer> entry : source.entrySet()) {
            target.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
    }

    private record PrimaryIssue(MarineWeapon weapon, EquipmentGrade grade) {}
    private record TemplateRequirements(Map<PrimaryIssue, Integer> primaries,
                                        Map<MarineArmorPattern, Integer> armor,
                                        Map<MarineSecondary, Integer> secondaries) {}
    private record RefitRequest(String squadId, int teamIndex, String cardId) {}
    private record RefitPlan(MarineSquad squad, int teamIndex,
                             List<MarineSoldier> team, FireTeamTemplateCard card) {}

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
        refreshLeadership();
    }

    /** Applies the richer personnel report used by the squad debrief. */
    public void applySoldierOutcome(Map<String, MarineSoldierStatus> outcomes,
                                    int survivorXp, float currentDay, float wiaDays) {
        applySoldierOutcome(outcomes, survivorXp, currentDay, wiaDays,
                Collections.emptyMap(), false);
    }

    /**
     * Applies the personnel report and folds the mission's frozen combat
     * telemetry into each deployed marine's career record
     * ({@code progression-nouns.md}).
     *
     * <p>{@code outcomes} is the deployment manifest: it holds survivors and
     * casualties alike, so every key is a marine who went. A marine the battle
     * recorded nothing for still counts as deployed — they were there, they
     * just never got a shot off.
     */
    public void applySoldierOutcome(Map<String, MarineSoldierStatus> outcomes,
                                    int survivorXp, float currentDay, float wiaDays,
                                    Map<String, CombatTelemetryRow> telemetry,
                                    boolean victory) {
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
            CombatTelemetryRow row = telemetry != null ? telemetry.get(entry.getKey()) : null;
            soldier.career().recordDeployment(
                    victory,
                    status == MarineSoldierStatus.WIA,
                    row != null ? row.roundsFired() : 0,
                    row != null ? row.roundsHit() : 0,
                    row != null ? row.damageDealt() : 0f,
                    row != null ? row.damageTaken() : 0f,
                    row != null ? row.kills() : 0);
        }
        refreshLeadership();
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
        refreshLeadership();
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

    /** Seniority order for picking a leader: rank, then experience, then a stable id tiebreak. */
    private static final Comparator<MarineSoldier> SENIORITY =
            Comparator.comparingInt((MarineSoldier s) -> s.enlistedRank().ordinal()).reversed()
                    .thenComparing(Comparator.comparingInt(MarineSoldier::experienceXp).reversed())
                    .thenComparing(MarineSoldier::id);

    /** The NCO leading this squad, or null when nobody in it is fit for duty. */
    public MarineSoldier squadLeader(MarineSquad squad) {
        return squad == null ? null : soldierById(squad.leaderSoldierId());
    }

    /**
     * Re-derives squad and fire-team leadership across the line. Rank follows the
     * billet rather than being awarded on its own: the squad leader wears
     * corporal's stripes (a sergeant's once they are a veteran), the leader of
     * each other manned fire team wears a lance corporal's, and everyone else is
     * a marine. Casualties therefore promote a successor deterministically —
     * highest rank, then most experienced, then by id.
     *
     * <p>Only marines fit for duty are ranked. Personnel on the wounded list keep
     * their stripes, so a corporal who returns outranks the marine who stood in
     * and resumes the billet instead of the squad drifting to a new leader every
     * time someone is hurt.
     */
    /**
     * <p>Public because experience is one of the inputs and
     * {@link MarineSoldier#addExperience} is not routed through the roster —
     * a caller that awards XP outside {@link #applySoldierOutcome} has to say
     * so. Idempotent; safe to call as often as a caller likes.
     */
    public void refreshLeadership() {
        for (MarineSquad squad : squads) {
            List<MarineSoldier> onDuty = new ArrayList<>();
            if (!squad.reserve()) {
                for (MarineSoldier soldier : squadMembers(squad)) {
                    if (soldier.status() == MarineSoldierStatus.ACTIVE) onDuty.add(soldier);
                }
            }
            if (onDuty.isEmpty()) {
                squad.setLeaderSoldierId(null);
                continue;
            }
            MarineSoldier leader = Collections.min(onDuty, SENIORITY);
            squad.setLeaderSoldierId(leader.id());
            for (MarineSoldier soldier : onDuty) soldier.setEnlistedRank(EnlistedRank.MARINE);
            int leaderTeam = squad.teamIndexOf(leader.id());
            for (int team = 0; team < MarineSquad.TEAMS_PER_SQUAD; team++) {
                if (team == leaderTeam) continue;
                MarineSoldier teamLeader = seniorOnTeam(squad, team, onDuty);
                if (teamLeader != null) teamLeader.setEnlistedRank(EnlistedRank.LANCE_CORPORAL);
            }
            leader.setEnlistedRank(
                    leader.experienceXp() >= ExperienceTier.VETERAN.minimumXp
                            ? EnlistedRank.SERGEANT : EnlistedRank.CORPORAL);
        }
    }

    private static MarineSoldier seniorOnTeam(MarineSquad squad, int team,
                                              List<MarineSoldier> onDuty) {
        MarineSoldier best = null;
        for (MarineSoldier soldier : onDuty) {
            if (squad.teamIndexOf(soldier.id()) != team) continue;
            if (best == null || SENIORITY.compare(soldier, best) < 0) best = soldier;
        }
        return best;
    }

    private void assignToSquad(MarineSoldier recruit) {
        for (MarineSquad squad : squads) {
            if (!squad.reserve() && !squad.stationed()
                    && manningCount(squad) < MarineSquad.CAPACITY) {
                squad.add(recruit.id());
                return;
            }
        }
        MarineSquad reserve = squadById(reserveSquadId);
        if (reserve != null && reserve.reserve()) {
            reserve.add(recruit.id());
            return;
        }
        MarineSquad squad = createSquad();
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

    /**
     * Gives a new campaign a readable mixed roster before the player reaches the
     * armory. Keyed to squad structure so the pattern holds as the roster grows:
     * one anti-armor billet per squad, and the first two squads issued distinct
     * armor so they read apart on sight.
     */
    private void autoIssueRecruit(MarineSoldier recruit, int number) {
        int billet = Math.floorMod(number - 1, MarineSquad.CAPACITY) + 1;
        if (billet % 6 == 2) {
            allocatePrimary(recruit.id(), MarineWeapon.SMG, EquipmentGrade.SERVICE);
        } else if (billet % 6 == 4) {
            allocatePrimary(recruit.id(), MarineWeapon.DMR, EquipmentGrade.SERVICE);
        }
        if (number <= MarineSquad.CAPACITY) {
            allocateArmor(recruit.id(), MarineArmorPattern.CHARCOAL);
        } else if (number <= 2 * MarineSquad.CAPACITY) {
            allocateArmor(recruit.id(), MarineArmorPattern.ARMY_GREEN);
        }
        if (billet == MarineSquad.CAPACITY) {
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
        if (captainCandidates == null) captainCandidates = new ArrayList<>();
        if (armory == null) armory = new MarineArmory();
        if (nextSoldierNumber <= 0) nextSoldierNumber = soldiers.size() + 1;
        if (nextSquadNumber <= 0) nextSquadNumber = squads.size() + 1;
        if (!soldiers.isEmpty()) initialComplementIssued = true;
        for (MarineSoldier soldier : soldiers) {
            if (squadForSoldier(soldier.id()) == null) assignToSquad(soldier);
        }
        repairSquadCommands();
        repairStationingBindings();
        refreshLeadership();
        repairCaptainCandidates();
        return this;
    }

    private void repairCaptainCandidates() {
        Set<String> sources = new HashSet<>();
        List<CaptainCandidate> repaired = new ArrayList<>();
        for (CaptainCandidate candidate : captainCandidates) {
            if (candidate == null || !candidate.valid()
                    || !sources.add(candidate.sourceKey())) continue;
            if (candidate.state() == CaptainCandidateState.AVAILABLE
                    && byId(candidate.id()) != null) {
                candidate.markAccepted();
            }
            repaired.add(candidate);
        }
        captainCandidates = repaired;
    }

    private static String normalizeSourceKey(String sourceKey) {
        if (sourceKey == null) return null;
        String normalized = sourceKey.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isStationed(String soldierId) {
        MarineSquad squad = squadForSoldier(soldierId);
        return squad != null && squad.stationed();
    }

    private void repairStationingBindings() {
        for (MarineSquad squad : squads) {
            if (squad.reserve() && squad.stationed()) {
                squad.setStationingContractId(-1L);
            }
        }
    }

    private void repairSquadCommands() {
        Map<String, Integer> assignedByCaptain = new HashMap<>();
        for (MarineSquad squad : squads) {
            String captainId = squad.homeCaptainId();
            if (captainId == null) continue;
            MarineCaptain captain = byId(captainId);
            int assigned = assignedByCaptain.getOrDefault(captainId, 0);
            if (squad.reserve() || captain == null || assigned >= captain.rank().squadCommandCap()) {
                squad.setHomeCaptainId(null);
                continue;
            }
            assignedByCaptain.put(captainId, assigned + 1);
        }
    }
}
