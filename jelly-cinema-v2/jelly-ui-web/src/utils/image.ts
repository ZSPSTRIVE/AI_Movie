const COLORS = [
  '#e11d48',
  '#db2777',
  '#c026d3',
  '#9333ea',
  '#7c3aed',
  '#6366f1',
  '#3b82f6',
  '#0ea5e9',
  '#06b6d4',
  '#14b8a6',
  '#10b981',
  '#22c55e',
]

const BAD_HOSTS = ['img.example.com', 'via.placeholder.com', 'example.com']

function pickColor(seed: string): string {
  let hash = 0
  for (let i = 0; i < seed.length; i += 1) {
    hash = (hash << 5) - hash + seed.charCodeAt(i)
    hash |= 0
  }
  const index = Math.abs(hash) % COLORS.length
  return COLORS[index]
}

export function buildPlaceholderImage(title?: string): string {
  const labelSource = (title || 'No Image').trim()
  const label = labelSource.length > 4 ? labelSource.slice(0, 4) : labelSource
  const color = pickColor(labelSource || 'No Image')
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="600" viewBox="0 0 400 600">
    <rect fill="${color}" width="400" height="600"/>
    <text x="200" y="280" text-anchor="middle" fill="white" font-size="24" font-family="sans-serif">${label}</text>
    <text x="200" y="320" text-anchor="middle" fill="white" font-size="16" font-family="sans-serif">N/A</text>
  </svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

export function shouldUsePlaceholder(src?: string | null): boolean {
  if (!src) return true
  return BAD_HOSTS.some((host) => src.includes(host))
}

export function normalizeImageUrl(src?: string | null, title?: string): string {
  if (shouldUsePlaceholder(src)) {
    return buildPlaceholderImage(title)
  }
  return src || buildPlaceholderImage(title)
}
