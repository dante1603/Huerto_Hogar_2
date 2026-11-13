const catalogHost = (import.meta.env.VITE_CATALOGO_API_URL || '').replace(/\/$/, '')
const usersHost = (import.meta.env.VITE_USUARIOS_API_URL || '/usuarios-api').replace(/\/$/, '')

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
    throw new ApiError(response.status, 'La solicitud no pudo completarse. Código ' + response.status + '.')
  }
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
  return request(catalogHost + '/api/v1/pedidos', {
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
  return request(catalogHost + '/api/v1/pedidos' + endpoint, {
    headers: { Authorization: 'Bearer ' + token },
  })
}
