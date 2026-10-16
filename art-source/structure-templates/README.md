# Shared structures and working yards

These templates reuse the installed environment art. No new sprite generation
is required. The durable placement model lives in `mapgen-nouns.md`.

| Template | Source | Shared consumers |
| --- | --- | --- |
| L freight depot and T transfer workshop | Junction, Relay Nine and Thunder Run | Industrial building parcels and compound warehouses |
| Freight marshalling yard | Junction warehouse overflow | `ServiceYardTemplate.FREIGHT_MARSHALLING`, industrial yards and compounds |
| Maintenance yard | Relay Nine service court | `ServiceYardTemplate.MAINTENANCE`, industrial yards and compounds |
| Whole maintenance outpost, standard or extra magazine | A Cold Account | Larger remote Assault missions and the campaign host |
| Medical service court and supply annex | Ordinary medical campus | Rotatable claimed courts, procedural medical compounds |
| Power court | Relay Nine outdoor power installation | `ServiceYardTemplate.POWER_COURT`, industrial yards and compounds |

Building plans retain their console banks, stocked shelves, windows and usable
interior anchors. Yards retain their equipment, vehicle headings, loading marks
and four gates. Hosts supply owned ground; neither template carries story beats,
force budgets or capture objectives. The bounded industrial parcel planner
creates room for these larger plans before streets are published as a road graph.

Run `gradlew.bat createSnapshots -Psnapshot=service-yard-templates` for the review
boards: all four yard orientations, ordinary industrial building selection, a
constructed three-parcel compound using the production filler, and a close view
of a naturally generated city. The suite checks connected floor and interior
anchors, preserved road cells and spawns, and adoption by actual generated cities.
It also checks both Conquest axes at 560x336. Retained PNGs are production Java2D
renders at 1920x1080 and 100%, not live OpenGL captures.

Run `gradlew.bat createSnapshots -Psnapshot=whole-compounds` for whole-site review:
four cardinal orientations, the actual remote-Assault map selection, infantry
access to every facility and doorway, vehicle-width travel from the shed apron
to the external road, and clear arrival berths. The suite also initializes an
ordinary Assault simulation and checks the original campaign host. These are
construction checks, not a battle-balance replay or live campaign capture.
The `outpost-staff-turn-*` frames show dispatch, bunks and mess seating at
24 pixels per cell. Dispatch combines seated terminals, a meeting nook and
records storage; accommodation uses the complete bunk and mess kits. Wider
support-room windows retain individual barriers and the room's floor finish.
These fittings are shared by authored campaign compounds and packed ground
installations, including the modern clinical kit for patient wards. Existing
sprite damage states are reused with the furniture.

In ordinary missions, choose **Assault**, **Remote**, and **Reinforced** or
**Full Strength**. The standard or extra-magazine outpost and its orientation
come from the map seed. Single-gate citadels and missions declaring additional
forts/batteries/nests retain the normal generator. Smaller tiers retain their
existing maps because this compound needs a separate approach as well as its
80-by-70-cell footprint. Campaign-specific mountains, the supply-run approach,
records objective and story forces remain in A Cold Account's host.

The template's buildings, courts, doorways and service roads live in
`MaintenanceOutpost.layout`; `CompoundGround` supplies the shared claim, surfaces
and road reservations. `CompoundMissionMap` supplies the ordinary remote host.
General program-driven and nonrectangular city claims remain in
`cross-leaf-footprint-planning.md`. Relief's checkpoint and Shepherd's salvage
yard also have reusable working areas, but their mission gates, road progression,
landing reservations and unique hull objectives still belong to their hosts.

Run `gradlew.bat createSnapshots -Psnapshot=medical-campus` for the medical
compound review: compact and expanded clinics, all four service-court poses,
and a campus selected by the ordinary city generator. These use the existing
clinic, storage and parked-vehicle art, including their registered damage states.
The delivery hauler is receiving stock; the patient transfer bay is deliberately
empty. Shared concrete circulation and a three- to five-cell court drive remain clear.
Supply annexes contain medical cabinets, clean handling, stock racks and packing
space. Court modules shrink in number when a parcel cannot accommodate them.

The medical evidence checks furnished floor connectivity, real interior anchors,
reserved roads and landing berths across eight ordinary cities, existing small-city
adoption, and infantry connectivity on both full-size Conquest axes. The broader
Conquest road-graph check finds a blocked cell at (208, 300) in seed 42 on the
unchanged baseline as well; this pass does not claim to repair that separate issue.
The review frames are production Java2D renders, not live game captures.
