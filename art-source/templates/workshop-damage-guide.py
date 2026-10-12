"""Geometry guide only; finished sprites are ImageGen reskins, exported by tileset_export."""
from pathlib import Path
from PIL import Image, ImageDraw
import random

out = Path(__file__).with_suffix(".png")
im = Image.new("RGBA", (1536, 1024))
d = ImageDraw.Draw(im)

# Six 512-square slots; physical aspect ratios retained inside each cut.
# Breach 6x4, wreckage 5x3, broken lathe 4x2; broken mill 4x2, scorch 5x4, patch 6x4.
outer = [(46,155),(108,121),(178,134),(235,106),(299,135),(355,123),(453,188),
         (430,246),(466,319),(370,373),(303,361),(225,400),(175,354),(96,374),(42,290),(70,237)]
d.polygon(outer, fill="#4c514e")
inner = [(95,185),(150,166),(209,181),(248,154),(327,176),(387,195),(366,253),
         (408,299),(331,319),(268,312),(222,349),(175,314),(115,320),(89,267),(123,232)]
d.polygon(inner, fill="#02060b")
for x,y in [(189,235),(302,261),(226,288)]: d.point((x,y), fill="#829aaa")
for a,b in [((55,170),(123,203)),((375,160),(397,206)),((118,350),(164,310)),((329,342),(356,383))]:
    d.line([a,b], fill="#88908b",width=16)
# Collapsed beam fan and folded plating.
for poly in [[(551,181),(702,142),(735,262),(610,315)],[(706,268),(849,175),(912,245),(836,361)],
             [(573,331),(674,271),(717,335),(664,388)],[(872,309),(957,291),(973,352),(889,388)]]:
    d.polygon(poly, fill="#64706b",outline="#282d2c")
d.line([(550,212),(941,358)], fill="#333a38", width=24)
d.line([(628,384),(886,159)], fill="#91988e", width=18)
rng=random.Random(16)
for i in range(38):
    x=rng.randrange(554,968);y=rng.randrange(166,383)
    d.polygon([(x,y),(x+12,y-6),(x+20,y+11),(x+3,y+16)],fill=rng.choice(["#424b46","#7c8376","#a09c83"]))
# Broken 4x2 machines retain machine-bed registration with a huge missing center.
for ox,oy,kind in [(1024,0,0),(0,512,1)]:
    d.polygon([(ox+38,oy+150),(ox+462,oy+157),(ox+447,oy+354),(ox+49,oy+367)],fill="#343c34")
    d.rectangle((ox+48,oy+166,ox+120,oy+344),fill="#6c7565")
    d.rectangle((ox+367,oy+177,ox+450,oy+346),fill="#5f6b5d")
    d.polygon([(ox+140,oy+175),(ox+257,oy+164),(ox+310,oy+208),(ox+251,oy+248),(ox+156,oy+221)],fill="#9a9e91")
    d.line([(ox+138,oy+284),(ox+261,oy+308),(ox+350,oy+280)],fill="#818c7e",width=17)
    d.polygon([(ox+177,oy+223),(ox+300,oy+224),(ox+356,oy+267),(ox+287,oy+297),(ox+178,oy+270)],fill="#131915")
    if kind: d.polygon([(ox+202,oy+213),(ox+337,oy+154),(ox+371,oy+183),(ox+250,oy+252)],fill="#707c69")
    for i in range(14):
        x=ox+rng.randrange(140,351); y=oy+rng.randrange(193,342)
        d.line([(x,y),(x+16,y+7)], fill="#9b9883",width=5)
# Feathered-looking guide for scorch only; reskin supplies organic alpha and soot.
for i in range(32):
    x=512+rng.randrange(72,440);y=512+rng.randrange(103,412);r=rng.randrange(16,58)
    d.ellipse((x-r,y-r,x+r,y+r),fill=(18,15,12,100))
# Matching six-by-four repair patch, welded plates without a hole.
patch=[(x+1024,y+512) for x,y in outer]
d.polygon(patch,fill="#566368",outline="#8e9a96")
for x0,y0,x1,y1 in [(1120,699,1341,823),(1277,731,1430,851),(1170,818,1320,868)]:
    d.rectangle((x0,y0,x1,y1),fill="#65767b",outline="#bcc1aa",width=4)
out.parent.mkdir(parents=True,exist_ok=True)
im.save(out)
print(out)
