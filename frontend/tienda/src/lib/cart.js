export const CART_KEY = 'galaxym-huerto-carrito-v1'

export function safeCart(input) {
  if (!Array.isArray(input)) return []
  return input.filter((p) =>
    p && Number.isSafeInteger(p.id) && p.id > 0 &&
    typeof p.nombre === 'string' &&
    Number.isFinite(Number(p.precio)) && Number(p.precio) >= 0 &&
    Number.isInteger(p.cantidad) && p.cantidad > 0
  ).map((p) => ({
    id: p.id,
    nombre: p.nombre,
    precio: Number(p.precio),
    cantidad: Math.min(p.cantidad, 999),
    planetaOrigen: typeof p.planetaOrigen === 'string' ? p.planetaOrigen : '',
  }))
}

export function readCart(storage) {
  try {
    const raw = storage.getItem(CART_KEY)
    return raw ? safeCart(JSON.parse(raw)) : []
  } catch {
    return []
  }
}

export function writeCart(storage, items) {
  try {
    storage.setItem(CART_KEY, JSON.stringify(safeCart(items)))
    return true
  } catch {
    return false
  }
}

export function addToCart(items, producto) {
  const stock = Math.max(0, Number(producto.stock) || 0)
  if (!Number.isSafeInteger(producto.id) || stock < 1) return items
  const current = safeCart(items)
  const found = current.find((p) => p.id === producto.id)
  if (found) {
    return current.map((p) => p.id === producto.id
      ? { ...p, cantidad: Math.min(p.cantidad + 1, stock), precio: Number(producto.precio) }
      : p)
  }
  return [...current, {
    id: producto.id,
    nombre: producto.nombre,
    precio: Number(producto.precio),
    cantidad: 1,
    planetaOrigen: producto.planetaOrigen,
  }]
}

export function removeFromCart(items, id) {
  return safeCart(items).filter((p) => p.id !== id)
}

export function totalCart(items) {
  return safeCart(items).reduce((sum, p) => sum + p.precio * p.cantidad, 0)
}
