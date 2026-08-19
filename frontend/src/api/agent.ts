import http from './index'
import type { AgentRequest, AgentReply, Conversation, AgentMessage, Result } from '@/types'

export const agentApi = {
  chat(data: AgentRequest, apiKey: string, apiBaseUrl?: string) {
    return http.post<Result<AgentReply>>('/agent/chat', data, {
      headers: {
        'X-API-Key': apiKey,
        ...(apiBaseUrl ? { 'X-API-Base-URL': apiBaseUrl } : {}),
      },
    })
  },

  listConversations() {
    return http.get<Result<Conversation[]>>('/agent/conversations')
  },

  getMessages(conversationId: number) {
    return http.get<Result<AgentMessage[]>>(`/agent/conversations/${conversationId}/messages`)
  },

  deleteConversation(id: number) {
    return http.delete<Result<void>>(`/agent/conversations/${id}`)
  },
}
