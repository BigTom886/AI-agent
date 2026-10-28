/**
 * Markdown 渲染工具
 *
 * 为什么要自己封装一层：
 * 1. markdown-it 实例是「重量级」的（要建各种规则和解析器），
 *    放在模块顶层全局复用一份，不要每个组件 new 一个。
 * 2. 代码高亮用 highlight.js 的 core 版本 + 手动注册常用语言，
 *    全量引入会多打几百 KB 的包（core 只有 ~30KB）。
 */
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js/lib/core'
import 'highlight.js/styles/github.css'

// —— 手动注册常用语言 ——
// 只注册 AI 实际会吐出来的语言，够用且省体积。
// 需要加别的语言时：npm i 后在这里 import + registerLanguage 即可
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import java from 'highlight.js/lib/languages/java'
import python from 'highlight.js/lib/languages/python'
import json from 'highlight.js/lib/languages/json'
import bash from 'highlight.js/lib/languages/bash'
import shell from 'highlight.js/lib/languages/shell'
import xml from 'highlight.js/lib/languages/xml'
import css from 'highlight.js/lib/languages/css'
import sql from 'highlight.js/lib/languages/sql'
import yaml from 'highlight.js/lib/languages/yaml'
import markdown from 'highlight.js/lib/languages/markdown'
import go from 'highlight.js/lib/languages/go'
import rust from 'highlight.js/lib/languages/rust'
import cpp from 'highlight.js/lib/languages/cpp'
import php from 'highlight.js/lib/languages/php'
import kotlin from 'highlight.js/lib/languages/kotlin'
import swift from 'highlight.js/lib/languages/swift'
import diff from 'highlight.js/lib/languages/diff'
import plaintext from 'highlight.js/lib/languages/plaintext'

const languages = {
  javascript,
  typescript,
  java,
  python,
  json,
  bash,
  shell,
  xml,
  css,
  sql,
  yaml,
  markdown,
  go,
  rust,
  cpp,
  php,
  kotlin,
  swift,
  diff,
  plaintext,
}

Object.entries(languages).forEach(([name, lang]) => {
  hljs.registerLanguage(name, lang)
})

/**
 * markdown-it 实例
 *
 * 几个关键选项（关系到安全和体验）：
 * - html: false   —— 不解析消息里内嵌的 HTML。这是防 XSS 的第一道闸：
 *                    AI 返回的 <script>、<img onerror=...> 会原样当文本显示。
 *                    加上 markdown-it 自带的链接协议白名单（拦掉 javascript:），
 *                    就不需要再额外引 DOMPurify 了。
 * - linkify: true —— 自动把裸 URL（http://xxx）变成可点链接
 * - breaks: true  —— 单个换行也换行。聊天里 AI 常随手换行，
 *                    不开的话这些换行会被当成同一段挤在一起。
 */
const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  highlight(code, lang) {
    const language = (lang || '').trim().toLowerCase()
    if (language && hljs.getLanguage(language)) {
      try {
        const highlighted = hljs.highlight(code, {
          language,
          ignoreIllegals: true,
        }).value
        // 以 <pre 开头表示「这个 HTML 我自己拼好了」，
        // markdown-it 就不会再往里套一层 <pre><code>。
        // class 里的 hljs 是 highlight.js 主题的样式开关，缺了就只有一堆没颜色的 token。
        const languageClass = language ? ` language-${language}` : ''
        return `<pre class="hljs${languageClass}"><code>${highlighted}</code></pre>`
      } catch {
        // 高亮失败就退回纯文本，不影响消息展示
      }
    }
    // 没指定语言（或语言不认识）：返回空串，
    // markdown-it 会用默认的转义逻辑输出 <pre><code>纯文本</code></pre>
    return ''
  },
})

/**
 * 把 Markdown 源码渲染成 HTML
 *
 * @param {string} text Markdown 源码
 * @returns {string} HTML 字符串（供 v-html 使用）
 */
export function renderMarkdown(text) {
  if (!text) {
    return ''
  }
  return md.render(text)
}

/**
 * 在渲染结果的末尾插入一个「正在输入」的光标
 *
 * 直接把光标放在渲染结果外面的话，它会掉到最后一个块级元素（段落/代码块）的
 * 下一行，视觉上跟内容脱节。所以这里塞进最后一个 </p> 里面，
 * 让它紧跟在最后一行文字后面。
 *
 * @param {string} html 已渲染的 HTML
 * @returns {string} 插入光标后的 HTML
 */
export function withCursor(html) {
  const cursor = '<span class="cursor">▍</span>'
  if (!html) {
    return cursor
  }
  const index = html.lastIndexOf('</p>')
  if (index >= 0) {
    return html.slice(0, index) + cursor + html.slice(index)
  }
  return html + cursor
}
