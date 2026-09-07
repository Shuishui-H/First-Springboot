$ErrorActionPreference = 'Stop'
$serverRoot = $PSScriptRoot
$jarPath = Join-Path $serverRoot 'target\erp-server-0.0.1-SNAPSHOT.jar'

if (-not (Test-Path -LiteralPath $jarPath)) {
    Write-Host '未找到已编译的后端程序，请先在 erp-server 目录执行 mvn clean package -DskipTests。' -ForegroundColor Yellow
    Read-Host '按 Enter 关闭'
    exit 1
}

$securePassword = Read-Host '请输入 MySQL 数据库密码（输入内容不会显示，也不会写入文件）' -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    Write-Host '正在以 MySQL 持久化模式启动 ERP 后端：http://localhost:8080' -ForegroundColor Green
    & java -jar $jarPath
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
}
