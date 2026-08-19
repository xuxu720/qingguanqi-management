// 单张测试 — 快速试不同模型/提示词
// $env:SF_API_KEY="sk-xxx"; node test-one-icon.mjs

const API_KEY = process.env.SF_API_KEY
if (!API_KEY) {
  console.error('请先: $env:SF_API_KEY="sk-你的key"')
  process.exit(1)
}

// ====== 改这里 ======
const MODEL  = 'Kwai-Kolors/Kolors'          // 换模型在这行
const PROMPT = process.argv[2] || `
a circular industrial emblem featuring a pipeline cleaning gauge tool
inside a pipe cross-section, surrounded by gear teeth border,
deep navy blue #002EA6 and warm gold #FFE76F two-tone,
clean geometric flat design, no animals, no organic shapes,
no text, no letters, no watermarks, white background
`.trim().replace(/\n/g, ' ')
// ====================

const res = await fetch('https://api.siliconflow.cn/v1/images/generations', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${API_KEY}`,
    'Content-Type': 'application/json',
  },
  body: JSON.stringify({ model: MODEL, prompt: PROMPT, n: 1, size: '1024x1024' }),
})

const json = await res.json()
if (!res.ok) {
  console.error(`❌ HTTP ${res.status}:`, JSON.stringify(json, null, 2).slice(0, 500))
  process.exit(1)
}

const url = json.data?.[0]?.url
if (url) {
  console.log(`✓ 生成成功`)
  console.log(`URL: ${url}`)
  // 下载
  const img = await fetch(url)
  const buf = Buffer.from(await img.arrayBuffer())
  const out = `test-${MODEL.replace(/[\/\\]/g, '_')}.png`
  import('fs').then(fs => {
    import('fs').then(fs2 => fs2.writeFileSync(out, buf))
  }).catch(() => {})
  const fs = await import('fs')
  fs.writeFileSync(out, buf)
  console.log(`✓ 已保存: ${out}`)
  console.log(`文件大小: ${(buf.length / 1024).toFixed(1)} KB`)
} else {
  console.log('响应:', JSON.stringify(json, null, 2).slice(0, 500))
}
