import { useEffect, useRef, useState } from 'react'
import { createModelRenderer } from './core/modelRenderer.js'
import { prototypeById } from './modelRegistry.js'

const clamp = (value, min, max) => Math.max(min, Math.min(max, value))

/** Un único canvas compartido. El modelo se importa solo cuando se selecciona. */
export default function ModelCanvas({ modelId, modelName }) {
  const canvasRef = useRef(null)
  const [loadState, setLoadState] = useState({ id: null, status: 'loading' })

  useEffect(() => {
    const canvas = canvasRef.current
    let disposed = false
    let renderer = null
    let resizeObserver = null
    let frame = 0
    let lastFrame = 0
    let yaw = 0.6
    let pitch = 0.2
    let distance = 4.9
    let autoRotate = true
    let pinchDistance = null
    const pointers = new Map()

    function draw() {
      if (!renderer || disposed) return
      const rect = canvas.getBoundingClientRect()
      const pixelRatio = Math.min(window.devicePixelRatio || 1, 1.6)
      const width = Math.max(2, Math.round(rect.width * pixelRatio))
      const height = Math.max(2, Math.round(rect.height * pixelRatio))
      if (canvas.width !== width) canvas.width = width
      if (canvas.height !== height) canvas.height = height
      renderer.draw({ yaw, pitch, distance })
    }

    function loop(time) {
      if (disposed || !autoRotate || renderer?.kind !== 'webgl') return
      if (time - lastFrame > 32) {
        yaw += Math.min(time - lastFrame, 100) * 0.00027
        lastFrame = time
        draw()
      }
      frame = window.requestAnimationFrame(loop)
    }

    function stopRotation() {
      autoRotate = false
      window.cancelAnimationFrame(frame)
    }

    function pointerDown(event) {
      event.preventDefault()
      canvas.setPointerCapture(event.pointerId)
      pointers.set(event.pointerId, [event.clientX, event.clientY])
      stopRotation()
    }

    function pointerMove(event) {
      if (!pointers.has(event.pointerId)) return
      const prev = pointers.get(event.pointerId)
      const dx = event.clientX - prev[0]
      const dy = event.clientY - prev[1]
      pointers.set(event.pointerId, [event.clientX, event.clientY])
      if (pointers.size === 1) {
        yaw += dx * 0.012
        pitch = clamp(pitch + dy * 0.009, -1.25, 1.25)
      } else if (pointers.size === 2) {
        const points = [...pointers.values()]
        const currentDistance = Math.hypot(
          points[0][0] - points[1][0],
          points[0][1] - points[1][1],
        )
        if (pinchDistance !== null) {
          distance = clamp(distance + (pinchDistance - currentDistance) * 0.009, 3.2, 8.2)
        }
        pinchDistance = currentDistance
      }
      draw()
    }

    function pointerUp(event) {
      pointers.delete(event.pointerId)
      pinchDistance = null
    }

    function wheel(event) {
      event.preventDefault()
      stopRotation()
      distance = clamp(distance + event.deltaY * 0.006, 3.2, 8.2)
      draw()
    }

    function keyDown(event) {
      const key = event.key
      if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', '+', '-'].includes(key)) return
      event.preventDefault()
      stopRotation()
      if (key === 'ArrowLeft') yaw -= 0.14
      if (key === 'ArrowRight') yaw += 0.14
      if (key === 'ArrowUp') pitch = clamp(pitch - 0.12, -1.25, 1.25)
      if (key === 'ArrowDown') pitch = clamp(pitch + 0.12, -1.25, 1.25)
      if (key === '+') distance = clamp(distance - 0.35, 3.2, 8.2)
      if (key === '-') distance = clamp(distance + 0.35, 3.2, 8.2)
      draw()
    }

    canvas.addEventListener('pointerdown', pointerDown)
    canvas.addEventListener('pointermove', pointerMove)
    canvas.addEventListener('pointerup', pointerUp)
    canvas.addEventListener('pointercancel', pointerUp)
    canvas.addEventListener('wheel', wheel, { passive: false })
    canvas.addEventListener('keydown', keyDown)

    async function mount() {
      setLoadState({ id: modelId, status: 'loading' })
      const descriptor = prototypeById(modelId)
      if (!descriptor) {
        setLoadState({ id: modelId, status: 'error' })
        return
      }
      try {
        const module = await descriptor.load()
        if (disposed) return
        renderer = createModelRenderer(canvas, module.default())
        if ('ResizeObserver' in window) {
          resizeObserver = new ResizeObserver(draw)
          resizeObserver.observe(canvas)
        } else {
          window.addEventListener('resize', draw)
        }
        setLoadState({ id: modelId, status: 'ready' })
        draw()
        if (renderer.kind === 'webgl') frame = window.requestAnimationFrame(loop)
      } catch (error) {
        if (!disposed) {
          console.warn('Visor del prototipo no disponible:', error)
          setLoadState({ id: modelId, status: 'error' })
        }
      }
    }
    mount()

    return () => {
      disposed = true
      window.cancelAnimationFrame(frame)
      resizeObserver?.disconnect()
      window.removeEventListener('resize', draw)
      canvas.removeEventListener('pointerdown', pointerDown)
      canvas.removeEventListener('pointermove', pointerMove)
      canvas.removeEventListener('pointerup', pointerUp)
      canvas.removeEventListener('pointercancel', pointerUp)
      canvas.removeEventListener('wheel', wheel)
      canvas.removeEventListener('keydown', keyDown)
      pointers.clear()
      renderer?.dispose()
    }
  }, [modelId])

  const status = loadState.id === modelId ? loadState.status : 'loading'

  return (
    <div className="prototype-canvas-stage">
      <canvas
        ref={canvasRef}
        className="prototype-canvas"
        role="img"
        tabIndex={0}
        aria-label={'Modelo 3D provisional de ' + modelName + '. Arrastra para girar; usa flechas o rueda para controlar la vista.'}
      />
      {status === 'loading' && <span className="prototype-canvas-message" role="status">Preparando modelo 3D…</span>}
      {status === 'error' && <span className="prototype-canvas-message" role="status">Visor no disponible en este navegador.</span>}
      <span className="prototype-canvas-hint">Arrastra para girar · rueda o pellizca para ampliar</span>
    </div>
  )
}
