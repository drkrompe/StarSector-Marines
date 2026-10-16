"""Lossless atlas crops and precise native-input provenance; bundled Python/Pillow."""
from pathlib import Path
from PIL import Image
import hashlib,json
OUT=Path(__file__).resolve().parent
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
atlas=OUT/'assets/arrival-material-atlas-v1.png';im=Image.open(atlas);w,h=im.size
regions={'cream':(0,0,w//2,h//2),'oxide':(w//2,0,w,h//2),'gunmetal':(0,h//2,w//2,h),'backing':(w//2,h//2,w,h)}
for role,box in regions.items():im.crop(box).save(OUT/'assets'/(role+'.png'))
(OUT/'assets/atlas-crops.json').write_text(json.dumps({'source':atlas.name,'native_dimensions':[w,h],'sha256':sha(atlas),'regions':regions},indent=2))
inputs=[]
for folder in ('assets','prompts','guides'):
    for p in sorted((OUT/folder).glob('*')):
        if not p.is_file() or p.suffix not in ('.png','.txt'):continue
        record={'path':str(p.relative_to(OUT)).replace('\\','/'),'sha256':sha(p),'bytes':p.stat().st_size}
        if p.suffix=='.png':record['dimensions']=list(Image.open(p).size)
        inputs.append(record)
(OUT/'provenance.json').write_text(json.dumps({'inputs':inputs,'roles':{
 'guides/side-surface-guide-v1.png':'Exact model/camera geometry and layout input for side ImageGen. Immutable copy.',
 'assets/side-surface-v1.png':'Generated near-side material finish,1672x941 native RGB. No canonical geometry authority, no full hidden-side coverage.',
 'assets/arrival-material-atlas-v1.png':'Generated neutral four-material albedo atlas,1254x1254 native. Four627square lossless crops;9 provisional metres per repeat.',
 '../../second-wind-skins/arrival-heavy.reference.png':'External source material/damage direction only; accepted actual V3 mesh owns identity.'},
 'external_reference_sha256':{'arrival-heavy.reference.png':sha(OUT.parent.parent/'second-wind-skins/arrival-heavy.reference.png'),
 'second-wind-geometry-v3.blend':sha(OUT.parent.parent/'second-wind-geometry/second-wind-geometry.blend')},
 'generation':'OpenAI built-in ImageGen. Exact prompt files retained in prompts; generated native images preserved without rescaling.',
 'side_registration':'Identity normalized camera UV:1920x1080 guide ->1672x941 master. No arbitrary bounds fitting; individual generated details not asserted registered.',
 'limits':'Partly baked side RGB lighting remains. Neutral box atlas covers other faces. Bounded side proof, not a complete multiangle textured ship.'},indent=2))
