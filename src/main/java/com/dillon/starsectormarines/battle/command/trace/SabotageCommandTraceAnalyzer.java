package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.command.trace.CommandTraceAnalyzer.FactionMetrics;
import com.dillon.starsectormarines.battle.command.trace.CommandTraceAnalyzer.RunMetrics;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/** Mission-owned analysis of named-site Sabotage command evidence. */
public final class SabotageCommandTraceAnalyzer {

    private SabotageCommandTraceAnalyzer() { }

    public record CoverageMetrics(
            int commandSamples,
            int eligibleForceSamples,
            int minimumPlanterOrRetrieverSites,
            int maximumPlanterOrRetrieverSites,
            int minimumSecuritySites,
            int maximumSecuritySites,
            int samplesWithEveryUnfinishedSiteSupported) { }

    public record RoleTransitionMetrics(
            int planterEntries,
            int planterExits,
            int retrieverEntries,
            int retrieverExits,
            int retrieverToPlanter,
            int planterToRetriever,
            int sitePlanterLosses,
            int siteRecoveryStarts,
            int siteRecoverySupportChanges,
            int siteRecoveryToPlanter) { }

    public record SiteMetrics(
            String siteId,
            int planterSquadPulses,
            int retrieverSquadPulses,
            int securitySquadPulses,
            int reinforcingSquadPulses,
            int maximumProgressBasisPoints,
            int completionTick) { }

    public record SabotageMetrics(
            int siteCount,
            int completedSites,
            CoverageMetrics coverage,
            RoleTransitionMetrics roleTransitions,
            Map<String, SiteMetrics> sites) {

        public SabotageMetrics {
            sites = Collections.unmodifiableMap(new TreeMap<>(sites));
        }
    }

    public record Analysis(
            RunMetrics run,
            FactionMetrics marineCommand,
            SabotageMetrics sabotage) {

        /** Stable, timestamp-free summary bytes for replay comparison. */
        public String canonicalJson() {
            StringBuilder out = new StringBuilder(2_048).append('{');
            nullableStringField(out, "fixtureKind", run.fixtureKind(), false);
            stringField(out, "schedulerMode", run.schedulerMode(), true);
            numberField(out, "startTick", run.startTick());
            numberField(out, "endTick", run.endTick());
            numberField(out, "durationTicks", run.durationTicks());
            stringField(out, "termination", run.termination().name(), true);
            nullableStringField(out, "winner", run.winner());
            numberField(out, "observationWindows", run.observationWindows());
            out.append(",\"combatantCasualties\":{");
            rawNumberField(out, "MARINE", run.combatantCasualties()
                    .getOrDefault(Faction.MARINE, 0));
            numberField(out, "DEFENDER", run.combatantCasualties()
                    .getOrDefault(Faction.DEFENDER, 0));
            out.append('}');

            out.append(",\"directiveChurn\":{");
            rawNumberField(out, "retargets", marineCommand.retargets());
            numberField(out, "releases", marineCommand.releases());
            numberField(out, "reissues", marineCommand.reissues());
            numberField(out, "rejectedProposals",
                    marineCommand.rejectedProposals());
            numberField(out, "stabilityHolds", marineCommand.stabilityHolds());
            out.append('}');

            CoverageMetrics coverage = sabotage.coverage();
            out.append(",\"sabotage\":{");
            rawNumberField(out, "siteCount", sabotage.siteCount());
            numberField(out, "completedSites", sabotage.completedSites());
            out.append(",\"coverage\":{");
            rawNumberField(out, "commandSamples", coverage.commandSamples());
            numberField(out, "eligibleForceSamples",
                    coverage.eligibleForceSamples());
            numberField(out, "minimumPlanterOrRetrieverSites",
                    coverage.minimumPlanterOrRetrieverSites());
            numberField(out, "maximumPlanterOrRetrieverSites",
                    coverage.maximumPlanterOrRetrieverSites());
            numberField(out, "minimumSecuritySites",
                    coverage.minimumSecuritySites());
            numberField(out, "maximumSecuritySites",
                    coverage.maximumSecuritySites());
            numberField(out, "samplesWithEveryUnfinishedSiteSupported",
                    coverage.samplesWithEveryUnfinishedSiteSupported());
            out.append('}');

            RoleTransitionMetrics transitions = sabotage.roleTransitions();
            out.append(",\"roleTransitions\":{");
            rawNumberField(out, "planterEntries", transitions.planterEntries());
            numberField(out, "planterExits", transitions.planterExits());
            numberField(out, "retrieverEntries", transitions.retrieverEntries());
            numberField(out, "retrieverExits", transitions.retrieverExits());
            numberField(out, "retrieverToPlanter",
                    transitions.retrieverToPlanter());
            numberField(out, "planterToRetriever",
                    transitions.planterToRetriever());
            numberField(out, "sitePlanterLosses",
                    transitions.sitePlanterLosses());
            numberField(out, "siteRecoveryStarts",
                    transitions.siteRecoveryStarts());
            numberField(out, "siteRecoverySupportChanges",
                    transitions.siteRecoverySupportChanges());
            numberField(out, "siteRecoveryToPlanter",
                    transitions.siteRecoveryToPlanter());
            out.append('}');

            out.append(",\"sites\":{");
            boolean first = true;
            for (SiteMetrics site : sabotage.sites().values()) {
                if (!first) out.append(',');
                first = false;
                string(out, site.siteId());
                out.append(":{");
                rawNumberField(out, "planterSquadPulses",
                        site.planterSquadPulses());
                numberField(out, "retrieverSquadPulses",
                        site.retrieverSquadPulses());
                numberField(out, "securitySquadPulses",
                        site.securitySquadPulses());
                numberField(out, "reinforcingSquadPulses",
                        site.reinforcingSquadPulses());
                numberField(out, "maximumProgressBasisPoints",
                        site.maximumProgressBasisPoints());
                numberField(out, "completionTick", site.completionTick());
                out.append('}');
            }
            return out.append("}}}\n").toString();
        }
    }

    public static Analysis analyze(String jsonLines) throws Exception {
        CommandTraceAnalyzer.Analysis common =
                CommandTraceAnalyzer.analyze(jsonLines);
        if (common.run().fixtureKind() != null
                && !"SABOTAGE".equals(common.run().fixtureKind())) {
            throw new IllegalArgumentException(
                    "Sabotage evidence requires a SABOTAGE fixture");
        }
        FactionMetrics marine = common.factions().get(Faction.MARINE);
        if (marine == null) {
            throw new IllegalArgumentException(
                    "Sabotage evidence requires Marine perspective rows");
        }

        TreeMap<String, MutableSite> sites = new TreeMap<>();
        Map<Integer, String> previousRoles = new HashMap<>();
        Map<String, String> previousGroupReasons = new HashMap<>();
        int commandSamples = 0;
        int eligibleForceSamples = 0;
        int minCapability = Integer.MAX_VALUE;
        int maxCapability = 0;
        int minSecurity = Integer.MAX_VALUE;
        int maxSecurity = 0;
        int fullySupported = 0;
        int planterEntries = 0;
        int planterExits = 0;
        int retrieverEntries = 0;
        int retrieverExits = 0;
        int retrieverToPlanter = 0;
        int planterToRetriever = 0;
        int sitePlanterLosses = 0;
        int siteRecoveryStarts = 0;
        int siteRecoverySupportChanges = 0;
        int siteRecoveryToPlanter = 0;

        for (String line : jsonLines.split("\\R")) {
            if (line.isBlank()) continue;
            JSONObject row = new JSONObject(line);
            String stream = row.getString("stream");
            if ("control".equals(stream)) {
                previousRoles.clear();
                previousGroupReasons.clear();
                continue;
            }
            if ("referee".equals(stream)
                    && "charge-site-state".equals(row.optString("event"))) {
                MutableSite site = sites.computeIfAbsent(
                        row.getString("siteId"), MutableSite::new);
                site.maximumProgress = Math.max(site.maximumProgress,
                        row.getInt("progressBasisPoints"));
                if (row.getBoolean("complete") && site.completionTick < 0) {
                    site.completionTick = row.getInt("tick");
                }
                continue;
            }
            if (!"perspective".equals(stream)
                    || !Faction.MARINE.name().equals(
                    row.optString("perspective"))) continue;
            JSONObject sabotage = row.optJSONObject("sabotage");
            if (sabotage == null) continue;
            commandSamples++;

            Map<Integer, String> siteIds = new HashMap<>();
            int unfinished = 0;
            int capabilitySites = 0;
            int securitySites = 0;
            JSONArray siteRows = sabotage.getJSONArray("sites");
            for (int i = 0; i < siteRows.length(); i++) {
                JSONObject siteRow = siteRows.getJSONObject(i);
                String siteId = siteRow.getString("id");
                siteIds.put(siteRow.getInt("index"), siteId);
                sites.computeIfAbsent(siteId, MutableSite::new);
                String groupReason = siteRow.optString("groupReason", "");
                String priorReason = previousGroupReasons.put(
                        siteId, groupReason);
                if (priorReason != null && !priorReason.equals(groupReason)) {
                    if ("PLANTER_ACTIVE".equals(priorReason)
                            && !"PLANTER_ACTIVE".equals(groupReason)) {
                        sitePlanterLosses++;
                    }
                    if (!isRecovery(priorReason) && isRecovery(groupReason)) {
                        siteRecoveryStarts++;
                    }
                    if (isRecovery(priorReason) && isRecovery(groupReason)) {
                        siteRecoverySupportChanges++;
                    }
                    if (isRecovery(priorReason)
                            && "PLANTER_ACTIVE".equals(groupReason)) {
                        siteRecoveryToPlanter++;
                    }
                }
                if (siteRow.getBoolean("complete")) continue;
                unfinished++;
                boolean capability = siteRow.getInt("planterSquads") > 0
                        || siteRow.getInt("retrieverSquads") > 0;
                boolean security = siteRow.getInt("securitySquads") > 0;
                if (capability) capabilitySites++;
                if (security) securitySites++;
            }
            if (unfinished > 0 && row.getInt("commandPoolSize") > 0) {
                eligibleForceSamples++;
                minCapability = Math.min(minCapability, capabilitySites);
                maxCapability = Math.max(maxCapability, capabilitySites);
                minSecurity = Math.min(minSecurity, securitySites);
                maxSecurity = Math.max(maxSecurity, securitySites);
                if (capabilitySites == unfinished && securitySites == unfinished) {
                    fullySupported++;
                }
            }

            Map<Integer, String> currentRoles = new HashMap<>();
            JSONArray actions = sabotage.getJSONArray("actions");
            for (int i = 0; i < actions.length(); i++) {
                JSONObject action = actions.getJSONObject(i);
                int squadId = action.getInt("squadId");
                String role = action.getString("groupRole");
                currentRoles.put(squadId, role);
                String siteId = siteIds.get(action.getInt("siteIndex"));
                MutableSite site = siteId != null ? sites.get(siteId) : null;
                if (site != null) site.record(role);

                String prior = previousRoles.get(squadId);
                if (prior == null || prior.equals(role)) continue;
                if ("PLANTER".equals(role)) planterEntries++;
                if ("PLANTER".equals(prior)) planterExits++;
                if ("KIT_RETRIEVER".equals(role)) retrieverEntries++;
                if ("KIT_RETRIEVER".equals(prior)) retrieverExits++;
                if ("KIT_RETRIEVER".equals(prior)
                        && "PLANTER".equals(role)) retrieverToPlanter++;
                if ("PLANTER".equals(prior)
                        && "KIT_RETRIEVER".equals(role)) planterToRetriever++;
            }
            for (Map.Entry<Integer, String> previous : previousRoles.entrySet()) {
                if (currentRoles.containsKey(previous.getKey())) continue;
                if ("PLANTER".equals(previous.getValue())) planterExits++;
                if ("KIT_RETRIEVER".equals(previous.getValue())) retrieverExits++;
            }
            previousRoles = currentRoles;
        }

        TreeMap<String, SiteMetrics> immutableSites = new TreeMap<>();
        int completed = 0;
        for (MutableSite site : sites.values()) {
            if (site.completionTick >= 0) completed++;
            immutableSites.put(site.siteId, site.freeze());
        }
        CoverageMetrics coverage = new CoverageMetrics(commandSamples,
                eligibleForceSamples,
                minCapability == Integer.MAX_VALUE ? 0 : minCapability,
                maxCapability,
                minSecurity == Integer.MAX_VALUE ? 0 : minSecurity,
                maxSecurity, fullySupported);
        RoleTransitionMetrics transitions = new RoleTransitionMetrics(
                planterEntries, planterExits, retrieverEntries, retrieverExits,
                retrieverToPlanter, planterToRetriever, sitePlanterLosses,
                siteRecoveryStarts, siteRecoverySupportChanges,
                siteRecoveryToPlanter);
        SabotageMetrics metrics = new SabotageMetrics(sites.size(), completed,
                coverage, transitions, immutableSites);
        return new Analysis(common.run(), marine, metrics);
    }

    private static boolean isRecovery(String reason) {
        return "KIT_RECOVERY_ASSIGNED".equals(reason)
                || "KIT_RECOVERY_UNSUPPORTED".equals(reason);
    }

    private static final class MutableSite {
        final String siteId;
        int planterPulses;
        int retrieverPulses;
        int securityPulses;
        int reinforcingPulses;
        int maximumProgress;
        int completionTick = -1;

        private MutableSite(String siteId) {
            this.siteId = siteId;
        }

        void record(String role) {
            switch (role) {
                case "PLANTER" -> planterPulses++;
                case "KIT_RETRIEVER" -> retrieverPulses++;
                case "SECURITY" -> securityPulses++;
                case "REINFORCING" -> reinforcingPulses++;
                default -> { }
            }
        }

        SiteMetrics freeze() {
            return new SiteMetrics(siteId, planterPulses, retrieverPulses,
                    securityPulses, reinforcingPulses, maximumProgress,
                    completionTick);
        }
    }

    private static void rawNumberField(StringBuilder out, String name,
                                       int value) {
        string(out, name);
        out.append(':').append(value);
    }

    private static void numberField(StringBuilder out, String name, int value) {
        out.append(',');
        rawNumberField(out, name, value);
    }

    private static void stringField(StringBuilder out, String name,
                                    String value, boolean comma) {
        if (comma) out.append(',');
        string(out, name);
        out.append(':');
        string(out, value);
    }

    private static void nullableStringField(StringBuilder out, String name,
                                            String value) {
        nullableStringField(out, name, value, true);
    }

    private static void nullableStringField(StringBuilder out, String name,
                                            String value, boolean comma) {
        if (comma) out.append(',');
        string(out, name);
        out.append(':');
        if (value == null) out.append("null");
        else string(out, value);
    }

    private static void string(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        out.append('"');
    }
}
