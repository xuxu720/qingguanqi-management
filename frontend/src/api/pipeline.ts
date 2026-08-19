import http from './index'
import type { Pipeline, Page, Result } from '@/types'

export const pipelineApi = {
  create(data: Pipeline) {
    return http.post<Result<Pipeline>>('/pipelines', data)
  },
  delete(id: number) {
    return http.delete<Result<void>>(`/pipelines/${id}`)
  },
  update(data: Pipeline) {
    return http.put<Result<Pipeline>>('/pipelines', data)
  },
  getById(id: number) {
    return http.get<Result<Pipeline>>(`/pipelines/${id}`)
  },
  list(params?: { mediumType?: string }) {
    return http.get<Result<Pipeline[]>>('/pipelines', { params })
  },
  page(current: number, size: number) {
    return http.get<Result<Page<Pipeline>>>('/pipelines/page', { params: { current, size } })
  },
}
