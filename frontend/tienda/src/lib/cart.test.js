import test from 'node:test'
import assert from 'node:assert/strict'
import { addToCart, readCart, removeFromCart, safeCart, totalCart, writeCart } from './cart.js'

const p = { id: 1, nombre: 'Tubérculo de Bruma', precio: 120, stock: 2, planetaOrigen: 'Bruma' }

test('agrega y respeta límite de stock', () => {
  let cart = []
  for (let i = 0; i < 4; i += 1) cart = addToCart(cart, p)
  assert.equal(cart.length, 1)
  assert.equal(cart[0].cantidad, 2)
  assert.equal(totalCart(cart), 240)
})

test('no agrega productos agotados', () => {
  assert.deepEqual(addToCart([], { ...p, stock: 0 }), [])
})

test('elimina productos por id', () => {
  assert.deepEqual(removeFromCart(addToCart([], p), 1), [])
})

test('ignora almacenamiento corrupto sin romper la tienda', () => {
  const storage = { getItem: () => '{json malo' }
  assert.deepEqual(readCart(storage), [])
  assert.deepEqual(safeCart([{ id: -1, nombre: 'x', precio: 1, cantidad: 1 }]), [])
})

test('persiste y recupera un carrito válido', () => {
  let value = ''
  const storage = { getItem: () => value, setItem: (_key, next) => { value = next } }
  assert.equal(writeCart(storage, addToCart([], p)), true)
  assert.deepEqual(readCart(storage), addToCart([], p))
})
