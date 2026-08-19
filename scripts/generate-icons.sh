#!/bin/bash
# 批量生图 — SiliconFlow API (OpenAI-compatible)
# 用法: export SF_API_KEY="sk-你的key" && bash generate-icons.sh

API_KEY="${SF_API_KEY:-}"
if [ -z "$API_KEY" ]; then
  echo "请先设置环境变量: export SF_API_KEY=\"sk-你的key\""
  exit 1
fi

API="https://api.siliconflow.cn/v1/images/generations"
MODEL="Tongyi-MAI/Z-Image-Turbo"   # 6B, 8步, 速度快
OUTDIR="./icons"
mkdir -p "$OUTDIR"

declare -A ICONS
ICONS["logo"]="a circular industrial badge emblem, pipeline cross-section with a mechanical pig scraper inside, gear teeth around the edge, deep navy blue #002EA6 and warm gold #FFE76F two-tone, clean geometric, thick lines, no text, no letters, no numbers, white background"
ICONS["agent"]="a friendly compact robot head with a small chat bubble, deep navy blue and warm gold two-tone, clean geometric shapes, no text, no letters, white background"
ICONS["pig"]="a cylindrical pipeline pig tool with two rubber sealing discs on each end, industrial equipment, deep navy blue body with gold accents, clean silhouette, no text, white background"
ICONS["pipeline"]="a straight horizontal oil pipeline with a round pressure gauge attached on top, industrial, deep navy blue and gold two-tone, thick clean lines, no text, white background"
ICONS["station"]="a small industrial valve station building with a small flag on top, deep navy blue and warm gold, clean geometric, no text, white background"
ICONS["segment"]="two connected industrial pipe segments with a flanged bolted joint between them, deep navy blue and gold, thick clean lines, no text, white background"
ICONS["warning"]="a bold warning shield shape with an exclamation mark inside, deep navy blue background with gold border and mark, clean geometric, no text, white background"
ICONS["calc"]="a calculator with a small rising trend line chart next to it, deep navy blue and gold, clean shapes, no text, white background"
ICONS["create"]="a pipeline pig launcher barrel with a bold plus symbol overlay, industrial equipment, deep navy blue and gold, clean geometric, no text, white background"
ICONS["running"]="a play button triangle with dynamic speed lines behind it and a pipeline silhouette, deep navy blue and gold, clean bold shapes, no text, white background"
ICONS["dashboard"]="a round dashboard gauge meter with a needle pointing to the green zone, deep navy blue face with gold markings and needle, clean geometric, no text, white background"

for name in "${!ICONS[@]}"; do
  prompt="${ICONS[$name]}"
  echo "=== 生成: $name ==="
  echo "Prompt: $prompt"

  curl -s -X POST "$API" \
    -H "Authorization: Bearer $API_KEY" \
    -H "Content-Type: application/json" \
    -d "$(jq -n --arg prompt "$prompt" --arg model "$MODEL" '{
      model: $model,
      prompt: $prompt,
      n: 1,
      size: "1024x1024"
    }')" -o "$OUTDIR/${name}_response.json"

  # 尝试从响应中提取 base64 或 url 并保存
  url=$(jq -r '.data[0].url // empty' "$OUTDIR/${name}_response.json" 2>/dev/null)
  b64=$(jq -r '.data[0].b64_json // empty' "$OUTDIR/${name}_response.json" 2>/dev/null)

  if [ -n "$url" ] && [ "$url" != "null" ]; then
    echo "  下载: $url"
    curl -s -o "$OUTDIR/${name}.png" "$url"
  elif [ -n "$b64" ] && [ "$b64" != "null" ]; then
    echo "  解码 base64..."
    echo "$b64" | base64 -d > "$OUTDIR/${name}.png"
  else
    echo "  ⚠ 解析失败，查看: $OUTDIR/${name}_response.json"
    cat "$OUTDIR/${name}_response.json" | head -c 500
  fi

  sleep 1  # 避免触发限流
done

echo ""
echo "=== 完成 ==="
echo "图标输出目录: $OUTDIR/"
ls -la "$OUTDIR"/*.png 2>/dev/null
