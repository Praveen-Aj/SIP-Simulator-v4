# Generate 8 kHz mono WAV files for SIP Simulator spoken tones using Windows TTS
# RBT_8k.wav, Hold_8k.wav, Guide_8k.wav, Conversation_8k.wav under ..\tones

Add-Type -AssemblyName System.Speech

$tonesDir = Join-Path $PSScriptRoot "..\tones"
if (-not (Test-Path $tonesDir)) {
    New-Item -ItemType Directory -Path $tonesDir | Out-Null
}

$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
$format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(
    8000,
    [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen,
    [System.Speech.AudioFormat.AudioChannel]::Mono
)

$toneTexts = @{
    "RBT" = "You are hearing a ringing tone played by the SIP Simulator application.";
    "Hold" = "You are hearing a hold tone played by the SIP Simulator application. Please remain on the line.";
    "Guide" = "This is a media test from the SIP Simulator. You should hear this message clearly on your phone.";
    "Conversation" = "You are now hearing the conversation media from the SIP Simulator.";
}

foreach ($name in $toneTexts.Keys) {
    $wavPath = Join-Path $tonesDir ("{0}_8k.wav" -f $name)
    Write-Host "[INFO] Generating" $wavPath
    $synth.SetOutputToWaveFile($wavPath, $format)
    $synth.Speak($toneTexts[$name])
    $synth.SetOutputToNull()
}

$synth.Dispose()
Write-Host "[DONE] Spoken tone WAVs generated in" $tonesDir
