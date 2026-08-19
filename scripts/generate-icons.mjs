// 批量生图 — SiliconFlow API
// 用法:
//   CMD:      set SF_API_KEY=sk-你的key && node generate-icons.mjs
//   PowerShell: $env:SF_API_KEY="sk-你的key"; node generate-icons.mjs

const API_KEY = process.env.SF_API_KEY
if (!API_KEY) {
  console.error('请先设置: set SF_API_KEY=sk-你的key')
  process.exit(1)
}

const API = 'https://api.siliconflow.cn/v1/images/generations'
const MODEL = 'Tongyi-MAI/Z-Image-Turbo'
const OUTDIR = './icons'

import { mkdirSync, writeFileSync } from 'fs'

mkdirSync(OUTDIR, { recursive: true })

const ICONS = {
  logo:      'a circular industrial badge emblem, pipeline cross-section with a mechanical pig scraper inside, gear teeth around the edge, deep navy blue #002EA6 and warm gold #FFE76F two-tone, clean geometric, thick lines, no text, no letters, no numbers, white background',
  agent:     'a friendly compact robot head with a small chat bubble, deep navy blue and warm gold two-tone, clean geometric shapes, no text, no letters, white background',
  pig:       'a cylindrical pipeline pig tool with two rubber sealing discs on each end, industrial equipment, deep navy blue body with gold accents, clean silhouette, no text, white background',
  pipeline:  'a straight horizontal oil pipeline with a round pressure gauge attached on top, industrial, deep navy blue and gold two-tone, thick clean lines, no text, white background',
  station:   'a small industrial valve station building with a small flag on top, deep navy blue and warm gold, clean geometric, no text, white background',
  segment:   'two connected industrial pipe segments with a flanged bolted joint between them, deep navy blue and gold, thick clean lines, no text, white background',
  warning:   'a bold warning shield shape with an exclamation mark inside, deep navy blue background with gold border and mark, clean geometric, no text, white background',
  calc:      'a calculator with a small rising trend line chart next to it, deep navy blue and gold, clean shapes, no text, white background',
  create:    'a pipeline pig launcher barrel with a bold plus symbol overlay, industrial equipment, deep navy blue and gold, clean geometric, no text, white background',
  running:   'a play button triangle with dynamic speed lines behind it and a pipeline silhouette, deep navy blue and gold, clean bold shapes, no text, white background',
  dashboard: 'a round dashboard gauge meter with a needle pointing to the green zone, deep navy blue face with gold markings and needle, clean geometric, no text, white background',
}

async function generate(name, prompt) {
  console.log(`\n=== 生成: ${name} ===`)
  const body = JSON.stringify({ model: MODEL, prompt, n: 1, size: '1024x1024' })

  const res = await fetch(API, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${API_KEY}`,
      'Content-Type': 'application/json',
    },
    body,
  })

  const json = await res.json()
  if (!res.ok) {
    console.error(`  ❌ HTTP ${res.status}:`, JSON.stringify(json).slice(0, 300))
    return
  }

  const data = json.data?.[0]
  const url = data?.url
  const b64 = data?.b64_json

  if (url) {
    console.log(`  下载: ${url.slice(0, 80)}...`)
    const img = await fetch(url)
    const buf = Buffer.from(await img.arrayBuffer())
    writeFileSync(`${OUTDIR}/${name}.png`, buf)
    console.log(`  ✓ ${OUTDIR}/${name}.png (${buf.length} bytes)`)
  } else if (b64) {
    writeFileSync(`${OUTDIR}/${name}.png`, Buffer.from(b64, 'base64'))
    console.log(`  ✓ ${OUTDIR}/${name}.png (base64 decoded)`)
  } else {
    console.log(`  ⚠ 未找到图片数据，响应:`, JSON.stringify(json).slice(0, 300))
  }
}

// 串行执行，避免限流
for (const [name, prompt] of Object.entries(ICONS)) {
  await generate(name, prompt)
  await new Promise(r => setTimeout(r, 1500)) // 1.5s 间隔
}

console.log('\n=== 完成 ===')
