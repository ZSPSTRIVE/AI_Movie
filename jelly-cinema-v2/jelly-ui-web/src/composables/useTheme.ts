import { ref, watch, onMounted } from 'vue'

const isDark = ref(false)

function applyTheme(dark: boolean) {
  if (dark) {
    document.documentElement.classList.add('dark')
  } else {
    document.documentElement.classList.remove('dark')
  }
}

export function useTheme() {
  onMounted(() => {
    const saved = localStorage.getItem('jelly-theme')
    if (saved === 'dark') {
      isDark.value = true
    } else if (saved === 'light') {
      isDark.value = false
    } else {
      isDark.value = window.matchMedia('(prefers-color-scheme: dark)').matches
    }
    applyTheme(isDark.value)
  })

  watch(isDark, (val) => {
    localStorage.setItem('jelly-theme', val ? 'dark' : 'light')
    applyTheme(val)
  })

  function toggleTheme() {
    isDark.value = !isDark.value
  }

  return { isDark, toggleTheme }
}
