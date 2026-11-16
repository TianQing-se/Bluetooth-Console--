
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$deps=Join-Path $env:TEMP BluetoothConsoleBuild
$javac=Get-ChildItem -LiteralPath $deps -Recurse -Filter javac.exe -File | Select-Object -First 1
if(!$javac){throw 'JDK missing'}
$env:JAVA_HOME=$javac.Directory.Parent.FullName
$android=(Get-ChildItem (Join-Path $deps platform) -Recurse -Filter android.jar | Select-Object -First 1).FullName
$tools=(Get-ChildItem (Join-Path $deps tools) -Recurse -Filter aapt.exe | Select-Object -First 1).Directory.FullName
$work=Join-Path $deps ('apk-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path "$work\res\drawable","$work\assets","$work\classes","$work\dex" | Out-Null
Copy-Item -LiteralPath (Join-Path $root 'Bluetooth Console.png') -Destination "$work\res\drawable\icon.png"
$page=Get-Content -Raw -LiteralPath (Join-Path $root 'HC-05.html')
$bridge=Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot bridge.html)
$page=[regex]::new('<script>').Replace($page, ($bridge.Replace('$','$$')+'<script>'),1)
$page=[regex]::Replace($page,'<details><summary>连接设置</summary>.*?</details>','<input hidden id="baud" type="number" value="9600"><button hidden id="applyBaud"></button><span hidden id="baudStatus"></span>')
$page=$page.Replace("'蓝牙串口已连接，串口打开参数为 '+baudRate+' bps。'","'蓝牙串口已连接。'")
$export='$(''exportProfile'').onclick=()=>AndroidBluetooth.exportProfile(JSON.stringify({version:1,fields:state.fields,cards:state.cards},null,2));'+[Environment]::NewLine
$page=[regex]::Replace($page,'(?s)\$\(''exportProfile''\)\.onclick=\(\)=>\{.*?(?=\$\(''importProfile''\))',$export.Replace('$','$$'))
Set-Content -LiteralPath "$work\assets\console.html" -Value $page -Encoding utf8
[regex]::Matches($page,'(?s)<script>(.*?)</script>') | ForEach-Object {
 $_.Groups[1].Value | node --check
 if($LASTEXITCODE){throw 'Invalid JavaScript'}
}
& $javac.FullName -encoding UTF-8 --release 8 -classpath $android -d "$work\classes" (Join-Path $PSScriptRoot MainActivity.java)
if($LASTEXITCODE){throw 'Java compilation failed'}
& (Join-Path $env:JAVA_HOME 'bin\jar.exe') cf "$work\classes.jar" -C "$work\classes" .
if($LASTEXITCODE){throw 'JAR creation failed'}
& "$tools\d8.bat" --lib $android --min-api 26 --output "$work\dex" "$work\classes.jar"
if($LASTEXITCODE){throw 'DEX generation failed'}
& "$tools\aapt.exe" package -f -M (Join-Path $PSScriptRoot AndroidManifest.xml) -S "$work\res" -A "$work\assets" -I $android -F "$work\unsigned.apk"
if($LASTEXITCODE){throw 'Packaging failed'}
Push-Location "$work\dex"
try {
 & "$tools\aapt.exe" add "$work\unsigned.apk" classes.dex
 if($LASTEXITCODE){throw 'DEX packaging failed'}
} finally { Pop-Location }
& "$tools\zipalign.exe" -f 4 "$work\unsigned.apk" "$work\aligned.apk"
if($LASTEXITCODE){throw 'APK alignment failed'}
$key=Join-Path $env:LOCALAPPDATA 'BluetoothConsoleSigning'
New-Item -ItemType Directory $key -Force | Out-Null
$store=Join-Path $key 'mobile.p12'
$secret=Join-Path $key 'signing-password'
if(!(Test-Path -LiteralPath $store)){
 [IO.File]::WriteAllText($secret,[guid]::NewGuid().ToString('N'))
 & (Join-Path $env:JAVA_HOME 'bin\keytool.exe') -genkeypair -keystore $store -storepass:file $secret -alias console -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Bluetooth Console'
 if($LASTEXITCODE){throw 'Key generation failed'}
}
$out=Join-Path $root 'Bluetooth Console Android.apk'
& "$tools\apksigner.bat" sign --ks $store --ks-pass "file:$secret" --out $out "$work\aligned.apk"
if($LASTEXITCODE){throw 'Signing failed'}
& "$tools\apksigner.bat" verify --verbose $out
if($LASTEXITCODE){throw 'Signature verification failed'}
& "$tools\aapt.exe" dump badging $out | Select-String 'package:|sdkVersion:|targetSdkVersion:|launchable-activity:'
Get-Item -LiteralPath $out | Select-Object FullName,Length

