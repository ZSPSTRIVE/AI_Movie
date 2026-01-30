import type { Film } from '@/types/film'

/**
 * TVBox 影视数据模拟服务
 * 提供电影、电视剧等数据
 */
class TVBoxMockService {
  private filmDatabase: Film[] = []

  constructor() {
    this.initDatabase()
  }

  /**
   * 初始化模拟数据库
   */
  private initDatabase() {
    const genres = ['动作', '喜剧', '爱情', '科幻', '悬疑', '恐怖', '动画', '剧情']
    const years = [2024, 2023, 2022, 2021, 2020]
    
    const movieTemplates = [
      { title: '流浪地球3', desc: '人类为拯救地球，再次踏上征程' },
      { title: '长安三万里', desc: '盛唐时期的诗人传奇故事' },
      { title: '热辣滚烫', desc: '励志女性成长故事' },
      { title: '第二十条', desc: '基层检察官的正义之路' },
      { title: '飞驰人生2', desc: '赛车手的梦想与坚持' },
      { title: '志愿军', desc: '抗美援朝的英雄史诗' },
      { title: '消失的她', desc: '悬疑惊悚的失踪案件' },
      { title: '封神第一部', desc: '中国神话经典改编' },
      { title: '孤注一掷', desc: '反诈题材犯罪片' },
      { title: '满江红', desc: '南宋抗金的历史故事' },
      { title: '深海', desc: '少女的奇幻冒险之旅' },
      { title: '无名', desc: '谍战题材动作片' },
      { title: '想见你', desc: '穿越时空的爱情故事' },
      { title: '三体', desc: '科幻史诗巨制' },
      { title: '狂飙', desc: '扫黑除恶题材' },
      { title: '开端', desc: '时间循环悬疑剧' },
      { title: '人世间', desc: '平民家族的奋斗史' },
      { title: '漫长的季节', desc: '东北小城的悬疑故事' },
      { title: '繁花', desc: '上海往事的浮华' },
      { title: '庆余年2', desc: '古装权谋续篇' },
    ]

    this.filmDatabase = movieTemplates.map((template, index) => ({
      id: index + 1,
      title: template.title,
      description: template.desc,
      coverUrl: `https://picsum.photos/seed/${index + 100}/400/600`,
      year: years[Math.floor(Math.random() * years.length)],
      genre: genres[Math.floor(Math.random() * genres.length)],
      rating: (Math.random() * 2 + 7).toFixed(1),
      playCount: Math.floor(Math.random() * 1000000),
      isVip: Math.random() > 0.6,
      director: '未知',
      actors: '未知',
      region: '中国大陆',
      language: '中文',
      duration: Math.floor(Math.random() * 60 + 90),
      createTime: new Date().toISOString(),
      updateTime: new Date().toISOString(),
    }))
  }

  /**
   * 获取推荐电影
   */
  getRecommend(limit: number = 18): Promise<Film[]> {
    return new Promise((resolve) => {
      setTimeout(() => {
        const shuffled = [...this.filmDatabase].sort(() => Math.random() - 0.5)
        resolve(shuffled.slice(0, limit))
      }, 300)
    })
  }

  /**
   * 获取电影列表（带分页）
   */
  getList(page: number = 1, pageSize: number = 18): Promise<{ list: Film[], total: number }> {
    return new Promise((resolve) => {
      setTimeout(() => {
        const start = (page - 1) * pageSize
        const end = start + pageSize
        resolve({
          list: this.filmDatabase.slice(start, end),
          total: this.filmDatabase.length,
        })
      }, 300)
    })
  }

  /**
   * 搜索电影
   */
  search(keyword: string): Promise<Film[]> {
    return new Promise((resolve) => {
      setTimeout(() => {
        const results = this.filmDatabase.filter(film =>
          film.title.includes(keyword) || film.description.includes(keyword)
        )
        resolve(results)
      }, 300)
    })
  }

  /**
   * 获取电影详情
   */
  getDetail(id: number): Promise<Film | null> {
    return new Promise((resolve) => {
      setTimeout(() => {
        const film = this.filmDatabase.find(f => f.id === id)
        resolve(film || null)
      }, 300)
    })
  }

  /**
   * 添加新电影到数据库
   */
  addFilm(film: Omit<Film, 'id'>): Film {
    const newFilm = {
      ...film,
      id: this.filmDatabase.length + 1,
    }
    this.filmDatabase.push(newFilm)
    return newFilm
  }
}

export const tvboxService = new TVBoxMockService()
export default tvboxService
