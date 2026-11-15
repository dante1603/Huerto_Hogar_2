import { useCallback, useEffect, useState } from 'react'
import { clearSession, readSession, saveSession } from '../lib/session.js'

export function useSesion() {
  const [session, setSession] = useState(() => readSession())

  const onAuthenticated = useCallback(data => setSession(saveSession(data)), [])
  const logout = useCallback(() => {
    clearSession()
    setSession(null)
  }, [])

  // El UI y las rutas se actualizan al vencer el JWT, sin esperar a navegar.
  // La autorización efectiva, incluida la expiración, sigue en Spring Security.
  useEffect(() => {
    if (!session) return
    const remaining = Math.max(0, session.expiresAt - Date.now())
    const timeout = window.setTimeout(() => {
      clearSession()
      setSession(null)
    }, remaining)
    return () => window.clearTimeout(timeout)
  }, [session])

  return { session, onAuthenticated, logout }
}
