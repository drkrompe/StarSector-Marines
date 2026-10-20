Status: IN PROGRESS

Written: 2026-10-20

# Mission defenders man garrisonables reactively

Closes the open question in `garrisonables.md` ("Mission defenders"): yes. The
Raid, Assault, Sabotage and Extraction defender commanders man the garrisonables
on their maps — building envelopes first among them — in answer to their own
believed pressure, through the side-neutral tier Conquest uses
(`GarrisonDirector`, `GarrisonableRegistry`, `ObjectiveAssignment.holdEnvelope`).

## Scope

- One shared planner (`MissionGarrisons`), one instance per mission defender
  commander, called at the end of its plan. The commander decides its mission
  duties first, exactly as today; it then **offers** squads to the planner:
  - a **roving** offer for a squad it would otherwise hold in reserve;
  - a **duty** offer for a squad on a stationary mission post (a Raid target
    guard or responder, an Assault routine area post, a Sabotage site post, an
    Extraction source guard), which may man only a place within
    `DUTY_RADIUS_CELLS` of that post: the post stays covered, from a window.
  - Nothing else is offered: authored garrisons, external owners, Assault
    contact responders, Extraction interdiction, machines, crews, demolition
    squads.
- The planner answers which offered squad mans, stands on, falls back from, or
  is given back from which place; the commander replaces its own proposal for
  those squads and leaves the rest untouched. A squad the mission stops
  offering (the alarm took it, its duty moved off the place) is forgotten at
  once: mission logic outranks manning.
- Laws as in `garrisonables.md`: belief only; start/release hysteresis with a
  dwell; pickets before massing; the reserve floor applies to roving offers
  only (a duty offer's post is its reserve); useful-strength floor; arrival
  bound; spatial lookup through the registry's block index; bounded sends per
  pulse. Buildings whose born garrison stands in them count as manned.
- Fall back through layers: an apron to the envelope of the building it fronts,
  an envelope to its rooms (an area defence inside the building, kept until the
  building has been quiet for a few pulses), otherwise release.
- Switch: `-Dbattle.mission.reactiveDefence=false` is the control and leaves the
  battle as it was.
- Story missions built on these commanders (the Veteran's Job, Cold Account)
  inherit it. The bespoke story commanders give every defender squad an
  authored duty and stay as they are.

## Acceptance

- Unit tests of the planner under one second each.
- Scene: a non-Conquest defender commander mans a pressed building from its
  pool and gives it back when the pressure passes; existing garrison scenes stay
  green.
- `commanderEvidence -Pmission=raid|assault|sabotage|extraction` against the
  control: manning episodes, releases, fall backs, losses, outcomes.
