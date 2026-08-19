import http from './index'
import type { Station, Page, Result } from '@/types'

export const stationApi = {
  create(data: Station) {
    return http.post<Result<Station>>('/stations', data)
  },
  delete(id: number) {
    return http.delete<Result<void>>(`/stations/${id}`)
  },
  update(data: Station) {
    return http.put<Result<Station>>('/stations', data)
  },
  getById(id: number) {
    return http.get<Result<Station>>(`/stations/${id}`)
  },
  list(params?: { pipelineId?: number }) {
    return http.get<Result<Station[]>>('/stations', { params })
  },
  page(current: number, size: number) {
    return http.get<Result<Page<Station>>>('/stations/page', { params: { current, size } })
  },
  getByPipeline(pipelineId: number) {
    return http.get<Result<Station[]>>(`/stations/by-pipeline/${pipelineId}`)
  },
}
