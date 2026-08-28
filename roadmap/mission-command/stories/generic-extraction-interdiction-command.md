# Generic Extraction interdiction command

Status: IN PROGRESS — bounded source security, alarm response, diagnostics,
and canonical headless evidence are implemented; live acceptance remains.

Written: 2026-08-27

Updated: 2026-08-27 — added a deterministic alarm-reaching fixture with
surviving source response and belief-driven interdiction.

Read `mission-command-nouns.md`, `extraction-command.md`,
`generic-extraction-corridor-command.md`, and
`commander-trace-and-balance-harness.md` before working this story.

## Intent

Give conventional Extraction defenders an honest zero-input response without
revealing the Marine egress or turning the package objective into a tracking
beacon. A small mobile security force should guard the public source, mobilize
on the identity-free alarm, and interdict only where defender beliefs support
it while authored garrisons retain their posts.

## Scope

- Reserve a bounded defender patrol pool during generic Extraction setup.
- Keep ordinary authored garrisons under garrison authority and preserve
  reinforcement or scripted ownership unless explicitly handed over later.
- Assign one routine source guard while quiet and hold the remaining patrols
  as an inspectable reserve.
- Mobilize at most three patrol squads after the public source alarm.
- Send responders toward faction-local believed contacts when available;
  otherwise reinforce separate reachable points on the source perimeter.
- Publish source, alarm, contact summary, reserve count, role, reason,
  assignment, and own rally through overlay, dump, and perspective trace.
- Exercise both commanders through the existing argument-selected Extraction
  evidence adapter.

## Acceptance

- [x] Quiet generic Extraction assigns one mobile source guard and holds the
  rest of the mission-owned patrol pool in reserve.
- [x] The identity-free alarm mobilizes no more than three patrol squads.
- [x] Interdiction uses only defender-local contacts and never consumes hidden
  egress, live package position, progress, cohort, escort, or Marine identity.
- [x] Authored garrisons remain externally posted and do not join the mobile
  pool.
- [x] Defender overlay, squad dump, and perspective trace expose the same
  public source, alarm, belief summary, reserve, role, reason, and rally.
- [x] The generic production setup installs the paired Extraction commanders.
- [x] Canonical-duration duplicate evidence remains byte-stable and shows an
  intelligible source response plus belief-driven interception when contact is
  acquired.
- [ ] Live play confirms defenders mobilize promptly without omnisciently
  tracing the package corridor or abandoning the whole defensive network.

## Constraints

- The objective remains sole authority for package motion and terminal law.
- The public alarm is permission to mobilize, not exact package telemetry.
- Contact coordinates are faction beliefs; no hostile unit identifier enters
  the published defender command picture.
- Civilian Rescue and Silent Colony keep their scenario-specific opposition.
- Do not add an Extraction-specific Gradle task.

## Exit

Fold durable defender laws into `extraction-command.md`, record the paired
generic migration in the shipped ledger, and retire this story only after
canonical and live acceptance.
