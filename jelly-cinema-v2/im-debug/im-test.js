const axios = require('axios')
const WebSocket = require('ws')

const TOKEN = process.env.TOKEN || 'PLEASE_SET_TOKEN'
const BASE_HTTP = process.env.BASE_HTTP || 'http://localhost:8080'
const WS_URL =
  process.env.WS_URL ||
  `ws://localhost:8080/ws/chat?token=${encodeURIComponent(TOKEN)}`

async function testHttp() {
  const client = axios.create({
    baseURL: BASE_HTTP,
    timeout: 8000,
    headers: {
      Authorization: TOKEN
    }
  })

  console.log('\n=== HTTP: /im/sessions ===')
  try {
    const r1 = await client.get('/im/sessions')
    console.log('status=', r1.status)
    console.log('body=', JSON.stringify(r1.data))
  } catch (e) {
    console.error('HTTP /im/sessions failed:', e?.response?.status, e?.message)
    if (e?.response?.data) console.error('resp.data=', e.response.data)
  }

  console.log('\n=== HTTP: /im/search/user?keyword=admin ===')
  try {
    const r2 = await client.get('/im/search/user', { params: { keyword: 'admin' } })
    console.log('status=', r2.status)
    console.log('body=', JSON.stringify(r2.data))
  } catch (e) {
    console.error('HTTP /im/search/user failed:', e?.response?.status, e?.message)
    if (e?.response?.data) console.error('resp.data=', e.response.data)
  }
}

function testWs() {
  console.log('\n=== WS CONNECT ===')
  console.log('WS_URL=', WS_URL)

  const ws = new WebSocket(WS_URL, {
    headers: {
      Authorization: TOKEN
    }
  })

  const heartbeat = setInterval(() => {
    if (ws.readyState === WebSocket.OPEN) {
      ws.ping()
    }
  }, 10000)

  ws.on('open', () => {
    console.log('[ws] open')
  })

  ws.on('message', (data) => {
    console.log('[ws] message:', data.toString())
  })

  ws.on('pong', () => {
    console.log('[ws] pong')
  })

  ws.on('close', (code, reason) => {
    console.log(`[ws] close: code=${code}, reason=${reason ? reason.toString() : '(empty)'}`)
    clearInterval(heartbeat)
  })

  ws.on('error', (err) => {
    console.log('[ws] error:', err.message)
  })
}

async function main() {
  if (!TOKEN || TOKEN === 'PLEASE_SET_TOKEN') {
    console.error(
      'Missing TOKEN. Use:\n' +
        '  PowerShell: $env:TOKEN="xxx"; npm run test:im\n' +
        '  or set TOKEN in your environment.'
    )
    process.exit(1)
  }

  await testHttp()
  testWs()
}

main()
