const PENDING_KEY = 'galaxym-huerto-intento-pedido-v1'

export function prepareOrderAttempt(storage, userId, cart, destino, createId) {
  const productos = cart.map((item) => ({
    productoId: item.id,
    cantidad: item.cantidad,
  })).sort((a, b) => a.productoId - b.productoId)
  const signature = JSON.stringify({ userId, productos, destino })
  try {
    const previous = JSON.parse(storage.getItem(PENDING_KEY))
    if (previous?.signature === signature && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(previous.id)) {
      return { id: previous.id, productos }
    }
  } catch {
    // Se crea un nuevo intento solo si el almacen anterior no era legible.
  }
  const id = createId()
  storage.setItem(PENDING_KEY, JSON.stringify({ id, signature }))
  return { id, productos }
}

export function clearOrderAttempt(storage) {
  storage.removeItem(PENDING_KEY)
}
