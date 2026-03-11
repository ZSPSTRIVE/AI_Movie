/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        // Apple-inspired Primary
        primary: {
          DEFAULT: '#007AFF',
          50:  '#E5F2FF',
          100: '#CCE4FF',
          200: '#99CAFF',
          300: '#66AFFF',
          400: '#3395FF',
          500: '#007AFF',
          600: '#0062CC',
          700: '#004999',
          800: '#003166',
          900: '#001833',
        },
        // Semantic
        success: { DEFAULT: '#34C759', light: '#D1FAE5' },
        warning: { DEFAULT: '#FF9500', light: '#FEF3C7' },
        danger:  { DEFAULT: '#FF3B30', light: '#FEE2E2' },
        info:    { DEFAULT: '#5856D6', light: '#EDE9FE' },
        // Apple Gray Scale
        gray: {
          50:  '#F9FAFB',
          100: '#F3F4F6',
          200: '#E5E7EB',
          300: '#D1D5DB',
          400: '#9CA3AF',
          500: '#6B7280',
          600: '#4B5563',
          700: '#374151',
          800: '#1F2937',
          900: '#111827',
          950: '#030712',
        },
      },
      fontFamily: {
        sans: [
          '-apple-system', 'BlinkMacSystemFont',
          'SF Pro Display', 'SF Pro Text',
          'PingFang SC', 'Noto Sans SC',
          'Microsoft YaHei', 'Helvetica Neue',
          'Arial', 'sans-serif'
        ],
        mono: ['SF Mono', 'JetBrains Mono', 'Consolas', 'monospace'],
      },
      borderRadius: {
        'sm': '6px',
        DEFAULT: '10px',
        'md': '10px',
        'lg': '14px',
        'xl': '20px',
        '2xl': '28px',
      },
      boxShadow: {
        'sm':    '0 1px 3px rgba(0, 0, 0, 0.08)',
        DEFAULT: '0 4px 12px rgba(0, 0, 0, 0.08)',
        'md':    '0 4px 12px rgba(0, 0, 0, 0.08)',
        'lg':    '0 8px 24px rgba(0, 0, 0, 0.10)',
        'xl':    '0 16px 48px rgba(0, 0, 0, 0.12)',
        '2xl':   '0 24px 64px rgba(0, 0, 0, 0.16)',
        'glass': '0 8px 32px rgba(0, 0, 0, 0.06)',
      },
      animation: {
        'fade-in':    'fadeIn 0.3s ease-out',
        'slide-up':   'slideUp 0.4s cubic-bezier(0.34, 1.56, 0.64, 1)',
        'float':      'float 20s ease-in-out infinite',
        'glow-pulse': 'glowPulse 3s ease-in-out infinite',
      },
      keyframes: {
        fadeIn: {
          from: { opacity: '0', transform: 'translateY(8px)' },
          to:   { opacity: '1', transform: 'translateY(0)' },
        },
        slideUp: {
          from: { opacity: '0', transform: 'translateY(20px)' },
          to:   { opacity: '1', transform: 'translateY(0)' },
        },
        float: {
          '0%, 100%': { transform: 'translate(0, 0) scale(1)' },
          '33%':      { transform: 'translate(30px, -30px) scale(1.05)' },
          '66%':      { transform: 'translate(-20px, 20px) scale(0.95)' },
        },
        glowPulse: {
          '0%, 100%': { opacity: '0.6' },
          '50%':      { opacity: '1' },
        },
      },
      transitionTimingFunction: {
        'apple':  'cubic-bezier(0.25, 0.1, 0.25, 1)',
        'spring': 'cubic-bezier(0.34, 1.56, 0.64, 1)',
      },
    },
  },
  plugins: [],
}
