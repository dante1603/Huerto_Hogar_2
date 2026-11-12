import ProductCard from './ProductCard.jsx'

export default function ProductGrid({ products, loading, error, add, retry }) {
  if (loading) return <div className="panel" role="status">Consultando los productos de la API…</div>
  if (error) return <section className="panel error-panel" role="alert"><h2>No se pudo cargar el catálogo</h2><p>{error}</p><p className="small">Comprueba que Spring Boot Catálogo esté en ejecución en el puerto 8080.</p><button className="button button-primary" type="button" onClick={retry}>Reintentar</button></section>
  if (!products.length) return <section className="panel"><h2>No hay productos publicados</h2><p>La base de datos respondió correctamente, pero el catálogo está vacío.</p><p className="small">Para una demostración local, ejecuta Spring Boot con el perfil <code>demo</code>.</p></section>
  return <div className="product-grid">{products.map((product) => <ProductCard key={product.id} product={product} add={add} />)}</div>
}
