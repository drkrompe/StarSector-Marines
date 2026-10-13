# Boat condition and repairs

Status: PLANNED
Written: 2026-10-11
Updated: 2026-10-13 — Automatic campaign-day repairs replace mandatory repair bills; resources expedite recovery and destroyed aircraft lose their fittings.

Read `company-view-nouns.md` and `air-nouns.md` before implementation.

## Player loop

A surviving damaged shuttle comes home with its identity and fittings intact.
The company automatically repairs it over campaign time, with no required material
order. Damage costs availability for a short period; spending resources buys an
earlier return to service. Destruction costs the entire aircraft and its installed
upgrades. This preserves a reason to protect valuable lift without turning every
scraped hull into a compulsory shopping trip.

- **Routine recovery:** start automatically at mission settlement. Scale duration
  with damage, capped at two in-game days even for a barely surviving hull; minor
  damage may take less than one day. Proposed initial tuning is two days times the
  missing hull fraction. Surviving boats repair concurrently aboard the company
  ship, including while travelling; no docking, working machine shop or cargo
  minimum is required for ordinary recovery.
- **Availability:** proposed Alpha baseline is grounded until recovery completes.
  Show condition and time remaining in Boat Deck and readiness/launch explanations.
  Healthy boats remain usable. When all lift is repairing, return to campaign and
  let time pass, or expedite a boat; a paused management screen cannot advance time.
  Validate this baseline against the opening one-Hermes campaign before shipping.
- **Expedite repairs:** a paid order completes the selected boat's remaining work
  immediately. Quote supplies/metals/machinery against remaining damage, so waiting
  reduces the bill. The maximum acceleration bill must be materially cheaper than
  a standard replacement of the same pattern. Exact recipe fractions remain tuning
  work. Recheck condition and affordability atomically; a healthy boat costs nothing
  and cannot accept a stale paid order.
- **Total loss:** remove the aircraft and every installed fitting permanently.
  Keep its shipboard berth available. Commission a replacement through the ordinary
  full fabrication bill, with a new identity and standard fittings; upgrading it
  again requires new purchases. Market-bought commodities feed the existing workshop
  order. A separate direct aircraft-market inventory is outside this story.

The replacement/lost-fitting behavior already exists; persistent surviving hull
condition, campaign repair clocks and acceleration orders are the work still to do.
The Hermes remains a standard-fit courier; this story creates no Hermes upgrades.
Upgraded Aeroshuttles make the investment at risk especially visible.

## Campaign and battle boundaries

Campaign recovery measures elapsed campaign days with sub-day precision, never
wall-clock time, calendar day-of-month or time spent viewing a screen. Save/load,
leaving and alternate UI entrances preserve progress. Moving compatible boats to
another company hull preserves each boat's condition and recovery deadline.

Settle each deployed company boat once by its persistent identity, including
victory, defeat and Retreat. Repeated trips are one aircraft, not several repair
jobs. Destruction takes precedence over surviving damage. Employer craft and debug
missions cannot create company repair jobs. Old saves default existing boats to
healthy; missing condition is not an invented repair debt.

Tactical turnaround remains distinct from campaign overhaul. Track surviving damage
across trips so off-map carrier servicing or a fresh tactical entity cannot silently
erase it. The implementation must document and prove how battle condition becomes
one campaign recovery job; do not infer pristine condition from whichever entity
happens to exist at settlement. Repeated settlement and opening Boat Deck never
restart the timer, restore a destroyed hull or duplicate resource spending.

Second Wind's original wrecked Hermes, sealed second bay, workshop and other ship
facilities remain authored restoration projects with their existing costs and
story gates. They never recover merely because two days passed. Workshop tooling
may affect eligible paid quotes through existing resource rules, but ordinary
recovery must remain available before workshop restoration.

## Castle and presentation

On the first damaged return, Castle's portrait popover explains that the crew has
started repairs, shows the selected boat's remaining time, and highlights its real
condition and acceleration controls with click-through apertures. Her delivery is
pragmatic and protective of the company: bring the aircraft home and give the crew
time; spend stores when the next job cannot wait.

The lesson teaches the choice without requiring paid acceleration. The player may
acknowledge it and return to campaign to wait. Resume unfinished guidance after
leaving, save/load or entering from another company/briefing route. If recovery
finishes while away, reconcile to the ready boat rather than waiting for an obsolete
repair action. If the boat is lost or replaced, explain the current state without
resurrecting it or regranting opening materials. Preserve founding induction's
precedence and ordinary launch guards.

Use localized condition/countdown/action copy. Keep Available in the shared stock
location; paid acceleration and replacement use Cost and Short by. Ordinary timed
recovery needs a progress indication and remaining duration, not a Free badge.
Boat Deck and briefing read the same availability, and update as orders complete.

## Acceptance and delivery

1. Persist condition and settle surviving damage once, with legacy healthy defaults
   and evidence for repeated trips, carrier servicing, defeat, Retreat and loss.
2. Add automatic recovery and atomic acceleration. Verify partial-day progress,
   the two-day ceiling, simultaneous repairs, save/load, ship transfer, stale quotes
   and insufficient resources. No screen visit is required for completion.
3. Connect Boat Deck, mission readiness and Castle's resumable first-return lesson.
   Show one repairing boat beside usable lift and the no-ready-lift countdown.
4. Prove the campaign choices: a damaged lone Hermes becomes usable by waiting with
   no repair materials; a second boat keeps lift available; acceleration spends only
   its reviewed bill; a lost upgraded Aeroshuttle returns only through a full paid
   replacement with standard fittings. Initial wrecks and sealed facilities stay put.
5. Check replacement affordability after an early total loss against accessible
   markets, travel reserves and remaining income. Timed repair is not a recovery
   path for a destroyed aircraft; any insolvent-company rescue policy must be an
   explicit economy decision, not a hidden respawn or tutorial grant.
6. Render condition, countdown, cost and tutorial states at 1920x1080 / 100% UI
   scale, including expanded localized text and large stock counts. Live-accept
   leave/re-entry and wait/expedite/redeploy with a normal campaign save.

`campaign-learning-curve.md` owns the wider affordability and force-growth pass.
Price acceleration as convenience and replacement as the larger loss; do not silently
change story rewards or initial depot allocations to balance this feature.
