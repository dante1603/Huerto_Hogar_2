import test from 'node:test'
import assert from 'node:assert/strict'
import { activeRole, canAccessRoute, canAddToCart, homeForRole } from './roles.js'

const now = 100_000
function session(rolUsuario, expiresAt = now + 60_000) {
  return { rolUsuario, expiresAt, accessToken: 'token-para-test' }
}

test('el invitado solo accede a tienda y acceso, no a pedidos o panel administrativo', () => {
  assert.equal(activeRole(null, now), null)
  for (const path of ['/', '/productos', '/nosotros', '/carrito', '/acceso']) {
    assert.equal(canAccessRoute(null, path), true, path)
  }
  for (const path of ['/pedidos', '/consulta', '/administracion']) {
    assert.equal(canAccessRoute(null, path), false, path)
  }
})

test('CLIENTE puede comprar y consultar sus pedidos, no editar ni consultar órdenes de otros', () => {
  const role = activeRole(session('CLIENTE'), now)
  assert.equal(canAccessRoute(role, '/carrito'), true)
  assert.equal(canAccessRoute(role, '/pedidos'), true)
  assert.equal(canAccessRoute(role, '/consulta'), false)
  assert.equal(canAccessRoute(role, '/administracion'), false)
  assert.equal(canAddToCart(role), true)
})

test('VENDEDOR solo consulta lista/detalles y órdenes, además de su cuenta', () => {
  const role = activeRole(session('VENDEDOR'), now)
  assert.equal(homeForRole(role), '/consulta')
  for (const path of ['/consulta', '/pedidos', '/acceso']) assert.equal(canAccessRoute(role, path), true)
  for (const path of ['/', '/productos', '/carrito', '/nosotros', '/administracion']) {
    assert.equal(canAccessRoute(role, path), false, path)
  }
  assert.equal(canAddToCart(role), false)
})

test('ADMINISTRADOR tiene acceso completo al panel y lecturas, sin fingir compra de cliente', () => {
  const role = activeRole(session('ADMINISTRADOR'), now)
  for (const path of ['/', '/productos', '/consulta', '/administracion', '/pedidos', '/acceso']) {
    assert.equal(canAccessRoute(role, path), true, path)
  }
  assert.equal(homeForRole(role), '/administracion')
  assert.equal(canAddToCart(role), false)
})

test('sesiones caducadas o roles desconocidos no autorizan accesos privados', () => {
  assert.equal(activeRole(session('ADMINISTRADOR', now), now), null)
  assert.equal(activeRole(session('FALSO'), now), null)
  assert.equal(activeRole({rolUsuario: 'ADMINISTRADOR', expiresAt: now+100}, now), null)
  assert.equal(canAccessRoute(activeRole(session('VENDEDOR', now-1), now), '/pedidos'), false)
})
