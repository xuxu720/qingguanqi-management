import http from './index'
import type { PipelineSegment, Page, Result } from '@/types'

export const segmentApi = {
  create(data: PipelineSegment) {
    return http.post<Result<PipelineSegment>>('/pipeline-segments', data)
  },
  delete(id: number) {
    return http.delete<Result<void>>(`/pipeline-segments/${id}`)
  },
  update(data: PipelineSegment) {
    return http.put<Result<PipelineSegment>>('/pipeline-segments', data)
  },
  getById(id: number) {
    return http.get<Result<PipelineSegment>>(`/pipeline-segments/${id}`)
  },
  list(params?: { pipelineId?: number }) {
    return http.get<Result<PipelineSegment[]>>('/pipeline-segments', { params })
  },
  page(current: number, size: number) {
    return http.get<Result<Page<PipelineSegment>>>('/pipeline-segments/page', { params: { current, size } })
  },
  getByPipeline(pipelineId: number) {
    return http.get<Result<PipelineSegment[]>>(`/pipeline-segments/by-pipeline/${pipelineId}`)
  },
}
