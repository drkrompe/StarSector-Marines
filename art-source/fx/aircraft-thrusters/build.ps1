# Code-owned analytic effect masks, tinted by the production engine renderer.
# New procedural textures, not edits of the vanilla art. White RGB, smooth alpha.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$repository = Split-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) -Parent
$outputDirectory = Join-Path $repository 'mod/graphics/battle/fx'
[System.IO.Directory]::CreateDirectory($outputDirectory) | Out-Null

function Smooth-Step([double]$value) {
    $t = [Math]::Clamp($value, 0.0, 1.0)
    return $t * $t * (3.0 - 2.0 * $t)
}

foreach ($kind in @('glow', 'flame')) {
    $width = 64
    $height = if ($kind -eq 'glow') { 64 } else { 128 }
    $texture = [System.Drawing.Bitmap]::new($width, $height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        for ($y = 0; $y -lt $height; $y++) {
            for ($x = 0; $x -lt $width; $x++) {
                $lateral = (2.0 * $x / ($width - 1)) - 1.0
                if ($kind -eq 'glow') {
                    $vertical = (2.0 * $y / ($height - 1)) - 1.0
                    $radius = [Math]::Sqrt($lateral * $lateral + $vertical * $vertical)
                    $opacity = [Math]::Exp(-4.5 * $radius * $radius) * (Smooth-Step ((1.0 - $radius) / 0.35))
                } else {
                    # Nose-up texture convention: nozzle at the bottom, tail at the top.
                    $tailDistance = 1.0 - $y / ($height - 1.0)
                    $halfWidth = 0.9 * [Math]::Pow(1.0 - $tailDistance, 0.72)
                    $side = if ($halfWidth -gt 0) { [Math]::Abs($lateral) / $halfWidth } else { 1.0 }
                    $crossSection = [Math]::Pow([Math]::Max(0.0, 1.0 - $side * $side), 2.0)
                    $baseFade = Smooth-Step ($tailDistance / 0.025)
                    $tipFade = Smooth-Step ((1.0 - $tailDistance) / 0.2)
                    $opacity = $crossSection * $baseFade * $tipFade * [Math]::Pow(1.0 - $tailDistance, 0.6)
                }
                # Explicit transparent borders prevent a filtered rectangle at any tint/intensity.
                if ($x -eq 0 -or $x -eq $width - 1 -or $y -eq 0 -or $y -eq $height - 1) { $opacity = 0 }
                $alpha = [int][Math]::Round(255.0 * [Math]::Clamp($opacity, 0.0, 1.0))
                $texture.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($alpha, 255, 255, 255))
            }
        }
        $path = Join-Path $outputDirectory ('aircraft-engine-' + $kind + '.png')
        $texture.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
        Write-Output ('{0}: {1} x {2}, {3} bytes' -f $kind, $width, $height, (Get-Item -LiteralPath $path).Length)
    } finally { $texture.Dispose() }
}
