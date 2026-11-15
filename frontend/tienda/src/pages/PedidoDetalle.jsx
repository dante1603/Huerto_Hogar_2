import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { consultarPedido } from '../lib/api.js'
import { formatGalatiCoins } from '../lib/money.js'

export default function PedidoDetalle({ session }) {
  const { id } = useParams()
  const [order, setOrder] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    setOrder(null)
    setError('')
    setLoading(true)
    consultarPedido(id, session.accessToken)
      .then(data => { if (!cancelled) setOrder(data) })
      .catch(cause => { if (!cancelled) setError(cause.message || 'No se pudo consultar esta orden.') })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [id, session.accessToken])

  return <section>
    <div className="page-title">
      <span className="eyebrow">Detalle desde el backend</span>
      <h1>Pedido #{id}</h1>
      <p>La API valida que el cliente sea propietario o que su rol tenga permiso de consulta.</p>
    </div>
    <p><Link className="text-link" to="/pedidos">← Volver a órdenes</Link></p>
    {loading && <div className="panel" role="status">Consultando pedido…</div>}
    {error && <div className="panel error-panel" role="alert">{error}</div>}
    {!loading && !error && order && <article className="panel order-card">
      <span className="eyebrow">Estado actual: {order.estado}</span>
      <h2>Destino: {order.planetaDestino}</h2>
      <p>Fecha registrada: {order.creadoEn ? new Date(order.creadoEn).toLocaleString('es-CL') : 'No informada'}</p>
      {session.rolUsuario !== 'CLIENTE' && <p>Cliente #{order.clienteId}</p>}
      <ul className="order-items">{order.productos.map(item =>
        <li key={item.productoId}>{item.nombreProducto} — {item.cantidad} × {formatGalatiCoins(item.precioUnitario)} = {formatGalatiCoins(item.subtotal)}</li>
      )}</ul>
      <p><strong>Total: {formatGalatiCoins(order.total)}</strong></p>
      <p className="small">La reserva está registrada. No representa pago o entrega.</p>
    </article>}
  </section>
}
