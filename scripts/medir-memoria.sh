#!/bin/bash
# Mede a memória do Vortex em uma tabela de texto:
#   - RSS de cada processo iniciado pelo start-vortex.sh (pid files em logs/), sozinho e com os filhos
#   - uso de memória de cada container vortex-* (docker stats --no-stream)
#   - total da VM do WSL (free -m) e RAM livre do Windows (powershell.exe pela interop do WSL)
# Sempre sai com 0, mesmo com partes paradas.
# Uso: scripts/medir-memoria.sh

cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 0
set +e
# ${#texto} conta caracteres (não bytes) só com locale UTF-8: rótulos com acento ficam alinhados
export LC_ALL=C.UTF-8

# Texto alinhado à esquerda numa coluna de largura fixa (printf %-Ns conta bytes)
pad() {
    local text=$1 width=$2 len
    len=${#text}
    printf '%s%*s' "$text" $(( width > len ? width - len : 0 )) ''
}

rss_kb() {
    awk '/^VmRSS:/ { print $2 }' "/proc/$1/status" 2>/dev/null
}

# PID e todos os descendentes
descendants() {
    local pid=$1 child
    echo "$pid"
    for child in $(pgrep -P "$pid" 2>/dev/null); do
        descendants "$child"
    done
}

mb() {
    awk -v k="${1:-0}" 'BEGIN { printf "%.0f", k / 1024 }'
}

label_for() {
    case "$1" in
        backend) echo "Backend principal" ;;
        backend-jvm) echo "  JVM filha do spring-boot:run" ;;
        auth-backend) echo "Backend de autorização" ;;
        auth-backend-jvm) echo "  JVM filha do quarkus:dev" ;;
        frontend) echo "Frontend principal (Vite)" ;;
        auth-frontend) echo "Frontend de autorização (Vite)" ;;
        aot-train-backend) echo "Treino AOT do backend" ;;
        aot-train-auth) echo "Treino AOT do auth" ;;
        network-monitor) echo "Monitor de redes Docker" ;;
        *) echo "$1" ;;
    esac
}

# Converte "123.4MiB", "1.2GiB", "512KiB", "800kB", "1.1GB" em MB
to_mb() {
    awk -v v="$1" 'BEGIN {
        n = v; sub(/[A-Za-z]+$/, "", n); u = v; sub(/^[0-9.]+/, "", u)
        f = 1
        if (u == "B") f = 1 / 1048576
        else if (u == "KiB" || u == "kB" || u == "KB") f = 1 / 1024
        else if (u == "MiB" || u == "MB") f = 1
        else if (u == "GiB" || u == "GB") f = 1024
        printf "%.0f", n * f
    }'
}

line() {
    printf '%s\n' "------------------------------------------------------------------------------"
}

echo ""
echo "MEMÓRIA DO VORTEX ($(date '+%Y-%m-%d %H:%M:%S'))"
line
printf '%-34s %8s %10s %14s\n' "Processo (pid file em logs/)" "PID" "RSS (MB)" "c/ filhos (MB)"
line

declare -A seen=()
total_kb=0
found_proc=0
shopt -s nullglob
for pidfile in logs/*.pid; do
    name=$(basename "$pidfile" .pid)
    pid=$(tr -dc '0-9' < "$pidfile")
    if [[ -z "$pid" ]] || ! kill -0 "$pid" 2>/dev/null; then
        printf '%s %8s %10s %14s\n' "$(pad "$(label_for "$name")" 34)" "${pid:--}" "parado" "-"
        continue
    fi
    found_proc=1
    own=$(rss_kb "$pid")
    tree=0
    for p in $(descendants "$pid"); do
        r=$(rss_kb "$p")
        tree=$(( tree + ${r:-0} ))
        if [[ -z "${seen[$p]}" ]]; then
            seen[$p]=1
            total_kb=$(( total_kb + ${r:-0} ))
        fi
    done
    printf '%s %8s %10s %14s\n' "$(pad "$(label_for "$name")" 34)" "$pid" "$(mb "$own")" "$(mb "$tree")"
done
shopt -u nullglob
if [[ $found_proc -eq 0 ]]; then
    echo "(nenhum processo do launcher rodando)"
fi
line
printf '%-34s %8s %10s\n' "Total dos processos (sem repetir)" "" "$(mb "$total_kb")"
echo ""

printf '%-34s %21s %8s\n' "Container" "Uso / limite" "% lim."
line
containers_mb=0
if command -v docker > /dev/null 2>&1 && docker info > /dev/null 2>&1; then
    names=$(docker ps --filter "name=vortex-" --format '{{.Names}}' 2>/dev/null)
    if [[ -n "$names" ]]; then
        # shellcheck disable=SC2086
        while IFS='|' read -r cname usage perc; do
            [[ -z "$cname" ]] && continue
            used=${usage%% / *}
            limit=${usage##* / }
            printf '%-34s %21s %8s\n' "$cname" "$(to_mb "$used") / $(to_mb "$limit") MB" "$perc"
            containers_mb=$(( containers_mb + $(to_mb "$used") ))
        done < <(docker stats --no-stream --format '{{.Name}}|{{.MemUsage}}|{{.MemPerc}}' $names 2>/dev/null | sort)
    else
        echo "(nenhum container vortex-* rodando)"
    fi
else
    echo "(Docker indisponível)"
fi
line
printf '%-34s %21s\n' "Total dos containers" "$containers_mb MB"
printf '%-34s %21s\n' "Total do Vortex (processos + containers)" "$(( $(mb "$total_kb") + containers_mb )) MB"
echo ""

echo "VM do WSL (free -m)"
line
read -r _ mem_total mem_used _ _ _ mem_avail < <(free -m 2>/dev/null | grep '^Mem:')
read -r _ swap_total swap_used _ < <(free -m 2>/dev/null | grep '^Swap:')
printf '%s total %6s MB | usada %6s MB | disponível %6s MB\n' "$(pad "Memória" 34)" "${mem_total:-?}" "${mem_used:-?}" "${mem_avail:-?}"
printf '%s total %6s MB | usada %6s MB\n' "$(pad "Swap" 34)" "${swap_total:-?}" "${swap_used:-?}"

PS_EXE=$(command -v powershell.exe 2>/dev/null)
[[ -z "$PS_EXE" && -x /mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe ]] && PS_EXE=/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe
if [[ -n "$PS_EXE" ]]; then
    win=$(timeout 30 "$PS_EXE" -NoProfile -Command '$o = Get-CimInstance Win32_OperatingSystem; "{0} {1}" -f [int]($o.FreePhysicalMemory/1024), [int]($o.TotalVisibleMemorySize/1024)' 2>/dev/null | tr -d '\r')
    read -r win_free win_total <<< "$win"
    if [[ -n "$win_free" ]]; then
        printf '%-34s livre %6s MB de %s MB\n' "Windows (host)" "$win_free" "$win_total"
    else
        printf '%-34s %s\n' "Windows (host)" "não foi possível ler (powershell.exe não respondeu)"
    fi
else
    printf '%-34s %s\n' "Windows (host)" "powershell.exe indisponível (interop do WSL desligada?)"
fi
line
exit 0
