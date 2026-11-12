import { useState } from 'react'
import { Link } from 'react-router-dom'
import { crearPedido } from '../lib/api.js'
import { formatGalatiCoins } from '../lib/money.js'
import { clearOrderAttempt, prepareOrderAttempt } from '../lib/order.js'

export default function Carrito({ cart, products, remove, total, session, onOrdered }) {
  const [destino, setDestino] = useState('Bruma')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState('')
  const [confirmacion, setConfirmacion] = useState(null)
  const canOrder = session?.rolUsuario === 'CLIENTE' && Date.now() < (session.expiresAt || 0)
  const lacksStock = cart.some(item => {
    const producto = products.find(p => p.id === item.id)
    return !producto || producto.stock < item.cantidad
  })

  async function confirmarPedido() {
    if (!canOrder || !cart.length || lacksStock || enviando) return
    setEnviando(true)
    setError('')
    try {
      const intento = prepareOrderAttempt(window.sessionStorage,
        session.idUsuario, cart, destino, () => window.crypto.randomUUID())
      const pedido = await crearPedido(
        { planetaDestino: destino, productos: intento.productos },
        session.accessToken, intento.id)
      setConfirmacion(pedido)
      clearOrderAttempt(window.sessionStorage)
      onOrdered()
    } catch (err) {
      setError(err.message || 'No se pudo registrar el pedido.')
    } finally {
      setEnviando(false)
    }
  }

  return <section>
    <div className="page-title">
      <span className="eyebrow">Tu cargamento</span>
      <h1>Carrito interplanetario</h1>
      <p>Las unidades se reservan al confirmar. Las entregas dentro de Galaxym aún no están conectadas.</p>
    </div>
    {confirmacion ? <section className="panel" role="status">
      <span className="eyebrow">Reserva confirmada en el backend</span>
      <h2>Pedido #{confirmacion.id}</h2>
      <p>Estado: <strong>{confirmacion.estado}</strong>. Destino: <strong>{confirmacion.planetaDestino}</strong>.</p>
      <p>Total registrado: <strong>{formatGalatiCoins(confirmacion.total)}</strong></p>
      <p className="small">Esto no representa un pago ni una entrega completada. El pedido espera a que el sistema de despacho esté conectado.</p>
      <div className="product-actions"><Link className="button button-primary" to="/pedidos">Consultar mis pedidos</Link><button className="button button-quiet" onClick={() => setConfirmacion(null)} type="button">Crear otra reserva</button></div>
    </section> : !cart.length ? <div className="panel empty-state">
      <span className="large-icon">🧺</span><h2>Tu cesta está vacía</h2>
      <p>Elige algunos productos del sistema solar.</p>
      <Link className="button button-primary" to="/productos">Ver productos</Link>
    </div> : <div className="checkout-layout">
      <div className="panel cart-list">
        {cart.map((item) => {
          const live = products.find(p => p.id === item.id)
          return <div className="cart-item" key={item.id}>
            <div><h3>{item.nombre}</h3><span className="small">{item.planetaOrigen} · {item.cantidad} ud.</span>
              {(!live || live.stock < item.cantidad) && <p className="small text-warning">Stock insuficiente o producto no disponible. Actualiza el catálogo antes de reservar.</p>}
            </div>
            <div className="cart-right"><strong>{formatGalatiCoins(item.precio * item.cantidad)}</strong>
              <button className="button button-quiet" onClick={() => remove(item.id)} type="button" disabled={enviando}>Quitar</button>
            </div>
          </div>
        })}
      </div>
      <aside className="panel order-summary">
        <h2>Reserva de inventario</h2>
        <label className="order-label">Planeta de entrega
          <select value={destino} onChange={e => setDestino(e.target.value)} disabled={enviando}>
            <option value="Bruma">Bruma</option>
            <option value="Árido">Árido</option>
            <option value="Yermo">Yermo</option>
          </select>
        </label>
        <div className="summary-row"><span>Estimación</span><strong>{formatGalatiCoins(total)}</strong></div>
        <p className="small">El importe definitivo y las existencias se validan en Spring Boot. Sin pago real.</p>
        {!session && <p className="small">Para reservar debes <Link to="/acceso" className="text-link">iniciar sesión</Link>.</p>}
        {session && !canOrder && <p className="small text-warning">La sesión venció o tu rol no puede hacer pedidos. Vuelve a ingresar como CLIENTE.</p>}
        {error && <p role="alert" className="text-warning">{error}</p>}
        <button type="button" className="button button-primary" onClick={confirmarPedido} disabled={!canOrder || lacksStock || enviando}>
          {enviando ? 'Reservando…' : 'Confirmar pedido'}
        </button>
        <p className="small">Al confirmar, el pedido queda PENDIENTE y el stock disponible se descuenta en una transacción.</p>
      </aside>
    </div>}
  </section>
}
