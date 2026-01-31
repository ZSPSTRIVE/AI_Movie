import { buildPlaceholderImage, shouldUsePlaceholder } from '@/utils/image'

type ImgState = {
  handler: () => void
  lastSrc: string | null
  title: string
}

const STATE_KEY = '__imgFallbackState'

function getTitle(el: HTMLImageElement, bindingValue: unknown): string {
  if (typeof bindingValue === 'string') return bindingValue
  if (bindingValue && typeof bindingValue === 'object' && 'title' in (bindingValue as any)) {
    const t = (bindingValue as any).title
    if (typeof t === 'string') return t
  }
  return el.getAttribute('alt') || 'No Image'
}

function applyFallback(el: HTMLImageElement, title: string) {
  const placeholder = buildPlaceholderImage(title)
  if (el.src !== placeholder) {
    el.src = placeholder
  }
}

export const imgFallback = {
  mounted(el: HTMLImageElement, binding: { value: unknown }) {
    const title = getTitle(el, binding.value)
    const handler = () => {
      const state = (el as any)[STATE_KEY] as ImgState | undefined
      if (state) {
        applyFallback(el, state.title)
      } else {
        applyFallback(el, title)
      }
    }

    const state: ImgState = {
      handler,
      lastSrc: el.getAttribute('src'),
      title,
    }
    ;(el as any)[STATE_KEY] = state

    el.addEventListener('error', handler)

    const currentSrc = el.getAttribute('src')
    if (shouldUsePlaceholder(currentSrc)) {
      applyFallback(el, title)
    }
  },
  updated(el: HTMLImageElement, binding: { value: unknown }) {
    const state = (el as any)[STATE_KEY] as ImgState | undefined
    const nextTitle = getTitle(el, binding.value)
    const currentSrc = el.getAttribute('src')

    if (state) {
      state.title = nextTitle
      if (state.lastSrc !== currentSrc) {
        state.lastSrc = currentSrc
      }
    }

    if (shouldUsePlaceholder(currentSrc)) {
      applyFallback(el, nextTitle)
    }
  },
  unmounted(el: HTMLImageElement) {
    const state = (el as any)[STATE_KEY] as ImgState | undefined
    if (state) {
      el.removeEventListener('error', state.handler)
      delete (el as any)[STATE_KEY]
    }
  },
}

export default imgFallback
