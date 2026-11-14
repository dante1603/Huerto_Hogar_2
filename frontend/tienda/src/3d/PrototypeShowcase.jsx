import { useState } from 'react'
import ModelCanvas from './ModelCanvas.jsx'
import { PROTOTYPE_MODELS } from './modelRegistry.js'
import './prototype-showcase.css'

export default function PrototypeShowcase() {
  const [selectedId, setSelectedId] = useState(PROTOTYPE_MODELS[0].id)
  const selected = PROTOTYPE_MODELS.find(model => model.id === selectedId) || PROTOTYPE_MODELS[0]

  return (
    <section className="prototype-showcase" id="cultivos-3d" aria-labelledby="prototype-title">
      <div className="prototype-heading">
        <div>
          <span className="eyebrow">✧ Colección experimental · Galaxym</span>
          <h2 id="prototype-title">Cultivos de otros mundos</h2>
        </div>
        <span className="prototype-state">6 modelos provisionales</span>
      </div>
      <p className="prototype-intro">
        Recuperados del prototipo HTML original. Puedes rotarlos y observar su geometría low-poly.
        No están aprobados todavía ni representan artículos disponibles para comprar.
      </p>
      <div className="prototype-content">
        <div className="prototype-view">
          <ModelCanvas modelId={selected.id} modelName={selected.nombre} />
          <div className="prototype-view-details">
            <div>
              <span className="prototype-origin">{selected.categoria} · {selected.origen}</span>
              <h3>{selected.nombre}</h3>
              <p>{selected.descripcion}</p>
            </div>
            <span className="prototype-watermark">Muestra visual<br/>Sin precio ni stock</span>
          </div>
        </div>
        <div className="prototype-options" aria-label="Seleccionar cultivo 3D provisional">
          {PROTOTYPE_MODELS.map(model => (
            <button
              type="button"
              key={model.id}
              className={'prototype-option' + (selectedId === model.id ? ' active' : '')}
              onClick={() => setSelectedId(model.id)}
              aria-pressed={selectedId === model.id}
            >
              <span className="prototype-option-symbol" aria-hidden="true">✳</span>
              <span><strong>{model.nombre}</strong><small>{model.categoria}</small></span>
              <span className="prototype-option-arrow" aria-hidden="true">↗</span>
            </button>
          ))}
          <p className="prototype-disclaimer">Los precios y existencias válidos siguen estando exclusivamente en el catálogo conectado a Spring Boot.</p>
        </div>
      </div>
    </section>
  )
}
