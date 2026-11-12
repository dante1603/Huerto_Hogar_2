import { Link } from 'react-router-dom'

const clp = (n) => new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 }).format(n)

export default function Carrito({ cart, products, remove, total }) {
  return <section>
    <div className="page-title"><span className="eyebrow">Tu cargamento</span><h1>Carrito</h1><p>Selección local. Todavía no genera pedidos reales ni reserva stock.</p></div>
    {!cart.length ? <div className="panel empty-state"><span className="large-icon">🧺</span><h2>Tu cesta está vacía</h2><p>Elige algunos productos del sistema solar.</p><Link className="button button-primary" to="/productos">Ver productos</Link></div> :
      <div className="checkout-layout">
        <div className="panel cart-list">
          {cart.map((item) => {
            const live = products.find((p) => p.id === item.id)
            return <div className="cart-item" key={item.id}>
              <div><h3>{item.nombre}</h3><span className="small">{item.planetaOrigen} · {item.cantidad} ud.</span>{live && live.stock < item.cantidad && <p className="small text-warning">Stock disponible: {live.stock}. Ajusta al realizar el pedido.</p>}</div>
              <div className="cart-right"><strong>{clp(item.precio * item.cantidad)}</strong><button className="button button-quiet" onClick={() => remove(item.id)} type="button">Quitar</button></div>
            </div>
          })}
        </div>
        <aside className="panel order-summary"><h2>Resumen</h2><div className="summary-row"><span>Productos</span><strong>{clp(total)}</strong></div><p className="small">Precio indicativo: se validará contra el servidor al implementar pedidos.</p><button type="button" className="button button-primary" disabled aria-describedby="checkout-pending">Realizar pedido — próximamente</button><p className="small" id="checkout-pending">Aún no está habilitado el servicio de pedidos.</p></aside>
      </div>
    }
  </section>
}
