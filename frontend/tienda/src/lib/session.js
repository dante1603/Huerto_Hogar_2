// El access JWT y el refresh token opaco viven solo en esta pestaña.
// Son secretos accesibles a JavaScript: no usar esta estrategia en producción sin revisar XSS/CSP.
const SESSION_KEY = 'galaxym-huerto-sesion-v1'

export function readSession(storage = globalThis.sessionStorage) {
  try {
    const value = JSON.parse(storage.getItem(SESSION_KEY))
    if (!value || typeof value.accessToken !== 'string' || !value.accessToken ||
        !Number.isFinite(value.expiresAt)) return null
    const now = Date.now()
    const refreshVigente = typeof value.refreshToken === 'string' && value.refreshToken.length === 43 &&
      Number.isFinite(value.refreshExpiresAt) && value.refreshExpiresAt > now
    // Una recarga con JWT expirado conserva únicamente el material para intentar renovar.
    if (now >= value.expiresAt && !refreshVigente) {
      storage.removeItem(SESSION_KEY)
      return null
    }
    return value
  } catch {
    return null
  }
}

export function saveSession(data, storage = globalThis.sessionStorage) {
  const now = Date.now()
  const validRefresh = typeof data.refreshToken === 'string' && data.refreshToken.length === 43 &&
    Number(data.refreshExpiresInSeconds) > 0
  const session = {
    accessToken: data.accessToken,
    idUsuario: data.idUsuario,
    rolUsuario: data.rolUsuario,
    expiresAt: now + Math.max(1, Number(data.expiresInSeconds) || 1) * 1000,
    ...(validRefresh ? {
      refreshToken: data.refreshToken,
      refreshExpiresAt: now + Number(data.refreshExpiresInSeconds) * 1000,
    } : {}),
  }
  storage.setItem(SESSION_KEY, JSON.stringify(session))
  return session
}

export function clearSession(storage = globalThis.sessionStorage) {
  storage.removeItem(SESSION_KEY)
}
