// 后端地址配置（前端唯一需要手填的地方）。
//
// 把下面的 API_BASE 改成你部署在 Railway 上的后端公网域名：
//   1. 后端先部署到 Railway，拿到形如 https://xxxx.up.railway.app 的地址
//   2. 填到这里（不要带结尾斜杠，不要带 /api）
//   3. 重新部署 Vercel（push 即自动重建）
//
// 例：
//   window.API_BASE = "https://deltaforcetools-production.up.railway.app";
//
// 留空则回退到「同源 /api」，仅适合本地调试或 Vercel 反代场景，
// 直接部署到 Vercel 时留空会导致接口 404。
window.API_BASE = "https://deltaforcetools-production.up.railway.app";
