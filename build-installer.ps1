# Builds the all-in-one Windows installer: dist\installer\Ushantha Super Mart POS-<version>.exe
# Needs: JDK 17+ (jpackage), Maven, and WiX Toolset 3.x (candle.exe/light.exe) - set the three paths below if they differ.
param(
    [string]$JavaHome = $(if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot" }),
    [string]$MavenBin = "$env:USERPROFILE\tools\apache-maven-3.9.9\bin",
    [string]$WixBin   = "$env:USERPROFILE\tools\wix314",
    [string]$Version  = "1.0"
)
$ErrorActionPreference = "Stop"
$env:JAVA_HOME = $JavaHome
$env:PATH = "$JavaHome\bin;$MavenBin;$WixBin;$env:PATH"
Set-Location $PSScriptRoot

mvn -B -q clean package -DskipTests
New-Item -ItemType Directory -Force package-input | Out-Null
Copy-Item target\EasyMartPOS-1.0-SNAPSHOT-all.jar package-input\ -Force

# WiX wizard artwork: installer-assets\resources\main.wxs is jpackage's JDK 21 template plus two WixVariable
# lines that read the bitmaps from EASYMART_ASSETS. If you change JDK major version, re-copy that template.
$assets = Join-Path $PSScriptRoot "installer-assets"
$resources = Join-Path $assets "resources"
$env:EASYMART_ASSETS = $assets

Remove-Item dist\installer -Recurse -Force -ErrorAction SilentlyContinue
jpackage --type exe --name "Ushantha Super Mart POS" --app-version $Version --vendor "Ushantha Super Mart" `
    --description "Ushantha Super Mart POS" --icon "$assets\app.ico" --resource-dir $resources `
    --input package-input --main-jar EasyMartPOS-1.0-SNAPSHOT-all.jar --main-class com.mycompany.easymartpos.EasyMartPOS `
    --app-content bundle\mariadb --win-menu --win-shortcut --win-dir-chooser --dest dist\installer
Get-ChildItem dist\installer
