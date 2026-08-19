import { ref, watch } from 'vue'

type ThemeName = 'gold' | 'ocean'

const THEME_KEY = 'qingguanqi_theme'
const current = ref<ThemeName>(
  (localStorage.getItem(THEME_KEY) as ThemeName) || 'gold'
)

function apply() {
  document.documentElement.setAttribute('data-theme', current.value)
  localStorage.setItem(THEME_KEY, current.value)
}

function toggle() {
  current.value = current.value === 'gold' ? 'ocean' : 'gold'
}

watch(current, apply, { immediate: true })

export function useTheme() {
  return {
    theme: current,
    toggle,
  }
}
