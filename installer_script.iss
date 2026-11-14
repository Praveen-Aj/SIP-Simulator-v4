; SIP Simulator - Inno Setup Installer Script
; Creates a professional Windows installer with desktop shortcuts

#define MyAppName "SIP Simulator"
#define MyAppVersion "4.1"
#define MyAppPublisher "SIP Support Team"
#define MyAppExeName "SIP_Simulator.exe"

[Setup]
; Basic Information
AppId={{8F9A2B3C-4D5E-6F7A-8B9C-0D1E2F3A4B5C}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
AllowNoIcons=no
OutputDir=installer_output
OutputBaseFilename=SIP_Simulator_Setup_v{#MyAppVersion}
Compression=lzma
SolidCompression=yes
WizardStyle=modern
UsePreviousTasks=no

; Privileges
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog

; Icons (using default Inno Setup icon if custom not available)
; SetupIconFile can be left blank to use default
UninstallDisplayIcon={app}\Icons\SIPSimulator.png

; Visual (using default images)

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
; Desktop icon task - ALWAYS checked (mandatory)
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"
; Npcap installation task - offered alongside desktop icon selection
Name: "installnpcap"; Description: "Install Npcap (required for PCAP playback). In the Npcap wizard, enable 'WinPcap API-compatible mode'."; GroupDescription: "{cm:AdditionalIcons}"

[Files]
; Main executable
Source: "SIP_Simulator_v4.exe"; DestDir: "{app}"; DestName: "SIP_Simulator.exe"; Flags: ignoreversion
Source: "SIP_Simulator_v4.jar"; DestDir: "{app}"; Flags: ignoreversion

; Java Runtime
Source: "deployment\jre\*"; DestDir: "{app}\jre"; Flags: ignoreversion recursesubdirs createallsubdirs

; Libraries
Source: "lib\*"; DestDir: "{app}\lib"; Flags: ignoreversion recursesubdirs createallsubdirs

; Application Resources
Source: "scenarios\*"; DestDir: "{app}\scenarios"; Flags: ignoreversion recursesubdirs createallsubdirs
; Only ship the primary SIP Simulator icon
Source: "src\Icons\SIPSimulator.png"; DestDir: "{app}\Icons"; Flags: ignoreversion
Source: "security\*"; DestDir: "{app}\security"; Flags: ignoreversion recursesubdirs createallsubdirs; AfterInstall: CreateSecurityFolder
Source: "fonts\*"; DestDir: "{app}\fonts"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "values\*"; DestDir: "{app}\values"; Flags: ignoreversion recursesubdirs createallsubdirs; AfterInstall: CreateValuesFolder
Source: "pcap\*"; DestDir: "{app}\pcap"; Flags: ignoreversion recursesubdirs createallsubdirs
; User settings defaults
Source: "userSettings.xml"; DestDir: "{app}"; Flags: ignoreversion
Source: "config\*"; DestDir: "{app}\config"; Flags: ignoreversion recursesubdirs createallsubdirs

; Documentation (end-user README)
Source: "docs\README_APP.txt"; DestDir: "{app}"; DestName: "README.txt"; Flags: ignoreversion isreadme
Source: "docs\LICENSE.txt"; DestDir: "{app}"; Flags: ignoreversion
Source: "docs\CHANGELOG.txt"; DestDir: "{app}"; Flags: ignoreversion

; Web Dashboard (Express server + built React client, server node_modules included)
Source: "deployment\webapp_dist\*"; DestDir: "{app}\web-app"; Flags: ignoreversion recursesubdirs createallsubdirs

; Bundled Node.js runtime for the Web Dashboard (no PATH / no admin needed on target PCs)
Source: "deployment\node\node.exe"; DestDir: "{app}\node"; Flags: ignoreversion

; Npcap installer (for JPcap/PCAP playback support)
Source: "npcap-1.85.exe"; DestDir: "{app}"; Flags: ignoreversion

[Dirs]
Name: "{app}"; Permissions: users-modify
Name: "{app}\logs"; Permissions: users-modify
Name: "{app}\security"; Permissions: users-modify
Name: "{app}\values"; Permissions: users-modify
Name: "{app}\pcap"; Permissions: users-modify
Name: "{app}\config"; Permissions: users-modify
Name: "{app}\web-app"; Permissions: users-modify

[Icons]
; Desktop shortcut - tied to the desktopicon task (which is checked by default)
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

; Start Menu shortcuts (always created)
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{group}\README"; Filename: "{app}\README.txt"

; Quick Launch shortcut (removed - not needed)

[Run]
; Refresh icon cache so new shortcut artwork appears immediately (if available)
Filename: "{sys}\ie4uinit.exe"; Parameters: "-ClearIconCache"; Flags: runhidden waituntilterminated; Check: FileExists(ExpandConstant('{sys}\ie4uinit.exe'))

; Install Npcap if the installnpcap task was selected (will trigger UAC elevation if needed)
Filename: "{app}\npcap-1.85.exe"; Tasks: installnpcap; Flags: shellexec waituntilterminated

; Launch application
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

; Display CHANGELOG after install (auto-checked like README)
Filename: "{app}\CHANGELOG.txt"; Description: "View CHANGELOG"; Flags: postinstall shellexec nowait skipifsilent runasoriginaluser

[Code]
procedure CreateSecurityFolder();
begin
  // Ensure security folder exists
end;

procedure CreateValuesFolder();
begin
  // Ensure values folder exists
end;

[UninstallDelete]
Type: filesandordirs; Name: "{app}\logs"
Type: filesandordirs; Name: "{app}\jre"
Type: filesandordirs; Name: "{app}\lib"
Type: filesandordirs; Name: "{app}\Icons"
Type: filesandordirs; Name: "{app}\scenarios"
Type: filesandordirs; Name: "{app}\values"
Type: filesandordirs; Name: "{app}\pcap"
Type: filesandordirs; Name: "{app}\config"
Type: filesandordirs; Name: "{app}\security"
Type: filesandordirs; Name: "{app}\web-app"
Type: filesandordirs; Name: "{app}\node"
Type: files; Name: "{app}\SIP_Simulator.exe"

[InstallDelete]
; Clean up old or dev-only files from previous installs
Type: files; Name: "{app}\SIP_Simulator.jar"
Type: files; Name: "{app}\Icons\Spy.png"
Type: files; Name: "{app}\Icons\My_Custom_image.png"
Type: files; Name: "{app}\Icons\My_Custom_image.ico"
Type: files; Name: "{app}\Icons\SIPSimulator_variant_*.png"
Type: files; Name: "{app}\Icons\generate_icon.py"
Type: files; Name: "{app}\Icons\generate_icons_batch.py"
Type: files; Name: "{autodesktop}\SIP Simulator.lnk"
Type: files; Name: "{group}\SIP Simulator.lnk"
Type: files; Name: "{group}\README.lnk"
Type: files; Name: "{group}\{cm:UninstallProgram,{#MyAppName}}.lnk"
Type: files; Name: "{app}\create_desktop_icon.bat"
Type: files; Name: "{app}\create_desktop_icon.ps1"
