#define AppName "Inventory Control"
#ifndef AppVersion
  #define AppVersion "1.0.0"
#endif

[Setup]
AppId={{A4F5BE99-B9D5-48A6-B04E-CB47D8CFB137}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher=Inventory Control
DefaultDirName={autopf}\Inventory Control
DefaultGroupName=Inventory Control
DisableDirPage=no
DisableProgramGroupPage=yes
Uninstallable=yes
UninstallDisplayIcon={app}\Inventory Control.exe
OutputDir=..\..\target\distribution
OutputBaseFilename=InventoryControl-Setup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog commandline
ArchitecturesInstallIn64BitMode=x64compatible

[Dirs]
Name: "{localappdata}\Inventory Control\data"; Flags: uninsneveruninstall
Name: "{localappdata}\Inventory Control\backups"; Flags: uninsneveruninstall

[Files]
Source: "..\..\target\jpackage\Inventory Control\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\Inventory Control"; Filename: "{app}\runtime\bin\javaw.exe"; Parameters: "-jar ""{app}\app\controle-estoque-1.0.0.jar"""; WorkingDir: "{app}"; IconFilename: "{app}\Inventory Control.exe"
Name: "{group}\Desinstalar Inventory Control"; Filename: "{uninstallexe}"
Name: "{autodesktop}\Inventory Control"; Filename: "{app}\runtime\bin\javaw.exe"; Parameters: "-jar ""{app}\app\controle-estoque-1.0.0.jar"""; WorkingDir: "{app}"; IconFilename: "{app}\Inventory Control.exe"

[Run]
Filename: "{app}\runtime\bin\javaw.exe"; Parameters: "-jar ""{app}\app\controle-estoque-1.0.0.jar"""; WorkingDir: "{app}"; Description: "Iniciar Inventory Control"; Flags: nowait postinstall skipifsilent
