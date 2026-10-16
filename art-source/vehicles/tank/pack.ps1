# Mechanical uniform component registration. Preserve generated proportions and alpha.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$repository = Split-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) -Parent
$source = [System.Drawing.Bitmap]::new((Join-Path $PSScriptRoot 'master.png'))
$registration = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'registration.json') -Raw | ConvertFrom-Json
$target = [System.Drawing.Bitmap]::new($registration.outputSize[0],$registration.outputSize[1],[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($target)
try {
    if ($source.Width -ne $registration.sourceSize[0] -or $source.Height -ne $registration.sourceSize[1]) {
        throw 'Re-measure registration when replacing the tank master'
    }
    $g.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $frameWidth = $target.Width / $registration.frames.Count
    for ($index=0; $index -lt $registration.frames.Count; $index++) {
        $frame = $registration.frames[$index]
        $r = $frame.sourceRect
        $scale = $frame.scale
        $x = $frame.targetCenter[0] - ($frame.sourceCenter[0] - $r[0]) * $scale
        $y = $frame.targetCenter[1] - ($frame.sourceCenter[1] - $r[1]) * $scale
        $g.SetClip([System.Drawing.Rectangle]::new($index*$frameWidth,0,$frameWidth,$target.Height))
        $g.DrawImage($source,[System.Drawing.RectangleF]::new($x,$y,$r[2]*$scale,$r[3]*$scale),
                [System.Drawing.RectangleF]::new($r[0],$r[1],$r[2],$r[3]),[System.Drawing.GraphicsUnit]::Pixel)
    }
    $target.Save((Join-Path $repository 'mod/graphics/battle/vehicles/army-tank.png'),[System.Drawing.Imaging.ImageFormat]::Png)
} finally { $g.Dispose(); $target.Dispose(); $source.Dispose() }
