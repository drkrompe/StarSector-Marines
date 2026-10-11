# Encode approved full-frame masters for runtime. No crop, recolor or composition changes.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$repository = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$destination = Join-Path $repository 'mod/graphics/illustrations/wayline'
New-Item -ItemType Directory -Path $destination -Force | Out-Null
$codec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object MimeType -eq 'image/jpeg'
foreach ($name in @('relief', 'counterattack', 'junction', 'last-lift', 'relay-nine')) {
    $image = [System.Drawing.Image]::FromFile((Join-Path $PSScriptRoot "$name.master.png"))
    $parameters = [System.Drawing.Imaging.EncoderParameters]::new(1)
    $parameters.Param[0] = [System.Drawing.Imaging.EncoderParameter]::new([System.Drawing.Imaging.Encoder]::Quality, [long]92)
    try { $image.Save((Join-Path $destination "$name.jpg"), $codec, $parameters) }
    finally { $parameters.Dispose(); $image.Dispose() }
}
