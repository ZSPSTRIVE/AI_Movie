import { buildPlaceholderImage, shouldUsePlaceholder } from '@/utils/image'

type ImgState = {
  title: string
  originalSrc: string | null
  observer?: IntersectionObserver
  isLoaded: boolean
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

function applyPlaceholder(el: HTMLImageElement, title: string) {
  const placeholder = buildPlaceholderImage(title)
  // 只有当当前 src 不是 placeholder 时才设置，避免闪烁
  if (el.src !== placeholder) {
    el.src = placeholder
    // 初始样式：稍微透明或模糊，具体效果可调整
    el.style.opacity = '1'
    el.style.transition = 'opacity 0.5s ease-in-out'
  }
}

function loadRealImage(el: HTMLImageElement, realSrc: string, state: ImgState) {
  if (shouldUsePlaceholder(realSrc) || state.isLoaded) return

  // 创建隐藏图片预加载
  const img = new Image()

  img.onload = () => {
    state.isLoaded = true
    el.style.opacity = '0.5' // 先淡出一点

    requestAnimationFrame(() => {
      el.src = realSrc
      el.style.opacity = '1' // 再淡入
    })
  }

  img.onerror = () => {
    console.warn('Image load failed, keeping placeholder:', realSrc)
    // 保持占位符
    applyPlaceholder(el, state.title)
  }

  img.src = realSrc
}

export const imgFallback = {
  mounted(el: HTMLImageElement, binding: { value: unknown }) {
    const title = getTitle(el, binding.value)

    // 优先从 data-src 获取真实地址，如果没有则看 src (但 src 可能已经被设为 placeholder 了)
    // 我们约定：如果是 SSR 或初始渲染，src 可能是真实地址。
    // 为了支持 lazy load，建议 img 标签上 :src="realUrl" 
    // 我们的指令会在 mounted 时迅速替换为 placeholder
    const originalSrc = el.getAttribute('src') || el.dataset.src

    // 初始化状态
    const state: ImgState = {
      title,
      originalSrc,
      isLoaded: false
    }
      ; (el as any)[STATE_KEY] = state

    // 1. 立即显示多彩占位符
    applyPlaceholder(el, title)

    // 2. 如果没有真实图片地址，直接返回
    if (!originalSrc || shouldUsePlaceholder(originalSrc)) return

    // 3. 使用 IntersectionObserver 实现懒加载
    if ('IntersectionObserver' in window) {
      const observer = new IntersectionObserver((entries) => {
        const entry = entries[0]
        if (entry.isIntersecting) {
          loadRealImage(el, originalSrc, state)
          observer.disconnect()
        }
      }, {
        rootMargin: '100px' // 提前 100px 加载
      })

      observer.observe(el)
      state.observer = observer
    } else {
      // 降级：直接加载
      setTimeout(() => loadRealImage(el, originalSrc, state), 100)
    }
  },

  updated(el: HTMLImageElement, binding: { value: unknown }) {
    const state = (el as any)[STATE_KEY] as ImgState
    const newTitle = getTitle(el, binding.value)
    const newSrc = el.getAttribute('src') || el.dataset.src

    if (!state) return

    // 简单处理：如果 title 变了，更新 placeholder
    if (newTitle !== state.title) {
      state.title = newTitle
      if (!state.isLoaded) {
        applyPlaceholder(el, newTitle)
      }
    }

    // 如果 src 变了且不是 placeholder，说明有新图要加载
    if (newSrc && newSrc !== state.originalSrc && !shouldUsePlaceholder(newSrc)) {
      state.originalSrc = newSrc
      state.isLoaded = false
      applyPlaceholder(el, newTitle)

      // 重新观察
      if (state.observer) {
        state.observer.disconnect()
        state.observer.observe(el)
      } else {
        loadRealImage(el, newSrc, state)
      }
    }
  },

  unmounted(el: HTMLImageElement) {
    const state = (el as any)[STATE_KEY] as ImgState
    if (state && state.observer) {
      state.observer.disconnect()
    }
    delete (el as any)[STATE_KEY]
  }
}

export default imgFallback
