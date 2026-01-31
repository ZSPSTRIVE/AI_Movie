import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import type { R } from '@/types/common'

// 创建 axios 实例
const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
service.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    console.log('请求拦截器 - URL:', config.url, 'Token:', userStore.token ? '有' : '无')
    if (userStore.token) {
      // Sa-Token 不需要 Bearer 前缀
      ; (config.headers as any).Authorization = userStore.token
    } else {
      console.warn('请求未携带Token:', config.url)
    }

    // 文件上传使用 FormData 时，让浏览器自动设置 multipart/form-data 边界
    if (config.data instanceof FormData) {
      if (config.headers) {
        delete (config.headers as any)['Content-Type']
      }
    }

    return config
  },
  (error) => {
    console.error('请求错误:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse<any>) => {
    const res = response.data

    // 成功
    if (res.code === 200) {
      return res
    }

    // Token 过期
    if (res.code === 401) {
      ElMessageBox.confirm('登录已过期，请重新登录', '提示', {
        confirmButtonText: '重新登录',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        const userStore = useUserStore()
        userStore.resetState()
        location.href = '/login'
      })
      return Promise.reject(new Error(res.msg || '未授权'))
    }

    // 其他错误
    ElMessage.error(res.msg || '请求失败')
    return Promise.reject(new Error(res.msg || '请求失败'))
  },
  (error) => {
    console.error('响应错误:', error)

    // 如果是 IM 相关的接口报错，不显示全局提示
    if (error.config && error.config.url && error.config.url.includes('/im/')) {
      console.warn('IM服务异常，已忽略全局提示:', error.config.url)
      return Promise.reject(error)
    }

    let message = error.message
    if (error.response?.status === 404) {
      message = '请求的资源不存在'
    } else if (error.response?.status === 500) {
      message = '服务器内部错误'
    } else if (error.code === 'ECONNABORTED') {
      message = '请求超时'
    }
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

// 封装请求方法
export function request<T = any>(config: AxiosRequestConfig): Promise<R<T>> {
  return service(config) as Promise<R<T>>
}

export function get<T = any>(url: string, params?: any): Promise<R<T>> {
  return request<T>({ method: 'get', url, params })
}

export function post<T = any>(url: string, data?: any): Promise<R<T>> {
  return request<T>({ method: 'post', url, data })
}

export function put<T = any>(url: string, data?: any): Promise<R<T>> {
  return request<T>({ method: 'put', url, data })
}

export function del<T = any>(url: string, params?: any): Promise<R<T>> {
  return request<T>({ method: 'delete', url, params })
}

export default service
