import { useMemo, useState } from 'react'
import ProductGrid from '../components/ProductGrid.jsx'

export default function Productos({ products, loading, error, add, retry }) {
  const [planet, setPlanet] = useState('Todos')
  const planets = useMemo(() => ['Todos', ...new Set(products.map((p) => p.planetaOrigen).filter(Boolean))], [products])
  const filtered = useMemo(() => planet === 'Todos' ? products : products.filter((p) => p.planetaOrigen === planet), [products, planet])
  return <section>
    <div className="page-title"><span className="eyebrow">Semillas, frutos y hallazgos</span><h1>Catálogo galáctico</h1><p>Precios y existencias consultados al backend Spring Boot.</p></div>
    {!loading && !error && products.length > 0 && <div className="filter-bar" role="group" aria-label="Filtrar por planeta">
      {planets.map((name) => <button key={name} type="button" className={name === planet ? 'chip selected' : 'chip'} onClick={() => setPlanet(name)} aria-pressed={name === planet}>{name}</button>)}
    </div>}
    <ProductGrid products={filtered} loading={loading} error={error} add={add} retry={retry} />
  </section>
}
