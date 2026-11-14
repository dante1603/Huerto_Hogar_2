// Visor reutilizable de las mallas procedurales originales de Huerto Estelar.
// WebGL 1 nativo y fallback Canvas 2D: no se crean contextos por modelo.
const VERTEX_SOURCE=`
attribute vec3 aPos;attribute vec3 aNorm;attribute vec3 aCol;
uniform float uYaw,uPitch,uAspect,uDist,uFocal;
varying vec3 vN;varying vec3 vC;
void main(){
 float cy=cos(uYaw),sy=sin(uYaw),cx=cos(uPitch),sx=sin(uPitch);
 vec3 p=vec3(cy*aPos.x+sy*aPos.z,aPos.y,-sy*aPos.x+cy*aPos.z);
 vec3 n=vec3(cy*aNorm.x+sy*aNorm.z,aNorm.y,-sy*aNorm.x+cy*aNorm.z);
 p=vec3(p.x,cx*p.y-sx*p.z,sx*p.y+cx*p.z);
 n=vec3(n.x,cx*n.y-sx*n.z,sx*n.y+cx*n.z);
 float d=uDist-p.z;
 gl_Position=vec4(p.x*uFocal/uAspect,p.y*uFocal,1.005*d-.2005,d);
 vN=n;vC=aCol;
}`;
const FRAGMENT_SOURCE=`
precision mediump float;varying vec3 vN;varying vec3 vC;
void main(){
 vec3 n=normalize(vN);vec3 light=normalize(vec3(-.40,.83,1.0));
 float lambert=max(dot(n,light),0.0);
 float rim=pow(1.0-abs(n.z),3.0);
 float lighting=.67+.47*lambert+.13*rim;
 gl_FragColor=vec4(min(vC*lighting,vec3(1.0)),1.0);
}`;

function compile(gl, type, source) {
  const shader = gl.createShader(type)
  gl.shaderSource(shader, source)
  gl.compileShader(shader)
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    const reason = gl.getShaderInfoLog(shader)
    gl.deleteShader(shader)
    throw new Error(reason || 'Shader inválido')
  }
  return shader
}

function drawSoftware(canvas, ctx, mesh, yaw, pitch, distance) {
  const width = canvas.width
  const height = canvas.height
  ctx.clearRect(0, 0, width, height)
  const sinYaw = Math.sin(yaw), cosYaw = Math.cos(yaw)
  const sinPitch = Math.sin(pitch), cosPitch = Math.cos(pitch)
  const light = [-0.4, 0.83, 1]
  const length = Math.hypot(...light)
  const lit = light.map(value => value / length)
  const faces = []
  for (let i = 0; i < mesh.p.length; i += 9) {
    const points = []
    let depth = 0
    for (let j = 0; j < 3; j++) {
      const offset = i + j * 3
      const x = mesh.p[offset], y = mesh.p[offset + 1], z = mesh.p[offset + 2]
      const rx = cosYaw * x + sinYaw * z
      const rz = -sinYaw * x + cosYaw * z
      const ry = cosPitch * y - sinPitch * rz
      const rz2 = sinPitch * y + cosPitch * rz
      const dz = Math.max(0.1, distance - rz2)
      depth += dz
      points.push([width / 2 + rx * 2.35 * height / (2 * dz), height / 2 - ry * 2.35 * height / (2 * dz)])
    }
    const nx = mesh.n[i], ny = mesh.n[i + 1], nz = mesh.n[i + 2]
    const rnx = cosYaw * nx + sinYaw * nz
    const rnz = -sinYaw * nx + cosYaw * nz
    const rny = cosPitch * ny - sinPitch * rnz
    const nnz = sinPitch * ny + cosPitch * rnz
    const lighting = 0.67 + 0.47 * Math.max(0, rnx * lit[0] + rny * lit[1] + nnz * lit[2])
      + 0.13 * Math.pow(1 - Math.abs(nnz), 3)
    const color = [mesh.c[i], mesh.c[i + 1], mesh.c[i + 2]]
      .map(value => Math.min(255, Math.round(value * lighting * 255)))
    faces.push({ points, depth: depth / 3, color: 'rgb(' + color.join(',') + ')' })
  }
  faces.sort((a, b) => b.depth - a.depth)
  for (const face of faces) {
    ctx.beginPath()
    ctx.moveTo(face.points[0][0], face.points[0][1])
    ctx.lineTo(face.points[1][0], face.points[1][1])
    ctx.lineTo(face.points[2][0], face.points[2][1])
    ctx.closePath()
    ctx.fillStyle = face.color
    ctx.fill()
  }
}

export function createModelRenderer(canvas, mesh) {
  if (!canvas || !mesh || mesh.p.length !== mesh.n.length || mesh.p.length !== mesh.c.length) {
    throw new Error('Modelo 3D inválido')
  }
  let gl = null
  try {
    gl = canvas.getContext('webgl', {
      alpha: true, antialias: true, depth: true,
      premultipliedAlpha: false, powerPreference: 'low-power',
    })
  } catch {
    // Algunos dispositivos bloquean WebGL; usar Canvas 2D si está disponible.
  }
  if (!gl) {
    const ctx = canvas.getContext('2d', { alpha: true })
    if (!ctx) throw new Error('Este navegador no dispone de un visor 3D compatible')
    return {
      kind: 'canvas2d',
      draw({ yaw, pitch, distance }) { drawSoftware(canvas, ctx, mesh, yaw, pitch, distance) },
      dispose() {},
    }
  }

  const vertex = compile(gl, gl.VERTEX_SHADER, VERTEX_SOURCE)
  const fragment = compile(gl, gl.FRAGMENT_SHADER, FRAGMENT_SOURCE)
  const program = gl.createProgram()
  gl.attachShader(program, vertex)
  gl.attachShader(program, fragment)
  gl.linkProgram(program)
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
    const reason = gl.getProgramInfoLog(program)
    gl.deleteShader(vertex)
    gl.deleteShader(fragment)
    gl.deleteProgram(program)
    throw new Error(reason || 'No se pudo iniciar el visor WebGL')
  }
  gl.useProgram(program)
  const names = ['aPos', 'aNorm', 'aCol']
  const attributes = names.map(name => gl.getAttribLocation(program, name))
  const uniforms = Object.fromEntries(['uYaw', 'uPitch', 'uAspect', 'uDist', 'uFocal']
    .map(name => [name, gl.getUniformLocation(program, name)]))
  const buffers = [mesh.p, mesh.n, mesh.c].map(values => {
    const buffer = gl.createBuffer()
    gl.bindBuffer(gl.ARRAY_BUFFER, buffer)
    gl.bufferData(gl.ARRAY_BUFFER, values, gl.STATIC_DRAW)
    return buffer
  })
  gl.enable(gl.DEPTH_TEST)
  gl.depthFunc(gl.LEQUAL)
  gl.disable(gl.CULL_FACE)

  return {
    kind: 'webgl',
    draw({ yaw, pitch, distance }) {
      const width = canvas.width, height = canvas.height
      gl.viewport(0, 0, width, height)
      gl.clearColor(0, 0, 0, 0)
      gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT)
      gl.useProgram(program)
      gl.uniform1f(uniforms.uYaw, yaw)
      gl.uniform1f(uniforms.uPitch, pitch)
      gl.uniform1f(uniforms.uAspect, width / height)
      gl.uniform1f(uniforms.uDist, distance)
      gl.uniform1f(uniforms.uFocal, 2.35)
      for (let i = 0; i < 3; i++) {
        if (attributes[i] < 0) continue
        gl.bindBuffer(gl.ARRAY_BUFFER, buffers[i])
        gl.vertexAttribPointer(attributes[i], 3, gl.FLOAT, false, 0, 0)
        gl.enableVertexAttribArray(attributes[i])
      }
      gl.drawArrays(gl.TRIANGLES, 0, mesh.count)
      for (const attribute of attributes) if (attribute >= 0) gl.disableVertexAttribArray(attribute)
    },
    dispose() {
      for (const buffer of buffers) gl.deleteBuffer(buffer)
      gl.deleteProgram(program)
      gl.deleteShader(vertex)
      gl.deleteShader(fragment)
    },
  }
}
