import { useCallback, useEffect, useMemo, useState } from 'react'
import { addToCart, readCart, removeFromCart, totalCart, writeCart } from '../lib/cart.js'

export function useCarrito(onAdded) {
  const [cart, setCart] = useState(() => readCart(window.localStorage))

  useEffect(() => {
    writeCart(window.localStorage, cart)
  }, [cart])

  const add = useCallback((product) => {
    if (!(Number(product.stock) > 0)) return
    setCart((old) => addToCart(old, product))
    onAdded()
  }, [onAdded])

  const remove = useCallback((id) => setCart((old) => removeFromCart(old, id)), [])
  const clear = useCallback(() => setCart([]), [])
  const cartCount = useMemo(() => cart.reduce((sum, item) => sum + item.cantidad, 0), [cart])
  const total = useMemo(() => totalCart(cart), [cart])

  return { cart, add, remove, clear, cartCount, total }
}
