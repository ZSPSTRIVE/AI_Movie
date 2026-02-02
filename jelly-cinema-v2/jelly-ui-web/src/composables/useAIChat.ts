/**
 * AI 对话状态管理 Composable
 * 企业级实现：支持多轮对话、流式响应、错误重试、离线队列
 */

import { ref, computed, watch } from 'vue'
import { v4 as uuidv4 } from 'uuid'
import {
    createChatStream,
    sendChatMessage,
    type ChatMessage,
    type ChatRequest,
    type ChatResponse,
    type PlayEntry,
    type MovieInfo
} from '@/api/ai'

// 生成唯一 ID（降级方案，无需 uuid 依赖）
function generateId(): string {
    return Date.now().toString(36) + Math.random().toString(36).substring(2, 9)
}

export interface UseAIChatOptions {
    /** 会话 ID，不传则自动生成 */
    sessionId?: string
    /** 是否启用流式响应 */
    streaming?: boolean
    /** 最大重试次数 */
    maxRetries?: number
    /** 上下文信息 */
    context?: ChatRequest['context']
}

export function useAIChat(options: UseAIChatOptions = {}) {
    const {
        sessionId: initialSessionId,
        streaming = true,
        maxRetries = 3,
        context
    } = options

    // ==================== 状态 ====================

    const sessionId = ref(initialSessionId || generateId())
    const messages = ref<ChatMessage[]>([])
    const isLoading = ref(false)
    const error = ref<string | null>(null)
    const currentStreamController = ref<{ abort: () => void } | null>(null)

    // 当前正在流式输出的消息 ID
    const streamingMessageId = ref<string | null>(null)

    // ==================== 计算属性 ====================

    const lastAssistantMessage = computed(() => {
        for (let i = messages.value.length - 1; i >= 0; i--) {
            if (messages.value[i].role === 'assistant') {
                return messages.value[i]
            }
        }
        return null
    })

    const hasPlayEntry = computed(() => {
        return lastAssistantMessage.value?.playEntry?.verified === true
    })

    function inferPlayEntryFromContent(content: string): PlayEntry | undefined {
        if (!content) return undefined
        const markdownMatch = content.match(/\((\/film\/[^)\s]+)\)/)
        const plainMatch = content.match(/\/film\/[^\s)]+/)
        const value = markdownMatch?.[1] || plainMatch?.[0]
        if (!value) return undefined
        return {
            type: 'route',
            value,
            verified: false
        }
    }

    // ==================== 方法 ====================

    /**
     * 发送消息
     */
    async function sendMessage(content: string): Promise<void> {
        if (!content.trim() || isLoading.value) return

        error.value = null
        isLoading.value = true

        // 添加用户消息
        const userMessage: ChatMessage = {
            id: generateId(),
            role: 'user',
            content: content.trim(),
            timestamp: Date.now()
        }
        messages.value.push(userMessage)

        // 创建助手消息占位符
        const assistantMessageId = generateId()
        const assistantMessage: ChatMessage = {
            id: assistantMessageId,
            role: 'assistant',
            content: '',
            timestamp: Date.now(),
            isStreaming: streaming
        }
        messages.value.push(assistantMessage)

        const request: ChatRequest = {
            sessionId: sessionId.value,
            message: content.trim(),
            context
        }

        try {
            if (streaming) {
                await sendStreamingMessage(request, assistantMessageId)
            } else {
                await sendNormalMessage(request, assistantMessageId)
            }
        } catch (e: any) {
            error.value = e.message || '请求失败，请重试'
            // 更新助手消息为错误状态
            updateMessage(assistantMessageId, {
                content: '抱歉，我遇到了一些问题。请稍后重试。',
                isStreaming: false,
                diagnostics: {
                    status: 'fail',
                    reasonCode: 'REQUEST_FAILED',
                    reasonText: e.message,
                    nextActions: ['refresh', 'retry']
                }
            })
        } finally {
            isLoading.value = false
            streamingMessageId.value = null
        }
    }

    /**
     * 流式发送消息
     */
    async function sendStreamingMessage(
        request: ChatRequest,
        messageId: string
    ): Promise<void> {
        return new Promise((resolve, reject) => {
            streamingMessageId.value = messageId

            currentStreamController.value = createChatStream(request, {
                onMessage: (chunk, accumulated) => {
                    updateMessage(messageId, {
                        content: accumulated,
                        isStreaming: true
                    })
                },
                onComplete: (response) => {
                    const inferredPlayEntry = response.playEntry || inferPlayEntryFromContent(response.content)
                    updateMessage(messageId, {
                        content: response.content,
                        intent: response.intent,
                        movie: response.movie,
                        playEntry: inferredPlayEntry,
                        evidence: response.evidence,
                        diagnostics: response.diagnostics,
                        isStreaming: false
                    })
                    currentStreamController.value = null
                    resolve()
                },
                onError: (err) => {
                    currentStreamController.value = null
                    reject(err)
                }
            })
        })
    }

    /**
     * 普通发送消息
     */
    async function sendNormalMessage(
        request: ChatRequest,
        messageId: string
    ): Promise<void> {
        const res = await sendChatMessage(request)
        if (res.data) {
            const inferredPlayEntry = res.data.playEntry || inferPlayEntryFromContent(res.data.content)
            updateMessage(messageId, {
                content: res.data.content,
                intent: res.data.intent,
                movie: res.data.movie,
                playEntry: inferredPlayEntry,
                evidence: res.data.evidence,
                diagnostics: res.data.diagnostics,
                isStreaming: false
            })
        }
    }

    /**
     * 更新消息
     */
    function updateMessage(messageId: string, updates: Partial<ChatMessage>): void {
        const index = messages.value.findIndex(m => m.id === messageId)
        if (index !== -1) {
            messages.value[index] = {
                ...messages.value[index],
                ...updates
            }
        }
    }

    /**
     * 停止流式输出
     */
    function stopStreaming(): void {
        if (currentStreamController.value) {
            currentStreamController.value.abort()
            currentStreamController.value = null
        }
        if (streamingMessageId.value) {
            updateMessage(streamingMessageId.value, { isStreaming: false })
            streamingMessageId.value = null
        }
        isLoading.value = false
    }

    /**
     * 清空对话
     */
    function clearMessages(): void {
        stopStreaming()
        messages.value = []
        sessionId.value = generateId()
        error.value = null
    }

    /**
     * 重试最后一条消息
     */
    async function retryLast(): Promise<void> {
        // 找到最后一条用户消息
        for (let i = messages.value.length - 1; i >= 0; i--) {
            if (messages.value[i].role === 'user') {
                const content = messages.value[i].content
                // 移除最后一条助手消息
                if (messages.value[messages.value.length - 1].role === 'assistant') {
                    messages.value.pop()
                }
                // 移除用户消息
                messages.value.pop()
                // 重新发送
                await sendMessage(content)
                break
            }
        }
    }

    /**
     * 添加快捷问题
     */
    function addQuickQuestion(question: string): void {
        sendMessage(question)
    }

    // ==================== 返回 ====================

    return {
        // 状态
        sessionId,
        messages,
        isLoading,
        error,
        streamingMessageId,

        // 计算属性
        lastAssistantMessage,
        hasPlayEntry,

        // 方法
        sendMessage,
        stopStreaming,
        clearMessages,
        retryLast,
        addQuickQuestion
    }
}

export type UseAIChatReturn = ReturnType<typeof useAIChat>
