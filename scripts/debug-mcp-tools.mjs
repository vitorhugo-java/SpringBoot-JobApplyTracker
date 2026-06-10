// Debug: full OAuth flow + real MCP tool calls against production, to inspect what
// an MCP client actually receives from the tools (empty-data investigation).
// Usage: node debug-mcp-tools.mjs   (requires playwright-core + Edge)
import { chromium } from 'playwright-core';
import crypto from 'node:crypto';

const BASE = 'https://jobapply-api.hugojava.dev';
const CLIENT_ID = 'jobapplytracker';
const REDIRECT_URI = 'http://localhost:6274/oauth/callback/debug';
const SCOPE = 'read:applications read:google-drive openid read:profile read:resume read:metrics write:applications';

const email = `claude-debug-${Date.now()}@example.com`;
const password = 'DebugPass!2026';

const b64url = (buf) => buf.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

async function mcpPost(body, accessToken, sessionId) {
  const headers = {
    'Content-Type': 'application/json',
    'Accept': 'application/json, text/event-stream',
    'Authorization': `Bearer ${accessToken}`,
  };
  if (sessionId) headers['Mcp-Session-Id'] = sessionId;
  const res = await fetch(`${BASE}/mcp`, { method: 'POST', headers, body: JSON.stringify(body) });
  return res;
}

async function main() {
  const reg = await fetch(`${BASE}/api/v1/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name: 'Claude Debug', email, password, confirmPassword: password, acceptedPrivacyPolicy: true }),
  });
  console.log(`[1] register: HTTP ${reg.status} (${email})`);

  const verifier = b64url(crypto.randomBytes(32));
  const challenge = b64url(crypto.createHash('sha256').update(verifier).digest());
  const authorizeUrl = `${BASE}/oauth2/authorize?response_type=code&client_id=${CLIENT_ID}` +
      `&redirect_uri=${encodeURIComponent(REDIRECT_URI)}&scope=${encodeURIComponent(SCOPE)}` +
      `&state=dbg&code_challenge=${challenge}&code_challenge_method=S256`;

  const browser = await chromium.launch({ headless: true, channel: 'msedge' });
  const page = await browser.newPage();
  let callbackUrl = null;
  await page.route('**localhost:6274**', r => { callbackUrl = r.request().url(); r.fulfill({ status: 200, body: 'ok' }); });
  await page.goto(authorizeUrl, { waitUntil: 'domcontentloaded' });
  if (page.url().includes('/login')) {
    await page.fill('input[name="username"]', email);
    await page.fill('input[name="password"]', password);
    await Promise.all([page.waitForLoadState('domcontentloaded').catch(() => {}), page.click('button[type="submit"]')]);
  }
  for (let i = 0; i < 20 && !callbackUrl; i++) await new Promise(r => setTimeout(r, 250));
  await browser.close();
  const code = new URL(callbackUrl).searchParams.get('code');
  console.log(`[2] code obtido`);

  const tokenRes = await fetch(`${BASE}/oauth2/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'authorization_code', code, redirect_uri: REDIRECT_URI, client_id: CLIENT_ID, code_verifier: verifier }),
  });
  const tokenJson = await tokenRes.json();
  console.log(`[3] token: HTTP ${tokenRes.status} | refresh_token presente: ${Boolean(tokenJson.refresh_token)}`);
  const accessToken = tokenJson.access_token;
  const claims = JSON.parse(Buffer.from(accessToken.split('.')[1], 'base64url').toString());
  console.log(`    claims: sub=${claims.sub} user_id=${claims.user_id} scope=${(claims.scope || []).length} itens`);

  // --- MCP handshake ---
  const init = await mcpPost({
    jsonrpc: '2.0', id: 1, method: 'initialize',
    params: { protocolVersion: '2025-06-18', capabilities: {}, clientInfo: { name: 'debug', version: '1.0' } },
  }, accessToken);
  const sessionId = init.headers.get('mcp-session-id');
  console.log(`[4] initialize: HTTP ${init.status} | session: ${sessionId}`);

  await mcpPost({ jsonrpc: '2.0', method: 'notifications/initialized' }, accessToken, sessionId);

  const listRes = await mcpPost({ jsonrpc: '2.0', id: 2, method: 'tools/list' }, accessToken, sessionId);
  const listText = await listRes.text();
  console.log(`[5] tools/list: HTTP ${listRes.status}`);
  // streamable HTTP may answer as SSE; extract the JSON data line
  const listJsonText = listText.startsWith('event:') || listText.includes('data:')
      ? listText.split('\n').filter(l => l.startsWith('data:')).map(l => l.slice(5)).join('')
      : listText;
  const tools = JSON.parse(listJsonText).result?.tools ?? [];
  console.log(`    ${tools.length} tools: ${tools.slice(0, 8).map(t => t.name).join(', ')}...`);

  // call a read-only list-style tool
  const target = tools.find(t => /list|get/.test(t.name) && /application/.test(t.name)) ?? tools[0];
  console.log(`[6] tools/call -> ${target.name}`);
  const callRes = await mcpPost({
    jsonrpc: '2.0', id: 3, method: 'tools/call',
    params: { name: target.name, arguments: {} },
  }, accessToken, sessionId);
  const callText = await callRes.text();
  const callJsonText = callText.includes('data:')
      ? callText.split('\n').filter(l => l.startsWith('data:')).map(l => l.slice(5)).join('')
      : callText;
  console.log(`    HTTP ${callRes.status}`);
  console.log(`    ${callJsonText.slice(0, 600)}`);
}

main().catch(err => { console.error(err); process.exit(1); });
