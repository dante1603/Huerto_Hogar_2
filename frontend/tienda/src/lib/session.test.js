import test from 'node:test'
import assert from 'node:assert/strict'
import { clearSession, readSession, saveSession } from './session.js'

function memoryStorage() {
  const values = new Map()
  return {
    getItem(key) { return values.get(key) ?? null },
    setItem(key, value) { values.set(key, value) },
    removeItem(key) { values.delete(key) },
    snapshot() { return new Map(values) },
  }
}

test('guarda sesión y recupera token/rol al recargar en la misma pestaña', () => {
  const storage = memoryStorage()
  const saved = saveSession({
    accessToken:'jwt-demostracion', idUsuario: 10, rolUsuario:'CLIENTE', expiresInSeconds:1800,
  }, storage)
  assert.equal(readSession(storage).accessToken, saved.accessToken)
  assert.equal(readSession(storage).rolUsuario, 'CLIENTE')
  assert.ok(saved.expiresAt > Date.now())
})

test('descarta sesión expirada y elimina sus datos', () => {
  const storage = memoryStorage()
  storage.setItem('galaxym-huerto-sesion-v1', JSON.stringify({
    accessToken:'vencido', expiresAt:Date.now()-1000, rolUsuario:'ADMINISTRADOR',
  }))
  assert.equal(readSession(storage), null)
  assert.equal(storage.snapshot().size, 0)
})

test('logout invalida sesión guardada', () => {
  const storage = memoryStorage()
  saveSession({
    accessToken:'jwt-demo', idUsuario:1, rolUsuario:'CLIENTE', expiresInSeconds:100,
  }, storage)
  clearSession(storage)
  assert.equal(readSession(storage), null)
})

test('storage corrupto no provoca sesión autenticada ni error sin recuperar', () => {
  const storage = memoryStorage()
  storage.setItem('galaxym-huerto-sesion-v1', '{esto no es JSON}')
  assert.equal(readSession(storage), null)
})
