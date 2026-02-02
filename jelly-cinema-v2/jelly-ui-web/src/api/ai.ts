/**
 * AI 智能助手 API
 * 支持流式响应 (SSE) 的企业级对话接口
 */

import { get, post } from '@/utils/request'
import type { R } from '@/types/common'

// ==================== 类型定义 ====================

// ==================== 兼容旧版/Agent API ====================

/** 电影上下文 (与 ChatRequestDTO.FilmContext 对应) */
export interface FilmContext {
  tvboxId?: string
  title?: string
  description?: string
  actors?: string
  director?: string
  year?: string | number
  region?: string
  category?: string
}

/** 聊天请求参数 (与 ChatRequestDTO 对应) */
export interface ChatDTO {
  prompt: string
  filmId?: string | number
  filmContext?: FilmContext
  enableRag?: boolean
  history?: Array<{ role: 'user' | 'assistant'; content: string }>
}

/**
 * 智能对话 (Agent 模式)
 * 供 detail.vue 调用
 */
export function chat(data: ChatDTO): Promise<R<string>> {
  return post('/ai/agent/chat', data)
}

// ==================== 小说创作 API ====================

export interface NovelOutlineRequest {
  theme: string
  style: string
  protagonist: string
  chapterCount: number
  extraRequirements?: string
}

export function generateOutline(req: NovelOutlineRequest): Promise<R<string>> {
  return post('/ai/novel/outline', req)
}

// ==================== 新版流式 API 类型定义 ====================

/** 对话意图 */
export type ChatIntent =
  | 'find_movie'
  | 'get_play'
  | 'explain_movie'
  | 'recommend'
  | 'troubleshoot'
  | 'other'

/** 影片信息 */
export interface MovieInfo {
  movieId: string | number
  title: string
  year?: number
  aliases?: string[]
  region?: string
  genres?: string[]
  coverUrl?: string
  rating?: number
}

/** 播放入口 */
export interface PlayEntry {
  type: 'deeplink' | 'url' | 'route'
  value: string
  sourceName?: string
  quality?: string
  verified: boolean
  latencyMs?: number
}

/** 证据项 */
export interface Evidence {
  kind: 'db' | 'api' | 'rag'
  ref: string
  snippet: string
  updatedAt?: string
}

/** 诊断信息 */
export interface Diagnostics {
  status: 'ok' | 'partial' | 'fail'
  reasonCode?: string
  reasonText?: string
  nextActions?: string[]
}

/** 对话消息 */
export interface ChatMessage {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  timestamp: number
  intent?: ChatIntent
  movie?: MovieInfo
  playEntry?: PlayEntry
  evidence?: Evidence[]
  diagnostics?: Diagnostics
  isStreaming?: boolean
}

/** 对话请求 */
export interface ChatRequest {
  sessionId?: string
  message: string
  context?: {
    currentPage?: string
    movieId?: string | number
    resourceId?: string | number
  }
  constraints?: {
    region?: string
    device?: string
    quality?: string
    onlyPlayable?: boolean
  }
}

/** 对话响应 */
export interface ChatResponse {
  sessionId: string
  messageId: string
  intent: ChatIntent
  content: string
  movie?: MovieInfo
  playEntry?: PlayEntry
  evidence?: Evidence[]
  diagnostics?: Diagnostics
  suggestions?: string[]
}

// ==================== API 接口 ====================

/**
 * 发送对话请求（普通模式）
 */
export function sendChatMessage(req: ChatRequest): Promise<R<ChatResponse>> {
  return post('/ai/chat', req)
}

/**
 * 创建流式对话（SSE via POST）
 * 使用 fetch + ReadableStream 实现 POST 请求的 SSE
 */
export function createChatStream(
  req: ChatRequest,
  callbacks: {
    onMessage: (chunk: string, accumulated: string) => void
    onComplete: (response: ChatResponse) => void
    onError: (error: Error) => void
  }
): { abort: () => void } {
  // Mock 模式开关 - 当后端不可用时启用
  const useMock = import.meta.env.VITE_AI_USE_MOCK === 'true'

  if (useMock) {
    return simulateChatStream(req, callbacks)
  }

  const controller = new AbortController()
  let accumulated = ''

  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
  const token = localStorage.getItem('token')

  fetch(`${baseUrl}/ai/agent/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream',
      ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({
      prompt: req.message,
      enableRag: true,
      filmContext: req.context?.movieId ? { tvboxId: String(req.context.movieId) } : undefined,
    }),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`)
      }

      const reader = response.body?.getReader()
      if (!reader) {
        throw new Error('ReadableStream not supported')
      }

      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })

        // 处理 SSE 事件块：以空行分隔（\n\n 或 \r\n\r\n）
        while (true) {
          const lfIndex = buffer.indexOf('\n\n')
          const crlfIndex = buffer.indexOf('\r\n\r\n')

          let splitIndex = -1
          let splitLen = 0
          if (crlfIndex !== -1 && (lfIndex === -1 || crlfIndex < lfIndex)) {
            splitIndex = crlfIndex
            splitLen = 4
          } else if (lfIndex !== -1) {
            splitIndex = lfIndex
            splitLen = 2
          }

          if (splitIndex === -1) break

          const eventBlock = buffer.slice(0, splitIndex)
          buffer = buffer.slice(splitIndex + splitLen)

          const dataLines: string[] = []
          const lines = eventBlock.split(/\r?\n/)
          for (const line of lines) {
            if (!line.startsWith('data:')) continue

            // SSE 规范：data: 后允许 1 个可选空格，不应 trim 掉所有空白
            let data = line.slice(5)
            if (data.startsWith(' ')) data = data.slice(1)
            dataLines.push(data)
          }

          if (dataLines.length === 0) continue

          const data = dataLines.join('\n')
          if (data === '[DONE]') {
            callbacks.onComplete({
              sessionId: '',
              messageId: '',
              intent: 'other',
              content: accumulated,
            })
            return
          }

          accumulated += data
          callbacks.onMessage(data, accumulated)
        }
      }

      // 流结束，如果没有收到 complete 事件，手动完成
      if (accumulated) {
        callbacks.onComplete({
          sessionId: '',
          messageId: '',
          intent: 'other',
          content: accumulated,
        })
      }
    })
    .catch((error) => {
      if (error.name !== 'AbortError') {
        console.error('Stream error:', error)
        callbacks.onError(error)
      }
    })

  return {
    abort: () => {
      controller.abort()
    }
  }
}

/**
 * 模拟流式响应 (Mock)
 */
function simulateChatStream(
  req: ChatRequest,
  callbacks: {
    onMessage: (chunk: string, accumulated: string) => void
    onComplete: (response: ChatResponse) => void
    onError: (error: Error) => void
  }
): { abort: () => void } {
  let active = true
  let accumulated = ''
  let timer: any = null

  // 模拟 AI 思考和生成
  const mockResponse = generateMockResponse(req)
  const chunks = mockResponse.content.split('')
  let index = 0

  // 模拟网络延迟
  setTimeout(() => {
    if (!active) return

    timer = setInterval(() => {
      if (!active) return

      // 每次输出 1-3 个字符
      const count = Math.ceil(Math.random() * 3)
      const chunk = chunks.slice(index, index + count).join('')
      index += count

      if (chunk) {
        accumulated += chunk
        callbacks.onMessage(chunk, accumulated)
      }

      if (index >= chunks.length) {
        clearInterval(timer)
        // 完成
        callbacks.onComplete({
          ...mockResponse,
          content: accumulated
        })
      }
    }, 30) // 打字机速度
  }, 800) // 思考延迟

  return {
    abort: () => {
      active = false
      if (timer) clearInterval(timer)
    }
  }
}

/**
 * 生成 Mock 响应数据
 */
function generateMockResponse(req: ChatRequest): ChatResponse {
  const msg = req.message.toLowerCase()

  // 1. 找电影意图
  if (msg.includes('科幻') || msg.includes('星际')) {
    return {
      sessionId: req.sessionId || 'mock-session',
      messageId: 'mock-msg-' + Date.now(),
      intent: 'recommend',
      content: '为您推荐经典科幻电影《星际穿越》。\n\n这部电影由克里斯托弗·诺兰执导，讲述了一队探险家利用他们新发现的虫洞，超越人类太空旅行极限的故事。\n\n目前系统内已收录 4K 修复版。',
      movie: {
        movieId: 1, // 假设 ID 1 是存在的
        title: '星际穿越',
        year: 2014,
        rating: 9.4,
        coverUrl: 'https://img9.doubanio.com/view/photo/s_ratio_poster/public/p2614988097.jpg'
      },
      playEntry: {
        type: 'route',
        value: '/film/1',
        verified: true,
        quality: '4K'
      },
      evidence: [
        { kind: 'db', ref: 'movie:1', snippet: '星际穿越 (Interstellar) [2014] - 豆瓣 9.4' },
        { kind: 'rag', ref: 'chunk:892', snippet: '诺兰导演的硬核科幻代表作...' }
      ]
    }
  }

  // 2. 播放问题
  if (msg.includes('播放') || msg.includes('卡')) {
    return {
      sessionId: req.sessionId || 'mock-session',
      messageId: 'mock-msg-' + Date.now(),
      intent: 'troubleshoot',
      content: '检测到您当前遇到播放问题。\n\n已自动为您诊断：\n1. 当前播放源 "TVBox-Proxy" 响应延迟 45ms (正常)\n2. 您的网络连接状态良好\n\n如果仍然卡顿，建议：\n- 切换到 "备用线路 B"\n- 降低画质到 1080P',
      diagnostics: {
        status: 'partial',
        reasonText: '播放源偶发性波动',
        nextActions: ['切换线路', '刷新页面']
      }
    }
  }

  // 3. 默认闲聊
  return {
    sessionId: req.sessionId || 'mock-session',
    messageId: 'mock-msg-' + Date.now(),
    intent: 'other',
    content: `收到您的消息："${req.message}"\n\n我是果冻 AI 助手。我可以帮您检索电影、解决播放故障或推荐内容。由于目前是演示模式，您可以尝试问我 "推荐科幻片" 或 "播放卡顿怎么办"。`,
    suggestions: ['推荐科幻片', '播放卡顿怎么办', '最近有什么新片']
  }
}


/**
 * 提交用户反馈
 */
export function submitFeedback(params: {
  messageId: string
  rating: 'helpful' | 'unhelpful'
  comment?: string
}): Promise<R<void>> {
  return post('/ai/feedback', params)
}

/**
 * 获取对话历史
 */
export function getChatHistory(sessionId: string): Promise<R<ChatMessage[]>> {
  return get('/ai/history', { sessionId })
}

/**
 * 获取热门问题
 */
export function getPopularQuestions(): Promise<R<string[]>> {
  return get('/ai/popular-questions')
}
