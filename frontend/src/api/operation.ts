import http from './index'
import type { Operation, OperationVO, TrackingRecordVO, Page, Result } from '@/types'

export const operationApi = {
  createWithTracking(data: Operation) {
    return http.post<Result<Operation>>('/operations', data)
  },

  delete(id: number) {
    return http.delete<Result<void>>(`/operations/${id}`)
  },

  update(data: Operation) {
    return http.put<Result<Operation>>('/operations', data)
  },

  getById(id: number) {
    return http.get<Result<Operation>>(`/operations/${id}`)
  },

  list(params?: { pipelineId?: number; status?: string }) {
    return http.get<Result<OperationVO[]>>('/operations', { params })
  },

  page(current: number, size: number) {
    return http.get<Result<Page<Operation>>>('/operations/page', { params: { current, size } })
  },

  updateStatus(id: number, status: string) {
    return http.put<Result<void>>(`/operations/${id}/status`, null, { params: { status } })
  },

  nodeArrival(id: number, data: { stationId: number; actualArrivalTime: string }) {
    return http.post<Result<void>>(`/operations/${id}/node-arrival`, data)
  },

  getTracking(id: number) {
    return http.get<Result<TrackingRecordVO[]>>(`/operations/${id}/tracking`)
  },
}
