# Alpha player-facing localization

Status: PLANNED
Written: 2026-10-12

Read `company-view-nouns.md`. `Strings` and the marineOps namespace in
strings.json are the translation authority; translation mods override keys.

- [ ] Inventory player-facing literals in campaign start, company rooms,
  dossiers, tutorials, briefing, results, resource bills and battle guidance.
- [ ] Move prose and labels out of Java and MLX into stable catalog keys;
  format whole translated sentences rather than concatenating English fragments.
- [ ] Keep internal IDs, save values and authored mechanical identifiers stable.
- [ ] Update headless fixtures to load the same catalog, with missing-key and
  placeholder checks and a translated/expanded-copy layout pass.
- [ ] Validate apostrophes, plural/quantity phrasing and variable names/costs.
- [ ] No untranslated player-facing text in the selected Alpha campaign route.

New Castle campaign lessons must satisfy this now. Existing broader UI and
story narration require the remaining sweep before Alpha; do not present the
tutorial slice as full-project localization compliance.
