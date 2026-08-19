const STORAGE_KEY = 'qingguanqi_agent_config'

export interface AgentConfig {
  apiKey: string
  apiBaseUrl: string
  model: string
}

export function getConfig(): AgentConfig | null {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) {
      const cfg = JSON.parse(saved)
      if (cfg.apiKey) return cfg
    }
  } catch { /* */ }
  return null
}
