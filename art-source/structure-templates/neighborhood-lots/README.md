# Neighborhood rooms and lot yards

`createSnapshots -Psnapshot=neighborhood-lots` renders production shell programs
and yard lots for homes, shops, workshops and offices at two frontages, plus
three ordinary generated neighborhoods. Frames are 1920 x 1080 through the
production Java2D renderer at 24 pixels/cell, not live game captures. Retained
frames live in `review/`.

Homes have sleeping rooms with lockers, kitchens with dining tables and stools,
and living rooms with sofas, coffee tables and media furniture where they fit.
Shallow strips receive serial rooms; apartment compounds preserve their shared
hall. Commercial floors separate sales from stock at medium sizes. Compact
workshops and offices get work/storage or reception/office divisions. Existing
large factory and civic room programs remain in use.

Large eligible lots may choose a medium building with a fenced garden and patio,
a shop forecourt, a cargo yard, or public seating. Paved approaches follow the
actual doors. Furniture cannot occupy those approaches. Fences retain side gates
and a walkable outer strip; the lot refuses existing roads, reservations, berths,
edge barriers and props before any mutation. Building POIs retain the actual
shell bounds rather than the full lot.

This pass reuses the established colony furnishings, residential beds/sofas,
ship mess and lounge tables/seating, main-stores stock, and industrial fence art.
No new bitmap art was generated. Full-footprint groups and authored plan rotations
remain authoritative; kitchen/living labels and partitions carry the arrangement.
