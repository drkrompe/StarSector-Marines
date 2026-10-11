"""Sole producer of terrain materials and their id-addressed catalog. Python standard library only.
Run from any directory: python art-source/terrain/bake_materials.py
The surfaces are procedural repeating fields, not cut sprite art.
"""
from pathlib import Path
import json, math, random, struct, zlib
root = Path(__file__).resolve().parents[2]
recipe = json.loads(Path(__file__).with_name("materials.recipe.json").read_text(encoding="utf-8"))
size, variants = recipe["cellPx"], recipe["variants"]
rows = [("talus", 3), ("rock", 3)] + [("face", light) for light in range(7)]
width, height = size * variants, size * len(rows)
pixels = bytearray(width * height * 4)
blocks = []
for row, (family, light) in enumerate(rows):
    cells = []
    for variant in range(variants):
        rng = random.Random(recipe["seed"] + variant * 1337)
        cells.append([variant, row])
        # Common periodic frequencies keep strata continuous at tile boundaries.
        for y in range(size):
            for x in range(size):
                u, v = x / size, y / size
                def lattice(ix, iy, period):
                    ix, iy = ix % period, iy % period
                    value = (ix*73856093 ^ iy*19349663 ^ (recipe["seed"]+variant*191)) & 0xffffffff
                    value = ((value ^ (value >> 13))*1274126177) & 0xffffffff
                    return (value & 65535)/32767.5-1
                def noise(period):
                    fx, fy = u*period, v*period
                    ix, iy = math.floor(fx), math.floor(fy)
                    tx, ty = fx-ix, fy-iy
                    tx, ty = tx*tx*(3-2*tx), ty*ty*(3-2*ty)
                    a=lattice(ix,iy,period)*(1-tx)+lattice(ix+1,iy,period)*tx
                    b=lattice(ix,iy+1,period)*(1-tx)+lattice(ix+1,iy+1,period)*tx
                    return a*(1-ty)+b*ty
                grain = rng.uniform(-6, 6)
                broad = noise(4)*4 + noise(8)*3 + noise(16)*2
                # A deformed bedding line, without the diagonal lattice a sine field creates.
                strata = math.sin(2*math.pi*(v*4 + noise(4)*.55))
                vein = -10 if strata < -.91 else 3 if strata > .85 else 0
                if family == "grass":
                    value = grain*1.4 + broad*.65
                elif family == "face":
                    value = grain + broad + vein
                else:
                    value = grain + broad + vein*.35
                brightness = (light-3)*9
                rgb = [max(0,min(255,round(c+value+brightness))) for c in recipe[family]]
                at=((row*size+y)*width+variant*size+x)*4
                pixels[at:at+4] = bytes(rgb+[255])
    ident="terrain."+family+("."+str(light) if family=="face" else "")
    blocks.append({"id":ident,"cells":cells})
def chunk(tag, data):
    return struct.pack(">I",len(data))+tag+data+struct.pack(">I",zlib.crc32(tag+data)&0xffffffff)
raw=b"".join(b"\0"+pixels[y*width*4:(y+1)*width*4] for y in range(height))
png=b"\x89PNG\r\n\x1a\n"+chunk(b"IHDR",struct.pack(">IIBBBBB",width,height,8,6,0,0,0))+chunk(b"IDAT",zlib.compress(raw,9))+chunk(b"IEND",b"")
asset="graphics/tilesets/natural-terrain.png"
(root/"mod"/asset).write_bytes(png)
(root/"mod/data/tilesets/natural-terrain.tileset.json").write_text(json.dumps({"sheet":asset,"cellPx":size,"blocks":blocks},indent=2)+"\n",encoding="utf-8")
