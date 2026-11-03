# ============================================================
# AI 工具规则入口同步校验脚本
#
# 唯一真源：AGENTS.md
#   生成类入口：内容 = 文件头 + AGENTS.md 正文，由本脚本生成/校验
#   指针类入口：只指向 AGENTS.md，由本脚本校验引用是否存在
#
# 用法：
#   pwsh scripts/sync-agent-rules.ps1 -Check   # 只校验（默认），失败 exit 1
#   pwsh scripts/sync-agent-rules.ps1 -Write   # 以 AGENTS.md 为准重新生成
#
# CI：见 docs/agent-rules/tool-matrix.md §2「接入 CI」
# ============================================================
param(
    [switch]$Check,
    [switch]$Write
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$sourcePath = Join-Path $repoRoot 'AGENTS.md'

if (-not (Test-Path $sourcePath)) {
    Write-Host "[ERROR] 真源不存在：$sourcePath" -ForegroundColor Red
    exit 2
}

$lf = "`n"
$autoHeader = @(
    '<!-- AUTO-GENERATED from AGENTS.md. DO NOT EDIT.'
    '     修改规则请改 AGENTS.md，然后运行: pwsh scripts/sync-agent-rules.ps1 -Write -->'
    ''
) -join $lf

function Normalize-Text([string]$text) {
    if ($null -eq $text) { return '' }
    # 统一换行符并去掉尾部空白，避免 CRLF/末尾空行造成假差异
    return ($text -replace "`r`n", $lf).TrimEnd()
}

$sourceBody = Normalize-Text ([IO.File]::ReadAllText($sourcePath, [Text.Encoding]::UTF8))

# 生成类入口：路径 → frontmatter 前导（无 frontmatter 则为空）
$generated = [ordered]@{
    'GEMINI.md'                                    = ''
    '.github/copilot-instructions.md'              = ''
    '.cursor/rules/project-rules.mdc'              = "---`ndescription: 项目规则（自动生成自 AGENTS.md，请勿手改）`nalwaysApply: true`n---`n"
    '.windsurf/rules/project-rules.md'             = ''
    '.clinerules/00-project-rules.md'              = ''
    '.roo/rules/00-project-rules.md'               = ''
    '.continue/rules/00-project-rules.md'          = "---`ndescription: 项目规则（自动生成自 AGENTS.md，请勿手改）`nalwaysApply: true`n---`n"
    '.junie/guidelines.md'                         = ''
    'CONVENTIONS.md'                               = ''
    '.trae/rules/project_rules.md'                 = "---`ndescription: 项目规则（自动生成自 AGENTS.md，请勿手改）`nalwaysApply: true`n---`n"
    '.qoder/rules/00-project-rules.md'             = "---`ndescription: 项目规则（自动生成自 AGENTS.md，请勿手改）`nalwaysApply: true`n---`n"
}

# 指针类入口：路径 → 必须命中的正则（指向 AGENTS.md / 其入口文件）
$pointers = [ordered]@{
    'CLAUDE.md'                    = '@AGENTS\.md'
    '.aider.conf.yml'              = 'CONVENTIONS\.md'
    'docs/agent-rules/00-index.md' = 'AGENTS\.md'
    'docs/agent-rules/tool-matrix.md' = 'AGENTS\.md'
    '.joycode/rules/00-index.md'   = 'AGENTS\.md|docs/agent-rules'
    '.serena/project.yml'          = 'AGENTS\.md'
}

$failures = New-Object System.Collections.Generic.List[string]
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Get-FirstDiff([string]$expected, [string]$actual) {
    $expLines = $expected -split "`n"
    $actLines = $actual -split "`n"
    $max = [Math]::Min($expLines.Count, $actLines.Count)
    for ($i = 0; $i -lt $max; $i++) {
        if ($expLines[$i] -ne $actLines[$i]) {
            return "第 $($i + 1) 行：期望 [$($expLines[$i])] / 实际 [$($actLines[$i])]"
        }
    }
    return "行数不一致：期望 $($expLines.Count) 行 / 实际 $($actLines.Count) 行"
}

foreach ($rel in $generated.Keys) {
    $target = Join-Path $repoRoot $rel
    $expected = Normalize-Text ($generated[$rel] + $autoHeader + $sourceBody)

    if ($Write) {
        $dir = Split-Path -Parent $target
        if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
        [IO.File]::WriteAllText($target, $expected + $lf, $utf8NoBom)
        Write-Host "[WRITE] $rel" -ForegroundColor Green
        continue
    }

    if (-not (Test-Path $target)) {
        $failures.Add("[缺失] $rel（生成类入口不存在，运行 -Write 生成）")
        continue
    }

    $actual = Normalize-Text ([IO.File]::ReadAllText($target, [Text.Encoding]::UTF8))
    if ($actual -ne $expected) {
        $failures.Add("[不同步] $rel -> $(Get-FirstDiff $expected $actual)")
    }
}

if (-not $Write) {
    foreach ($rel in $pointers.Keys) {
        $target = Join-Path $repoRoot $rel
        if (-not (Test-Path $target)) {
            $failures.Add("[缺失] $rel（指针类入口不存在）")
            continue
        }
        $text = [IO.File]::ReadAllText($target, [Text.Encoding]::UTF8)
        if ($text -notmatch $pointers[$rel]) {
            $failures.Add("[指针失效] $rel 未包含指向 AGENTS.md 的引用（期望匹配 /$($pointers[$rel])/）")
        }
    }
}

if ($Write) {
    Write-Host ""
    Write-Host "已生成 $($generated.Count) 个生成类入口；指针类入口未改动。" -ForegroundColor Cyan
    exit 0
}

if ($failures.Count -gt 0) {
    Write-Host ""
    Write-Host "规则入口校验失败（$($failures.Count) 项）：" -ForegroundColor Red
    $failures | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
    Write-Host ""
    Write-Host "修复：改 AGENTS.md 后运行 pwsh scripts/sync-agent-rules.ps1 -Write" -ForegroundColor Yellow
    exit 1
}

Write-Host "[OK] 生成类 $($generated.Count) 个与 AGENTS.md 同步；指针类 $($pointers.Count) 个均有效。" -ForegroundColor Green
exit 0
