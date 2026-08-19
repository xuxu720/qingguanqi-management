<template>
  <div v-if="widget" class="chat-widget">
    <!-- ======== option_list ======== -->
    <div v-if="widget.type === 'option_list'" class="widget-option-list">
      <div class="widget-title" v-if="widget.title">{{ widget.title }}</div>
      <div class="widget-desc" v-if="widget.description">{{ widget.description }}</div>
      <div class="option-chips">
        <div
          v-for="opt in widget.options"
          :key="opt.value"
          :class="['option-chip', { disabled: opt.disabled }]"
          @click="!opt.disabled && $emit('select', opt.value)"
        >
          <span class="chip-label">{{ opt.label }}</span>
          <span class="chip-desc" v-if="opt.description">{{ opt.description }}</span>
        </div>
      </div>
    </div>

    <!-- ======== form_card ======== -->
    <div v-else-if="widget.type === 'form_card'" class="widget-form-card">
      <div class="widget-title" v-if="widget.title">{{ widget.title }}</div>
      <div class="form-fields">
        <div v-for="f in widget.fields" :key="f.key" class="form-field">
          <label>
            {{ f.label }}
            <span v-if="f.required" class="required">*</span>
          </label>
          <!-- select -->
          <el-select
            v-if="f.type === 'select'"
            v-model="formValues[f.key!]"
            :placeholder="f.placeholder || '请选择' + f.label"
            size="small"
          >
            <el-option
              v-for="o in f.options"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
          <!-- datetime -->
          <el-date-picker
            v-else-if="f.type === 'datetime'"
            v-model="formValues[f.key!]"
            type="datetime"
            :placeholder="f.placeholder || '请选择' + f.label"
            size="small"
            value-format="YYYY-MM-DD HH:mm"
          />
          <!-- number -->
          <el-input
            v-else-if="f.type === 'number'"
            v-model="formValues[f.key!]"
            :placeholder="f.placeholder || '请输入' + f.label"
            size="small"
            type="number"
          />
          <!-- text / default -->
          <el-input
            v-else
            v-model="formValues[f.key!]"
            :placeholder="f.placeholder || '请输入' + f.label"
            size="small"
          />
          <span class="field-hint" v-if="f.hint">{{ f.hint }}</span>
        </div>
      </div>
      <el-button
        type="primary"
        size="small"
        :disabled="!canSubmit"
        @click="handleSubmit"
        style="margin-top: 10px"
      >
        {{ widget.submitLabel || '确认提交' }}
      </el-button>
    </div>

    <!-- ======== info_card ======== -->
    <div v-else-if="widget.type === 'info_card'" class="widget-info-card">
      <div class="widget-title" v-if="widget.title">{{ widget.title }}</div>
      <div class="info-rows">
        <div v-for="r in widget.rows" :key="r.label" class="info-row">
          <span class="info-label">{{ r.label }}</span>
          <span class="info-value">{{ r.value }}</span>
        </div>
      </div>
      <div class="info-actions" v-if="widget.actions && widget.actions.length">
        <el-button
          v-for="act in widget.actions"
          :key="act.label"
          :type="act.style || 'default'"
          size="small"
          @click="$emit('action', act)"
        >
          {{ act.label }}
        </el-button>
      </div>
    </div>

    <!-- ======== nav_card ======== -->
    <div v-else-if="widget.type === 'nav_card'" class="widget-nav-card">
      <div class="widget-title" v-if="widget.title">{{ widget.title }}</div>
      <div class="widget-desc" v-if="widget.description">{{ widget.description }}</div>
      <el-button type="primary" size="small" @click="$emit('navigate', widget.route!)">
        {{ widget.routeLabel || '前往编辑' }} →
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import type { Widget, WidgetAction } from '@/types'

const props = defineProps<{
  widget: Widget
}>()

// Use function syntax for emits with method signature
const emit = defineEmits<{
  (e: 'select', value: string): void
  (e: 'submit', text: string): void
  (e: 'action', action: WidgetAction): void
  (e: 'navigate', route: string): void
}>()

// form_card state
const formValues = ref<Record<string, string>>({})

// Reset form when widget changes
watch(() => props.widget, (w) => {
  if (w?.type === 'form_card' && w.fields) {
    const init: Record<string, string> = {}
    for (const f of w.fields) {
      init[f.key!] = f.value || ''
    }
    formValues.value = init
  }
}, { immediate: true })

const canSubmit = computed(() => {
  if (!props.widget.fields) return true
  return props.widget.fields
    .filter(f => f.required)
    .every(f => formValues.value[f.key!]?.trim())
})

function handleSubmit() {
  // Build natural language string from filled fields
  const parts: string[] = []
  for (const f of props.widget.fields!) {
    const val = formValues.value[f.key!]
    if (val && val.trim()) {
      parts.push(f.label + '：' + val.trim())
    }
  }
  const text = parts.join('，')
  if (text) {
    formValues.value = {}
    emit('submit', text)
  }
}
</script>

<style scoped>
.chat-widget {
  margin-top: 10px;
  padding: 10px;
  border-radius: 8px;
  background: #fafbfc;
  border: 1px solid #e8ecf1;
}

.widget-title {
  font-weight: 600;
  font-size: 13px;
  margin-bottom: 6px;
  color: #303133;
}

.widget-desc {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}

/* option_list */
.option-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.option-chip {
  display: flex;
  flex-direction: column;
  padding: 8px 12px;
  border-radius: 8px;
  border: 1px solid #d9ecff;
  background: #ecf5ff;
  cursor: pointer;
  transition: all .2s;
  min-width: 120px;
}
.option-chip:hover { background: #d9ecff; border-color: #409eff; }
.option-chip.disabled { opacity: .5; cursor: not-allowed; background: #f5f7fa; border-color: #e4e7ed; }

.chip-label { font-size: 13px; font-weight: 500; color: #409eff; }
.chip-desc { font-size: 11px; color: #909399; margin-top: 2px; }

/* form_card */
.form-field {
  margin-bottom: 10px;
}
.form-field label {
  display: block;
  font-size: 12px;
  color: #606266;
  margin-bottom: 4px;
}
.required { color: #f56c6c; }
.field-hint {
  display: block;
  font-size: 11px;
  color: #c0c4cc;
  margin-top: 2px;
}

/* info_card */
.info-rows { display: flex; flex-direction: column; gap: 4px; }
.info-row {
  display: flex;
  align-items: baseline;
  padding: 4px 0;
  border-bottom: 1px solid #f0f0f0;
}
.info-label {
  font-size: 12px;
  color: #909399;
  min-width: 70px;
  flex-shrink: 0;
}
.info-value {
  font-size: 13px;
  color: #303133;
  font-weight: 500;
}
.info-actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}

/* nav_card */
.widget-nav-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
</style>
