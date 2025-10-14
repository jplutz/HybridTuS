/** @type {import('tailwindcss').Config} */
export default {
    content: ["./index.html", "./src/**/*.{ts,tsx,js,jsx}"],
    theme: {
        extend: {
            fontFamily: {
                sans: ['Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'],
                mono: ['JetBrains Mono', 'ui-monospace', 'SFMono-Regular', 'monospace'],
            },
            borderRadius: {
                '2xl': '1rem',
            },
            boxShadow: {
                soft: '0 6px 24px rgba(0,0,0,0.06)',
            },
        },
    },
    plugins: [],
};
