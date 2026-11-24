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
