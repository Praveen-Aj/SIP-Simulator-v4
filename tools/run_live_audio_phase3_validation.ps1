param(
    [int]$Cycles = 20,
    [int]$MediaPort = 41000
)

$ErrorActionPreference = "Stop"

Write-Host "=== Live Audio Phase 3 Validation Runner ==="
Write-Host "Cycles: $Cycles"
Write-Host "MediaPort: $MediaPort"

Write-Host ""
Write-Host "Compiling soak validator..."
javac -encoding UTF-8 -cp "build;lib/*;src" -d build -sourcepath src src\sipinspector\Media\LiveAudioSoakValidationMain.java
if ($LASTEXITCODE -ne 0) {
    throw "Failed to compile LiveAudioSoakValidationMain"
}

Write-Host "Running soak validator..."
$output = java -cp "build;lib/*" sipinspector.Media.LiveAudioSoakValidationMain $Cycles $MediaPort
$output | Write-Host

$resultLine = $output | Where-Object { $_ -like "RESULT *" } | Select-Object -Last 1
if (-not $resultLine) {
    throw "RESULT line not found in validator output"
}

Write-Host ""
Write-Host "Parsed RESULT:"
Write-Host $resultLine

$latestLog = Get-ChildItem "$env:USERPROFILE\Desktop\SIP Simulator logs\UDP" -Filter *.log | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($latestLog) {
    Write-Host ""
    Write-Host "Latest UDP debug log:"
    Write-Host $latestLog.FullName
    Write-Host ""
    Write-Host "Recent RTP/SRTP diagnostics:"
    Get-Content $latestLog.FullName | Select-String -Pattern "\[RTP |\[SRTP |\[LiveAudioDiag\]" | Select-Object -Last 40 | ForEach-Object { $_.Line } | Write-Host
}

Write-Host ""
Write-Host "Phase 3 local validation run complete."
