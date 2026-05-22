package com.dillon.starsectormarines.ops.intel;

import java.util.List;

/**
 * Static map from industry id to the mission archetypes that industry offers.
 * The mission generator walks a planet's industries, looks them up here, and emits
 * one mission per (industry, archetype) pair it picks.
 *
 * <p>Content lives in {@code mod/data/marines/industry_missions.json} and is
 * loaded by {@link IndustryMissionTemplates}. Content authors (and translation
 * mods) edit the JSON, not this class — the catalog is a thin facade kept for
 * call-site stability.
 *
 * <p>Not every industry is represented — Population, Waystation, Aquaculture,
 * Farming, Cryosanctum, etc. are deliberately omitted as too low-value or too
 * narrative-specific for the generic pipeline. Add entries to the JSON as
 * gameplay grows into them.
 *
 * <p>Orbital/military fortifications aren't keyed individually — they raise the
 * planet's {@code DefenseLevel} score instead of producing their own mission
 * strands. Their gameplay weight is "ambient difficulty," not "go hit the Star
 * Fortress directly."
 */
public final class IndustryMissionCatalog {

    private IndustryMissionCatalog() {}

    public static List<MissionArchetype> archetypesFor(String industryId) {
        return IndustryMissionTemplates.forIndustry(industryId);
    }
}
