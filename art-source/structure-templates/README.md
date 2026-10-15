# Shared structures and working yards

These templates reuse the installed environment art. No new sprite generation
is required. The durable placement model lives in `mapgen-nouns.md`.

| Template | Source | Shared consumers |
| --- | --- | --- |
| L freight depot and T transfer workshop | Junction, Relay Nine and Thunder Run | Industrial building parcels and compound warehouses |
| Freight marshalling yard | Junction warehouse overflow | `ServiceYardTemplate.FREIGHT_MARSHALLING`, industrial yards and compounds |
| Maintenance yard | Relay Nine service court | `ServiceYardTemplate.MAINTENANCE`, industrial yards and compounds |
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

The maintenance-outpost layout used by A Cold Account is another whole-compound
candidate; its local building, yard and road relationships already rotate
together. General program-driven and nonrectangular city claims remain in
`cross-leaf-footprint-planning.md`. Relief's checkpoint and Shepherd's salvage
yard also have reusable working areas, but their mission gates, road progression,
landing reservations and unique hull objectives still belong to their hosts.
