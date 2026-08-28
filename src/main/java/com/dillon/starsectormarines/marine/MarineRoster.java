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
import java.util.Objects;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.ExperienceTier;
import com.dillon.starsectormarines.battle.infantry.SoldierAptitude;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryRow;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;

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

    /** The layout every company got before the seed belonged to the company. */
    private static final long DEFAULT_DECK_SEED = 0x5AFE_DECEL;

    private final List<MarineCaptain> captains = new ArrayList<>();
    // Non-final so xstream's readResolve can backfill on legacy saves that
    // predate this field (xstream bypasses the constructor on deserialization,
    // so the inline initializer doesn't run for old save streams).
    private Set<String> completedStoryIds = new HashSet<>();
    private List<MarineSoldier> soldiers = new ArrayList<>();
    /** Derived lookup only; campaign saves continue to persist the ordered soldier list. */
    private transient Map<String, MarineSoldier> soldierIndex;
    private List<MarineSquad> squads = new ArrayList<>();
    private List<CaptainCandidate> captainCandidates = new ArrayList<>();
    private MarineArmory armory = new MarineArmory();
    private MechBay mechBay = new MechBay();
    private int nextSoldierNumber = 1;
    private int nextSquadNumber = 1;
    private String reserveSquadId;
    private boolean initialComplementIssued;
    private int capacity = DEFAULT_CAPACITY;
    /**
     * The fleet member the company lives aboard, by id. Null until the company
     * has been given a ship, and again once that ship is gone — which is why it
     * is resolved through {@code CompanyShipDesignation} rather than read
     * directly.
     */
    private String companyShipId;
    /**
     * Her name, kept alongside her id so she can still be named after she is
     * gone. A ship that has left the fleet cannot be asked what she was called.
     */
    private String companyShipName;
    /** The ship the company lost, until they are given another. */
    private String formerShipName;
    /**
     * Whether the company's ship was lost in action rather than let go. Set
     * from the engagement she failed to come home from, and only believed once
     * she is actually missing from the fleet — a ship can be disabled in a
     * battle and recovered from the field afterwards.
     */
    private boolean formerShipLostInAction;
    /** Evidence from the last engagement, pending confirmation she is gone. */
    private boolean companyShipCasualty;
    /**
     * Fixes every deck this company will ever generate. Combined with the ship
     * it is generating for, so each hull has its own stable layout and a ship
     * the company returns to is the ship they left.
     */
    private long deckSeed = DEFAULT_DECK_SEED;

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

    /** @see #companyShipId */
    public String companyShipId() {
        return companyShipId;
    }

    /** @see #companyShipName */
    public String companyShipName() {
        return companyShipName;
    }

    /** Quarter the company aboard a ship, which is also how a loss is put behind them. */
    public void setCompanyShip(String companyShipId, String companyShipName) {
        this.companyShipId = companyShipId;
        this.companyShipName = companyShipName;
        this.formerShipName = null;
        this.formerShipLostInAction = false;
        this.companyShipCasualty = false;
    }

    /**
     * The company's ship is gone and they have nowhere to live.
     *
     * <p>Deliberately does not move them to another hull. Losing a home is not
     * the same as being handed one, and where the company goes next is the
     * player's decision for the same reason the first one was.
     */
    public void reportCompanyShipGone() {
        formerShipName = companyShipName;
        formerShipLostInAction = companyShipCasualty;
        companyShipId = null;
        companyShipName = null;
        companyShipCasualty = false;
    }

    /** @see #formerShipName */
    public String formerShipName() {
        return formerShipName;
    }

    /** @see #formerShipLostInAction */
    public boolean formerShipLostInAction() {
        return formerShipLostInAction;
    }

    /** @see #companyShipCasualty */
    public void reportCompanyShipCasualty() {
        companyShipCasualty = true;
    }

    /** @see #deckSeed */
    public long deckSeed() {
        return deckSeed;
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
    public MechBay mechBay() { return mechBay; }

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

    public boolean isWeaponDoctrineAssigned(String doctrineId) {
        if (doctrineId == null) return false;
        for (MarineSquad squad : squads) {
            if (doctrineId.equals(squad.weaponDoctrineId())) return true;
        }
        return false;
    }

    public boolean isArmorDoctrineAssigned(String doctrineId) {
        if (doctrineId == null) return false;
        for (MarineSquad squad : squads) {
            if (doctrineId.equals(squad.armorDoctrineId())) return true;
        }
        return false;
    }

    public boolean deleteWeaponDoctrine(String doctrineId) {
        return !isWeaponDoctrineAssigned(doctrineId)
                && armory.deleteWeaponDoctrine(doctrineId);
    }

    public boolean deleteArmorDoctrine(String doctrineId) {
        return !isArmorDoctrineAssigned(doctrineId)
                && armory.deleteArmorDoctrine(doctrineId);
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

    /**
     * Current billet holders in stable roster order. Permanent casualty records remain on the
     * squad's historical roll, but do not displace a later replacement from a fire-team slot.
     * WIA marines remain here because their billet is still theirs while they recover.
     */
    public List<String> manningMemberIds(MarineSquad squad) {
        if (squad == null) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (String id : squad.memberIds()) {
            MarineSoldier soldier = soldierById(id);
            if (soldier != null && (soldier.status() == MarineSoldierStatus.ACTIVE
                    || soldier.status() == MarineSoldierStatus.WIA)) {
                result.add(id);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** Members of one current fire team in billet order. */
    public List<String> teamMemberIds(MarineSquad squad, int teamIndex) {
        if (teamIndex < 0 || teamIndex >= MarineSquad.TEAMS_PER_SQUAD) {
            return Collections.emptyList();
        }
        List<String> members = manningMemberIds(squad);
        int from = Math.min(teamIndex * MarineSquad.TEAM_SIZE, members.size());
        int to = Math.min(from + MarineSquad.TEAM_SIZE, members.size());
        return Collections.unmodifiableList(new ArrayList<>(members.subList(from, to)));
    }

    /** Current fire-team index, excluding historical KIA/MIA records from billet order. */
    public int teamIndexOf(MarineSquad squad, String soldierId) {
        int billet = manningMemberIds(squad).indexOf(soldierId);
        return billet < 0 ? -1 : billet / MarineSquad.TEAM_SIZE;
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
        List<MarineSoldier> recruits = recruitToSquad(squadId, 1);
        return recruits.isEmpty() ? null : recruits.get(0);
    }

    /**
     * Hires up to {@code count} replacements as one roster mutation.
     *
     * <p>The single-recruit path remains the ordinary campaign operation. This
     * bulk form exists for formation bootstrap and detached fixtures, where a
     * complete squad is authored together. Basic issue and derived leadership
     * are repaired once after the batch instead of once per billet. Recruits
     * arrive identical; what makes a squad good is the kit it is then issued.
     */
    public List<MarineSoldier> recruitToSquad(String squadId, int count) {
        MarineSquad squad = squadById(squadId);
        if (squad == null || squad.stationed() || count <= 0) {
            return Collections.emptyList();
        }
        int recruitCount = squad.reserve()
                ? count : Math.min(count, vacancies(squad));
        if (recruitCount <= 0) return Collections.emptyList();
        List<MarineSoldier> recruits = new ArrayList<>(recruitCount);
        for (int index = 0; index < recruitCount; index++) {
            MarineSoldier recruit = createRecruit();
            squad.add(recruit.id());
            recruits.add(recruit);
        }
        armory.ensureBasicIssue(activeSoldierCount());
        refreshLeadership();
        return Collections.unmodifiableList(recruits);
    }

    /** Materializes one cargo-backed replacement in the first available line billet. */
    MarineSoldier createLineReplacement() {
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
            if (soldierIndex != null) soldierIndex.remove(soldierId);
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

    /** Assigns one ready reserve to the next line billet, creating a line squad if needed. */
    boolean assignReadyReserveToLine() {
        if (firstReadyReserve() == null) return false;
        MarineSquad target = null;
        for (MarineSquad squad : squads) {
            if (!squad.reserve() && !squad.stationed() && vacancies(squad) > 0) {
                target = squad;
                break;
            }
        }
        if (target == null) target = createSquad();
        return fillVacancyFromReserve(target.id());
    }

    /** Fills as many true open billets as possible from ready reserve personnel. */
    public int fillVacanciesFromReserve(String targetSquadId) {
        int filled = 0;
        while (vacancies(squadById(targetSquadId)) > 0 && fillVacancyFromReserve(targetSquadId)) {
            filled++;
        }
        return filled;
    }

    public int readyReserveCount() {
        return readyCount(squadById(reserveSquadId));
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
        if (soldierIndex == null || soldierIndex.size() != soldiers.size()) {
            soldierIndex = new HashMap<>(Math.max(16, soldiers.size() * 2));
            for (MarineSoldier soldier : soldiers) {
                if (soldier != null) soldierIndex.put(soldier.id(), soldier);
            }
        }
        return soldierIndex.get(id);
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

    public boolean allocatePrimary(String soldierId, String weaponId, EquipmentGrade grade) {
        MarineSoldier soldier = soldierById(soldierId);
        if (!canAllocatePrimary(soldierId, weaponId, grade)) return false;
        soldier.setPrimary(weaponId, grade);
        return true;
    }
    public boolean allocatePrimary(String soldierId, WeaponDef weapon,
                                   EquipmentGrade grade) {
        return weapon != null && allocatePrimary(soldierId, weapon.id, grade);
    }

    public boolean canAllocatePrimary(String soldierId, String weaponId, EquipmentGrade grade) {
        MarineSoldier soldier = soldierById(soldierId);
        if (soldier == null || soldier.status() != MarineSoldierStatus.ACTIVE
                || isStationed(soldierId)
                || !armory.isPrimaryUnlocked(weaponId, grade)) return false;
        if (WeaponRegistry.STARTER_PRIMARY_ID.equals(weaponId)
                && grade == EquipmentGrade.SERVICE) return true;
        int allocated = 0;
        for (MarineSoldier other : soldiers) {
            if (other != soldier && holdsAllocatedGear(other)
                    && Objects.equals(other.primaryId(), weaponId)
                    && other.primaryGrade() == grade) allocated++;
        }
        return allocated < armory.ownedPrimary(weaponId, grade);
    }
    public boolean canAllocatePrimary(String soldierId,
                                      WeaponDef weapon,
                                      EquipmentGrade grade) {
        return weapon != null && canAllocatePrimary(soldierId, weapon.id, grade);
    }

    public boolean allocateSecondary(String soldierId, String specialEquipmentId) {
        MarineSoldier soldier = soldierById(soldierId);
        if (!canAllocateSecondary(soldierId, specialEquipmentId)) return false;
        if (specialEquipmentId == null) {
            soldier.setSpecialEquipment(null);
            return true;
        }
        soldier.setSpecialEquipment(specialEquipmentId);
        return true;
    }
    public boolean allocateSecondary(String soldierId, SpecialEquipmentDef special) {
        return special != null && allocateSecondary(soldierId, special.id());
    }

    public boolean canAllocateSecondary(String soldierId, String specialEquipmentId) {
        MarineSoldier soldier = soldierById(soldierId);
        if (soldier == null || soldier.status() != MarineSoldierStatus.ACTIVE
                || isStationed(soldierId)) return false;
        if (specialEquipmentId == null) return true;
        if (!armory.isSecondaryUnlocked(specialEquipmentId)) return false;
        int allocated = 0;
        for (MarineSoldier other : soldiers) {
            if (other != soldier && holdsAllocatedGear(other)
                    && Objects.equals(other.specialEquipmentId(),
                    specialEquipmentId)) allocated++;
        }
        return allocated < armory.ownedSecondary(specialEquipmentId);
    }
    public boolean canAllocateSecondary(String soldierId, SpecialEquipmentDef special) {
        return special != null && canAllocateSecondary(soldierId, special.id());
    }

    public boolean allocateArmor(String soldierId, MarineArmorPattern armor) {
        MarineSoldier soldier = soldierById(soldierId);
        if (!canAllocateArmor(soldierId, armor)) return false;
        soldier.setArmor(armor);
        // Armour sets the experience band, and stripes follow the band, so an
        // armour change is a leadership change ({@code progression-nouns.md}).
        refreshLeadership();
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
        List<String> weapons = EquipmentTemplateCatalog.playerPrimaryIds().stream()
                .filter(id -> !WeaponRegistry.STARTER_PRIMARY_ID.equals(id)).toList();
        EquipmentGrade[] grades = EquipmentGrade.values();
        int familyIndex = weapons.indexOf(soldier.primaryId());
        int start = familyIndex < 0 ? 0
                : 1 + familyIndex * grades.length + soldier.primaryGrade().ordinal();
        int total = 1 + weapons.size() * grades.length;
        for (int offset = 1; offset <= total; offset++) {
            int index = (start + offset) % total;
            String weaponId = index == 0 ? WeaponRegistry.STARTER_PRIMARY_ID
                    : weapons.get((index - 1) / grades.length);
            EquipmentGrade grade = index == 0 ? EquipmentGrade.SERVICE
                    : grades[(index - 1) % grades.length];
            if (allocatePrimary(soldierId, weaponId, grade)) return true;
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

    /** Headless/debug preview with no cargo constraint. Campaign callers provide resources. */
    public SquadEquipmentPreview previewSquadEquipment(
            String squadId, String weaponDoctrineId, String armorDoctrineId) {
        return previewSquadEquipment(squadId, weaponDoctrineId, armorDoctrineId,
                EquipmentIssueResources.UNLIMITED);
    }

    /**
     * Previews one squad-wide issue as permanent template ownership plus the cargo cost
     * of changed incoming equipment. Removed equipment is not refunded.
     */
    public SquadEquipmentPreview previewSquadEquipment(
            String squadId, String weaponDoctrineId, String armorDoctrineId,
            EquipmentIssueResources resources) {
        return previewSquadEquipment(squadId, weaponDoctrineId, armorDoctrineId,
                resources, null);
    }

    private SquadEquipmentPreview previewSquadEquipment(
            String squadId, String weaponDoctrineId, String armorDoctrineId,
            EquipmentIssueResources resources, List<EquipmentGrade> primaryGrades) {
        if (resources == null) throw new IllegalArgumentException("resources are required");
        if (primaryGrades != null && (primaryGrades.size() != MarineSquad.CAPACITY
                || primaryGrades.stream().anyMatch(Objects::isNull))) {
            throw new IllegalArgumentException(
                    "A squad equipment variant requires one grade per billet");
        }
        MarineSquad squad = squadById(squadId);
        if (squad == null || squad.reserve()) {
            return squadEquipmentFailure(SquadEquipmentResult.INVALID_SQUAD);
        }
        SquadWeaponDoctrine weapons = armory.weaponDoctrineById(weaponDoctrineId);
        if (weapons == null) {
            return squadEquipmentFailure(SquadEquipmentResult.UNKNOWN_WEAPON_DOCTRINE);
        }
        SquadArmorDoctrine armor = armory.armorDoctrineById(armorDoctrineId);
        if (armor == null) {
            return squadEquipmentFailure(SquadEquipmentResult.UNKNOWN_ARMOR_DOCTRINE);
        }

        List<SquadEquipmentBillet> billets = new ArrayList<>();
        for (int index = 0; index < MarineSquad.CAPACITY; index++) {
            SquadWeaponIssue weapon = weapons.issue(index);
            billets.add(new SquadEquipmentBillet(
                    weapon.role(), weapon.primaryId(), primaryGrades != null
                            ? primaryGrades.get(index) : weapon.grade(),
                    weapon.specialEquipmentId(), armor.issueId(index)));
        }
        if (squad.stationed()) {
            return new SquadEquipmentPreview(
                    SquadEquipmentResult.STATIONED, billets, Collections.emptyList());
        }

        List<String> memberIds = manningMemberIds(squad);
        if (memberIds.size() != MarineSquad.CAPACITY) {
            return new SquadEquipmentPreview(
                    SquadEquipmentResult.SQUAD_NOT_READY, billets, Collections.emptyList());
        }
        List<MarineSoldier> members = new ArrayList<>();
        for (String memberId : memberIds) {
            MarineSoldier soldier = soldierById(memberId);
            if (soldier == null || soldier.status() != MarineSoldierStatus.ACTIVE) {
                return new SquadEquipmentPreview(
                        SquadEquipmentResult.SQUAD_NOT_READY, billets, Collections.emptyList());
            }
            members.add(soldier);
        }

        boolean ownsTemplates = true;
        for (SquadEquipmentBillet billet : billets) {
            ownsTemplates &= armory.ownsPrimaryTemplate(billet.primaryId(), billet.grade())
                    && armory.ownsArmorTemplate(billet.armorId())
                    && (billet.specialEquipmentId() == null
                    || armory.ownsSpecialTemplate(billet.specialEquipmentId()));
        }
        EquipmentTemplateCost cost = EquipmentTemplateCost.ZERO;
        for (int index = 0; index < members.size(); index++) {
            MarineSoldier soldier = members.get(index);
            SquadEquipmentBillet billet = billets.get(index);
            if (!soldier.primaryId().equals(billet.primaryId())
                    || soldier.primaryGrade() != billet.grade()) {
                cost = cost.plus(EquipmentTemplateCatalog
                        .primary(billet.primaryId(), billet.grade()).issueCost());
            }
            if (!soldier.armorId().equals(billet.armorId())) {
                cost = cost.plus(EquipmentTemplateCatalog.armor(billet.armorId()).issueCost());
            }
            if (!Objects.equals(soldier.specialEquipmentId(),
                    billet.specialEquipmentId()) && billet.specialEquipmentId() != null) {
                cost = cost.plus(EquipmentTemplateCatalog
                        .special(billet.specialEquipmentId()).issueCost());
            }
        }
        EquipmentTemplateCost available = resources.available();
        SquadEquipmentResult result = !ownsTemplates
                ? SquadEquipmentResult.MISSING_TEMPLATE
                : available.covers(cost)
                ? SquadEquipmentResult.APPLIED : SquadEquipmentResult.INSUFFICIENT_CARGO;
        return new SquadEquipmentPreview(result, billets, Collections.emptyList(),
                cost, available);
    }

    /** Headless/debug apply with no cargo constraint. Campaign callers provide resources. */
    public SquadEquipmentResult applySquadEquipment(
            String squadId, String weaponDoctrineId, String armorDoctrineId) {
        return applySquadEquipment(squadId, weaponDoctrineId, armorDoctrineId,
                EquipmentIssueResources.UNLIMITED);
    }

    /**
     * Issues one doctrine to all twelve billets with an exact primary-grade
     * variant. This remains one atomic squad transaction; fire-team order,
     * special equipment, armor, and the assigned doctrine all come from the
     * authored definitions.
     */
    public SquadEquipmentResult applySquadEquipmentVariant(
            String squadId, String weaponDoctrineId, String armorDoctrineId,
            List<EquipmentGrade> primaryGrades) {
        return applySquadEquipment(squadId, weaponDoctrineId, armorDoctrineId,
                EquipmentIssueResources.UNLIMITED, primaryGrades);
    }

    /** Applies both definitions after the complete template and cargo transaction succeeds. */
    public SquadEquipmentResult applySquadEquipment(
            String squadId, String weaponDoctrineId, String armorDoctrineId,
            EquipmentIssueResources resources) {
        return applySquadEquipment(squadId, weaponDoctrineId, armorDoctrineId,
                resources, null);
    }

    private SquadEquipmentResult applySquadEquipment(
            String squadId, String weaponDoctrineId, String armorDoctrineId,
            EquipmentIssueResources resources, List<EquipmentGrade> primaryGrades) {
        SquadEquipmentPreview preview = previewSquadEquipment(
                squadId, weaponDoctrineId, armorDoctrineId, resources, primaryGrades);
        if (!preview.canApply()) return preview.result();
        if (!preview.issueCost().isZero() && !resources.spend(preview.issueCost())) {
            return SquadEquipmentResult.INSUFFICIENT_CARGO;
        }
        MarineSquad squad = squadById(squadId);
        List<String> memberIds = manningMemberIds(squad);
        for (int index = 0; index < memberIds.size(); index++) {
            MarineSoldier soldier = soldierById(memberIds.get(index));
            SquadEquipmentBillet billet = preview.billet(index);
            soldier.setPrimary(billet.primaryId(), billet.grade());
            soldier.setSpecialEquipment(billet.specialEquipmentId());
            soldier.setArmor(billet.armorId());
        }
        squad.setEquipmentDoctrineIds(weaponDoctrineId, armorDoctrineId);
        refreshLeadership();
        return SquadEquipmentResult.APPLIED;
    }

    private static SquadEquipmentPreview squadEquipmentFailure(SquadEquipmentResult result) {
        return new SquadEquipmentPreview(result, Collections.emptyList(), Collections.emptyList());
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
        for (Map.Entry<String, Integer> entry : required.secondaries.entrySet()) {
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
            List<String> memberIds = teamMemberIds(squad, request.teamIndex);
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
        Map<String, Integer> requiredSecondaries = new HashMap<>();
        Map<PrimaryIssue, Integer> returnedPrimaries = new HashMap<>();
        Map<MarineArmorPattern, Integer> returnedArmor = new HashMap<>();
        Map<String, Integer> returnedSecondaries = new HashMap<>();
        boolean unlocked = true;

        for (RefitPlan plan : plans) {
            TemplateRequirements required = requirements(plan.card);
            mergeCounts(requiredPrimaries, required.primaries);
            mergeCounts(requiredArmor, required.armor);
            mergeCounts(requiredSecondaries, required.secondaries);
            unlocked &= recipesUnlocked(plan.card);
            for (MarineSoldier soldier : plan.team) {
                returnedPrimaries.merge(new PrimaryIssue(
                        soldier.primaryId(), soldier.primaryGrade()), 1, Integer::sum);
                returnedArmor.merge(soldier.armor(), 1, Integer::sum);
                if (soldier.specialEquipmentId() != null) {
                    returnedSecondaries.merge(soldier.specialEquipmentId(), 1, Integer::sum);
                }
            }
        }

        List<FireTeamGearDelta> gear = new ArrayList<>();
        for (String weaponId : EquipmentTemplateCatalog.playerPrimaryIds()) {
            for (EquipmentGrade grade : EquipmentGrade.values()) {
                PrimaryIssue issue = new PrimaryIssue(weaponId, grade);
                int returned = returnedPrimaries.getOrDefault(issue, 0);
                int required = requiredPrimaries.getOrDefault(issue, 0);
                if (returned == 0 && required == 0) continue;
                gear.add(new FireTeamGearDelta(FireTeamGearDelta.Kind.PRIMARY,
                        WeaponRegistry.require(weaponId).catalogName(grade.tier),
                        freePrimary(issue), returned, required));
            }
        }
        for (MarineArmorPattern armor : MarineArmorPattern.values()) {
            int returned = returnedArmor.getOrDefault(armor, 0);
            int required = requiredArmor.getOrDefault(armor, 0);
            if (returned == 0 && required == 0) continue;
            gear.add(new FireTeamGearDelta(FireTeamGearDelta.Kind.ARMOR,
                    armor.displayName, freeArmor(armor), returned, required));
        }
        for (SpecialEquipmentDef special : SpecialEquipmentRegistry.installed().all()) {
            int returned = returnedSecondaries.getOrDefault(special.id(), 0);
            int required = requiredSecondaries.getOrDefault(special.id(), 0);
            if (returned == 0 && required == 0) continue;
            gear.add(new FireTeamGearDelta(FireTeamGearDelta.Kind.SPECIAL,
                    special.displayName(), freeSecondary(special.id()), returned, required));
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
            List<String> members = teamMemberIds(squad, request.teamIndex);
            for (int i = 0; i < members.size(); i++) {
                MarineSoldier soldier = soldierById(members.get(i));
                FireTeamBillet billet = card.billet(i);
                soldier.setPrimary(billet.primaryId(), billet.grade());
                soldier.setSpecialEquipment(billet.specialEquipmentId());
                soldier.setArmor(billet.armor());
            }
            squad.setTeamTemplateCardId(request.teamIndex, card.id());
        }
        refreshLeadership();
    }

    private boolean recipesUnlocked(FireTeamTemplateCard card) {
        for (FireTeamBillet billet : card.billets()) {
            if (!armory.isPrimaryUnlocked(billet.primaryId(), billet.grade())
                    || !armory.isArmorUnlocked(billet.armor())
                    || billet.specialEquipmentId() != null
                    && !armory.isSecondaryUnlocked(billet.specialEquipmentId())) return false;
        }
        return true;
    }

    private TemplateRequirements requirements(FireTeamTemplateCard card) {
        Map<PrimaryIssue, Integer> primaries = new HashMap<>();
        Map<MarineArmorPattern, Integer> armor = new HashMap<>();
        Map<String, Integer> secondaries = new HashMap<>();
        for (FireTeamBillet billet : card.billets()) {
            primaries.merge(new PrimaryIssue(
                    billet.primaryId(), billet.grade()), 1, Integer::sum);
            armor.merge(billet.armor(), 1, Integer::sum);
            if (billet.specialEquipmentId() != null) {
                secondaries.merge(billet.specialEquipmentId(), 1, Integer::sum);
            }
        }
        return new TemplateRequirements(primaries, armor, secondaries);
    }

    private int freePrimary(PrimaryIssue issue) {
        if (WeaponRegistry.STARTER_PRIMARY_ID.equals(issue.weaponId)
                && issue.grade == EquipmentGrade.SERVICE) {
            return FireTeamGearDelta.UNLIMITED;
        }
        int allocated = 0;
        for (MarineSoldier soldier : soldiers) {
            if (holdsAllocatedGear(soldier)
                    && Objects.equals(soldier.primaryId(), issue.weaponId)
                    && soldier.primaryGrade() == issue.grade) allocated++;
        }
        return Math.max(0, armory.ownedPrimary(issue.weaponId, issue.grade) - allocated);
    }

    private int freeArmor(MarineArmorPattern armor) {
        int allocated = 0;
        for (MarineSoldier soldier : soldiers) {
            if (holdsAllocatedGear(soldier) && soldier.armor() == armor) allocated++;
        }
        return Math.max(0, armory.ownedArmor(armor) - allocated);
    }

    private int freeSecondary(String specialEquipmentId) {
        int allocated = 0;
        for (MarineSoldier soldier : soldiers) {
            if (holdsAllocatedGear(soldier)
                    && Objects.equals(soldier.specialEquipmentId(),
                    specialEquipmentId)) allocated++;
        }
        return Math.max(0, armory.ownedSecondary(specialEquipmentId) - allocated);
    }

    private static <K> void mergeCounts(Map<K, Integer> target, Map<K, Integer> source) {
        for (Map.Entry<K, Integer> entry : source.entrySet()) {
            target.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
    }

    private record PrimaryIssue(String weaponId, EquipmentGrade grade) {}
    private record TemplateRequirements(Map<PrimaryIssue, Integer> primaries,
                                        Map<MarineArmorPattern, Integer> armor,
                                        Map<String, Integer> secondaries) {}
    private record RefitRequest(String squadId, int teamIndex, String cardId) {}
    private record RefitPlan(MarineSquad squad, int teamIndex,
                             List<MarineSoldier> team, FireTeamTemplateCard card) {}

    public void applySoldierOutcome(Set<String> survivors, Set<String> fallen) {
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
                                    float currentDay, float wiaDays) {
        applySoldierOutcome(outcomes, currentDay, wiaDays,
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
     *
     * <p>The same pass credits each marine's <b>squad</b> career. Attribution
     * comes from the squad id frozen onto the marine at spawn, carried back on
     * the telemetry row — never from current membership, so reorganizing the
     * roster after a battle cannot rewrite who fought alongside whom. A marine
     * with no telemetry row therefore has no squad to credit; they still count
     * as deployed on their own record. The squad's mission tally counts once
     * per squad per mission, however many billets it filled.
     */
    public void applySoldierOutcome(Map<String, MarineSoldierStatus> outcomes,
                                    float currentDay, float wiaDays,
                                    Map<String, CombatTelemetryRow> telemetry,
                                    boolean victory) {
        if (outcomes == null) return;
        Set<String> creditedSquadIds = new LinkedHashSet<>();
        for (Map.Entry<String, MarineSoldierStatus> entry : outcomes.entrySet()) {
            MarineSoldier soldier = soldierById(entry.getKey());
            if (soldier == null || entry.getValue() == null) continue;
            MarineSoldierStatus status = entry.getValue();
            if (status == MarineSoldierStatus.ACTIVE) {
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
                    row != null ? row.friendlyFireDamage() : 0f,
                    row != null ? row.damageTaken() : 0f,
                    row != null ? row.kills() : 0);
            if (row == null || row.campaignSquadId() == null) continue;
            MarineSquad squad = squadById(row.campaignSquadId());
            if (squad == null) continue;
            squad.career().recordMarine(
                    status != MarineSoldierStatus.ACTIVE,
                    row.roundsFired(), row.roundsHit(),
                    row.damageDealt(), row.friendlyFireDamage(),
                    row.damageTaken(), row.kills());
            creditedSquadIds.add(row.campaignSquadId());
        }
        for (String squadId : creditedSquadIds) {
            MarineSquad squad = squadById(squadId);
            if (squad != null) squad.career().recordMission(victory);
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

    /**
     * Seniority order for picking a leader: rank, then time served, then a
     * stable id tiebreak. Time served is deployments on the marine's career
     * record — the marine who has actually been on more operations. It replaced
     * a persisted XP number, which stopped meaning anything once experience
     * became issued with the armour rather than accumulated.
     */
    private static final Comparator<MarineSoldier> SENIORITY =
            Comparator.comparingInt((MarineSoldier s) -> s.enlistedRank().ordinal()).reversed()
                    .thenComparing(Comparator.comparingInt(
                            (MarineSoldier s) -> s.career().missionsDeployed()).reversed())
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
     *
     * <p>Public because issued armour is one of the inputs: a refit changes a
     * marine's band and therefore whether their squad leader wears sergeant's
     * stripes. Idempotent; safe to call as often as a caller likes.
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
            int leaderTeam = teamIndexOf(squad, leader.id());
            for (int team = 0; team < MarineSquad.TEAMS_PER_SQUAD; team++) {
                if (team == leaderTeam) continue;
                MarineSoldier teamLeader = seniorOnTeam(squad, team, onDuty);
                if (teamLeader != null) teamLeader.setEnlistedRank(EnlistedRank.LANCE_CORPORAL);
            }
            // Stripes follow the band the squad's kit fields its NCO at. With
            // experience issued rather than accumulated, a marine never grows
            // into sergeant's stripes; the company equips its way there.
            leader.setEnlistedRank(
                    SquadExperienceStandard.bandFor(leader).minimumXp
                            >= ExperienceTier.VETERAN.minimumXp
                            ? EnlistedRank.SERGEANT : EnlistedRank.CORPORAL);
        }
    }

    private MarineSoldier seniorOnTeam(MarineSquad squad, int team,
                                       List<MarineSoldier> onDuty) {
        MarineSoldier best = null;
        for (MarineSoldier soldier : onDuty) {
            if (teamIndexOf(squad, soldier.id()) != team) continue;
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
        if (soldierIndex != null) soldierIndex.put(recruit.id(), recruit);
        autoIssueRecruit(recruit, number);
        return recruit;
    }

    /**
     * Gives a new campaign a readable mixed roster before the player reaches the
     * armory. Keyed to squad structure so the pattern holds as the roster grows:
     * one anti-armor billet per squad, and the first two squads issued distinct
     * armor so they read apart on sight.
     *
     * <p><b>Armour tier here is load-bearing, not decoration.</b> It sets the
     * band a marine deploys at ({@code SquadExperienceStandard}), so the two
     * founding squads are deliberately given <em>different tier-2 patterns</em>
     * rather than different-looking better ones: they read apart on sight and
     * still field as regulars. Green is reserved for the cargo replacements a
     * player has not kitted out yet, which is what makes losses cost something.
     */
    private void autoIssueRecruit(MarineSoldier recruit, int number) {
        int billet = Math.floorMod(number - 1, MarineSquad.CAPACITY) + 1;
        if (billet % 6 == 2) {
            recruit.setPrimary(WeaponRegistry.SMG_ID, EquipmentGrade.SERVICE);
        } else if (billet % 6 == 4) {
            recruit.setPrimary(WeaponRegistry.DMR_ID, EquipmentGrade.SERVICE);
        }
        if (number <= MarineSquad.CAPACITY) {
            recruit.setArmor(MarineArmorPattern.MILITIA);
        } else if (number <= 2 * MarineSquad.CAPACITY) {
            recruit.setArmor(MarineArmorPattern.BLUE_SCOUT);
        }
        if (billet == MarineSquad.CAPACITY) {
            recruit.setSpecialEquipment(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID);
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
        soldierIndex = null;
        if (squads == null) squads = new ArrayList<>();
        if (captainCandidates == null) captainCandidates = new ArrayList<>();
        if (armory == null) armory = new MarineArmory();
        if (mechBay == null) mechBay = new MechBay();
        if (nextSoldierNumber <= 0) nextSoldierNumber = soldiers.size() + 1;
        if (nextSquadNumber <= 0) nextSquadNumber = squads.size() + 1;
        if (!soldiers.isEmpty()) initialComplementIssued = true;
        migrateLegacySquadEquipmentIntent();
        for (MarineSoldier soldier : soldiers) {
            if (squadForSoldier(soldier.id()) == null) assignToSquad(soldier);
        }
        repairSquadCommands();
        repairStationingBindings();
        refreshLeadership();
        repairCaptainCandidates();
        return this;
    }

    private void migrateLegacySquadEquipmentIntent() {
        for (MarineSquad squad : squads) {
            if (squad == null || squad.reserve()
                    || squad.weaponDoctrineId() != null || squad.armorDoctrineId() != null) continue;
            List<SquadWeaponIssue> weapons = new ArrayList<>();
            List<MarineArmorPattern> armor = new ArrayList<>();
            boolean complete = true;
            for (int team = 0; team < MarineSquad.TEAMS_PER_SQUAD; team++) {
                FireTeamTemplateCard card = armory.templateCardById(
                        squad.teamTemplateCardId(team));
                if (card == null) {
                    complete = false;
                    break;
                }
                for (FireTeamBillet billet : card.billets()) {
                    weapons.add(new SquadWeaponIssue(
                            billet.name(), billet.primaryId(), billet.grade(),
                            billet.specialEquipmentId()));
                    armor.add(billet.armor() != null
                            ? billet.armor() : MarineArmorPattern.ARMORLESS);
                }
            }
            if (!complete || weapons.size() != MarineSquad.CAPACITY) continue;
            String weaponId = "migrated:weapons:" + squad.id();
            String armorId = "migrated:armor:" + squad.id();
            armory.ensureWeaponDoctrine(weaponId,
                    squad.name() + " Legacy Weapon Issue", weapons);
            armory.ensureArmorDoctrine(armorId,
                    squad.name() + " Legacy Armor Issue", armor);
            squad.migrateEquipmentDoctrineIds(weaponId, armorId);
        }
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
