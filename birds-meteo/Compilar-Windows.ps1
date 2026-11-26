# Requer Android Studio instalado, com SDK e Command-line Tools. PowerShell 5.1+.
# Compila e testa; não cria um ficheiro APK substituto quando a compilação falha.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$ProgressPreference = 'SilentlyContinue'
$out = Join-Path $PSScriptRoot 'Birds_Meteo_v0.8_RC_debug.apk'
if (Test-Path $out) { Remove-Item $out }
$env:ANDROID_HOME = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (!(Test-Path $env:ANDROID_HOME)) { throw 'SDK Android não encontrado. Instala Android Studio e o SDK no SDK Manager.' }
if (!$env:JAVA_HOME) {
    $jbr = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
    if (Test-Path (Join-Path $jbr 'bin\java.exe')) { $env:JAVA_HOME = $jbr }
}
if (!$env:JAVA_HOME -or !(Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { throw 'Configura JAVA_HOME para o JDK 17/21 ou o JBR do Android Studio.' }
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$gradleVersion = '8.9'
$tools = Join-Path $env:LOCALAPPDATA 'BirdsMeteo\build-tools'
New-Item -ItemType Directory -Force $tools | Out-Null
$gradle = Join-Path $tools "gradle-$gradleVersion\bin\gradle.bat"
if (!(Test-Path $gradle)) {
    $zip = Join-Path $tools "gradle-$gradleVersion-bin.zip"
    $uri = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"
    $checksum = ([string](Invoke-RestMethod "$uri.sha256")).Trim()
    if ($checksum -notmatch '^[a-fA-F0-9]{64}$') { throw 'Checksum Gradle inválido.' }
    Invoke-WebRequest -UseBasicParsing $uri -OutFile $zip
    if ((Get-FileHash $zip -Algorithm SHA256).Hash -ne $checksum) { Remove-Item $zip; throw 'Checksum Gradle não corresponde.' }
    Expand-Archive $zip -DestinationPath $tools -Force
    Remove-Item $zip
}
if (!(Test-Path (Join-Path $env:ANDROID_HOME 'platforms\android-35\android.jar')) -or !(Test-Path (Join-Path $env:ANDROID_HOME 'build-tools\35.0.0\apksigner.bat'))) {
    $manager = Get-ChildItem (Join-Path $env:ANDROID_HOME 'cmdline-tools') -Filter sdkmanager.bat -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if (!$manager) { throw 'Instala Android SDK Command-line Tools no SDK Manager.' }
    Write-Host 'Aceita as licenças oficiais do SDK se forem apresentadas.'
    & $manager.FullName --sdk_root="$env:ANDROID_HOME" 'platforms;android-35' 'build-tools;35.0.0'
    if ($LASTEXITCODE -ne 0) { throw 'Instalação de componentes do SDK falhou.' }
}
& $gradle --no-daemon :app:clean :app:assembleDebug :app:lintDebug
if ($LASTEXITCODE -ne 0) { throw 'A compilação ou lint falhou. Não será entregue um APK.' }
$apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk'
if (!(Test-Path $apk)) { throw 'APK não encontrado.' }
$signer = Join-Path $env:ANDROID_HOME 'build-tools\35.0.0\apksigner.bat'
& $signer verify --verbose $apk
if ($LASTEXITCODE -ne 0) { throw 'Falha na verificação de assinatura.' }
$out = Join-Path $PSScriptRoot 'Birds_Meteo_v0.8_RC_debug.apk'
Copy-Item $apk $out -Force
Get-FileHash $out -Algorithm SHA256
Write-Host "APK de desenvolvimento criado: $out"
