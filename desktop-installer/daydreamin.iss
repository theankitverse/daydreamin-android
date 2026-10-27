; Daydreamin's Windows installer (Inno Setup 6): the app built by Gradle (createDistributable),
; in a setup wizard dressed in Daydreamin's colours. CI builds it with:
;   ISCC /DAppVersion=1.2.3 /DAppDir=<…\app\build\compose\binaries\main\app\Daydreamin> /DOutDir=<dir> daydreamin.iss
; It installs for the current user (no administrator needed), replaces an older .msi install of
; Daydreamin if there is one, and keeps your library: that lives in %APPDATA%\Daydreamin.

#ifndef AppVersion
  #define AppVersion "1.0.0"
#endif
#ifndef AppDir
  #define AppDir "..\app\build\compose\binaries\main\app\Daydreamin"
#endif
#ifndef OutDir
  #define OutDir "..\dist"
#endif

[Setup]
AppId={{B2C4D6E8-1A3F-4C5B-8D7E-9F0A1B2C3D4E}
AppName=Daydreamin
AppVersion={#AppVersion}
AppVerName=Daydreamin {#AppVersion}
AppPublisher=Ankit
AppPublisherURL=https://github.com/theankitverse/daydreamin-android
AppSupportURL=https://github.com/theankitverse/daydreamin-android/issues
AppCopyright=© 2026 Ankit
VersionInfoVersion={#AppVersion}
VersionInfoProductName=Daydreamin
VersionInfoDescription=Daydreamin setup
DefaultDirName={autopf}\Daydreamin
DefaultGroupName=Daydreamin
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir={#OutDir}
OutputBaseFilename=Daydreamin-setup
SetupIconFile=..\app\desktop-icons\daydreamin.ico
UninstallDisplayIcon={app}\Daydreamin.exe
UninstallDisplayName=Daydreamin
WizardStyle=modern
WizardSizePercent=110
WizardImageFile=wizard-large.bmp,wizard-large@2x.bmp
WizardSmallImageFile=wizard-small.bmp,wizard-small@2x.bmp
WizardImageStretch=yes
Compression=lzma2/ultra64
SolidCompression=yes
CloseApplications=force
RestartApplications=no

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Messages]
WelcomeLabel1=Welcome to Daydreamin
WelcomeLabel2=Music from everywhere, played beautifully.%n%nThis installs Daydreamin [name/ver] on your computer — mixes made for you, lyrics that follow along, and songs you can keep for offline.%n%nNo account. No ads. If Daydreamin is open, it closes while it updates; your library stays as it is.
SelectDirDesc=Where should Daydreamin go?
ReadyLabel1=Daydreamin is ready to install.
FinishedHeadingLabel=Daydreamin is ready
FinishedLabel=Daydreamin is installed. Find it in the Start menu, or open it now.
ClickFinish=Click Finish to close setup.

[Tasks]
Name: "desktopicon"; Description: "Put Daydreamin on the desktop"; GroupDescription: "Shortcuts:"

[Files]
Source: "{#AppDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[InstallDelete]
; A new version's files replace the old ones completely (the bundled Java runtime changes between versions).
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"

[Icons]
Name: "{autoprograms}\Daydreamin"; Filename: "{app}\Daydreamin.exe"
Name: "{autodesktop}\Daydreamin"; Filename: "{app}\Daydreamin.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\Daydreamin.exe"; Description: "Open Daydreamin"; Flags: nowait postinstall skipifsilent

[Code]
// Daydreamin 1.0 shipped as a Windows Installer package (.msi). Installing this removes it first,
// so the computer has one Daydreamin, not two. (Its upgrade code, fixed since 1.0.)
const
  OldMsiUpgradeCode = '{6F3B2A1C-8D4E-4F5A-9B7C-2E1D0A9F8C34}';

function MsiEnumRelatedProducts(UpgradeCode: String; Reserved: DWORD; Index: DWORD; ProductBuf: String): UINT;
  external 'MsiEnumRelatedProductsW@msi.dll stdcall';

procedure RemoveOldMsi();
var
  Buf: String;
  ResultCode, Tries: Integer;
begin
  for Tries := 1 to 3 do
  begin
    Buf := StringOfChar(#0, 39);
    if MsiEnumRelatedProducts(OldMsiUpgradeCode, 0, 0, Buf) <> 0 then Exit;
    Log('Removing the older Daydreamin (' + Copy(Buf, 1, 38) + ')');
    Exec(ExpandConstant('{sys}\msiexec.exe'), '/x ' + Copy(Buf, 1, 38) + ' /qn /norestart', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
  end;
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssInstall then RemoveOldMsi();
end;
