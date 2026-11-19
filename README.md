# 额度看板 (QuotaBoard)

集中查看各家 AI 服务的订阅额度与按量余额的 Android 应用。参考 [tryusage.app (usAge)](https://tryusage.app/zh-Hans) 的思路。

- 凭证只用 `EncryptedSharedPreferences` 保存在手机本地
- 所有查询由手机直接请求服务商官方接口，不经过任何自建服务器
- 没有可查接口的服务明确显示「暂不支持查询」，不编造数据

## 技术栈

Kotlin · Jetpack Compose · Material 3 · minSdk 26 · OkHttp（Retrofit 兼容的请求封装）· kotlinx-serialization

## 构成

- `provider/QuotaProvider.kt`：Provider 插件接口。新增服务商只需实现接口并注册到 `ProviderRegistry`
- `data/`：凭证加密存储、账号/快照/余额记账（按刷新差值本地推算花销）
- `oauth/`：Custom Tabs / WebView OAuth（PKCE）
- `ui/screens/`：首页账号卡片列表（80%/95% 变色警示）、添加账号页、账号详情页、空状态页
- 首页支持下拉刷新；右上角菜单可开启「示例数据」模式（卡片明确标注"示例"）

## 构建要求

Android Studio 或本地 Gradle 8.9+，JDK 17，Android SDK（compileSdk 35）。

```bash
./gradlew assembleDebug   # 或 gradle assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 服务商接入状态（v0.1.0 · 2026-11）

| 服务 | 认证方式 | 订阅额度 | 按量余额 | 接口基础 |
|---|---|---|---|---|
| Claude | OAuth / API Key | ✅ OAuth 查询（公开用量接口，端点实测 401 鉴权） | ❌ 暂不支持（无公开 API Key 余额接口） | 端点真实存在 |
| Codex (ChatGPT) | OAuth / API Key | ✅ OAuth 查询（wham/usage，端点实测 401） | ❌ 暂不支持 | 端点真实存在 |
| Grok / xAI | Management Key | ❌ SuperGrok 无公开接口 | ✅ 预付余额（官方管理 API，端点实测可达） | 端点真实存在 |
| DeepSeek API | API Key | — | ✅ 余额（GET /user/balance，端点实测 401） | 官方公开接口 |
| Mistral API | API Key | — | ✅ 积分余额（GET /v1/billing/subscription，端点实测 401） | 官方 CLI 同款接口 |
| opencode go | API Key | ✅ 订阅窗口（GET /zen/go/v1/usage，按公开文档实现） | ❌ Zen 余额无公开接口 | 文档端点（沙箱被 Cloudflare 拦截，未能直连验证） |

**"实测"** 指在开发环境直接向该端点发起请求，确认接口存在且返回的是鉴权错误（而非 404），接口语义有官方文档或第一方开源实现佐证。OAuth 登录链路（Claude console.anthropic.com、Codex auth.openai.com）无法在开发环境完成真实账号授权，参数取自 codex CLI / Claude Code 官方开源实现，请在真机登录验证。

## 免责声明

各接口均为服务商未完全公开的内部接口，可能随时变化。查询失败时 App 显示错误原文，不会伪造数据。
