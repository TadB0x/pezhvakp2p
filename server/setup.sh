#!/usr/bin/env bash
# Pezhvak Server — one-command setup
# Usage: curl -fsSL https://raw.githubusercontent.com/TadB0x/pezhvakp2p/main/server/setup.sh | bash
set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
info()    { echo -e "${CYAN}[INFO]${NC} $*"; }
success() { echo -e "${GREEN}[OK]${NC}   $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC} $*"; }
error()   { echo -e "${RED}[ERR]${NC}  $*"; exit 1; }

echo ""
echo "  ██████╗ ███████╗███████╗██╗  ██╗██╗   ██╗ █████╗ ██╗  ██╗"
echo "  ██╔══██╗██╔════╝╚══███╔╝██║  ██║██║   ██║██╔══██╗██║ ██╔╝"
echo "  ██████╔╝█████╗    ███╔╝ ███████║██║   ██║███████║█████╔╝ "
echo "  ██╔═══╝ ██╔══╝   ███╔╝  ██╔══██║╚██╗ ██╔╝██╔══██║██╔═██╗ "
echo "  ██║     ███████╗███████╗██║  ██║ ╚████╔╝ ██║  ██║██║  ██╗"
echo "  ╚═╝     ╚══════╝╚══════╝╚═╝  ╚═╝  ╚═══╝  ╚═╝  ╚═╝╚═╝  ╚═╝"
echo "  Pezhvak P2P — Server Setup v1.0.0"
echo ""

# ─── Prerequisites ────────────────────────────────────────────────────────────

command -v docker  >/dev/null 2>&1 || error "Docker not found. Install: https://docs.docker.com/engine/install/"
command -v openssl >/dev/null 2>&1 || error "openssl not found. Install with your package manager."

DOCKER_COMPOSE="docker compose"
$DOCKER_COMPOSE version >/dev/null 2>&1 || DOCKER_COMPOSE="docker-compose"
$DOCKER_COMPOSE version >/dev/null 2>&1 || error "docker compose not found."

success "Docker and compose found"

# ─── Clone / download server files ───────────────────────────────────────────

INSTALL_DIR="${PEZHVAK_DIR:-$HOME/pezhvak-server}"
mkdir -p "$INSTALL_DIR"
cd "$INSTALL_DIR"

if [ ! -f docker-compose.yml ]; then
  info "Downloading server files…"
  curl -fsSL "https://raw.githubusercontent.com/TadB0x/pezhvakp2p/main/server/docker-compose.yml" -o docker-compose.yml
  curl -fsSL "https://raw.githubusercontent.com/TadB0x/pezhvakp2p/main/server/config/relay.toml"   -o config/relay.toml 2>/dev/null || \
    (mkdir -p config && curl -fsSL "https://raw.githubusercontent.com/TadB0x/pezhvakp2p/main/server/config/relay.toml" -o config/relay.toml)
  mkdir -p news-server
  curl -fsSL "https://raw.githubusercontent.com/TadB0x/pezhvakp2p/main/server/news-server/server.js"     -o news-server/server.js
  curl -fsSL "https://raw.githubusercontent.com/TadB0x/pezhvakp2p/main/server/news-server/package.json" -o news-server/package.json
fi

# ─── Generate .env ────────────────────────────────────────────────────────────

if [ ! -f .env ]; then
  info "Generating server configuration…"

  # Generate secp256k1 private key using openssl
  NEWS_PRIVKEY=$(openssl rand -hex 32)

  read -rp "$(echo -e "${CYAN}Enter your domain name (e.g. relay.example.com):${NC} ")" DOMAIN
  read -rp "$(echo -e "${CYAN}Enter email for Let's Encrypt TLS:${NC} ")" EMAIL
  read -rp "$(echo -e "${CYAN}Telegram Bot Token (leave blank to skip):${NC} ")" TG_TOKEN

  cat > .env <<EOF
RELAY_DOMAIN=${DOMAIN}
ACME_EMAIL=${EMAIL}
NEWS_SERVER_PRIVKEY=${NEWS_PRIVKEY}
TELEGRAM_BOT_TOKEN=${TG_TOKEN:-}
EOF

  success ".env created"
  echo ""
  warn "IMPORTANT — Your news server private key:"
  echo "  ${NEWS_PRIVKEY}"
  echo ""
  warn "Compute the pubkey and embed it in the Android app:"
  echo "  NewsRepository.kt → TRUSTED_NEWS_SERVER_KEY"
  echo ""
  warn "The pubkey will also be printed when the news server starts."
  echo ""
  echo "Press Enter to continue…"
  read -r
fi

# ─── Update relay config ──────────────────────────────────────────────────────

DOMAIN=$(grep RELAY_DOMAIN .env | cut -d= -f2)
sed -i "s|YOUR_DOMAIN|${DOMAIN}|g" config/relay.toml 2>/dev/null || true

# ─── npm install for news server ─────────────────────────────────────────────

info "Installing news server dependencies…"
docker run --rm -v "$INSTALL_DIR/news-server:/app" -w /app node:20-alpine npm install --silent

# ─── Start ────────────────────────────────────────────────────────────────────

info "Starting Pezhvak server stack…"
$DOCKER_COMPOSE up -d

# ─── Print pubkey ─────────────────────────────────────────────────────────────

sleep 3
info "Fetching server pubkey…"
PUBKEY=$(curl -s "http://localhost:7001/pubkey" 2>/dev/null | grep -o '"pubkey":"[^"]*"' | cut -d'"' -f4 || echo "")

echo ""
echo "═══════════════════════════════════════════════════════"
success "Pezhvak server is running!"
echo ""
echo "  Nostr relay:  wss://${DOMAIN}  (port 7000 internal)"
echo "  News API:     https://${DOMAIN}/news"
echo ""
if [ -n "$PUBKEY" ]; then
  echo "  Server pubkey (paste into app):"
  echo "  ${PUBKEY}"
  echo ""
  echo "  In Android app → NewsRepository.kt:"
  echo "  const val TRUSTED_NEWS_SERVER_KEY = \"${PUBKEY}\""
fi
echo "═══════════════════════════════════════════════════════"
echo ""
echo "Add your relay to the app:"
echo "  Settings → Nostr Relays → + → wss://${DOMAIN}"
echo ""
echo "Manage:"
echo "  $DOCKER_COMPOSE logs -f     # View logs"
echo "  $DOCKER_COMPOSE down        # Stop"
echo "  $DOCKER_COMPOSE pull && $DOCKER_COMPOSE up -d  # Update"
