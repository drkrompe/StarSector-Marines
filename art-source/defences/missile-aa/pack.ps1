# Mechanical runtime registration of approved transparent layers. No painting or rescaling.
# Integer pixel translations preserve every RGBA sample. Locust's half-pixel
# metadata pivot rounds to the nearest raster pixel (residual <= 0.5 pixel).
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$repo = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$out = Join-Path $repo 'mod/graphics/structures'
$items = @(
    @{ Source='locust-aa/layers/ground-base.png'; Output='missile-aa-base.png'; X=0; Y=14 },
    @{ Source='locust-aa/layers/locust-mount.png'; Output='aa-locust-mount.png'; X=-1; Y=8 },
    @{ Source='swarmer-aa/layers/mount.png'; Output='aa-swarmer-mount.png'; X=0; Y=0 },
    @{ Source='proximity-aa/layers/mount.png'; Output='aa-proximity-mount.png'; X=0; Y=0 }
)
foreach ($item in $items) {
    $source = [System.Drawing.Bitmap]::FromFile((Join-Path $PSScriptRoot ('../'+$item.Source)))
    $target = [System.Drawing.Bitmap]::new(512,512,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        if ($source.Width -ne 512 -or $source.Height -ne 512) { throw 'Expected approved 512px layer' }
        # Copy exact ARGB samples, including transparent RGB; refuse clipping any visible pixel.
        for ($y=0; $y -lt 512; $y++) {
            for ($x=0; $x -lt 512; $x++) {
                $pixel=$source.GetPixel($x,$y)
                $tx=$x+$item.X; $ty=$y+$item.Y
                if ($tx -ge 0 -and $tx -lt 512 -and $ty -ge 0 -and $ty -lt 512) {
                    $target.SetPixel($tx,$ty,$pixel)
                } elseif ($pixel.A -ne 0) { throw "Visible pixel clipped in $($item.Source)" }
            }
        }
        $target.Save((Join-Path $out $item.Output),[System.Drawing.Imaging.ImageFormat]::Png)
        Write-Output "$($item.Output): 512x512 RGBA, translation ($($item.X),$($item.Y))"
    } finally { $source.Dispose(); $target.Dispose() }
}
