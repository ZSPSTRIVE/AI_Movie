/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        // Neo-Brutalism 核心色板
        'nb-bg': '#F8FAFC',        // 极浅灰白
        'nb-card': '#FFFFFF',
        'nb-border': '#1E293B',    // 深蓝灰
        
        // Pop 强力主色 - 更新为大胆且不刺眼的阳光色
        'pop-yellow': '#FFA62B',   // 阳光橙黄 (Deep Saffron) - 替代原刺眼黄
        'pop-blue': '#3B82F6',     // Blue 500
        'pop-red': '#EF4444',      // Red 500
        'pop-green': '#10B981',    // Emerald 500
        'pop-orange': '#F97316',   // Orange 500
        'pop-purple': '#8B5CF6',   // Violet 500
        'pop-pink': '#EC4899',     // Pink 500
        
        // 文字色
        'nb-text': '#0F172A',      // Slate 900
        'nb-text-sub': '#475569',  // Slate 600
        
        // 兼容旧的 primary
        primary: {
          DEFAULT: '#10B981',
          500: '#10B981',
        }
      },
      fontFamily: {
        sans: ['Archivo', 'Microsoft YaHei', 'sans-serif'],
        mono: ['JetBrains Mono', 'Consolas', 'monospace'],
      },
      boxShadow: {
        'brutal': '4px 4px 0 #1E293B',
        'brutal-sm': '2px 2px 0 #1E293B',
        'brutal-lg': '6px 6px 0 #1E293B',
        'brutal-xl': '10px 10px 0 #1E293B', // 更大的阴影
        'brutal-hover': '8px 8px 0 #1E293B',
      },
      borderWidth: {
        '3': '3px',
      },
      animation: {
        'bounce-in': 'bounceIn 0.5s cubic-bezier(0.68, -0.55, 0.265, 1.55)', // 更Q弹
        'slide-up': 'slideUp 0.4s cubic-bezier(0.34, 1.56, 0.64, 1)',
        'shake': 'shake 0.5s ease-in-out',
        'wiggle': 'wiggle 1s ease-in-out infinite',
        'pop': 'pop 0.3s ease-out',
      },
      keyframes: {
        bounceIn: {
          '0%': { transform: 'scale(0.8)', opacity: '0' },
          '70%': { transform: 'scale(1.05)' },
          '100%': { transform: 'scale(1)', opacity: '1' },
        },
        slideUp: {
          '0%': { transform: 'translateY(40px)', opacity: '0' },
          '100%': { transform: 'translateY(0)', opacity: '1' },
        },
        shake: {
          '0%, 100%': { transform: 'translateX(0) rotate(0deg)' },
          '25%': { transform: 'translateX(-5px) rotate(-5deg)' }, // 增加旋转
          '75%': { transform: 'translateX(5px) rotate(5deg)' },
        },
        wiggle: {
          '0%, 100%': { transform: 'rotate(-3deg)' },
          '50%': { transform: 'rotate(3deg)' },
        },
        pop: {
          '0%': { transform: 'scale(1)' },
          '50%': { transform: 'scale(1.1)' },
          '100%': { transform: 'scale(1)' },
        }
      },
    }
  },
  plugins: []
}
