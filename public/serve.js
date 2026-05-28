const http = require('http')
const fs = require('fs')
const path = require('path')
const { URL } = require('url')

const frontendHost = process.env.FRONTEND_HOST || '0.0.0.0'
const frontendPort = Number(process.env.FRONTEND_PORT || 8081)
const backendHost = '127.0.0.1'
const backendPort = 8080
const rootDir = __dirname

const mimeTypes = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.txt': 'text/plain; charset=utf-8'
}

function proxyApiRequest(req, res) {
  const options = {
    hostname: backendHost,
    port: backendPort,
    path: req.url,
    method: req.method,
    headers: {
      ...req.headers,
      host: `${backendHost}:${backendPort}`
    }
  }

  const proxyReq = http.request(options, proxyRes => {
    res.writeHead(proxyRes.statusCode || 502, proxyRes.headers)
    proxyRes.pipe(res)
  })

  proxyReq.on('error', () => {
    res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' })
    res.end(JSON.stringify({ success: false, message: '无法连接后端服务 127.0.0.1:8080' }))
  })

  req.pipe(proxyReq)
}

function sendFile(res, filePath) {
  fs.readFile(filePath, (error, content) => {
    if (error) {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' })
      res.end('Not Found')
      return
    }

    const ext = path.extname(filePath).toLowerCase()
    res.writeHead(200, { 'Content-Type': mimeTypes[ext] || 'application/octet-stream' })
    res.end(content)
  })
}

function serveStatic(req, res) {
  const requestUrl = new URL(req.url, `http://${req.headers.host || 'localhost'}`)
  const decodedPath = decodeURIComponent(requestUrl.pathname)
  const normalizedPath = path.normalize(decodedPath).replace(/^([/\\])+/, '')
  const requestedPath = path.join(rootDir, normalizedPath)

  if (!requestedPath.startsWith(rootDir)) {
    res.writeHead(403, { 'Content-Type': 'text/plain; charset=utf-8' })
    res.end('Forbidden')
    return
  }

  fs.stat(requestedPath, (error, stat) => {
    if (!error && stat.isFile()) {
      sendFile(res, requestedPath)
      return
    }

    sendFile(res, path.join(rootDir, 'index.html'))
  })
}

const server = http.createServer((req, res) => {
  if (req.url === '/api' || req.url.startsWith('/api/')) {
    proxyApiRequest(req, res)
    return
  }

  serveStatic(req, res)
})

server.listen(frontendPort, frontendHost, () => {
  console.log(`Frontend server: http://${frontendHost}:${frontendPort}`)
  console.log(`API proxy: /api -> http://${backendHost}:${backendPort}`)
})
