// 后端统一响应格式
export interface Result<T> {
  code: number
  message: string
  data: T
}

// 分页
export interface Page<T> {
  records: T[]
  total: number
  size: number
  current: number
}

// 清管器
export interface Pig {
  id?: number
  type: string
  spec: string
  interferenceRate: number
  applicableScene?: string
  mediumType: string
  status: string
  remark?: string
  createTime?: string
  updateTime?: string
}

// 管线
export interface Pipeline {
  id?: number
  name: string
  mediumType: string
  diameter?: number
  designPressureMin?: number
  designPressureMax?: number
  totalLength?: number
  remark?: string
  createTime?: string
  updateTime?: string
}

// 站点
export interface Station {
  id?: number
  pipelineId: number
  name: string
  stationType: string
  mileage: number
  elevation?: number
  sortOrder: number
  remark?: string
  createTime?: string
  updateTime?: string
}

// 管段
export interface PipelineSegment {
  id?: number
  pipelineId: number
  fromStationId: number
  toStationId: number
  distance: number
  unitCapacity?: number
  innerDiameter?: number
  remark?: string
  createTime?: string
  updateTime?: string
}

// 作业
export interface Operation {
  id?: number
  pipelineId: number
  pigId: number
  operationType: string
  fromStationId: number
  toStationId: number
  dispatchTime: string
  displacement?: number
  gasFlowRate?: number
  outletPressure?: number
  inletPressure?: number
  status: string
  remark?: string
  keyStationIds?: number[]
  createTime?: string
  updateTime?: string
}

// 作业列表 VO（含关联名称，避免 N+1 查询）
export interface OperationVO extends Operation {
  pipelineName?: string
  fromStationName?: string
  toStationName?: string
  pigName?: string
  pigType?: string
  pigSpec?: string
}

// 液体计算结果
export interface SegmentCalcResult {
  fromStationName: string
  toStationName: string
  distance: number
  unitCapacity: number
  pipeCapacity: number
  runningTime: number
  pigSpeed: number
  estimatedArrivalTime: string
}

// 气体计算结果
export interface GasCalcResult {
  distance: number
  avgPressure: number
  compressFactor: number
  crossSectionArea: number
  theoreticalSpeed: number
  runningTime: number
  estimatedArrivalTime: string
}

// 跟踪记录
export interface TrackingRecord {
  id?: number
  operationId: number
  stationId: number
  segmentDistance: number
  predictedArrivalTime: string
  actualArrivalTime?: string
  pigSpeed: number
  isRevised: number
  parentRecordId?: number
  revisionCount: number
  remark?: string
  isKeyStation?: boolean
  createTime?: string
  updateTime?: string
}

// 跟踪记录 VO（含站名）
export interface TrackingRecordVO extends TrackingRecord {
  stationName: string
}

// 作业状态常量
export const OperationStatus = {
  准备: '准备',
  运行中: '运行中',
  已完成: '已完成',
  异常: '异常',
} as const

export const OperationTypes = ['常规清管', '应急清管'] as const

// 预警
export interface Warning {
  id?: number
  operationId: number
  warningType: string
  level: string
  content: string
  suggestion?: string
  status: string
  resolvedTime?: string
  remark?: string
  createTime?: string
  updateTime?: string
}

export const WarningType = {
  延迟: '延迟',
  速度异常: '速度异常',
  卡阻: '卡阻',
} as const

export const WarningLevel = {
  高: '高',
  中: '中',
  低: '低',
} as const

export const WarningStatus = {
  未处理: '未处理',
  已确认: '已确认',
  已关闭: '已关闭',
} as const

// 智能助手
export interface AgentRequest {
  message: string
  conversationId?: number
}

export interface AgentReply {
  reply: string
  intent: string
  data?: Record<string, unknown>
  needFollowUp: boolean
  conversationId: number
  widgets?: Widget[]
}

// Widget types for rich chat interactions
export type WidgetType = 'option_list' | 'form_card' | 'info_card' | 'nav_card'

export interface Widget {
  type: WidgetType
  title?: string
  description?: string
  // option_list
  options?: WidgetOption[]
  // form_card
  fields?: WidgetField[]
  submitLabel?: string
  // info_card
  rows?: WidgetField[]
  actions?: WidgetAction[]
  // nav_card
  route?: string
  routeLabel?: string
}

export interface WidgetOption {
  label: string
  value: string
  description?: string
  disabled?: boolean
}

export interface WidgetField {
  label: string
  key?: string
  type?: 'text' | 'number' | 'select' | 'datetime' | 'readonly'
  value?: string
  placeholder?: string
  hint?: string
  options?: WidgetOption[]
  required?: boolean
}

export interface WidgetAction {
  label: string
  action: string   // navigate | arrival | ack_warning
  value?: string   // route path or payload
  style?: 'primary' | 'danger' | 'default' | 'warning'
}

export interface Conversation {
  id?: number
  title?: string
  createTime?: string
  updateTime?: string
}

export interface AgentMessage {
  id?: number
  conversationId: number
  role: string
  content: string
  intent?: string
  operationId?: number
  metadataJson?: string
  createTime?: string
}
