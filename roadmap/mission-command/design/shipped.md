# Mission-command shipped ledger

Status: SHIPPED — folded mission-command implementation stories.

Written: 2026-08-27

| Retired story | Mission | Ship date | Evidence | Fold destination |
|---|---|---|---|---|
| `strategic-resolution-evidence.md` | Shared | 2026-09-27 | `5d8971331`, `d2e708d67`; 317 focused tests and paired frozen-grid evidence | `mission-command-nouns.md` — exact snapshot storage, strategic resolution and field-approximation boundaries |
| `sabotage-site-task-groups.md` | Sabotage | 2026-08-26 | `3efd0c42`, `30da6637` | `sabotage-command.md` |
| `sabotage-site-defense-command.md` | Sabotage | 2026-08-26 | `d598c1df` | `sabotage-command.md` |
| `extraction-objective-contract.md` | Extraction | 2026-08-27 | `34ce388a` | `extraction-command.md`, `mission-command-nouns.md` |
| `rescue-corridor-command-picture.md` | Civilian Rescue | 2026-08-27 | `4687398f`; deterministic `commanderEvidence -Pmission=extraction` result at tick 3,502 | `extraction-command.md`, `mission-command-nouns.md` |
| `lane-chain-tug-of-war.md` | Conquest | 2026-09-02 | `aa30b91d8`, this commit | `conquest-command.md` — the chain is the front and the track is the fence, on by default; the measured matrix both ways, the three capture-gate openings that were not the cause, and why a reading that walks a ladder cannot be judged against a ladder with a rung missing |
| `officer-command-groups.md` | Conquest / deployment | 2026-10-08 | this commit; focused planner/deployment/UI tests and 3,000-tick production launch replay | `mission-command-nouns.md`, `conquest-nouns.md`, `company-view-nouns.md`, `ui-nouns.md` |
| `fort-counterattack.md` | Conquest | 2026-10-20 | this commit; `FortCounterattackTest`, scene `fort-counterattack`, conquest-trial nine seeds at two garrisons against `-Dbattle.conquest.fortCounterattack=false` | `conquest-command.md` (Defender command), `ai-nouns.md` (place alarms), `forts.md`, `phased-assault.md`, `garrisonables.md` |
| `place-counterattack.md` | Conquest / Relay Nine / Thunder Run | 2026-10-20 | this commit; `PlaceCounterattackTest`, scenes `place-counterattack`, `fort-counterattack`, garrison scenes; commanderEvidence conquest, conquest-trial (two garrisons), relay-nine, junction against `-Dbattle.counterattack=false` | `conquest-command.md` (Defender command: places, observe before committing, worth it, story pools, figures), `garrisonables.md` (places, not forts), `ai-nouns.md` |
| `fort-counterattack-tuning.md` | Conquest | 2026-10-20 | this commit; conquest-trial nine seeds at two garrisons, sixteen variants against `-Dbattle.conquest.fortCounterattack=false`; scenes `fort-counterattack`, `reactive-belt`, `attacker-holds-ground`, `assault-muster`, `defence-structure-command` | `conquest-command.md` (Defender command: reserve, figures, what was tried), `ai-nouns.md`, `garrisonables.md` |
| `mission-counterattack.md` | Raid / Assault / Sabotage / Extraction | 2026-10-21 | `3138fc024`, this commit; `MissionCounterattackTest`, `MissionPlacesTest`, scene `mission-counterattack` (light, outmatched, triggered); commanderEvidence raid, assault, sabotage, extraction, six seeds each against `-Dbattle.mission.counterattack=false` | `conquest-command.md` (Defender command: the procedural missions, what reasonable became, triggered counter-attacks), `garrisonables.md` (mission defenders, place directives) |
| `side-objective-toolkit.md` | Dust Ledger / shared | 2026-10-26 | `451d955c3`, `672488f49`, `a1788dda5`, `62b023c98`, `bb62d3dae`, this commit; `SideObjectiveTest`, `SideObjectiveRowsTest`, `CampaignMarkSettlementTest`; commanderEvidence corvus DUST_LEDGER with and without `-Dbattle.dustLedger.sideObjectives=all`, identical before and after | `mission-command-nouns.md` (Side objectives), `ui-nouns.md` (Optional section, beacons), `optional-objectives.md`, `dust-ledger-crossing.md` (Side objectives) |
