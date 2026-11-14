@echo off
echo Creating emoji icon images using PowerShell...

powershell -Command ^
"Add-Type -AssemblyName System.Drawing; ^
$icons = @{ ^
    'file' = @{emoji='📁'; color='52,152,219'}; ^
    'edit' = @{emoji='✏️'; color='46,204,113'}; ^
    'options' = @{emoji='⚙️'; color='155,89,182'}; ^
    'tools' = @{emoji='🛠️'; color='230,126,34'}; ^
    'help' = @{emoji='❓'; color='231,76,60'}; ^
    'stats' = @{emoji='📊'; color='52,152,219'}; ^
    'export' = @{emoji='💾'; color='46,204,113'}; ^
    'sdp' = @{emoji='📊'; color='155,89,182'} ^
}; ^
foreach($key in $icons.Keys) { ^
    $bmp = New-Object System.Drawing.Bitmap(16,16); ^
    $g = [System.Drawing.Graphics]::FromImage($bmp); ^
    $g.SmoothingMode = 'AntiAlias'; ^
    $g.TextRenderingHint = 'AntiAlias'; ^
    $rgb = $icons[$key].color -split ','; ^
    $color = [System.Drawing.Color]::FromArgb([int]$rgb[0],[int]$rgb[1],[int]$rgb[2]); ^
    $brush = New-Object System.Drawing.SolidBrush($color); ^
    $g.FillEllipse($brush, 2, 2, 12, 12); ^
    $pen = New-Object System.Drawing.Pen($color.Darker(), 1); ^
    $g.DrawEllipse($pen, 2, 2, 12, 12); ^
    $bmp.Save(\"src\Icons\$key.png\"); ^
    $g.Dispose(); ^
    $bmp.Dispose(); ^
    Write-Host \"Created $key.png\"; ^
}"

echo.
echo ✓ Emoji icons created in src\Icons\
