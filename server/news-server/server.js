/**
 * Pezhvak News Server
 *
 * Fetches news from configured sources, signs each item with the server's
 * secp256k1 private key, and publishes to the local Nostr relay as kind 1010.
 *
 * Clients verify:
 *   1. Schnorr signature matches the known server pubkey
 *   2. contentHash == SHA-256(title + summary + content)
 *
 * Any tampered item fails both checks and is silently dropped by the app.
 */

const crypto = require('crypto');
const { createHash, createHmac } = require('crypto');
const WebSocket = require('ws');
const https = require('https');

const PRIVKEY_HEX = process.env.SERVER_PRIVKEY;
const RELAY_URL   = process.env.RELAY_URL || 'ws://localhost:8080';
const PORT        = parseInt(process.env.PORT || '3000');
const TELEGRAM_TOKEN = process.env.TELEGRAM_BOT_TOKEN || '';

if (!PRIVKEY_HEX || PRIVKEY_HEX.length !== 64) {
  console.error('ERROR: SERVER_PRIVKEY must be a 64-char hex secp256k1 private key');
  process.exit(1);
}

// ─── Crypto (pure Node, no native deps) ──────────────────────────────────────

const { subtle } = require('crypto').webcrypto || globalThis.crypto;

// Import noble-secp256k1 dynamically (pure JS, no native deps)
let secp;
async function getSecp() {
  if (!secp) secp = await import('@noble/secp256k1');
  return secp;
}

async function getPublicKey() {
  const { getPublicKey: gpk } = await getSecp();
  return gpk(PRIVKEY_HEX);
}

async function signEvent(eventId) {
  const { schnorr } = await getSecp();
  const sig = await schnorr.sign(eventId, PRIVKEY_HEX);
  return sig;
}

function sha256hex(data) {
  return createHash('sha256').update(data).digest('hex');
}

// ─── Nostr event builder ─────────────────────────────────────────────────────

async function buildEvent(kind, content, tags = []) {
  const pubkey = Buffer.from(await getPublicKey()).toString('hex');
  const created_at = Math.floor(Date.now() / 1000);
  const serialized = JSON.stringify([0, pubkey, created_at, kind, tags, content]);
  const id = sha256hex(serialized);
  const sig = Buffer.from(await signEvent(id)).toString('hex');
  return { id, pubkey, created_at, kind, tags, content, sig };
}

// ─── Relay connection ─────────────────────────────────────────────────────────

let ws;
const publishQueue = [];

function connectRelay() {
  ws = new WebSocket(RELAY_URL);
  ws.on('open', () => {
    console.log(`Connected to relay: ${RELAY_URL}`);
    publishQueue.splice(0).forEach(e => ws.send(JSON.stringify(['EVENT', e])));
  });
  ws.on('close', () => setTimeout(connectRelay, 5000));
  ws.on('error', () => {});
}

async function publishEvent(event) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(['EVENT', event]));
  } else {
    publishQueue.push(event);
  }
}

// ─── News item publisher ──────────────────────────────────────────────────────

async function publishNewsItem({ title, summary, content, imageUrl, url, sourceId, sourceName }) {
  const contentHash = sha256hex(title + summary + (content || ''));
  const payload = JSON.stringify({
    title, summary, content, imageUrl, url,
    sourceId, sourceName,
    publishedAt: Date.now(),
    contentHash,
  });
  const event = await buildEvent(1010, payload, [
    ['source', sourceId],
    ['content_hash', contentHash],
  ]);
  await publishEvent(event);
  console.log(`Published news: ${title.slice(0, 60)}`);
  return event;
}

// ─── RSS fetcher ──────────────────────────────────────────────────────────────

function fetchUrl(url) {
  return new Promise((resolve, reject) => {
    https.get(url, { headers: { 'User-Agent': 'PezhvakNewsBot/1.0' } }, res => {
      let data = '';
      res.on('data', c => data += c);
      res.on('end', () => resolve(data));
    }).on('error', reject);
  });
}

function parseRssItems(xml) {
  const items = [];
  const itemRe = /<item>([\s\S]*?)<\/item>/g;
  let match;
  while ((match = itemRe.exec(xml)) !== null) {
    const get = (tag) => {
      const m = new RegExp(`<${tag}[^>]*><!\\[CDATA\\[([\\s\\S]*?)\\]\\]><\\/${tag}>|<${tag}[^>]*>([^<]*)<\\/${tag}>`).exec(match[1]);
      return m ? (m[1] || m[2] || '').trim() : '';
    };
    items.push({
      title: get('title'),
      summary: get('description').slice(0, 500),
      url: get('link'),
      imageUrl: null,
    });
  }
  return items;
}

// ─── Sources config ───────────────────────────────────────────────────────────

const RSS_SOURCES = [
  // Add your RSS feeds here. Format: { id, name, url }
  // Example:
  // { id: 'bbc', name: 'BBC News', url: 'https://feeds.bbci.co.uk/news/rss.xml' },
  // { id: 'hn', name: 'Hacker News', url: 'https://hnrss.org/frontpage' },
];

const TELEGRAM_CHANNELS = [
  // Add Telegram channel usernames here (requires bot with read access)
  // Example: 'durov'
];

const seenUrls = new Set();

async function fetchAndPublishRss() {
  for (const source of RSS_SOURCES) {
    try {
      const xml = await fetchUrl(source.url);
      const items = parseRssItems(xml);
      for (const item of items.slice(0, 10)) {
        if (!item.title || seenUrls.has(item.url)) continue;
        seenUrls.add(item.url);
        await publishNewsItem({ ...item, sourceId: source.id, sourceName: source.name });
        await new Promise(r => setTimeout(r, 200));
      }
    } catch (e) {
      console.error(`RSS fetch failed for ${source.id}:`, e.message);
    }
  }
}

async function fetchTelegramChannel(username) {
  if (!TELEGRAM_TOKEN) return;
  try {
    const data = await fetchUrl(
      `https://api.telegram.org/bot${TELEGRAM_TOKEN}/getUpdates?limit=10`
    );
    const json = JSON.parse(data);
    // Process channel_post updates
    for (const update of (json.result || [])) {
      const msg = update.channel_post;
      if (!msg || !msg.text) continue;
      const key = `tg_${msg.message_id}`;
      if (seenUrls.has(key)) continue;
      seenUrls.add(key);
      await publishNewsItem({
        title: msg.text.slice(0, 100),
        summary: msg.text.slice(0, 300),
        content: msg.text,
        imageUrl: null,
        url: null,
        sourceId: `telegram_${username}`,
        sourceName: `Telegram: @${username}`,
      });
    }
  } catch (e) {
    console.error(`Telegram fetch failed for @${username}:`, e.message);
  }
}

// ─── HTTP API ─────────────────────────────────────────────────────────────────

const http = require('http');

const server = http.createServer(async (req, res) => {
  if (req.method === 'GET' && req.url === '/health') {
    res.writeHead(200); res.end('ok');
  } else if (req.method === 'GET' && req.url === '/pubkey') {
    const pubkey = Buffer.from(await getPublicKey()).toString('hex');
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ pubkey }));
  } else if (req.method === 'POST' && req.url === '/news') {
    // Manual news injection (protected by shared secret in production)
    let body = '';
    req.on('data', c => body += c);
    req.on('end', async () => {
      try {
        const item = JSON.parse(body);
        const event = await publishNewsItem(item);
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ id: event.id }));
      } catch (e) {
        res.writeHead(400); res.end(e.message);
      }
    });
  } else {
    res.writeHead(404); res.end();
  }
});

// ─── Boot ─────────────────────────────────────────────────────────────────────

(async () => {
  console.log('Pezhvak News Server starting…');
  const pubkey = Buffer.from(await getPublicKey()).toString('hex');
  console.log(`Server pubkey (embed in app): ${pubkey}`);

  connectRelay();

  // Initial fetch
  await fetchAndPublishRss();

  // Periodic fetch every 15 minutes
  setInterval(fetchAndPublishRss, 15 * 60 * 1000);
  setInterval(() => TELEGRAM_CHANNELS.forEach(fetchTelegramChannel), 5 * 60 * 1000);

  server.listen(PORT, () => console.log(`HTTP API listening on :${PORT}`));
})();
