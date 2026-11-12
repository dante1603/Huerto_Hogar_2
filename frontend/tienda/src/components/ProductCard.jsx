import { useState } from 'react'

const icons = { Bruma: '🌿', 'Árido': '🌵', Arido: '🌵', Yermo: '💎' }
const asCLP = (n) => new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 }).format(n)

export default function ProductCard({ product, add }) {
  const [expanded, setExpanded] = useState(false)
  return (
    <article className="product-card">
      <div className="product-art" aria-hidden="true">
        <span className="orbit" />
        <span>{icons[product.planetaOrigen] || '🪴'}</span>
      </div>
      <div className="product-body">
        <div className="product-eyebrow">{product.planetaOrigen || 'Sistema Galaxym'}</div>
        <h3>{product.nombre}</h3>
        <div className="product-price">{asCLP(Number(product.precio))}</div>
        <div className="stock">{product.stock > 0 ? `En inventario: ${product.stock}` : 'Agotado'}</div>
        {expanded && <p className="product-desc">{product.descripcion}</p>}
        <div className="product-actions">
          <button type="button" className="button button-primary" onClick={() => add(product)} disabled={!(Number(product.stock) > 0)}>Añadir</button>
          <button type="button" className="button button-quiet" onClick={() => setExpanded((x) => !x)} aria-expanded={expanded}>{expanded ? 'Menos' : 'Detalle'}</button>
        </div>
      </div>
    </article>
  )
}
