import { useCallback, useState } from 'react'
import { clearSession, readSession, saveSession } from '../lib/session.js'

export function useSesion() {
  const [session, setSession] = useState(() => readSession())
  const onAuthenticated = useCallback((data) => setSession(saveSession(data)), [])
  const logout = useCallback(() => {
    clearSession()
    setSession(null)
  }, [])

  return { session, onAuthenticated, logout }
}
