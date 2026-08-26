package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.AbandonedColonyArchiveOutcome;
import com.dillon.starsectormarines.campaign.CampaignClock;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.ChainState;
import com.dillon.starsectormarines.campaign.ChronicleConfidence;
import com.dillon.starsectormarines.campaign.CompanyAssessmentComposer;
import com.dillon.starsectormarines.campaign.CompanyClocks;
import com.dillon.starsectormarines.campaign.CompanyNews;
import com.dillon.starsectormarines.campaign.CompanyRating;
import com.dillon.starsectormarines.campaign.CompanyStanding;
import com.dillon.starsectormarines.campaign.OfficerMoodReader;
import com.dillon.starsectormarines.campaign.PlayerEventNotice;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.event.PlayerEventTarget;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;

import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Display-ready, read-only projection for the retained Company HQ dashboard. */
final class CompanyHqViewModel {

    private static final int EMPLOYER_LIMIT = 4;
    private static final int NEWS_LIMIT = 5;
    private static final int OBLIGATION_LIMIT = 6;
    private static final int URGENT_DAYS = 2;
    private static final int SOON_DAYS = 7;

    private final Map<String, Object> props;

    private CompanyHqViewModel(Map<String, Object> props) {
        this.props = Map.copyOf(props);
    }

    Map<String, Object> props() {
        return new LinkedHashMap<>(props);
    }

    static CompanyHqViewModel current(
            Runnable openBarracks,
            Runnable openArmory,
            Runnable openMechLab,
            Runnable close,
            Function<CompanyClocks.Entry, Runnable> respond) {
        int day = CampaignClock.day();
        CompanyStanding standing = CompanyStanding.current(EMPLOYER_LIMIT);
        List<CompanyClocks.Entry> clocks = CompanyClocks.current();
        CampaignStateScript script = CampaignStateScript.getInstance();
        CampaignState state = script != null ? script.state() : null;
        List<CompanyNews.Entry> news = CompanyNews.latest(
                state, day, NEWS_LIMIT, PlayerEventTarget::displayName);
        return build(standing, clocks, news, day, openBarracks, openArmory,
                openMechLab, close, respond);
    }

    private static CompanyHqViewModel build(
            CompanyStanding standing,
            List<CompanyClocks.Entry> clocks,
            List<CompanyNews.Entry> news,
            int day,
            Runnable openBarracks,
            Runnable openArmory,
            Runnable openMechLab,
            Runnable close,
            Function<CompanyClocks.Entry, Runnable> respond) {
        Map<String, Object> props = baseLabels();
        NumberFormat credits = NumberFormat.getIntegerInstance();
        CompanyRating rating = CompanyRating.fromMrbCredibility(
                standing.finances.mrbRep);
        props.put("rating", ratingLabel(rating));
        props.put("ratingScore", MessageFormat.format(
                Strings.get("companyHqRatingScore"),
                signedInt(standing.finances.mrbRep)));
        props.put("assessment", CompanyAssessmentComposer.render(
                standing.mood(), day, rating, standing.available, standing.strength,
                standing.lineSquads, standing.wounded, standing.stationed));

        props.put("statusCards", statusCards(standing));
        props.put("runway", runwayText(standing.finances.runwayMonths()));
        props.put("runwayClasses", runwayClasses(standing.finances.runwayMonths()));
        props.put("financeRows", financeRows(standing, credits));
        props.put("employers", employerRows(standing.employers));
        props.put("employerEmpty", standing.employers.isEmpty()
                ? Strings.get("companyHqNoEmployers") : "");

        int obligationCount = Math.min(clocks.size(), OBLIGATION_LIMIT);
        List<ObligationPost> obligations = new ArrayList<>();
        for (int index = 0; index < obligationCount; index++) {
            CompanyClocks.Entry entry = clocks.get(index);
            obligations.add(obligationPost(index, entry, day, respond.apply(entry)));
        }
        props.put("obligations", List.copyOf(obligations));
        props.put("obligationEmpty", clocks.isEmpty()
                ? Strings.get("companyHqClocksEmpty")
                : clocks.size() > obligationCount
                    ? MessageFormat.format(Strings.get("companyHqClocksMore"),
                            clocks.size() - obligationCount)
                    : "");

        List<NewsPost> newsPosts = new ArrayList<>();
        for (int index = 0; index < news.size(); index++) {
            newsPosts.add(newsPost(index, news.get(index), day));
        }
        props.put("newsPosts", List.copyOf(newsPosts));
        props.put("newsEmpty", news.isEmpty()
                ? Strings.get("companyHqNewsEmpty") : "");

        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.HQ, close,
                () -> { }, openBarracks, openArmory, openMechLab);
        return new CompanyHqViewModel(props);
    }

    /** Rich deterministic fixture used by the retained UI evidence suite. */
    static CompanyHqViewModel preview() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("purpose", "FLAGSHIP  /  BRIDGE  /  COMMAND NETWORK");
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.HQ,
                () -> { }, () -> { }, () -> { }, () -> { }, () -> { });
        props.put("assessmentHeader", "BRIDGE ADJUTANT  //  DAILY ASSESSMENT");
        props.put("ratingHeader", "MERCENARY RATING");
        props.put("rating", "RECOGNIZED");
        props.put("ratingScore", "MRB credibility  +24");
        props.put("assessment", "Recognized on the MRB file. 31 of 36 ready across 3 squads. That's the force available today.");
        props.put("statusHeader", "FORCE STATUS");
        props.put("financeHeader", "COMPANY FINANCE");
        props.put("standingHeader", "CLIENT STANDING");
        props.put("boardHeader", "SITUATION BOARD");
        props.put("boardIntro", "Obligations that can hurt the company, followed by confirmed reports and rumors already learned.");
        props.put("obligationsHeader", "ACTIVE OBLIGATIONS");
        props.put("newsHeader", "SECTOR POSTS");
        props.put("runway", "8.4 MONTHS");
        props.put("runwayCaption", "payroll runway  /  against last month's upkeep");
        props.put("runwayClasses", "runway-value tone-accent");
        props.put("statusCards", List.of(
                new StatCard("preview-ready", "stat-card good-surface", "31 / 36", "READY / ON BOOKS", "5 unavailable today"),
                new StatCard("preview-squads", "stat-card", "3", "LINE SQUADS", "2 officers  /  1 stationed"),
                new StatCard("preview-recovery", "stat-card", "2 WIA", "RECOVERY", "next return in 3 days"),
                new StatCard("preview-fleet", "stat-card", "7", "FLEET HULLS", "lift and support inventory")));
        props.put("financeRows", List.of(
                new InfoRow("preview-cash", "", "On hand", "Cr. 486,200"),
                new InfoRow("preview-upkeep", "", "Last upkeep", "Cr. 57,900"),
                new InfoRow("preview-net", "tone-good", "Last net", "+Cr. 18,400"),
                new InfoRow("preview-retainer", "tone-good", "Retainers", "Cr. 12,000 / mo")));
        props.put("employers", List.of(
                new EmployerRow("preview-employer-1", "", "Kazeron Compact", "+42", "5 completed  /  0 failed"),
                new EmployerRow("preview-employer-2", "", "Sindrian Diktat", "+18", "2 completed  /  1 failed"),
                new EmployerRow("preview-employer-3", "tone-danger", "House Umbra", "-6", "1 completed  /  2 failed")));
        props.put("employerEmpty", "");
        props.put("obligations", List.of(
                new ObligationPost("preview-obligation-1", "post-card danger-surface", "DUE IN 1 DAY", "Garrison defense", "Sindria  /  House Kardos  /  FAILS IF MISSED", "RESPOND", false, () -> { }),
                new ObligationPost("preview-obligation-2", "post-card", "12 DAYS", "Stationing term ends", "Kazeron  /  Compact Security Board", "TRACKING", true, () -> { })));
        props.put("obligationEmpty", "");
        props.put("newsPosts", List.of(
                new NewsPost("preview-news-1", "news-card", "CONFIRMED  /  TODAY", "House Cavor displaces House Tobin", "Eochu Bres  /  learned through company channels"),
                new NewsPost("preview-news-2", "news-card rumor-surface", "RUMOR  /  4 DAYS AGO", "Reports of Compact action against House Umbra", "Kazeron  /  outcome remains unconfirmed"),
                new NewsPost("preview-news-3", "news-card", "CONFIRMED  /  11 DAYS AGO", "Silent colony operation concluded", "17 of 22 survivors recovered  /  archive secured")));
        props.put("newsEmpty", "");
        return new CompanyHqViewModel(props);
    }

    private static Map<String, Object> baseLabels() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("purpose", Strings.get("companyHqPurpose"));
        props.put("assessmentHeader", Strings.get("companyHqAssessmentHeader"));
        props.put("ratingHeader", Strings.get("companyHqRatingHeader"));
        props.put("statusHeader", Strings.get("companyHqRosterHeader"));
        props.put("financeHeader", Strings.get("companyHqFinanceHeader"));
        props.put("standingHeader", Strings.get("companyHqStandingHeader"));
        props.put("boardHeader", Strings.get("companyHqBoardHeader"));
        props.put("boardIntro", Strings.get("companyHqBoardIntro"));
        props.put("obligationsHeader", Strings.get("companyHqClocksHeader"));
        props.put("newsHeader", Strings.get("companyHqNewsHeader"));
        props.put("runwayCaption", Strings.get("companyHqRunwayCaption"));
        return props;
    }

    private static List<StatCard> statusCards(CompanyStanding standing) {
        String readinessClasses = standing.available <= 0
                ? "stat-card danger-surface"
                : standing.available == standing.strength
                    ? "stat-card good-surface" : "stat-card";
        String recovery = standing.wounded <= 0
                ? Strings.get("companyHqRecoveryClear")
                : recoveryText(standing);
        return List.of(
                new StatCard("hq-stat-ready", readinessClasses,
                        standing.available + " / " + standing.strength,
                        Strings.get("companyHqReadyLabel"),
                        MessageFormat.format(Strings.get("companyHqUnavailableShort"),
                                standing.unavailable)),
                new StatCard("hq-stat-squads", "stat-card",
                        Integer.toString(standing.lineSquads),
                        Strings.get("companyHqSquadsLabel"),
                        MessageFormat.format(Strings.get("companyHqOfficersShort"),
                                standing.finances.activeCaptains, standing.stationedSquads)),
                new StatCard("hq-stat-recovery", standing.wounded > 0
                        ? "stat-card" : "stat-card good-surface",
                        standing.wounded > 0 ? standing.wounded + " WIA" : "CLEAR",
                        Strings.get("companyHqRecoveryLabel"), recovery),
                new StatCard("hq-stat-fleet", "stat-card",
                        Integer.toString(standing.finances.ships),
                        Strings.get("companyHqFleetLabel"),
                        Strings.get("companyHqFleetDetail")));
    }

    private static List<InfoRow> financeRows(CompanyStanding standing,
                                              NumberFormat credits) {
        List<InfoRow> rows = new ArrayList<>();
        rows.add(new InfoRow("hq-finance-cash", "", Strings.get("companyHqOnHandLabel"),
                "Cr. " + credits.format((long) standing.finances.credits)));
        if (standing.finances.hasMonthlyReport) {
            rows.add(new InfoRow("hq-finance-upkeep", "", Strings.get("companyHqUpkeepLabel"),
                    "Cr. " + credits.format((long) standing.finances.upkeepLastMonth)));
            float net = standing.finances.netLastMonth;
            rows.add(new InfoRow("hq-finance-net", net < 0f ? "tone-danger" : "tone-good",
                    Strings.get("companyHqNetLabel"), signedCredits(credits, net)));
        } else {
            rows.add(new InfoRow("hq-finance-report", "tone-muted",
                    Strings.get("companyHqReportLabel"), Strings.get("companyHqRunwayUnknown")));
        }
        if (standing.finances.debt > 0) {
            rows.add(new InfoRow("hq-finance-debt", "tone-danger",
                    Strings.get("companyHqDebtLabel"),
                    "Cr. " + credits.format(standing.finances.debt)));
        }
        rows.add(new InfoRow("hq-finance-retainers",
                standing.stationingContracts > 0 ? "tone-good" : "tone-muted",
                Strings.get("companyHqRetainerLabel"),
                standing.stationingContracts > 0
                        ? MessageFormat.format(Strings.get("companyHqRetainerValue"),
                                credits.format(standing.retainerPerMonth),
                                standing.stationingContracts)
                        : Strings.get("companyHqRetainerNone")));
        return List.copyOf(rows);
    }

    private static List<EmployerRow> employerRows(
            List<CompanyStanding.Employer> employers) {
        List<EmployerRow> rows = new ArrayList<>();
        for (int index = 0; index < employers.size(); index++) {
            CompanyStanding.Employer employer = employers.get(index);
            rows.add(new EmployerRow("hq-employer-" + index,
                    employer.reputation < 0 ? "tone-danger" : "",
                    employer.name, signedInt(employer.reputation),
                    MessageFormat.format(Strings.get("companyHqEmployerRecord"),
                            employer.contractsCompleted, employer.contractsFailed)));
        }
        return List.copyOf(rows);
    }

    private static ObligationPost obligationPost(int index, CompanyClocks.Entry entry,
                                                  int day, Runnable respond) {
        int days = entry.daysRemaining(day);
        String classes = days <= URGENT_DAYS ? "post-card danger-surface"
                : days <= SOON_DAYS ? "post-card warning-surface" : "post-card";
        String headline = entry.kind == CompanyClocks.Kind.TERM_ENDING
                ? Strings.get("companyHqClockTerm")
                : entry.notice != null
                    && entry.notice.kind == PlayerEventNotice.Kind.CADRE_INCIDENT
                        ? Strings.get("companyHqClockCadre")
                        : Strings.get("companyHqClockGarrison");
        String market = PlayerEventTarget.displayName(entry.marketId);
        String detail = join(market, entry.patronName);
        if (entry.failsOnExpiry) {
            detail = join(detail, Strings.get("companyHqClockTermFailsShort"));
        }
        boolean actionable = entry.kind == CompanyClocks.Kind.RESPONSE && entry.notice != null;
        return new ObligationPost("hq-obligation-" + index, classes,
                daysText(days), headline, detail,
                actionable ? Strings.get("companyHqClockRespond")
                        : Strings.get("companyHqClockTracking"),
                !actionable, respond);
    }

    private static NewsPost newsPost(int index, CompanyNews.Entry entry, int day) {
        int age = Math.max(0, day - entry.happenedDay());
        String confidence = entry.confidence() == ChronicleConfidence.RUMOR
                ? Strings.get("companyHqNewsRumor")
                : Strings.get("companyHqNewsConfirmed");
        String kicker = confidence + "  /  " + ageText(age);
        String actor = fallback(entry.actorName(), Strings.get("companyHqNewsUnknownHouse"));
        String target = fallback(entry.targetName(), Strings.get("companyHqNewsUnknownHouse"));
        String headline;
        String detail = fallback(entry.marketName(), Strings.get("companyHqNewsSectorSource"));
        switch (entry.type()) {
            case ACTIVE_CHAIN_RUMOR -> headline = MessageFormat.format(
                    Strings.get("companyHqNewsChainRumor"), actor, target);
            case CHAIN_OUTCOME -> headline = MessageFormat.format(
                    entry.chainOutcome() == ChainState.FAILED
                            ? Strings.get("companyHqNewsChainFailed")
                            : Strings.get("companyHqNewsChainResolved"), actor, target);
            case HOUSE_DORMANT -> headline = MessageFormat.format(
                    Strings.get("companyHqNewsHouseDormant"), actor);
            case THRONE_CLAIM_APPLIED -> headline = MessageFormat.format(
                    Strings.get("companyHqNewsThroneClaim"), actor, target);
            case KINGMAKER_TESTAMENT -> headline = MessageFormat.format(
                    Strings.get("companyHqNewsTestament"), actor, target);
            case SILENT_COLONY -> {
                headline = Strings.get("companyHqNewsSilentColony");
                detail = MessageFormat.format(Strings.get("companyHqNewsSurvivors"),
                        Math.max(0, entry.survivorsRescued()),
                        Math.max(0, entry.survivorsAtRisk()),
                        archiveLabel(entry.archiveOutcome()));
            }
            default -> throw new IllegalStateException("Unhandled Chronicle event " + entry.type());
        }
        return new NewsPost("hq-news-" + index,
                entry.confidence() == ChronicleConfidence.RUMOR
                        ? "news-card rumor-surface" : "news-card",
                kicker, headline, detail);
    }

    private static String archiveLabel(AbandonedColonyArchiveOutcome outcome) {
        return switch (outcome) {
            case RECOVERED -> Strings.get("companyHqNewsArchiveRecovered");
            case LOST -> Strings.get("companyHqNewsArchiveLost");
            case NONE -> Strings.get("companyHqNewsArchiveUnknown");
        };
    }

    private static String ratingLabel(CompanyRating rating) {
        return Strings.get(switch (rating) {
            case COMPROMISED -> "companyHqRatingCompromised";
            case UNPROVEN -> "companyHqRatingUnproven";
            case PROVISIONAL -> "companyHqRatingProvisional";
            case RECOGNIZED -> "companyHqRatingRecognized";
            case ESTABLISHED -> "companyHqRatingEstablished";
            case PREMIER -> "companyHqRatingPremier";
        });
    }

    private static String runwayText(float months) {
        return months < 0f ? Strings.get("companyHqRunwayUnknown")
                : MessageFormat.format(Strings.get("companyHqRunwayCompact"),
                        String.format("%.1f", months));
    }

    private static String runwayClasses(float months) {
        if (months < 0f) return "runway-value tone-muted";
        if (months < OfficerMoodReader.DESPERATE_RUNWAY_MONTHS) {
            return "runway-value tone-danger";
        }
        if (months < OfficerMoodReader.SEASONED_RUNWAY_MONTHS) {
            return "runway-value tone-accent";
        }
        return "runway-value tone-good";
    }

    private static String recoveryText(CompanyStanding standing) {
        if (standing.wounded <= 0 || standing.nextRecoveryDay < 0f) {
            return Strings.get("companyHqRecoveryClear");
        }
        int days = (int) Math.ceil(standing.nextRecoveryDay - CampaignClock.dayFloat());
        if (days <= 0) return Strings.get("companyHqRecoveryToday");
        if (days == 1) return Strings.get("companyHqRecoveryOneDay");
        return MessageFormat.format(Strings.get("companyHqRecoveryDays"), days);
    }

    private static String daysText(int days) {
        if (days <= 0) return Strings.get("companyHqClockDue");
        if (days == 1) return Strings.get("companyHqClockOneDay");
        return MessageFormat.format(Strings.get("companyHqClockDays"), days);
    }

    private static String ageText(int days) {
        if (days <= 0) return Strings.get("companyHqNewsToday");
        if (days == 1) return Strings.get("companyHqNewsOneDayAgo");
        return MessageFormat.format(Strings.get("companyHqNewsDaysAgo"), days);
    }

    private static String signedCredits(NumberFormat format, float value) {
        return (value < 0f ? "-" : "+") + "Cr. "
                + format.format((long) Math.abs(value));
    }

    private static String signedInt(int value) {
        return (value < 0 ? "" : "+") + value;
    }

    private static String join(String first, String second) {
        if (first == null || first.isBlank()) return second == null ? "" : second;
        if (second == null || second.isBlank()) return first;
        return first + "  /  " + second;
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    record StatCard(String id, String classes, String value, String label,
                    String detail) implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "classes" -> classes; case "value" -> value;
                case "label" -> label; case "detail" -> detail;
                case "valueId" -> id + ":value"; case "labelId" -> id + ":label";
                case "detailId" -> id + ":detail";
                default -> throw new IllegalArgumentException("Unknown stat-card property: " + property);
            };
        }
    }

    record InfoRow(String id, String classes, String label,
                   String value) implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "classes" -> classes; case "label" -> label;
                case "value" -> value;
                case "labelId" -> id + ":label"; case "valueId" -> id + ":value";
                case "valueClasses" -> "label info-value " + classes;
                default -> throw new IllegalArgumentException("Unknown info-row property: " + property);
            };
        }
    }

    record EmployerRow(String id, String classes, String name, String score,
                       String record) implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "classes" -> classes; case "name" -> name;
                case "score" -> score; case "record" -> record;
                case "nameId" -> id + ":name"; case "scoreId" -> id + ":score";
                case "recordId" -> id + ":record";
                case "scoreClasses" -> "label employer-score " + classes;
                default -> throw new IllegalArgumentException("Unknown employer-row property: " + property);
            };
        }
    }

    record ObligationPost(String id, String classes, String kicker, String headline,
                          String detail, String actionLabel, boolean disabled,
                          Runnable action) implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "classes" -> classes; case "kicker" -> kicker;
                case "headline" -> headline; case "detail" -> detail;
                case "actionLabel" -> actionLabel; case "disabled" -> disabled;
                case "action" -> action;
                case "bodyId" -> id + ":body"; case "kickerId" -> id + ":kicker";
                case "headlineId" -> id + ":headline"; case "detailId" -> id + ":detail";
                case "actionId" -> id + ":action";
                default -> throw new IllegalArgumentException("Unknown obligation property: " + property);
            };
        }
    }

    record NewsPost(String id, String classes, String kicker, String headline,
                    String detail) implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "classes" -> classes; case "kicker" -> kicker;
                case "headline" -> headline; case "detail" -> detail;
                case "kickerId" -> id + ":kicker"; case "headlineId" -> id + ":headline";
                case "detailId" -> id + ":detail";
                default -> throw new IllegalArgumentException("Unknown news property: " + property);
            };
        }
    }

}
