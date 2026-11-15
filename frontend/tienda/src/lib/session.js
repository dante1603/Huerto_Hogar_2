// JWT de sesión temporal, nunca credenciales o secretos de firma.
const SESSION_KEY = 'galaxym-huerto-sesion-v1'

export function readSession(storage = globalThis.sessionStorage) {
  try {
    const value = JSON.parse(storage.getItem(SESSION_KEY))
    if (!value || typeof value.accessToken !== 'string' || !value.accessToken ||
        !Number.isFinite(value.expiresAt)) return null
    if (Date.now() >= value.expiresAt) {
      storage.removeItem(SESSION_KEY)
      return null
    }
    return value
  } catch {
    return null
  }
}

export function saveSession(data, storage = globalThis.sessionStorage) {
  const expiresAt = Date.now() + Math.max(1, Number(data.expiresInSeconds) || 1) * 1000
  const session = {
    accessToken: data.accessToken,
    idUsuario: data.idUsuario,
    rolUsuario: data.rolUsuario,
    expiresAt,
  }
  storage.setItem(SESSION_KEY, JSON.stringify(session))
  return session
}

export function clearSession(storage = globalThis.sessionStorage) {
  storage.removeItem(SESSION_KEY)
}
