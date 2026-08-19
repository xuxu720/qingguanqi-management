import http from './index'
import type { Warning, Page, Result } from '@/types'

export const warningApi = {
  list(params?: { operationId?: number; warningType?: string; status?: string; level?: string }) {
    return http.get<Result<Warning[]>>('/warnings', { params })
  },

  page(current: number, size: number, params?: { operationId?: number; warningType?: string; status?: string; level?: string }) {
    return http.get<Result<Page<Warning>>>('/warnings/page', { params: { current, size, ...params } })
  },

  confirm(id: number) {
    return http.put<Result<void>>(`/warnings/${id}/confirm`)
  },

  resolve(id: number, remark?: string) {
    return http.put<Result<void>>(`/warnings/${id}/resolve`, remark ? { remark } : {})
  },

  batchConfirm(ids: number[]) {
    return http.put<Result<void>>('/warnings/batch-confirm', ids)
  },

  batchResolve(ids: number[], remark?: string) {
    return http.put<Result<void>>('/warnings/batch-resolve', { ids, remark })
  },
}
