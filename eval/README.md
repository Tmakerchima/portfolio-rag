# EnterpriseRAG 离线评估

本目录不会携带 EnterpriseRAG-Bench 数据集。官方仓库将导出文档作为按 source type 组织的 `.txt` 文件发布，文件名带有 `dsid_...` 数据集文档 ID；问题集 `questions.jsonl` 使用 `question_id`、`question`、`expected_doc_ids`、`gold_answer`、`answer_facts` 等字段。

官方来源：

- [EnterpriseRAG-Bench](https://github.com/onyx-dot-app/EnterpriseRAG-Bench)
- [Quickstart / export data format](https://github.com/onyx-dot-app/EnterpriseRAG-Bench/blob/main/quickstart.md)

## 1. 导入开发规模数据

先人工下载官方 release 的 `all_documents.zip` 或 source slice，再设置后端 `ENTERPRISE_RAG_ADMIN_TOKEN`。适配器会按 source type 轮询抽样，支持 1000、5000、10000、50000 文档，不会自动下载完整 500k+ corpus：

```bash
python eval/import_enterprise_bench.py \
  --archive ./data/EnterpriseRAG-Bench/all_documents.zip \
  --max-documents 5000 \
  --api-base http://localhost:8080 \
  --admin-token "$ENTERPRISE_RAG_ADMIN_TOKEN"
```

Windows PowerShell：

```powershell
python .\eval\import_enterprise_bench.py `
  --archive .\data\EnterpriseRAG-Bench\all_documents.zip `
  --max-documents 5000 `
  --api-base http://localhost:8080 `
  --admin-token $env:ENTERPRISE_RAG_ADMIN_TOKEN
```

`--max-documents` 可替换为 `1000`、`5000`、`10000` 或 `50000`。导入接口是 upsert，不会清空 Enterprise 表；未配置 token 时服务端拒绝请求。

## 2. Retrieval 指标

准备官方 `questions.jsonl` 后运行：

```bash
python eval/run_eval.py \
  --questions ./data/EnterpriseRAG-Bench/questions.jsonl \
  --api-base http://localhost:8080 \
  --strategy HYBRID \
  --output eval/results/hybrid.jsonl

python eval/retrieval_eval.py \
  --questions ./data/EnterpriseRAG-Bench/questions.jsonl \
  --predictions eval/results/hybrid.jsonl \
  --output eval/results/hybrid-metrics.json
```

脚本只对存在真实 `expected_doc_ids` 的问题计算 HitRate@K、Recall@K、Precision@K、MRR、nDCG；High Level 和 Info Not Found 等没有 ground-truth document 的问题明确标记为 unsupported，不生成虚假分数。对比 `VECTOR`、`KEYWORD`、`HYBRID`、`HYBRID_RERANK` 时使用不同输出文件。

## 3. RAGAS（可选）

RAGAS 只在线下运行。当前脚本按 Ragas 官方 `evaluate(dataset, metrics=...)` 接口构造 `question / ground_truth / answer / contexts` 列；如果当前安装的 provider、模型或 RAGAS API 不兼容，脚本会输出 `NOT_EXECUTED`，不会伪造结果。

```bash
python eval/ragas_eval.py \
  --predictions eval/results/hybrid.jsonl \
  --output eval/results/hybrid-ragas.json
```
