// Debug script: runs the full OAuth authorization-code + PKCE flow against PRODUCTION
// with a real authorization code, exactly like Claude's backend does (server-side
// exchange, no Origin header), to capture the token endpoint's exact response.
//
// Usage: node scripts/debug-oauth-flow.mjs
import { chromium } from 'playwright';
import crypto from 'node:crypto';

const BASE = 'https://jobapply-api.hugojava.dev';
const CLIENT_ID = 'jobapplytracker';
// Registered redirect URI (MCP inspector debug callback) — nothing listens there,
// we just intercept the navigation to grab the code.
const REDIRECT_URI = 'http://localhost:6274/oauth/callback/debug';
const SCOPE = 'read:applications read:google-drive openid read:profile read:resume read:metrics write:applications';
const RESOURCE = 'https://jobapply-api.hugojava.dev/mcp';

const email = `claude-debug-${Date.now()}@example.com`;
const password = 'DebugPass!2026';

function b64url(buf) {
  return buf.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

async function main() {
  // 1. Register a throwaway test user on prod
  const reg = await fetch(`${BASE}/api/v1/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      name: 'Claude Debug',
      email,
      password,
      confirmPassword: password,
      acceptedPrivacyPolicy: true,
    }),
  });
  console.log(`[1] register: HTTP ${reg.status} (${email})`);
  if (reg.status !== 201) {
    console.log(await reg.text());
    process.exit(1);
  }

  // 2. PKCE pair
  const verifier = b64url(crypto.randomBytes(32));
  const challenge = b64url(crypto.createHash('sha256').update(verifier).digest());

  const authorizeUrl = `${BASE}/oauth2/authorize?response_type=code` +
      `&client_id=${encodeURIComponent(CLIENT_ID)}` +
      `&redirect_uri=${encodeURIComponent(REDIRECT_URI)}` +
      `&scope=${encodeURIComponent(SCOPE)}` +
      `&state=debug-${Date.now()}` +
      `&code_challenge=${challenge}&code_challenge_method=S256` +
      `&resource=${encodeURIComponent(RESOURCE)}`;

  // 3. Browser: login + authorize, intercept the callback to localhost:6274
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  let callbackUrl = null;
  await page.route('**localhost:6274**', route => {
    callbackUrl = route.request().url();
    route.fulfill({ status: 200, body: 'callback captured' });
  });

  console.log('[2] opening authorize URL...');
  await page.goto(authorizeUrl, { waitUntil: 'domcontentloaded' });
  console.log(`    landed on: ${page.url()}`);

  if (page.url().includes('/login')) {
    await page.fill('input[name="username"]', email);
    await page.fill('input[name="password"]', password);
    await Promise.all([
      page.waitForLoadState('domcontentloaded').catch(() => {}),
      page.click('button[type="submit"]'),
    ]);
    console.log(`[3] after login: ${page.url()}`);
  }

  // wait briefly for the callback interception
  for (let i = 0; i < 20 && !callbackUrl; i++) await new Promise(r => setTimeout(r, 250));
  await browser.close();

  if (!callbackUrl) {
    console.log('!! callback never reached — authorize did not redirect to redirect_uri');
    process.exit(1);
  }
  console.log(`[4] callback: ${callbackUrl}`);
  const code = new URL(callbackUrl).searchParams.get('code');
  console.log(`    code: ${code?.slice(0, 20)}...`);

  // 4. Token exchange exactly like Claude's backend (no Origin header)
  const tokenRes = await fetch(`${BASE}/oauth2/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      code,
      redirect_uri: REDIRECT_URI,
      client_id: CLIENT_ID,
      code_verifier: verifier,
      resource: RESOURCE,
    }),
  });
  const tokenBody = await tokenRes.text();
  console.log(`[5] token exchange: HTTP ${tokenRes.status}`);
  console.log(`    ${tokenBody.slice(0, 400)}`);

  if (tokenRes.status !== 200) process.exit(1);
  const accessToken = JSON.parse(tokenBody).access_token;

  // 5. Authenticated MCP initialize
  const mcpRes = await fetch(`${BASE}/mcp`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json, text/event-stream',
      'Authorization': `Bearer ${accessToken}`,
    },
    body: JSON.stringify({
      jsonrpc: '2.0', id: 1, method: 'initialize',
      params: { protocolVersion: '2025-06-18', capabilities: {}, clientInfo: { name: 'debug', version: '1.0' } },
    }),
  });
  console.log(`[6] MCP initialize with token: HTTP ${mcpRes.status}`);
  console.log(`    ${(await mcpRes.text()).slice(0, 300)}`);
}

main().catch(err => { console.error(err); process.exit(1); });
