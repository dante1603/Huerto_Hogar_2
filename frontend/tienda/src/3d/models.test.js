import test from 'node:test'
import assert from 'node:assert/strict'
import { PRODUCT_SKU_TO_MODEL_ID, PROTOTYPE_MODELS, prototypeById, prototypeForSku } from './modelRegistry.js'

test('se conservaron los seis modelos procedurales originales sin asociarlos a ventas', () => {
  assert.deepEqual(
    PROTOTYPE_MODELS.map(model => model.id),
    ['zorbulbo', 'lunahoja', 'crisollo', 'solabaya', 'nebupoma', 'miramelon'],
  )
  assert.equal(Object.keys(PRODUCT_SKU_TO_MODEL_ID).length, 0)
  assert.equal(prototypeForSku('BRU-TUB-001'), null)
  assert.equal(prototypeById('desconocido'), null)
})

for (const item of PROTOTYPE_MODELS) {
  test(item.nombre + ': construye malla triangular con normales y colores finitos', async () => {
    const { default: createMesh } = await item.load()
    const mesh = createMesh()
    assert.ok(mesh.count > 0)
    assert.equal(mesh.count * 3, mesh.p.length)
    assert.equal(mesh.p.length, mesh.n.length)
    assert.equal(mesh.p.length, mesh.c.length)
    assert.equal(mesh.p.length % 9, 0)
    assert.ok(mesh.p.every(Number.isFinite), 'posiciones finitas')
    assert.ok(mesh.n.every(Number.isFinite), 'normales finitas')
    assert.ok(mesh.c.every(value => Number.isFinite(value) && value >= 0 && value <= 1), 'colores RGB válidos')
  })
}
