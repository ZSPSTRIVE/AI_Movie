import axios from 'axios'
import type { Film } from '@/types/film'

// TVBox 代理服务器地址
const PROXY_BASE_URL = 'http://localhost:3001/api/tvbox'

/**
 * TV Box API 服务
 * 通过代理服务器访问真实的 TVBox 数据源
 */
class TVBoxService {
  /**
   * 获取推荐电影
   */
  async getRecommend(limit: number = 18): Promise<Film[]> {
    try {
      console.log('正在请求真实 TVBox 推荐数据...')
      const response = await axios.get(`${PROXY_BASE_URL}/recommend`, {
        params: { limit },
        timeout: 20000,
      })
      console.log('真实数据获取成功:', response.data.data?.length || 0)
      return response.data.data || []
    } catch (error) {
      console.error('Failed to fetch recommend, using fallback:', error)
      return this.getFallbackData()
    }
  }

  /**
   * 获取电影列表（带分页）
   */
  async getList(page: number = 1, pageSize: number = 18): Promise<{ list: Film[], total: number }> {
    try {
      const response = await axios.get(`${PROXY_BASE_URL}/list`, {
        params: { page, pageSize },
        timeout: 20000,
      })
      return response.data.data || { list: [], total: 0 }
    } catch (error) {
      console.error('Failed to fetch list, using fallback:', error)
      const allData = this.getFallbackData()
      return { list: allData.slice(0, pageSize), total: allData.length }
    }
  }

  /**
   * 搜索电影
   */
  async search(keyword: string): Promise<Film[]> {
    try {
      const response = await axios.get(`${PROXY_BASE_URL}/search`, {
        params: { keyword },
        timeout: 10000,
      })
      return response.data.data || []
    } catch (error) {
      console.error('Failed to search:', error)
      return []
    }
  }

  /**
   * 获取电影详情
   */
  async getDetail(id: number | string): Promise<Film | null> {
    try {
      console.log('正在获取真实电影详情, ID:', id)
      const response = await axios.get(`${PROXY_BASE_URL}/detail/${encodeURIComponent(id)}`, {
        timeout: 15000,
      })
      return response.data.data || null
    } catch (error) {
      console.error('Failed to fetch detail:', error)
      // 如果是数字ID，尝试从降级数据找
      if (!isNaN(Number(id))) {
        const fallback = this.getFallbackData().find(f => f.id === Number(id))
        if (fallback) return fallback
      }
      return null
    }
  }

  /**
   * 获取播放链接
   */
  async getPlayUrl(id: number | string): Promise<{ playUrl: string, episodes: any[] }> {
    try {
      console.log('正在获取真实播放链接, ID:', id)

      const response = await axios.get(`${PROXY_BASE_URL}/play/${encodeURIComponent(id)}`, {
        timeout: 20000,
      })
      return response.data.data || { playUrl: '', episodes: [] }
    } catch (error) {
      console.error('Failed to fetch play url:', error)
      return {
        playUrl: '', // 失败时不返回示例视频
        episodes: []
      }
    }
  }

  /**
   * 降级数据（真实电影数据库）
   */
  private getFallbackData(): Film[] {
    const realMovies = [
      { title: '流浪地球2', desc: '太阳即将毁灭，人类开启流浪地球计划', year: 2023, genre: '科幻', cover: '101' },
      { title: '满江红', desc: '南宋绍兴年间，岳飞死后四年，秦桧率兵与金国会谈', year: 2023, genre: '悬疑', cover: '102' },
      { title: '熊出没·伴我"熊芯"', desc: '熊大熊二与光头强的新冒险', year: 2024, genre: '动画', cover: '103' },
      { title: '无名', desc: '地下工作者与日伪势力的殊死较量', year: 2023, genre: '动作', cover: '104' },
      { title: '深海', desc: '一个小女孩在神秘海底世界的奇幻之旅', year: 2023, genre: '动画', cover: '105' },
      { title: '交换人生', desc: '父女身体互换后的爆笑故事', year: 2024, genre: '喜剧', cover: '106' },
      { title: '我爱你！', desc: '一对老人的黄昏之恋', year: 2024, genre: '爱情', cover: '107' },
      { title: '中国乓乓之绝地反击', desc: '中国乒乓球队重返巅峰之路', year: 2023, genre: '剧情', cover: '108' },
      { title: '第二十条', desc: '检察官韩明的正义之路', year: 2024, genre: '剧情', cover: '109' },
      { title: '热辣滚烫', desc: '胖女孩的励志减肥故事', year: 2024, genre: '剧情', cover: '110' },
      { title: '飞驰人生2', desc: '赛车手张驰的逐梦之旅', year: 2024, genre: '喜剧', cover: '111' },
      { title: '周处除三害', desc: '通缉犯的自我救赎', year: 2024, genre: '动作', cover: '112' },
      { title: '封神第一部：朝歌风云', desc: '商周之战的神话史诗', year: 2023, genre: '动作', cover: '113' },
      { title: '八角笼中', desc: '格斗孤儿的奋斗人生', year: 2023, genre: '剧情', cover: '114' },
      { title: '消失的她', desc: '一场蓄谋已久的阴谋', year: 2023, genre: '悬疑', cover: '115' },
      { title: '孤注一掷', desc: '境外诈骗集团的罪恶揭露', year: 2023, genre: '犯罪', cover: '116' },
      { title: '长安三万里', desc: '盛唐诗人的青春与梦想', year: 2023, genre: '动画', cover: '117' },
      { title: '三大队', desc: '警察追凶十二年的故事', year: 2023, genre: '犯罪', cover: '118' },
      { title: '涉过愤怒的海', desc: '一桩命案牵扯的两个家庭', year: 2023, genre: '剧情', cover: '119' },
      { title: '年会不能停！', desc: '职场打工人的爆笑经历', year: 2023, genre: '喜剧', cover: '120' },
      { title: '我本是高山', desc: '张桂梅的感人事迹', year: 2023, genre: '剧情', cover: '121' },
      { title: '一闪一闪亮星星', desc: '穿越时空的青春爱情', year: 2023, genre: '爱情', cover: '122' },
      { title: '金手指', desc: '香港金融巨骗的罪恶一生', year: 2023, genre: '犯罪', cover: '123' },
      { title: '潜行', desc: '卧底警察的生死较量', year: 2023, genre: '动作', cover: '124' },
    ]

    return realMovies.map((movie, index) => ({
      id: index + 1,
      title: movie.title,
      description: movie.desc,
      coverUrl: `https://picsum.photos/seed/${movie.cover}/400/600`,
      year: movie.year,
      genre: movie.genre,
      rating: parseFloat((Math.random() * 1.5 + 7.5).toFixed(1)),
      playCount: Math.floor(Math.random() * 900000 + 100000),
      isVip: Math.random() > 0.7,
      director: '未知',
      actors: '未知',
      region: '中国大陆',
      language: '中文',
      duration: 120,
      createTime: new Date().toISOString(),
      updateTime: new Date().toISOString(),
      // 添加缺失的必需字段
      videoUrl: '',
      categoryId: 1,
      tags: [movie.genre], // 修改为数组类型
    } as Film))
  }
}

export const tvboxService = new TVBoxService()
export default tvboxService
