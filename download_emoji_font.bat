@echo off
echo Downloading Noto Emoji font...

if not exist fonts mkdir fonts

powershell -Command "$ProgressPreference = 'SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://noto-website-2.storage.googleapis.com/pkgs/NotoEmoji-unhinted.zip' -OutFile 'fonts\NotoEmoji.zip' -UseBasicParsing"

if exist fonts\NotoEmoji.zip (
    echo Extracting font...
    powershell -Command "Expand-Archive -Path 'fonts\NotoEmoji.zip' -DestinationPath 'fonts' -Force"
    
    if exist "fonts\NotoEmoji-Regular.ttf" (
        echo ✓ Font downloaded successfully!
        del fonts\NotoEmoji.zip
    ) else (
        echo ✗ Font extraction failed
    )
) else (
    echo ✗ Download failed
)
