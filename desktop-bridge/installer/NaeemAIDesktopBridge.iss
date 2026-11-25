#define AppName "Naeem AI Desktop Bridge"
#define AppVersion "1.0.0-alpha1"
#define AppPublisher "Naeem AI"

[Setup]
AppId={{9C51A0EF-8F5B-4D4A-A2B6-65C8D63C1A11}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
DefaultDirName={autopf}\Naeem AI Desktop Bridge
DefaultGroupName={#AppName}
OutputDir=..\installer-output
OutputBaseFilename=NaeemAI-DesktopBridge-Setup-alpha1
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
ArchitecturesInstallIn64BitMode=x64
UninstallDisplayIcon={app}\NaeemAIDesktopBridge.exe

[Files]
Source: "..\publish\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\Naeem AI Desktop Bridge"; Filename: "{app}\NaeemAIDesktopBridge.exe"
Name: "{autodesktop}\Naeem AI Desktop Bridge"; Filename: "{app}\NaeemAIDesktopBridge.exe"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "ساخت میانبر روی دسکتاپ"; GroupDescription: "میانبرها:"

[Run]
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall add rule name=""Naeem AI Desktop Bridge TCP"" dir=in action=allow protocol=TCP localport=8765 profile=any"; Flags: runhidden
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall add rule name=""Naeem AI Desktop Bridge UDP"" dir=in action=allow protocol=UDP localport=8766 profile=any"; Flags: runhidden
Filename: "{app}\NaeemAIDesktopBridge.exe"; Description: "اجرای برنامه پس از نصب"; Flags: postinstall nowait skipifsilent

[UninstallRun]
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall delete rule name=""Naeem AI Desktop Bridge TCP"""; Flags: runhidden
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall delete rule name=""Naeem AI Desktop Bridge UDP"""; Flags: runhidden
