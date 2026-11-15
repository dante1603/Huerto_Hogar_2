import test from 'node:test'
import assert from 'node:assert/strict'
import { eliminarProducto, actualizarProducto, crearProducto } from './api.js'

test('operaciones CRUD incluyen método, token y cuerpo; DELETE admite HTTP 204', async () => {
  const previous = globalThis.fetch
  const calls = []
  globalThis.fetch = async (url, options) => {
    calls.push({url, options})
    if (options.method === 'DELETE') return new Response(null, { status:204 })
    return new Response(JSON.stringify({id:3,sku:'X-001'}), {
      status: options.method === 'POST' ? 201 : 200,
      headers:{'Content-Type':'application/json'},
    })
  }
  try {
    const data = {sku:'X-001',nombre:'Cultivo',descripcion:'',precio:10,stock:2,planetaOrigen:'Bruma'}
    assert.equal((await crearProducto(data,'jwt-prueba')).sku,'X-001')
    await actualizarProducto(3,data,'jwt-prueba')
    assert.equal(await eliminarProducto(3,'jwt-prueba'),null)
    assert.deepEqual(calls.map(v=>v.options.method),['POST','PUT','DELETE'])
    assert.ok(calls.every(v=>v.options.headers.Authorization === 'Bearer jwt-prueba'))
    assert.equal(JSON.parse(calls[0].options.body).stock,2)
    assert.ok(calls[1].url.endsWith('/api/v1/productos/3'))
  } finally {
    globalThis.fetch = previous
  }
})


test('renovar y revocar usan el backend Usuarios y tratan 204 sin respuesta JSON', async () => {
  const { renovarSesion, revocarSesion } = await import('./api.js')
  const previous = globalThis.fetch
  const calls = []
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options })
    if (url.endsWith('/logout')) return new Response(null, {status:204})
    return new Response(JSON.stringify({
      accessToken:'jwt-renovado', refreshToken:'Y'.repeat(43),
      expiresInSeconds:1800, refreshExpiresInSeconds:86400,
    }), {status:200,headers:{'Content-Type':'application/json'}})
  }
  try {
    assert.equal((await renovarSesion('X'.repeat(43))).accessToken,'jwt-renovado')
    assert.equal(await revocarSesion('Y'.repeat(43)),null)
    assert.deepEqual(calls.map(x=>x.options.method),['POST','POST'])
    assert.ok(calls[0].url.endsWith('/api/v1/auth/refresh'))
    assert.ok(calls[1].url.endsWith('/api/v1/auth/logout'))
    assert.equal(JSON.parse(calls[0].options.body).refreshToken,'X'.repeat(43))
  } finally {
    globalThis.fetch = previous
  }
})

test('pedidos independiente, boleta y detalle boleta usan sus respectivos endpoints y cabeceras', async () => {
  const {
    crearPedido, consultarPedidos, consultarPedido,
    emitirBoleta, consultarBoleta, generarDetalleBoleta, consultarDetalleBoleta
  } = await import('./api.js')
  const previous = globalThis.fetch
  const calls = []
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options })
    if (url.includes('/api/v1/boletas/10')) {
      return new Response(JSON.stringify({ id: 10, pedidoId: 5, total: 240, moneda: 'GALATICOINS' }), {
        status: 200, headers: { 'Content-Type': 'application/json' }
      })
    }
    if (url.includes('/api/v1/boletas')) {
      return new Response(JSON.stringify({ id: 10, pedidoId: 5, total: 240, moneda: 'GALATICOINS' }), {
        status: 201, headers: { 'Content-Type': 'application/json' }
      })
    }
    if (url.includes('/api/v1/detalles-boleta/boleta/10')) {
      return new Response(JSON.stringify([{ id: 1, boletaId: 10, numeroLinea: 1, totalVenta: 240 }]), {
        status: 200, headers: { 'Content-Type': 'application/json' }
      })
    }
    if (url.includes('/api/v1/detalles-boleta')) {
      return new Response(JSON.stringify([{ id: 1, boletaId: 10, numeroLinea: 1, totalVenta: 240 }]), {
        status: 201, headers: { 'Content-Type': 'application/json' }
      })
    }
    if (url.includes('/api/v1/pedidos/mios')) {
      return new Response(JSON.stringify([{ id: 5, total: 240 }]), {
        status: 200, headers: { 'Content-Type': 'application/json' }
      })
    }
    if (url.includes('/api/v1/pedidos/5')) {
      return new Response(JSON.stringify({ id: 5, estado: 'PENDIENTE', total: 240 }), {
        status: 200, headers: { 'Content-Type': 'application/json' }
      })
    }
    return new Response(JSON.stringify({ id: 5, estado: 'PENDIENTE' }), {
      status: 201, headers: { 'Content-Type': 'application/json' }
    })
  }

  try {
    const p = await crearPedido({ planetaDestino: 'Bruma', productos: [{ productoId: 1, cantidad: 2 }] }, 'token-jwt', 'idem-uuid')
    assert.equal(p.id, 5)
    assert.equal(calls[0].options.headers['Idempotency-Key'], 'idem-uuid')
    assert.equal(calls[0].options.headers.Authorization, 'Bearer token-jwt')
    assert.ok(calls[0].url.endsWith('/api/v1/pedidos'))

    const mios = await consultarPedidos('token-jwt', 'CLIENTE')
    assert.equal(mios.length, 1)
    assert.ok(calls[1].url.endsWith('/api/v1/pedidos/mios'))

    const pend = await consultarPedidos('token-jwt', 'ADMINISTRADOR')
    assert.ok(calls[2].url.endsWith('/api/v1/pedidos/pendientes'))

    const det = await consultarPedido(5, 'token-jwt')
    assert.equal(det.id, 5)
    assert.ok(calls[3].url.endsWith('/api/v1/pedidos/5'))

    const b = await emitirBoleta(5, 'token-jwt')
    assert.equal(b.id, 10)
    assert.ok(calls[4].url.endsWith('/api/v1/boletas'))
    assert.equal(JSON.parse(calls[4].options.body).pedidoId, 5)

    const bConsulta = await consultarBoleta(10, 'token-jwt')
    assert.equal(bConsulta.id, 10)
    assert.ok(calls[5].url.endsWith('/api/v1/boletas/10'))

    const d = await generarDetalleBoleta(10, 'token-jwt')
    assert.equal(d.length, 1)
    assert.ok(calls[6].url.endsWith('/api/v1/detalles-boleta'))
    assert.equal(JSON.parse(calls[6].options.body).boletaId, 10)

    const dConsulta = await consultarDetalleBoleta(10, 'token-jwt')
    assert.equal(dConsulta.length, 1)
    assert.ok(calls[7].url.endsWith('/api/v1/detalles-boleta/boleta/10'))
  } finally {
    globalThis.fetch = previous
  }
})

test('recuperación de comprobante incompleto: boleta existente permite reintentar detalle sin duplicar boleta', async () => {
  const { emitirBoleta, generarDetalleBoleta } = await import('./api.js')
  const previous = globalThis.fetch
  let detalleFallo = true
  const postBoletaCalls = []
  const postDetalleCalls = []

  globalThis.fetch = async (url, options) => {
    if (url.includes('/api/v1/boletas')) {
      postBoletaCalls.push({ url, options })
      return new Response(JSON.stringify({ id: 99, pedidoId: 7, total: 500, moneda: 'GALATICOINS' }), {
        status: 201, headers: { 'Content-Type': 'application/json' }
      })
    }
    if (url.includes('/api/v1/detalles-boleta')) {
      postDetalleCalls.push({ url, options })
      if (detalleFallo) {
        return new Response(JSON.stringify({ message: 'Error transitorio en Detalle Boleta :8084' }), {
          status: 503, headers: { 'Content-Type': 'application/json' }
        })
      }
      return new Response(JSON.stringify([{ id: 101, boletaId: 99, numeroLinea: 1, totalVenta: 500 }]), {
        status: 201, headers: { 'Content-Type': 'application/json' }
      })
    }
    return new Response(null, { status: 404 })
  }

  try {
    // 1. Emitir boleta tiene éxito
    const boleta = await emitirBoleta(7, 'token-jwt')
    assert.equal(boleta.id, 99)
    assert.equal(postBoletaCalls.length, 1)

    // 2. Generar detalle falla en primer intento (estado comprobante incompleto)
    await assert.rejects(
      async () => await generarDetalleBoleta(boleta.id, 'token-jwt'),
      /Servicio temporalmente no disponible/
    )
    assert.equal(postDetalleCalls.length, 1)

    // 3. Reintento de detalle reutiliza boleta.id sin llamar a emitirBoleta de nuevo
    detalleFallo = false
    const lineas = await generarDetalleBoleta(boleta.id, 'token-jwt')
    assert.equal(lineas.length, 1)
    assert.equal(lineas[0].boletaId, 99)
    assert.equal(postBoletaCalls.length, 1, 'No se debe volver a emitir la boleta al reintentar solo el detalle')
    assert.equal(postDetalleCalls.length, 2, 'Se debió realizar el segundo intento a detalles-boleta')
  } finally {
    globalThis.fetch = previous
  }
})
