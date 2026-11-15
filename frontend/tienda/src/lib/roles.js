// Política visible de navegación. La seguridad efectiva sigue en Spring Security.
export const ROLE_CLIENTE = 'CLIENTE'
export const ROLE_VENDEDOR = 'VENDEDOR'
export const ROLE_ADMIN = 'ADMINISTRADOR'

const KNOWN_ROLES = new Set([ROLE_CLIENTE, ROLE_VENDEDOR, ROLE_ADMIN])

export function activeRole(session, now = Date.now()) {
  if (!session || typeof session.accessToken !== 'string' || !session.accessToken
      || !Number.isFinite(session.expiresAt) || session.expiresAt <= now) return null
  return KNOWN_ROLES.has(session.rolUsuario) ? session.rolUsuario : null
}

export function homeForRole(role) {
  if (role === ROLE_VENDEDOR) return '/consulta'
  if (role === ROLE_ADMIN) return '/administracion'
  return '/'
}

export function canAccessRoute(role, path) {
  if (path === '/acceso') return true
  if (path === '/pedidos') return KNOWN_ROLES.has(role)
  if (path === '/consulta') return role === ROLE_VENDEDOR || role === ROLE_ADMIN
  if (path === '/administracion') return role === ROLE_ADMIN
  if (['/', '/productos', '/carrito', '/nosotros'].includes(path)) {
    return role !== ROLE_VENDEDOR
  }
  return false
}

// Administrador tiene acceso de lectura a la tienda, pero el backend
// solo admite crear pedidos de CLIENTE. No mostrar botones de compra al admin.
export function canAddToCart(role) {
  return role === null || role === ROLE_CLIENTE
}
