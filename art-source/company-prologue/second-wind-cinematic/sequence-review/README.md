# Jangala approach continuity review

The [refined sequence](../port-refinement/review/jangala-approach-refined.mp4)
reuses these forest and farmland excerpts with the updated working-port shot.
Its source and evidence are in the [port refinement](../port-refinement/README.md).

Three four-second excerpts test the visual progression from the forest reveal
through cultivated land to the working port. The cuts represent elapsed travel;
this is not a twelve-second physical descent or the complete arrival cinematic.

The [forest reveal](../forest-reveal-realism/README.md) preserves the rising Atlas.
The accepted [managed-land shot](../managed-land-realism/README.md) continues its
rightward flight above the farms. The [working-port view](../port-approach-realism/README.md)
keeps the cargo district abeam and the landing berth farther ahead, outside this
side-window view. Each source remains separately editable.

Run `assemble_sequence.py` from this directory with Python and FFmpeg/FFprobe:

```powershell
python assemble_sequence.py --ffmpeg '<ffmpeg.exe>' --ffprobe '<ffprobe.exe>'
```

The clean [continuity movie](review/jangala-approach-continuity.mp4) is silent,
1920×1080 at 24 fps. The assembler checks each four-second input and the combined
288-frame output, performs a full decode, and records input hashes and source-time
mapping in [media validation](review/media-validation.json). Each source shot
owns its geometry, coverage, art provenance and motion evidence. Encoding does
not establish visual continuity; review the cuts, daylight, river/ridge vocabulary,
window materials, background flow and traffic by watching the result.

All source assets and review movies remain outside shipped `mod/` content.

The combined movie is 7,624,158 bytes. Normal-speed browser playback presented
all 288 frames with zero drops. [Motion review](review/motion-review.json) records
the checked movie hash and remaining visual limits: warmer, wider cabin framing
in the forest shot and the port district's early architecture and ground treatment.
