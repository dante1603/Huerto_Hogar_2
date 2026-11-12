import { Link } from 'react-router-dom'
import ProductGrid from '../components/ProductGrid.jsx'

export default function Inicio({ products, loading, error, add, retry }) {
  return <>
    <section className="hero">
      <div className="hero-copy">
        <span className="eyebrow">✳ Entregas más allá de la atmósfera</span>
        <h1>Verduras de otro <em>mundo.</em></h1>
        <p>Un pequeño mercado entre planetas. Descubre los cultivos de Bruma, los oasis de Árido y las rarezas del Yermo.</p>
        <Link className="button button-primary" to="/productos">Explorar catálogo</Link>
      </div>
      <div className="hero-scene" aria-hidden="true"><span className="planet planet-main" /><span className="planet planet-small" /><span className="scene-leaf">🌱</span></div>
    </section>
    <section className="section-title"><div><span className="eyebrow">Catálogo conectado</span><h2>Productos del sistema</h2></div><Link to="/productos" className="text-link">Ver todos →</Link></section>
    <ProductGrid products={products.slice(0, 3)} loading={loading} error={error} add={add} retry={retry} />
    <aside className="info-strip"><span>🛰️</span><p>Este es un comercio ficticio integrado con Galaxym. El catálogo se consulta a una API Spring Boot; las misiones y entregas todavía están en desarrollo.</p></aside>
  </>
}
