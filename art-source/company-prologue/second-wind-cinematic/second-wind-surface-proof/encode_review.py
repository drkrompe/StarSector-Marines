"""Encode the exact96 current-run renders, bind scene/frame/movie hashes and verify decode."""
from pathlib import Path
from PIL import Image
import hashlib,json,subprocess,shutil
OUT=Path(__file__).resolve().parent;review=OUT/'review';review.mkdir(exist_ok=True)
scene=OUT/'second-wind-daylight-landing-proof.blend';frames=OUT/'build/landing-frames'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
records=[]
for i in range(1,97):
    p=frames/f'{i:04d}.png'
    assert p.is_file(),p
    assert p.stat().st_mtime>=scene.stat().st_mtime,('Old render from previous run',p)
    assert Image.open(p).size==(1920,1080)
    records.append({'output':i,'source_frame':720+(i-1)*.5,'sha256':sha(p),'mtime_ns':p.stat().st_mtime_ns})
manifest={'scene':scene.name,'scene_sha256':sha(scene),'source_fps':12,'output_fps':24,'frames':records,'unique_png_hashes':len({r['sha256'] for r in records}),'dimensions':[1920,1080],'duration_seconds':4}
(OUT/'render-manifest.json').write_text(json.dumps(manifest,indent=2))
ffmpeg=shutil.which('ffmpeg');ffprobe=shutil.which('ffprobe');assert ffmpeg and ffprobe
movie=review/'second-wind-setdown-proof.mp4'
subprocess.run([ffmpeg,'-hide_banner','-loglevel','error','-y','-framerate','24','-start_number','1','-i',str(frames/'%04d.png'),'-frames:v','96','-c:v','libx264','-crf','18','-preset','medium','-pix_fmt','yuv420p','-movflags','+faststart',str(movie)],check=True)
info=json.loads(subprocess.check_output([ffprobe,'-v','error','-count_frames','-select_streams','v:0','-show_entries','stream=width,height,nb_read_frames,r_frame_rate','-of','json',str(movie)],text=True))
stream=info['streams'][0];assert stream['width']==1920 and stream['height']==1080 and int(stream['nb_read_frames'])==96
subprocess.run([ffmpeg,'-v','error','-i',str(movie),'-f','null','-'],check=True)
for tag,index in [('start',1),('poster',49),('end',96)]:shutil.copyfile(frames/f'{index:04d}.png',review/(tag+'.png'))
validation={'movie':str(movie.relative_to(OUT)).replace('\\','/'),'sha256':sha(movie),'bytes':movie.stat().st_size,'probe':info,'decode_all_frames':'PASS','render_manifest_sha256':sha(OUT/'render-manifest.json'),'unique_source_png_hashes':manifest['unique_png_hashes'],'audio':'none','visual_review':'Start/mid/end PNGs exported; playback review separately by parent.'}
(OUT/'media-validation.json').write_text(json.dumps(validation,indent=2));print(json.dumps(validation))
