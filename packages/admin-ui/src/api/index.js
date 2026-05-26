import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({
  baseURL: '/api/admin',
  timeout: 30000,
})

http.interceptors.response.use(
  (r) => r.data,
  (err) => {
    const msg = err?.response?.data?.detail || err?.message || '请求失败'
    ElMessage.error(typeof msg === 'string' ? msg : JSON.stringify(msg))
    return Promise.reject(err)
  }
)

export const getOverview = () => http.get('/overview')
export const getSystemStatus = () => http.get('/system/status')

export const listDocuments = () => http.get('/knowledge/documents')
export const listDocumentChunks = (name) => http.get(`/knowledge/documents/${encodeURIComponent(name)}/chunks`)
export const getRawDocument = (name) => http.get(`/knowledge/raw/${encodeURIComponent(name)}`)
export const searchKnowledge = (payload) => http.post('/knowledge/search', payload)
export const rebuildKnowledge = () => http.post('/knowledge/rebuild')

export const getRetrievalLogs = (params) => http.get('/logs/retrieval', { params })
export const getRetrievalStats = (since_hours = 24) => http.get('/logs/retrieval/stats', { params: { since_hours } })
export const clearRetrievalLogs = () => http.delete('/logs/retrieval')

export const listLlmGames = () => http.get('/logs/llm')
export const getLlmGameLog = (gameId, tail) => http.get(`/logs/llm/${gameId}`, { params: tail ? { tail } : {} })
