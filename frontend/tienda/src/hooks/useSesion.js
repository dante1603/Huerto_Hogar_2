import { useCallback, useEffect, useState } from 'react'
import { renovarSesion, revocarSesion } from '../lib/api.js'
import { clearSession, readSession, saveSession } from '../lib/session.js'

export function useSesion() {
  const [session, setSession] = useState(() => readSession())

  const onAuthenticated = useCallback(data => setSession(saveSession(data)), [])
  const logout = useCallback(() => {
    const refreshToken = session?.refreshToken
    clearSession()
    setSession(null)
    if (refreshToken) {
      // La revocación server-side es best-effort ante pérdida de conectividad.
      // El JWT de acceso emitido anteriormente seguirá válido hasta expirar.
      revocarSesion(refreshToken).catch(() => {})
    }
  }, [session?.refreshToken])

  useEffect(() => {
    if (!session) return
    let cancelled = false
    const now = Date.now()
    const canRefresh = typeof session.refreshToken === 'string' &&
      session.refreshToken.length === 43 && session.refreshExpiresAt > now
    const delay = canRefresh
      ? Math.max(0, session.expiresAt - now - 45000)
      : Math.max(0, session.expiresAt - now)
    const timer = window.setTimeout(async () => {
      if (cancelled) return
      if (!canRefresh) {
        clearSession()
        setSession(null)
        return
      }
      try {
        const updated = await renovarSesion(session.refreshToken)
        if (!cancelled) setSession(saveSession(updated))
      } catch {
        if (!cancelled) {
          clearSession()
          setSession(null)
        }
      }
    }, delay)
    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [session])

  return { session, onAuthenticated, logout }
}
