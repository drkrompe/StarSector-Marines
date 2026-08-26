package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.EquipmentAccessTier;
import com.dillon.starsectormarines.marine.EquipmentAcquisitionEligibility;
import com.dillon.starsectormarines.marine.EquipmentTemplateCard;
import com.dillon.starsectormarines.marine.EquipmentTemplateCatalog;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.ComputedSignal;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/** Read-only retained projection for the owned-company Armory landing view. */
public final class FleetArmoryOverviewViewModel {

    private static final String PRIMARY_COMPANY_ID = "primary";

    private final MarineRoster roster;
    private final Runnable openPrimaryCompany;
    private final DoubleSupplier currentDay;
    private final Supplier<EquipmentAcquisitionEligibility.Progress> accessProgress;
    private final MutableSignal<Integer> revision;
    private final ComputedSignal<List<CompanyCard>> companyCards;
    private final ComputedSignal<String> fleetSummary;
    private final ComputedSignal<String> templateCollectionSummary;
    private final ComputedSignal<String> accessStatusSummary;
    private final ComputedSignal<String> accessNextSummary;

    public FleetArmoryOverviewViewModel(
            Reactor reactor, MarineRoster roster, Runnable openPrimaryCompany) {
        this(reactor, roster, openPrimaryCompany, () -> 0d,
                EquipmentAcquisitionEligibility::currentProgress);
    }

    public FleetArmoryOverviewViewModel(Reactor reactor, MarineRoster roster,
                                        Runnable openPrimaryCompany,
                                        DoubleSupplier currentDay) {
        this(reactor, roster, openPrimaryCompany, currentDay,
                EquipmentAcquisitionEligibility::currentProgress);
    }

    FleetArmoryOverviewViewModel(Reactor reactor, MarineRoster roster,
                                 Runnable openPrimaryCompany,
                                 DoubleSupplier currentDay,
                                 Supplier<EquipmentAcquisitionEligibility.Progress> accessProgress) {
        if (reactor == null) throw new IllegalArgumentException("reactor is required");
        if (roster == null) throw new IllegalArgumentException("roster is required");
        if (openPrimaryCompany == null) {
            throw new IllegalArgumentException("openPrimaryCompany is required");
        }
        if (currentDay == null) throw new IllegalArgumentException("currentDay is required");
        if (accessProgress == null) {
            throw new IllegalArgumentException("accessProgress is required");
        }
        this.roster = roster;
        this.openPrimaryCompany = openPrimaryCompany;
        this.currentDay = currentDay;
        this.accessProgress = accessProgress;
        revision = reactor.signal(0);
        companyCards = reactor.computed(() -> {
            revision.get();
            return List.of(buildPrimaryCompany());
        });
        fleetSummary = reactor.computed(() -> {
            revision.get();
            CompanyCounts counts = counts();
            return "1 owned company  ·  " + counts.lineSquads + " marine squads  ·  "
                    + counts.mechSquads + " mech squads";
        });
        templateCollectionSummary = reactor.computed(this::buildTemplateCollectionSummary);
        accessStatusSummary = reactor.computed(this::buildAccessStatusSummary);
        accessNextSummary = reactor.computed(this::buildAccessNextSummary);
    }

    public Signal<List<CompanyCard>> companyCards() { return companyCards; }
    public Signal<String> fleetSummary() { return fleetSummary; }
    public Signal<String> templateCollectionSummary() { return templateCollectionSummary; }
    public Signal<String> accessStatusSummary() { return accessStatusSummary; }
    public Signal<String> accessNextSummary() { return accessNextSummary; }

    /** Reprojects mutable campaign authorities whenever the screen is re-entered. */
    public void refresh() {
        revision.update(value -> value + 1);
    }

    private CompanyCard buildPrimaryCompany() {
        CompanyCounts counts = counts();
        Readiness readiness = readiness(counts.readyMarines, counts.authorizedMarines);
        String stationed = counts.stationedSquads == 0
                ? "All formations aboard"
                : counts.stationedSquads + (counts.stationedSquads == 1
                ? " squad stationed" : " squads stationed");
        String base = "company:" + PRIMARY_COMPANY_ID;
        return new CompanyCard(
                base, base + ":designation", base + ":name", base + ":status",
                base + ":marine-squads", base + ":mech-squads", base + ":readiness",
                base + ":recovery", base + ":stationing", base + ":open",
                "company-card " + readiness.cardClass,
                "company-status heading " + readiness.toneClass,
                "FORMATION", "Fleet Marine Company", readiness.label,
                counts.lineSquads + (counts.lineSquads == 1
                        ? " marine squad" : " marine squads"),
                counts.mechSquads + (counts.mechSquads == 1
                        ? " mech squad" : " mech squads"),
                counts.readyMarines + " / " + counts.authorizedMarines + " marines RTD",
                recoverySummary(counts), stationed, "Open Armory", openPrimaryCompany);
    }

    private String buildTemplateCollectionSummary() {
        revision.get();
        int[] total = new int[EquipmentAccessTier.values().length];
        int[] owned = new int[EquipmentAccessTier.values().length];
        for (EquipmentTemplateCard card : EquipmentTemplateCatalog.all()) {
            int tier = card.accessTier().ordinal();
            total[tier]++;
            if (roster.armory().ownsEquipmentTemplate(card.id())) owned[tier]++;
        }
        int totalCards = total[0] + total[1] + total[2];
        int ownedCards = owned[0] + owned[1] + owned[2];
        return "TEMPLATE FILE  ·  " + ownedCards + " / " + totalCards + " known"
                + "  ·  Common " + owned[0] + " / " + total[0]
                + "  ·  Advanced " + owned[1] + " / " + total[1]
                + "  ·  Prestige " + owned[2] + " / " + total[2];
    }

    private String buildAccessStatusSummary() {
        revision.get();
        EquipmentAcquisitionEligibility.Progress progress = currentAccessProgress();
        EquipmentAccessTier licensed = EquipmentAcquisitionEligibility.licensedTier(progress);
        EquipmentAccessTier recovery = EquipmentAcquisitionEligibility.recoveryTier(progress);
        return "CURRENT ACCESS  ·  Licensed / patron " + accessLabel(licensed)
                + " at MRB " + signed(progress.mrbRep())
                + "  ·  Recovery " + accessLabel(recovery) + " at "
                + progress.victories() + " victories  ·  Open market Common only";
    }

    private String buildAccessNextSummary() {
        revision.get();
        EquipmentAcquisitionEligibility.Progress progress = currentAccessProgress();
        EquipmentAccessTier licensed = EquipmentAcquisitionEligibility.licensedTier(progress);
        EquipmentAccessTier recovery = EquipmentAcquisitionEligibility.recoveryTier(progress);
        if (licensed == EquipmentAccessTier.PRESTIGE
                && recovery == EquipmentAccessTier.PRESTIGE) {
            return "All reputation and operational access bands cleared."
                    + "  ·  Licensed stock still requires Favorable faction standing.";
        }
        List<String> next = new ArrayList<>();
        if (licensed != EquipmentAccessTier.PRESTIGE) {
            EquipmentAccessTier target = nextTier(licensed);
            next.add(accessLabel(target) + " licensed / patron at MRB +"
                    + EquipmentAcquisitionEligibility.requiredMrb(target));
        }
        if (recovery != EquipmentAccessTier.PRESTIGE) {
            EquipmentAccessTier target = nextTier(recovery);
            next.add(accessLabel(target) + " recovery after "
                    + EquipmentAcquisitionEligibility.requiredRecoveryVictories(target)
                    + " victories");
        }
        return "NEXT ACCESS  ·  " + String.join("  ·  ", next)
                + "  ·  Licensed stock requires Favorable faction standing";
    }

    private static EquipmentAccessTier nextTier(EquipmentAccessTier tier) {
        return tier == EquipmentAccessTier.COMMON
                ? EquipmentAccessTier.ADVANCED : EquipmentAccessTier.PRESTIGE;
    }

    private EquipmentAcquisitionEligibility.Progress currentAccessProgress() {
        EquipmentAcquisitionEligibility.Progress progress = accessProgress.get();
        return progress != null ? progress : EquipmentAcquisitionEligibility.Progress.OPENING;
    }

    private static String accessLabel(EquipmentAccessTier tier) {
        return switch (tier) {
            case COMMON -> "Common";
            case ADVANCED -> "Advanced";
            case PRESTIGE -> "Prestige";
        };
    }

    private static String signed(int value) {
        return (value < 0 ? "" : "+") + value;
    }

    private CompanyCounts counts() {
        int lineSquads = 0;
        int readyMarines = 0;
        int stationedSquads = 0;
        int woundedMarines = 0;
        float earliestRecovery = Float.POSITIVE_INFINITY;
        for (MarineSquad squad : roster.squads()) {
            if (squad.reserve()) continue;
            lineSquads++;
            readyMarines += roster.readyCount(squad);
            for (MarineSoldier soldier : roster.squadMembers(squad)) {
                if (soldier.status() != MarineSoldierStatus.WIA) continue;
                woundedMarines++;
                earliestRecovery = Math.min(earliestRecovery, soldier.unavailableUntilDay());
            }
            if (squad.stationed()) stationedSquads++;
        }
        return new CompanyCounts(lineSquads, roster.mechBay().squads().size(),
                readyMarines, lineSquads * MarineSquad.CAPACITY, stationedSquads,
                woundedMarines, earliestRecovery);
    }

    private String recoverySummary(CompanyCounts counts) {
        return counts.woundedMarines == 0 ? "No wounded personnel"
                : counts.woundedMarines + " WIA  ·  RTD "
                + FleetArmoryViewModel.formatRemainingCompact(
                counts.earliestRecovery, currentDay.getAsDouble());
    }

    private static Readiness readiness(int ready, int authorized) {
        if (authorized <= 0 || ready <= 0) {
            return new Readiness("STANDING DOWN", "company-card-unready", "tone-danger");
        }
        float ratio = ready / (float) authorized;
        if (ratio >= 1f) return new Readiness("READY", "company-card-ready", "tone-good");
        if (ratio >= 0.75f) {
            return new Readiness("OPERATIONAL", "company-card-operational", "tone-accent");
        }
        return new Readiness("RECONSTITUTING", "company-card-unready", "tone-danger");
    }

    private record CompanyCounts(int lineSquads, int mechSquads, int readyMarines,
                                 int authorizedMarines, int stationedSquads,
                                 int woundedMarines, float earliestRecovery) { }

    private record Readiness(String label, String cardClass, String toneClass) { }

    /** One literal selectable presentation card; not an equipment-template noun. */
    public record CompanyCard(
            String id, String designationId, String nameId, String statusId,
            String marineSquadsId, String mechSquadsId, String readinessId,
            String recoveryId, String stationingId, String openId,
            String classes, String statusClasses,
            String designation, String name, String status, String marineSquads,
            String mechSquads, String readiness, String recovery, String stationing,
            String openLabel,
            Runnable open) implements MarkupPropertySource {

        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "designationId" -> designationId;
                case "nameId" -> nameId;
                case "statusId" -> statusId;
                case "marineSquadsId" -> marineSquadsId;
                case "mechSquadsId" -> mechSquadsId;
                case "readinessId" -> readinessId;
                case "recoveryId" -> recoveryId;
                case "stationingId" -> stationingId;
                case "openId" -> openId;
                case "classes" -> classes;
                case "statusClasses" -> statusClasses;
                case "designation" -> designation;
                case "name" -> name;
                case "status" -> status;
                case "marineSquads" -> marineSquads;
                case "mechSquads" -> mechSquads;
                case "readiness" -> readiness;
                case "recovery" -> recovery;
                case "stationing" -> stationing;
                case "openLabel" -> openLabel;
                case "open" -> open;
                default -> throw new IllegalArgumentException(
                        "Unknown company-card property: " + property);
            };
        }
    }
}
