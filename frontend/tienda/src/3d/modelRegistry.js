// Catálogo de *referencias visuales*, no catálogo comercial.
// Las seis geometrías conservan su código original, pero NO están aprobadas.
export const PROTOTYPE_MODELS = Object.freeze([
  { id: 'zorbulbo', nombre: 'Zorbulbo', categoria: 'Verdura raíz', origen: 'Invernaderos de Bruma', descripcion: 'Bulbo crujiente de hojas turquesas y raíces violetas.', load: () => import('./models/zorbulbo.js') },
  { id: 'lunahoja', nombre: 'Lunahoja', categoria: 'Verdura de hoja', origen: 'Huertos lunares', descripcion: 'Hojas en forma de luna creciente con un delicado resplandor.', load: () => import('./models/lunahoja.js') },
  { id: 'crisollo', nombre: 'Crisollo', categoria: 'Verdura cristalina', origen: 'Hidroponía orbital', descripcion: 'Ramilletes facetados que recuerdan al brócoli.', load: () => import('./models/crisollo.js') },
  { id: 'solabaya', nombre: 'Solabaya', categoria: 'Fruta de racimo', origen: 'Valles cálidos de Bruma', descripcion: 'Racimos de bayas doradas y perfume cítrico.', load: () => import('./models/solabaya.js') },
  { id: 'nebupoma', nombre: 'Nebupoma', categoria: 'Fruta de pepita', origen: 'Jardines de estación', descripcion: 'Fruto violeta moteado con un surco luminoso.', load: () => import('./models/nebupoma.js') },
  { id: 'miramelon', nombre: 'Miramelón', categoria: 'Fruta de pulpa', origen: 'Huertas templadas', descripcion: 'Melón de corteza rayada y pulpa ámbar.', load: () => import('./models/miramelon.js') },
])

// Contrato futuro: cuando el producto real tenga una asociación aprobada,
// mapear aquí su SKU. Un nombre parecido nunca es evidencia suficiente.
export const PRODUCT_SKU_TO_MODEL_ID = Object.freeze({})

export function prototypeById(id) {
  return PROTOTYPE_MODELS.find(model => model.id === id) || null
}

export function prototypeForSku(sku) {
  const id = PRODUCT_SKU_TO_MODEL_ID[sku]
  return id ? prototypeById(id) : null
}
