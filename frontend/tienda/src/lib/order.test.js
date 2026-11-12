import test from 'node:test'
import assert from 'node:assert/strict'
import { clearOrderAttempt, prepareOrderAttempt } from './order.js'

function storage() {
  const map = new Map()
  return {
    getItem: (k) => map.get(k) ?? null,
    setItem: (k, v) => map.set(k, v),
    removeItem: (k) => map.delete(k),
  }
}
const cart = [{ id: 9, cantidad: 2 }, { id: 3, cantidad: 1 }]
test('reintentos con mismo carrito conservan Idempotency-Key', () => {
  const state = storage()
  let generated = 0
  const id = () => { generated += 1; return '00000000-0000-4000-8000-000000000001' }
  const a = prepareOrderAttempt(state, 7, cart, 'Bruma', id)
  const b = prepareOrderAttempt(state, 7, [...cart].reverse(), 'Bruma', id)
  assert.equal(a.id, b.id)
  assert.deepEqual(a.productos.map(x => x.productoId), [3, 9])
  assert.equal(generated, 1)
})
test('otro destino crea nueva clave y limpiar la elimina', () => {
  const state = storage()
  let n = 0
  const id = () => '00000000-0000-4000-8000-' + String(++n).padStart(12, '0')
  const a = prepareOrderAttempt(state, 7, cart, 'Bruma', id)
  const b = prepareOrderAttempt(state, 7, cart, 'Yermo', id)
  assert.notEqual(a.id, b.id)
  clearOrderAttempt(state)
  const c = prepareOrderAttempt(state, 7, cart, 'Yermo', id)
  assert.notEqual(b.id, c.id)
})
