# EnterpriseRAG Frontend

独立的 Vue 3 + TypeScript + Vite 展示前端，与 `portfolio-rag` 共用同一个 Spring Boot 后端。

```bash
npm ci
npm run dev
```

设置 `VITE_API_BASE_URL` 指向共享后端；不要把 Railway、Supabase 或模型密钥写入前端。没有真正部署 URL 时，Portfolio 卡片不会显示虚构的 Live Site 链接。
