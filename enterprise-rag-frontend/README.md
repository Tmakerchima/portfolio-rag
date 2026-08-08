# EnterpriseRAG Frontend

独立的 Vue 3 + TypeScript + Vite 展示前端，与 `portfolio-rag` 共用同一个 Spring Boot 后端。

```bash
npm ci
npm run dev
```

设置 `VITE_API_BASE_URL` 指向共享后端；不要把 Railway、Supabase 或模型密钥写入前端。没有真正部署 URL 时，Portfolio 卡片不会显示虚构的 Live Site 链接。

页面提供中文 / English 切换。若查询显示无法连接后端，请先检查 `https://api.tmakerchima.cn/api/enterprise/health`；Railway 返回 502 时，问题在后端服务或部署状态，不在浏览器查询文本。
