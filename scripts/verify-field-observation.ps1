[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$JarPath
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.Drawing

function Get-RequiredEntry($Archive, [string]$Path) {
    $entries = @($Archive.Entries | Where-Object { $_.FullName -ceq $Path })
    if ($entries.Count -ne 1) {
        throw "Expected exactly one JAR entry '$Path'; found $($entries.Count)."
    }
    return $entries[0]
}

function Read-JsonEntry($Archive, [string]$Path) {
    $entry = Get-RequiredEntry $Archive $Path
    $reader = [System.IO.StreamReader]::new($entry.Open(), [System.Text.UTF8Encoding]::new($false, $true))
    try {
        return ConvertFrom-Json -InputObject $reader.ReadToEnd()
    } catch {
        throw "Invalid JSON in JAR entry '$Path': $_"
    } finally {
        $reader.Dispose()
    }
}

$archive = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $JarPath).ProviderPath)
try {
    $mod = Read-JsonEntry $archive 'fabric.mod.json'
    if ($mod.id -cne 'thaumiclegacy' -or $mod.environment -cne '*' -or $mod.version -match '\$\{' -or -not $mod.version) {
        throw 'Expected expanded Thaumic Legacy metadata with the common environment.'
    }
    foreach ($entrypoint in @('main', 'client')) {
        $entrypointClasses = $mod.entrypoints.$entrypoint
        if (-not $entrypointClasses) {
            throw "Missing '$entrypoint' entrypoint in fabric.mod.json."
        }
        foreach ($className in $entrypointClasses) {
            $null = Get-RequiredEntry $archive (($className -replace '\.', '/') + '.class')
        }
    }
    foreach ($classPath in @(
        'aspect/AspectCatalog',
        'research/AspectDiscoveryData',
        'research/JournalSnapshotPayload',
        'research/ScanFeedbackPayload'
    )) {
        $null = Get-RequiredEntry $archive "dev/thaumiclegacy/$classPath.class"
    }

    $catalog = Read-JsonEntry $archive 'data/thaumiclegacy/thaumiclegacy/aspects/base.json'
    $assignments = Read-JsonEntry $archive 'data/thaumiclegacy/thaumiclegacy/assignments/base.json'
    if ($catalog.aspects.Count -ne 48 -or $assignments.assignments.Count -ne 98) {
        throw 'Expected 48 aspects and 98 bundled specimen profiles.'
    }

    foreach ($item in @('field_lens', 'research_journal')) {
        $model = Read-JsonEntry $archive "assets/thaumiclegacy/models/item/$item.json"
        if ($model.parent -cne 'minecraft:item/generated' -and $model.parent -cne 'item/generated') {
            throw "Expected '$item' to use the generated item model."
        }
        if ($model.textures.layer0 -cne "thaumiclegacy:item/$item") {
            throw "Expected model '$item' to reference its item texture."
        }

        $texture = Get-RequiredEntry $archive "assets/thaumiclegacy/textures/item/$item.png"
        $stream = $texture.Open()
        try {
            $bitmap = [System.Drawing.Image]::FromStream($stream, $false, $true)
            try {
                if ($bitmap.RawFormat.Guid -ne [System.Drawing.Imaging.ImageFormat]::Png.Guid -or $bitmap.Width -ne 16 -or $bitmap.Height -ne 16) {
                    throw "Expected '$item' to be a 16x16 PNG texture."
                }
            } finally {
                $bitmap.Dispose()
            }
        } finally {
            $stream.Dispose()
        }

        $recipe = Read-JsonEntry $archive "data/thaumiclegacy/recipe/$item.json"
        if ($recipe.result.id -cne "thaumiclegacy:$item" -or ($null -ne $recipe.result.count -and $recipe.result.count -ne 1)) {
            throw "Expected recipe '$item' to use result.id and produce one thaumiclegacy:$item."
        }
        if ($recipe.type -ceq 'minecraft:crafting_shaped') {
            if (-not $recipe.pattern -or -not $recipe.key) {
                throw "Missing pattern or key in shaped recipe '$item'."
            }
        } elseif ($recipe.type -ceq 'minecraft:crafting_shapeless') {
            if (-not $recipe.ingredients) {
                throw "Missing ingredients in shapeless recipe '$item'."
            }
        } else {
            throw "Expected crafting recipe type for '$item'; found '$($recipe.type)'."
        }
    }

    $requiredKeys = @(
        'item.thaumiclegacy.field_lens',
        'item.thaumiclegacy.research_journal',
        'message.thaumiclegacy.observed',
        'message.thaumiclegacy.already_observed',
        'message.thaumiclegacy.aspect_scan_hint',
        'message.thaumiclegacy.research_unlocked',
        'hud.thaumiclegacy.aspect_discovered',
        'screen.thaumiclegacy.journal.page',
        'screen.thaumiclegacy.journal.specimens',
        'screen.thaumiclegacy.journal.empty',
        'screen.thaumiclegacy.journal.specimen',
        'screen.thaumiclegacy.journal.kind.block',
        'screen.thaumiclegacy.journal.kind.item',
        'screen.thaumiclegacy.journal.kind.entity',
        'screen.thaumiclegacy.journal.discovered',
        'screen.thaumiclegacy.journal.discovered.empty',
        'screen.thaumiclegacy.journal.aspect.unknown',
        'screen.thaumiclegacy.journal.aspect.amount',
        'screen.thaumiclegacy.journal.locked.title',
        'screen.thaumiclegacy.journal.locked.body',
        'screen.thaumiclegacy.journal.unlocked.title',
        'screen.thaumiclegacy.journal.unlocked.body',
        'observation.minecraft.stone',
        'observation.minecraft.torch',
        'observation.minecraft.oak_log'
    )
    $requiredKeys += @($catalog.aspects | ForEach-Object { "aspect.thaumiclegacy.$($_.id)" })
    $referenceKeys = $null
    foreach ($locale in @('en_us', 'ru_ru')) {
        $translations = Read-JsonEntry $archive "assets/thaumiclegacy/lang/$locale.json"
        $keys = @($translations.PSObject.Properties.Name)
        if ($null -eq $referenceKeys) {
            $referenceKeys = $keys
        } elseif (Compare-Object -ReferenceObject $referenceKeys -DifferenceObject $keys -CaseSensitive) {
            throw 'The English and Russian locale keys differ.'
        }
        foreach ($key in $requiredKeys) {
            $value = $translations.$key
            if ($value -isnot [string] -or [string]::IsNullOrWhiteSpace($value)) {
                throw "Expected a nonempty '$key' translation in $locale."
            }
        }
        foreach ($key in @('message.thaumiclegacy.observed', 'screen.thaumiclegacy.journal.specimens', 'screen.thaumiclegacy.journal.discovered')) {
            if ($translations.$key -cnotmatch '%(?:1\$)?s') {
                throw "Expected the argument placeholder for '$key' in '$locale'."
            }
        }
    }

    Write-Output 'Field Lens, Research Journal, and aspect catalog packaging checks passed.'
} finally {
    $archive.Dispose()
}
