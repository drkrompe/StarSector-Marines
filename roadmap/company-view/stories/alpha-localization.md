# Alpha player-facing localization

Status: IMPLEMENTED — catalog sweep complete on the Alpha route; translation review and live acceptance open
Written: 2026-10-12
Updated: 2026-10-21 — squad definitions, mission flow and battle UI migrated; markup guards added

Read `company-view-nouns.md`. `Strings` and the marineOps namespace in
strings.json are the translation authority; translation mods override keys.

- [x] Inventory player-facing literals in campaign start, company rooms,
  dossiers, tutorials, briefing, results, resource bills and battle guidance.
- [x] Move prose and labels out of Java and MLX into stable catalog keys;
  format whole translated sentences rather than concatenating English fragments.
- [x] Keep internal IDs, save values and authored mechanical identifiers stable.
- [x] Update headless fixtures to load the same catalog, with missing-key and
  placeholder checks and a translated/expanded-copy layout pass.
- [x] Validate apostrophes, plural/quantity phrasing and variable names/costs.
- [ ] No untranslated player-facing text in the selected Alpha campaign route
  (needs the live pass below; the catalog side is done).

## Standing rules

- Java reads copy through `Strings.get/format/join/named/resolve`; MLX through
  `{i18n.key}`; data files through `@key` display fields. Variable messages are
  one MessageFormat pattern each, with `choice` for quantities and doubled
  apostrophes in any pattern that takes arguments.
- Built-in text that a save holds stays an English literal in the save and is
  translated when shown, for as long as it still equals the built-in default
  (default squad names, the reserve pool, first-refit plan names, built-in
  billet roles). Player-typed names and copies a player owns are never
  translated. Built-in doctrines, armor plans and fire-team templates are
  reinstalled from code and name `@key` references.
- Commendations are saved as a catalog reference with arguments and formatted
  in the reader's language (`Commendations`); legacy free-text entries display
  exactly as saved.
- Guards (`TranslationSmokeTest`): every key named in Java or MLX exists, every
  argument pattern parses, no argument pattern carries an undoubled apostrophe,
  and player-facing MLX contains no literal copy. Developer markup
  (`ui-workbench.mlx`, `debug-missions.mlx`) and the briefing's debug drawer
  are exempt. Per-surface tests (`OpeningLocalizationTest`,
  `EquipmentLocalizationTest`, `MissionSelectLocalizationTest`,
  `SquadDefinitionLocalizationTest`, `MissionFlowLocalizationTest`,
  `BattleUiLocalizationTest`) check quantity forms and that a translation
  overlay reaches output while names and ids pass through.
- Expanded-copy evidence: `opening-localization`, `equipment-localization` and
  `mission-flow-localization` snapshot suites render English and +50% copy at
  1920x1080 / 100%. They check layout, not a shipped translation.

## Covered surfaces

Opening tutorials and company rooms; shared equipment catalogs and spec sheets;
contract board; squad definitions, billet roles and service records; mission
briefing, pre-battle muster, stationing, results, debrief and salvage; mission
objective names, steps, measures and blockers; battle HUD, order, mech, power,
air and squad overlays, battlefield markers, order cursors and battle comms.
Debug launchers, debug drawers and debug panels are deliberately English.

## Remaining before the gate is green

- Translation review and live opening/re-entry acceptance.
- Expanded-copy layout for compact controls: rarity/firepower badges, long claim
  buttons and Mech Lab header/socket labels. Shared hover sheets wrap.
- Domain display names still read from English fields on some surfaces:
  `CommandPower.displayName`, `OperationTier.displayName` outside the contract
  board and briefing, `ExperienceTier`/`SoldierAptitude` display names outside
  the muster, generated map site, sequence and compound names, and
  `shipHull` names (`Strings.named` falls back to the id spelled out).
- Generated mission data (`Mission.requirements`, `Mission.flavor`) and
  `MissionOutcome.smallWarOverhead` are composed in English when generated and
  saved; they need the commendation treatment (key plus arguments).
- Objective phases other than the opening and raid phases have no catalog
  entries yet and read as the id in words.
- Cleanup: the never-called legacy widget builders in `BriefingScreen`
  (`buildMissionColumn`, `buildDetachmentColumn`, `buildButtons`,
  `buildLiftReadout`) and catalog keys left unused by the squad sweep
  (`openingBilletRole*`, `openingFirstRefitSelected`,
  `openingFleetArmoryNoKit`, `openingFleetArmoryUnclassified`).
