param(
    [string]$DbHost = $env:DB_HOST,
    [string]$DbPort = $env:DB_PORT,
    [string]$DbName = $env:DB_NAME,
    [string]$DbUser = $env:DB_USER,
    [string]$DbPassword = $env:DB_PASSWORD,
    [string]$BackupDir = $env:BACKUP_DIR,
    [int]$RetainCount = 10,
    [ValidateSet("scheduled", "manual")]
    [string]$TriggerType = "scheduled",
    [long]$ActorUserId = 0
)

$ErrorActionPreference = "Stop"
$Utf8NoBom = New-Object System.Text.UTF8Encoding $false
[Console]::OutputEncoding = $Utf8NoBom
$OutputEncoding = $Utf8NoBom

if ([string]::IsNullOrWhiteSpace($DbHost)) { $DbHost = "localhost" }
if ([string]::IsNullOrWhiteSpace($DbPort)) { $DbPort = "3306" }
if ([string]::IsNullOrWhiteSpace($DbName)) { $DbName = "test" }
if ([string]::IsNullOrWhiteSpace($DbUser)) { $DbUser = "root" }
if ($null -eq $DbPassword) { $DbPassword = "" }
if ([string]::IsNullOrWhiteSpace($BackupDir)) { $BackupDir = "backups" }
if ($RetainCount -lt 1) { $RetainCount = 10 }

function Convert-ToSqlValue {
    param([object]$Value)
    if ($null -eq $Value) {
        return "NULL"
    }
    $Text = [string]$Value
    $Text = $Text.Replace("\", "\\").Replace("'", "''").Replace("`r", " ").Replace("`n", " ")
    return "'$Text'"
}

function Invoke-MySql {
    param([string]$Sql)
    $Args = @(
        "--defaults-extra-file=$script:DefaultsFile",
        "--batch",
        "--raw",
        "--skip-column-names",
        "--database=$DbName",
        "--execute=$Sql"
    )
    $Output = & mysql @Args 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw ($Output -join [Environment]::NewLine)
    }
    return $Output
}

function Update-BackupRecord {
    param(
        [string]$RecordId,
        [string]$Status,
        [object]$FileSize,
        [string]$Message
    )
    if ([string]::IsNullOrWhiteSpace($RecordId)) {
        return
    }
    $FileSizeSql = if ($null -eq $FileSize) { "file_size_bytes = NULL" } else { "file_size_bytes = $FileSize" }
    $Sql = @"
UPDATE backup_records
   SET status = $(Convert-ToSqlValue $Status),
       ended_at = CURRENT_TIMESTAMP,
       $FileSizeSql,
       message = $(Convert-ToSqlValue $Message)
 WHERE id = $RecordId
"@
    Invoke-MySql $Sql | Out-Null
}

function Remove-OldBackups {
    $DbSql = Convert-ToSqlValue $DbName
    $Sql = @"
SELECT id, backup_directory, file_name
  FROM backup_records
 WHERE database_name = $DbSql
   AND status = 'success'
 ORDER BY started_at DESC, id DESC
 LIMIT $RetainCount, 18446744073709551615
"@
    $Rows = Invoke-MySql $Sql
    foreach ($Row in $Rows) {
        if ([string]::IsNullOrWhiteSpace($Row)) {
            continue
        }
        $Parts = $Row -split "`t"
        if ($Parts.Count -lt 3) {
            continue
        }
        $OldRecordId = $Parts[0]
        $OldPath = Join-Path $Parts[1] $Parts[2]
        if (Test-Path -LiteralPath $OldPath) {
            Remove-Item -LiteralPath $OldPath -Force
        }
        $Message = "Pruned by retention policy; keeping latest $RetainCount backup files."
        $UpdateSql = @"
UPDATE backup_records
   SET status = 'deleted',
       message = $(Convert-ToSqlValue $Message)
 WHERE id = $OldRecordId
"@
        Invoke-MySql $UpdateSql | Out-Null
    }
}

if (-not (Get-Command mysql -ErrorAction SilentlyContinue)) {
    throw "mysql client was not found in PATH."
}
if (-not (Get-Command mysqldump -ErrorAction SilentlyContinue)) {
    throw "mysqldump was not found in PATH."
}

New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null
$BackupDir = (Resolve-Path -LiteralPath $BackupDir).Path
$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$FileName = "${DbName}_${Timestamp}.sql"
$BackupPath = Join-Path $BackupDir $FileName
$script:DefaultsFile = Join-Path ([System.IO.Path]::GetTempPath()) ("mysql-client-" + [Guid]::NewGuid() + ".cnf")
$RecordId = $null

try {
    @"
[client]
host=$DbHost
port=$DbPort
user=$DbUser
password=$DbPassword
default-character-set=utf8mb4
"@ | Set-Content -LiteralPath $script:DefaultsFile -Encoding ASCII

    $ActorSql = if ($ActorUserId -gt 0) { [string]$ActorUserId } else { "NULL" }
    $InsertSql = @"
INSERT INTO backup_records(database_name, file_name, backup_directory, status, trigger_type, created_by, started_at, message)
VALUES($(Convert-ToSqlValue $DbName), $(Convert-ToSqlValue $FileName), $(Convert-ToSqlValue $BackupDir), 'started', $(Convert-ToSqlValue $TriggerType), $ActorSql, CURRENT_TIMESTAMP, 'mysqldump started');
SELECT LAST_INSERT_ID();
"@
    $RecordId = (Invoke-MySql $InsertSql | Select-Object -Last 1).Trim()

    $DumpArgs = @(
        "--defaults-extra-file=$script:DefaultsFile",
        "--single-transaction",
        "--routines",
        "--triggers",
        "--events",
        "--default-character-set=utf8mb4",
        "--databases",
        $DbName,
        "--result-file=$BackupPath"
    )
    $DumpOutput = & mysqldump @DumpArgs 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw ($DumpOutput -join [Environment]::NewLine)
    }

    $FileSize = (Get-Item -LiteralPath $BackupPath).Length
    Update-BackupRecord -RecordId $RecordId -Status "success" -FileSize $FileSize -Message "Backup completed."
    Remove-OldBackups
    Write-Output "Backup completed: $BackupPath"
} catch {
    $Message = $_.Exception.Message
    try {
        Update-BackupRecord -RecordId $RecordId -Status "failed" -FileSize $null -Message $Message
    } catch {
        Write-Warning "Failed to update backup_records: $($_.Exception.Message)"
    }
    if (Test-Path -LiteralPath $BackupPath) {
        Remove-Item -LiteralPath $BackupPath -Force -ErrorAction SilentlyContinue
    }
    throw
} finally {
    if (Test-Path -LiteralPath $script:DefaultsFile) {
        Remove-Item -LiteralPath $script:DefaultsFile -Force -ErrorAction SilentlyContinue
    }
}
