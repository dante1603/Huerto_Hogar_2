export function formatGalatiCoins(value) {
  return new Intl.NumberFormat('es-CL', { maximumFractionDigits: 0 }).format(Number(value) || 0) + ' GalatiCoins'
}
