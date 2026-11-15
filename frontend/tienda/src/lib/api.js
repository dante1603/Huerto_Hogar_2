const catalogHost = (import.meta.env?.VITE_CATALOGO_API_URL || '').replace(/\/$/, '')
const usersHost = (import.meta.env?.VITE_USUARIOS_API_URL || '/usuarios-api').replace(/\/$/, '')
const ordersHost = (import.meta.env?.VITE_PEDIDOS_API_URL || '').replace(/\/$/, '')
const boletaHost = (import.meta.env?.VITE_BOLETA_API_URL || '').replace(/\/$/, '')
const detalleBoletaHost = (import.meta.env?.VITE_DETALLE_BOLETA_API_URL || '').replace(/\/$/, '')

export class ApiError extends Error {
  constructor(status, message) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function request(url, options = {}) {
  let response
  try {
    response = await fetch(url, {
      ...options,
      headers: {
        Accept: 'application/json',
        ...(options.body ? { 'Content-Type': 'application/json' } : {}),
        ...(options.headers || {}),
      },
    })
  } catch {
    throw new ApiError(0, 'No se pudo conectar al servidor. Verifica que la API esté encendida.')
  }
  if (!response.ok) {
    if (response.status === 401) throw new ApiError(401, 'Credenciales incorrectas o sesión vencida.')
    if (response.status === 409) throw new ApiError(409, 'Conflicto: stock insuficiente, clave reutilizada o datos duplicados. Actualiza antes de reintentar.')
    if (response.status === 403) throw new ApiError(403, 'Tu cuenta no tiene permiso para esta operación.')
    if (response.status === 400) throw new ApiError(400, 'Revisa los datos ingresados.')
    if (response.status === 404) throw new ApiError(404, 'El recurso no existe o fue eliminado.')
    if (response.status === 503) throw new ApiError(503, 'Servicio temporalmente no disponible o validación remota pendiente.')
    throw new ApiError(response.status, 'La solicitud no pudo completarse. Código ' + response.status + '.')
  }
  if (response.status === 204) return null
  return response.json()
}

export async function fetchProductos({ signal } = {}) {
  const value = await request(catalogHost + '/api/v1/productos', { signal })
  if (!Array.isArray(value)) throw new ApiError(502, 'La API respondió con un catálogo inválido.')
  return value
}

export function loginUsuario(email, contrasena) {
  return request(usersHost + '/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, contrasena }),
  })
}

export function registrarUsuario(data) {
  return request(usersHost + '/api/v1/usuarios', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export function crearPedido(data, token, idempotencyKey) {
  return request(ordersHost + '/api/v1/pedidos', {
    method: 'POST',
    headers: {
      Authorization: 'Bearer ' + token,
      'Idempotency-Key': idempotencyKey,
    },
    body: JSON.stringify(data),
  })
}

export function consultarPedidos(token, rol) {
  const endpoint = rol === 'CLIENTE' ? '/mios' : '/pendientes'
  return request(ordersHost + '/api/v1/pedidos' + endpoint, {
    headers: { Authorization: 'Bearer ' + token },
  })
}

function adminHeaders(token) {
  return { Authorization: 'Bearer ' + token }
}

export function fetchProducto(id, { signal } = {}) {
  return request(catalogHost + '/api/v1/productos/' + encodeURIComponent(id), { signal })
}

export function crearProducto(producto, token) {
  return request(catalogHost + '/api/v1/productos', {
    method: 'POST', headers: adminHeaders(token), body: JSON.stringify(producto),
  })
}

export function actualizarProducto(id, producto, token) {
  return request(catalogHost + '/api/v1/productos/' + encodeURIComponent(id), {
    method: 'PUT', headers: adminHeaders(token), body: JSON.stringify(producto),
  })
}

export function eliminarProducto(id, token) {
  return request(catalogHost + '/api/v1/productos/' + encodeURIComponent(id), {
    method: 'DELETE', headers: adminHeaders(token),
  })
}

export function consultarPedido(id, token) {
  return request(ordersHost + '/api/v1/pedidos/' + encodeURIComponent(id), {
    headers: { Authorization: 'Bearer ' + token },
  })
}

export function emitirBoleta(pedidoId, token) {
  return request(boletaHost + '/api/v1/boletas', {
    method: 'POST',
    headers: { Authorization: 'Bearer ' + token },
    body: JSON.stringify({ pedidoId: Number(pedidoId) }),
  })
}

export function consultarBoleta(id, token) {
  return request(boletaHost + '/api/v1/boletas/' + encodeURIComponent(id), {
    headers: { Authorization: 'Bearer ' + token },
  })
}

export function generarDetalleBoleta(boletaId, token) {
  return request(detalleBoletaHost + '/api/v1/detalles-boleta', {
    method: 'POST',
    headers: { Authorization: 'Bearer ' + token },
    body: JSON.stringify({ boletaId: Number(boletaId) }),
  })
}

export function consultarDetalleBoleta(boletaId, token) {
  return request(detalleBoletaHost + '/api/v1/detalles-boleta/boleta/' + encodeURIComponent(boletaId), {
    headers: { Authorization: 'Bearer ' + token },
  })
}

export function renovarSesion(refreshToken) {
  return request(usersHost + '/api/v1/auth/refresh', {
    method: 'POST', body: JSON.stringify({ refreshToken }),
  })
}

export function revocarSesion(refreshToken) {
  return request(usersHost + '/api/v1/auth/logout', {
    method: 'POST', body: JSON.stringify({ refreshToken }),
  })
}
