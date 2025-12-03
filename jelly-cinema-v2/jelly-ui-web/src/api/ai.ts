import { get, post } from '@/utils/request'
import type { R } from '@/types/common'

/**
 * AI 对话请求
 */
export interface ChatRequest {
  prompt: string
  history?: { role: 'user' | 'assistant'; content: string }[]
  model?: string
  enableRag?: boolean
  filmId?: number
}

/**
 * 小说大纲请求
 */
export interface NovelOutlineRequest {
  theme: string
  style?: string
  protagonist?: string
  chapterCount?: number
  extraRequirements?: string
}

/**
 * 小说章节请求
 */
export interface NovelChapterRequest {
  bookId: string
  chapterIndex: number
  chapterTitle: string
  outline: string
  wordCount?: number
}

/**
 * 同步对话
 */
export function chat(data: ChatRequest): Promise<R<string>> {
  return post('/ai/chat', data)
}

/**
 * 流式对话 (SSE)
 */
export function chatStream(data: ChatRequest): EventSource {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
  return new EventSource(`${baseUrl}/ai/chat/completions?prompt=${encodeURIComponent(data.prompt)}&enableRag=${data.enableRag || false}`)
}

/**
 * 生成小说大纲
 */
export function generateOutline(data: NovelOutlineRequest): Promise<R<string>> {
  return post('/ai/novel/generate-outline', data)
}

/**
 * 上传 RAG 文档
 */
export function uploadDocument(file: File): Promise<R<number>> {
  const formData = new FormData()
  formData.append('file', file)
  return post('/ai/rag/upload', formData)
}

/**
 * RAG 检索
 */
export function ragSearch(query: string, topK?: number): Promise<R<string>> {
  return get('/ai/rag/search', { query, topK })
}
