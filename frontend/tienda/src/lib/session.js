const SESSION_KEY = 'galaxym-huerto-sesion-v1'
export function readSession() {
  try {
    const value = JSON.parse(sessionStorage.getItem(SESSION_KEY))
    if (!value || typeof value.accessToken !== 'string' || !Number.isFinite(value.expiresAt)) return null
    if (Date.now() >= value.expiresAt) {
      sessionStorage.removeItem(SESSION_KEY)
      return null
    }
    return value
  } catch {
    return null
  }
}
export function saveSession(data) {
  const expiresAt = Date.now() + Math.max(1, Number(data.expiresInSeconds) || 1) * 1000
  const session = { accessToken: data.accessToken, idUsuario: data.idUsuario, rolUsuario: data.rolUsuario, expiresAt }
  sessionStorage.setItem(SESSION_KEY, JSON.stringify(session))
  return session
}
export function clearSession() {
  sessionStorage.removeItem(SESSION_KEY)
}
