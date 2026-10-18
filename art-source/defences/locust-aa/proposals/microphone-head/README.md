# Approved Locust microphone-shaped launcher

The user approved this elongated geometry for ImageGen reskinning. The resulting
overhead guide and painted mount live two directories above, with the exact
reskin prompt. This retained scene remains the geometry source of truth.

The elongated head is a cylindrical rocket magazine with a convex rounded cap,
like a microphone head. Eighteen small launch bores run parallel along one
forward axis, shown elevated 20 degrees. Their openings follow the cap; they
do not radiate from a sphere. The head is 1.70 model units long and 1.24 wide.
Extra length is split fore and aft of the side axle (local Y -0.78 to +0.92)
rather than shifting the whole
head forward. The bores extend into the longer magazine to one rear plane.

The user requested a circular rotating floor after the elongated head was
approved and composited over the tower. Its radius is 1.02, thickness 0.22,
and center Z is 0.22 (top Z 0.33). The rear cooling ribs taper to remain
inside the round perimeter. This changes the floor silhouette while keeping
the head, bores, cheeks, side bearings, axle height and pitch geometry fixed.

The circular floor and both cheek supports form one yaw assembly over a circular
bearing. The cheeks clip onto opposite sides of the head at one horizontal
axle. The head, its bosses and bore interiors share a separate pitch parent,
so the launcher can elevate without tipping the floor or supports. The
axle is raised slightly to keep the extended rear clear. A local rotation
constraint limits pitch to 0-25 degrees; it is deliberately not a vertical
launcher. The script checks shell clearance over the deck and the rear drive
cover's height at each whole degree in this range. The cover check uses its
height as an infinite plane, conservative relative to its actual footprint.
These are sampled shape checks, not a full mechanical collision certification.

Port count, elevation and proportions are art choices, not gameplay data.
Views show the overhead footprint, an angled assembly and the side axle.

`build.py` runs in an isolated background Blender process with factory startup.
It writes `proposal.blend` and the transparent 1920x1080 `shape-views.png` at
100% render scale. `review.ps1` adds labels at the same resolution without
resizing. No ImageGen call is part of this geometry script. The separate reskin
workflow uses the new overhead guide and the prior approved painted mount at
`../../sources/locust-square-platform.png` as its style and unchanged-head
reference while retaining the ground foundation. The elongated head is already
approved; the rounded platform is user-requested and its painted finish remains
for review.

```powershell
sh art-source/blender.sh --background --factory-startup --python art-source/defences/locust-aa/proposals/microphone-head/build.py
./art-source/defences/locust-aa/proposals/microphone-head/review.ps1
```
