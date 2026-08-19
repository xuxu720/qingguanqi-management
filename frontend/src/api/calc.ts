import request from './index'
import type { SegmentCalcResult, GasCalcResult } from '@/types'

export const calcApi = {
  liquidPipeline(pipelineId: number, fromStationId: number, toStationId: number | undefined, displacement: number, dispatchTime: string) {
    return request.post<SegmentCalcResult[]>('/calc/liquid-pipeline', null, {
      params: { pipelineId, fromStationId, toStationId, displacement, dispatchTime },
    })
  },
  gasPipeline(
    pipelineId: number, fromStationId: number, toStationId: number | undefined,
    firstOutletPressure: number, lastInletPressure: number,
    gasFlowRate: number, dispatchTime: string,
  ) {
    return request.post<GasCalcResult[]>('/calc/gas-pipeline', null, {
      params: { pipelineId, fromStationId, toStationId, firstOutletPressure, lastInletPressure, gasFlowRate, dispatchTime },
    })
  },
}
