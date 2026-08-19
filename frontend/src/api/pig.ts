import http from './index'
import type { Pig, Operation, Page, Result } from '@/types'

export const pigApi = {
  create(data: Pig) {
    return http.post<Result<Pig>>('/pigs', data)
  },
  delete(id: number) {
    return http.delete<Result<void>>(`/pigs/${id}`)
  },
  update(data: Pig) {
    return http.put<Result<Pig>>('/pigs', data)
  },
  getById(id: number) {
    return http.get<Result<Pig>>(`/pigs/${id}`)
  },
  list(params?: { type?: string; status?: string }) {
    return http.get<Result<Pig[]>>('/pigs', { params })
  },
  page(current: number, size: number) {
    return http.get<Result<Page<Pig>>>('/pigs/page', { params: { current, size } })
  },
  updateStatus(id: number, status: string) {
    return http.put<Result<void>>(`/pigs/${id}/status`, null, { params: { status } })
  },
  getOperations(id: number) {
    return http.get<Result<Operation[]>>(`/pigs/${id}/operations`)
  },
  getOperationsPage(id: number, current: number, size: number) {
    return http.get<Result<Page<Operation>>>(`/pigs/${id}/operations/page`, { params: { current, size } })
  },
}
