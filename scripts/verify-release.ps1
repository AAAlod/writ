param(
    [switch]$SkipGameTest,
    [switch]$SkipBuild,
    # Keep in sync with the current required GameTest count.
    [int]$MinGameTests = 105
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
$oldJavaToolOptions = $env:JAVA_TOOL_OPTIONS
try {
    # The expected release version comes from build.gradle, the single source of truth.
    $versionMatch = [regex]::Match([System.IO.File]::ReadAllText((Join-Path $root 'build.gradle')), "(?m)^\s*version\s*=\s*['""]([^'""]+)['""]")
    if (-not $versionMatch.Success) { throw 'Could not read the project version from build.gradle.' }
    $expectedVersion = $versionMatch.Groups[1].Value
    if ($SkipBuild -or $SkipGameTest) {
        Write-Host 'Partial verification. Use a full run to associate a clean build and all tests with the release candidate.'
    }
    # ForgeGradle otherwise attempts an online certificate check even with --offline.
    $env:JAVA_TOOL_OPTIONS = "$oldJavaToolOptions -Dnet.minecraftforge.gradle.check.certs=false".Trim()
    if (-not $SkipBuild) {
        # Merge JVM stderr in cmd.exe so Windows PowerShell 5.1 does not treat it as a terminating error.
        & cmd /c ".\gradlew.bat clean build --offline --no-daemon 2>&1"
        if ($LASTEXITCODE -ne 0) { throw 'Offline build failed.' }
    }
    if (-not $SkipGameTest) {
        # Forge itself performs a version check independently of Gradle's --offline.
        $config = Join-Path $root 'run-gametest/config/fml.toml'
        $hadConfig = Test-Path -LiteralPath $config
        $originalConfig = if ($hadConfig) { [System.IO.File]::ReadAllBytes($config) } else { $null }
        try {
            New-Item -ItemType Directory -Force (Split-Path -Parent $config) | Out-Null
            $contents = if ($hadConfig) { [System.Text.Encoding]::UTF8.GetString($originalConfig) } else { '' }
            if ($contents -match '(?m)^\s*versionCheck\s*=') {
                $contents = [regex]::Replace($contents, '(?m)^(\s*versionCheck\s*=\s*)(true|false)', '${1}false')
            } else { $contents += "`nversionCheck = false`n" }
            [System.IO.File]::WriteAllText($config, $contents)
            # Redirect through cmd.exe: Windows PowerShell 5.1 turns JVM stderr lines
            # (e.g. "Picked up JAVA_TOOL_OPTIONS") into terminating NativeCommandError
            # under $ErrorActionPreference = 'Stop'.
            $output = & cmd /c ".\gradlew.bat runGameTestServer --offline --no-daemon -x downloadAssets 2>&1"
            $exitCode = $LASTEXITCODE
        } finally {
            if ($hadConfig) { [System.IO.File]::WriteAllBytes($config, $originalConfig) }
            else { Remove-Item -LiteralPath $config -Force -ErrorAction SilentlyContinue }
        }
        $output | ForEach-Object { Write-Host $_ }
        $summary = @($output | Select-String -Pattern 'All (\d+) required tests passed')
        if ($exitCode -ne 0 -or $summary.Count -eq 0 -or [int]$summary[-1].Matches[0].Groups[1].Value -lt $MinGameTests) {
            throw "GameTest failed or fewer than $MinGameTests required tests passed."
        }
    }
    $jars = @(Get-ChildItem -File 'build/libs/writ-*.jar')
    if ($jars.Count -ne 1) { throw "Expected exactly one release JAR, found $($jars.Count)." }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jars[0].FullName)
    try {
        $bad = @($zip.Entries | Where-Object { $_.FullName -match 'GameTests' -or $_.FullName.StartsWith('data/writ/structures/') -or $_.FullName -eq 'data/writ/writs/presets/example.json' })
        if ($bad.Count -ne 0) { throw "Test assets found in release JAR: $($bad.FullName -join ', ')" }
        if (@($zip.Entries | Where-Object { $_.Name -eq 'writ-common.toml' }).Count -ne 0) { throw 'Runtime Writ config must not be packaged in the JAR.' }
        $legacyNamespace = 'trade' + 'contracts'
        $legacyData = 'trade' + '_contracts'
        $legacyPaths = @($zip.Entries | Where-Object { $_.FullName.Contains($legacyNamespace) -or $_.FullName.Contains($legacyData) })
        if ($legacyPaths.Count -ne 0) { throw 'Legacy package or resource paths found in release JAR.' }
        foreach ($required in @('ke/writ/Writ.class', 'ke/writ/ConfigFile.class', 'ke/writ/WritIntegratedMod.class',
            'ke/writ/resonance/ResonanceModule.class', 'assets/writ/models/item/resonance_device.json', 'assets/writ/textures/item/resonance_device.png', 'assets/writ/models/item/trade_writ.json', 'assets/writ/models/item/chaos_writ.json', 'assets/writ/lang/en_us.json', 'assets/writ/lang/zh_cn.json', 'assets/writ/lang/fr_fr.json', 'data/writ/writs/presets/a_unlock.json',
            'ke/writ/HeroGifts.class', 'data/writ/loot_modifiers/hero_gifts.json', 'data/writ/recipes/random_writ.json',
            'data/writ/advancements/recipes/random_writ.json', 'assets/writ/models/item/random_writ.json',
            'data/writ/recipes/resonance_device.json', 'data/writ/advancements/recipes/resonance_device.json',
            'assets/writ/textures/item/random_writ.png', 'assets/writ/textures/item/random_writ.png.mcmeta')) {
            if ($null -eq $zip.GetEntry($required)) { throw "Missing required JAR entry: $required" }
        }
        $unexpected = @($zip.Entries | Where-Object {
            $_.FullName -eq 'ke/writ/WritCoreMod.class' -or $_.FullName.StartsWith('ke/writ/resonance/dev/')
        })
        if ($unexpected.Count -ne 0) { throw 'Standalone entrypoints or dev classes found in the integrated JAR.' }
        $languageKeys = @(
            'item.writ.random_writ', 'item.writ.resonance_device', 'writ.effect.contract_exemption.applied', 'writ.tooltip.random.summary', 'writ.tooltip.random.use',
            'writ.tooltip.random.notice', 'writ.tooltip.chaos.summary', 'writ.tooltip.expand_hint',
            'writ.message.random.empty_pool'
        )
        $deviceResources = Join-Path $root 'modules/resonance-device/src/main/resources'
        $englishDevice = [System.IO.File]::ReadAllText((Join-Path $deviceResources 'assets/writ/lang/en_us.json')) | ConvertFrom-Json
        foreach ($language in @('en_us', 'zh_cn', 'fr_fr')) {
            $languagePath = "assets/writ/lang/$language.json"
            $languageEntry = $zip.GetEntry($languagePath)
            if ($null -eq $languageEntry) { throw "Missing required JAR entry: $languagePath" }
            $languageReader = [System.IO.StreamReader]::new($languageEntry.Open(), [System.Text.Encoding]::UTF8)
            try { $translations = $languageReader.ReadToEnd() | ConvertFrom-Json } finally { $languageReader.Dispose() }
            foreach ($key in $languageKeys) {
                if ($null -eq $translations.PSObject.Properties[$key]) {
                    throw "Missing language key '$key' in JAR entry $languagePath."
                }
            }
            $defaultEffectKey = 'writ.resonance.tooltip.default_effect'
            $hostFragment = Join-Path $root "integration/src/main/resources/$languagePath"
            $expectedTooltip = ([System.IO.File]::ReadAllText($hostFragment) | ConvertFrom-Json).$defaultEffectKey
            if ([string]::IsNullOrWhiteSpace($expectedTooltip) -or
                $translations.$defaultEffectKey -cne $expectedTooltip) {
                throw "Missing Writ default-effect tooltip in $languagePath."
            }
            $deviceFragment = [System.IO.File]::ReadAllText((Join-Path $deviceResources $languagePath)) | ConvertFrom-Json
            foreach ($english in $englishDevice.PSObject.Properties) {
                if ($english.Name -ceq $defaultEffectKey) { continue }
                $localized = $translations.PSObject.Properties[$english.Name].Value
                if ([string]::IsNullOrWhiteSpace($localized) -or $localized -cne $deviceFragment.PSObject.Properties[$english.Name].Value) {
                    throw "Missing or changed device translation '$($english.Name)' in $languagePath."
                }
                $englishSlots = @([regex]::Matches($english.Value, '%%|%s') | Where-Object { $_.Value -ceq '%s' })
                $localizedSlots = @([regex]::Matches($localized, '%%|%s') | Where-Object { $_.Value -ceq '%s' })
                if ($localizedSlots.Count -ne $englishSlots.Count -or $localized -match '%\d+\$') {
                    throw "Unexpected format arguments for '$($english.Name)' in $languagePath."
                }
            }
        }
        $chaosPath = 'data/writ/writs/chaos/config.json'
        $chaosEntries = @($zip.Entries | Where-Object { $_.FullName -ceq $chaosPath })
        if ($chaosEntries.Count -ne 1) { throw "Expected one integrated $chaosPath in the JAR." }
        $chaosReader = [System.IO.StreamReader]::new($chaosEntries[0].Open())
        try { $chaos = $chaosReader.ReadToEnd() | ConvertFrom-Json } finally { $chaosReader.Dispose() }
        if ($chaos.exclude -cnotcontains 'writ:resonance_device') { throw 'Integrated chaos pools must exclude the resonance device by default.' }
        $glmPath = 'data/forge/loot_modifiers/global_loot_modifiers.json'
        $glmEntry = $zip.GetEntry($glmPath)
        if ($null -eq $glmEntry) { throw "Missing required JAR entry: $glmPath" }
        $glmReader = [System.IO.StreamReader]::new($glmEntry.Open(), [System.Text.Encoding]::UTF8)
        try { $glm = $glmReader.ReadToEnd() | ConvertFrom-Json } finally { $glmReader.Dispose() }
        if ($glm.replace -cne $false -or $glm.entries -notcontains 'writ:chest_loot' -or $glm.entries -notcontains 'writ:hero_gifts') {
            throw 'Global loot modifiers must retain chest loot and hero gifts with replace=false.'
        }
        $preset = $zip.GetEntry('data/writ/writs/presets/terracotta.json')
        if ($null -eq $preset) { throw 'Missing built-in terracotta preset.' }
        $presetReader = [System.IO.StreamReader]::new($preset.Open())
        try { $presetData = $presetReader.ReadToEnd() | ConvertFrom-Json } finally { $presetReader.Dispose() }
        if ($presetData.variants.Count -ne 16 -or $presetData.trade.result.item -ne 'minecraft:terracotta') { throw 'Terracotta preset must contain the default and 16 variants.' }
        $entry = $zip.GetEntry('META-INF/mods.toml')
        if ($null -eq $entry) { throw 'Missing META-INF/mods.toml.' }
        $reader = [System.IO.StreamReader]::new($entry.Open())
        try { $metadata = $reader.ReadToEnd() } finally { $reader.Dispose() }
        if (-not $metadata.Contains('license="MIT"') -or -not $metadata.Contains('versionRange="[47.4.10,48)"')) {
            throw 'JAR metadata does not contain the required license and Forge version.'
        }
        if (-not $metadata.Contains('modId="writ"') -or -not $metadata.Contains('version="${file.jarVersion}"')) { throw 'JAR mod identity or version substitution missing.' }
        $manifestEntry = $zip.GetEntry('META-INF/MANIFEST.MF')
        if ($null -eq $manifestEntry) { throw 'JAR manifest missing.' }
        $manifestReader = [System.IO.StreamReader]::new($manifestEntry.Open())
        try { $manifest = $manifestReader.ReadToEnd() } finally { $manifestReader.Dispose() }
        if (-not $manifest.Contains("Implementation-Version: $expectedVersion")) { throw "JAR manifest version is not $expectedVersion." }
    } finally { $zip.Dispose() }
    Write-Host "JAR: $($jars[0].FullName)"
    Write-Host "Bytes: $($jars[0].Length)"
    $sha = [System.Security.Cryptography.SHA256]::Create()
    $stream = [System.IO.File]::OpenRead($jars[0].FullName)
    try { $hash = -join ($sha.ComputeHash($stream) | ForEach-Object { $_.ToString('X2') }) }
    finally { $stream.Dispose(); $sha.Dispose() }
    Write-Host "SHA-256: $hash"
} catch {
    Write-Error $_ -ErrorAction Continue
    exit 1
} finally {
    $env:JAVA_TOOL_OPTIONS = $oldJavaToolOptions
    Pop-Location
}
exit 0
