"""Process-local Cycles GPU choice; never saves application preferences."""
import bpy
def configure_render_device(scene):
    prefs=bpy.context.preferences.addons['cycles'].preferences
    for backend in ('OPTIX','CUDA'):
        try:
            prefs.compute_device_type=backend;prefs.get_devices()
            active=[d for d in prefs.devices if d.type==backend]
            if active:
                for d in prefs.devices:d.use=d in active
                scene.cycles.device='GPU'
                return {'backend':backend,'devices':[d.name for d in active]}
        except (TypeError,RuntimeError):pass
    scene.cycles.device='CPU';return {'backend':'CPU','devices':[]}
