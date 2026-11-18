#!/usr/bin/env bash
# Firewall for the AI team's Flask service on the Jetson host (systemd melon_AI, port 5000).
#
# The service has no login and listens on 0.0.0.0, so anyone on the campus network could call it.
# Only these may reach the port:
#   lo          the Jetson itself (curl 127.0.0.1:5000)
#   docker0     the default Docker bridge
#   br-+        compose networks, which is how the backend container reaches host.docker.internal:5000
#   tailscale0  our own tailnet devices, for debugging from a laptop
# Everything else (LAN/Wi-Fi, campus network) is dropped. The AI service and its code are not touched.
#
# Usage (as root):
#   ai-port.sh install   copy to /usr/local/sbin, add a systemd unit so the rule survives reboots, apply
#   ai-port.sh apply     (re)apply the rule now; idempotent
#   ai-port.sh remove    remove the rule (port open again)
#   ai-port.sh status    show the rule with packet counters
#   ai-port.sh uninstall remove the rule, the unit and the copied script
#
# Settings (environment, or /etc/default/fertimata-ai-firewall when run by systemd):
#   AI_PORT=5000
#   AI_ALLOW_IFACES="lo docker0 br-+ tailscale0"   (iptables interface names; "+" is a wildcard)
set -euo pipefail

PORT="${AI_PORT:-5000}"
ALLOW_IFACES="${AI_ALLOW_IFACES:-lo docker0 br-+ tailscale0}"
CHAIN="FERTIMATA-AI"
INSTALL_PATH="/usr/local/sbin/fertimata-ai-firewall"
UNIT_PATH="/etc/systemd/system/fertimata-ai-firewall.service"

tools() {
    local t
    for t in iptables ip6tables; do
        command -v "$t" >/dev/null 2>&1 && echo "$t"
    done
}

remove_rule() {
    local ipt="$1"
    while "$ipt" -w -C INPUT -p tcp --dport "$PORT" -j "$CHAIN" 2>/dev/null; do
        "$ipt" -w -D INPUT -p tcp --dport "$PORT" -j "$CHAIN"
    done
    if "$ipt" -w -n -L "$CHAIN" >/dev/null 2>&1; then
        "$ipt" -w -F "$CHAIN"
        "$ipt" -w -X "$CHAIN"
    fi
}

apply_rule() {
    local ipt="$1" iface
    remove_rule "$ipt"
    "$ipt" -w -N "$CHAIN"
    for iface in $ALLOW_IFACES; do
        "$ipt" -w -A "$CHAIN" -i "$iface" -j ACCEPT
    done
    "$ipt" -w -A "$CHAIN" -j DROP
    # Top of INPUT so it wins over ufw or other accept rules.
    "$ipt" -w -I INPUT 1 -p tcp --dport "$PORT" -j "$CHAIN"
}

need_root() {
    if [ "$(id -u)" -ne 0 ]; then
        echo "Jalankan dengan sudo." >&2
        exit 1
    fi
}

cmd="${1:-status}"
case "$cmd" in
    apply)
        need_root
        for ipt in $(tools); do apply_rule "$ipt"; done
        echo "Port $PORT hanya terbuka untuk: $ALLOW_IFACES"
        ;;
    remove)
        need_root
        for ipt in $(tools); do remove_rule "$ipt"; done
        echo "Aturan port $PORT dihapus (port terbuka lagi)."
        ;;
    status)
        need_root
        for ipt in $(tools); do
            echo "== $ipt =="
            if "$ipt" -w -n -L "$CHAIN" >/dev/null 2>&1; then
                "$ipt" -w -n -v -L "$CHAIN"
            else
                echo "(tidak ada aturan: port $PORT terbuka)"
            fi
        done
        ;;
    install)
        need_root
        install -m 0755 "$0" "$INSTALL_PATH"
        cat >"$UNIT_PATH" <<EOF
[Unit]
Description=FertiMata: batasi port layanan AI ($PORT) ke Jetson, Docker dan Tailscale
After=network-pre.target docker.service
Wants=network-pre.target

[Service]
Type=oneshot
RemainAfterExit=yes
EnvironmentFile=-/etc/default/fertimata-ai-firewall
ExecStart=$INSTALL_PATH apply
ExecStop=$INSTALL_PATH remove

[Install]
WantedBy=multi-user.target
EOF
        systemctl daemon-reload
        systemctl enable fertimata-ai-firewall.service >/dev/null
        systemctl restart fertimata-ai-firewall.service
        echo "Terpasang: $UNIT_PATH (aktif saat boot)."
        "$INSTALL_PATH" status
        ;;
    uninstall)
        need_root
        systemctl disable --now fertimata-ai-firewall.service 2>/dev/null || true
        rm -f "$UNIT_PATH"
        systemctl daemon-reload
        for ipt in $(tools); do remove_rule "$ipt"; done
        rm -f "$INSTALL_PATH"
        echo "Firewall port AI dilepas."
        ;;
    *)
        echo "Pemakaian: $0 install|apply|remove|status|uninstall" >&2
        exit 2
        ;;
esac
