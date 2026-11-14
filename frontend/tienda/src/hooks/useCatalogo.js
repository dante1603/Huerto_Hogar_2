import { useCallback, useEffect, useState } from 'react'
import { fetchProductos } from '../lib/api.js'

export function useCatalogo() {
  const [products, setProducts] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const reload = useCallback(async (signal) => {
    setLoading(true)
    setError('')
    try {
      const data = await fetchProductos({ signal })
      if (signal?.aborted) return
      setProducts(data)
    } catch (err) {
      if (signal?.aborted || err?.name === 'AbortError') return
      setError(err.message || 'No se pudo cargar el catálogo.')
      setProducts([])
    } finally {
      if (!signal?.aborted) setLoading(false)
    }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    reload(controller.signal)
    return () => controller.abort()
  }, [reload])

  return { products, loading, error, reload }
}
