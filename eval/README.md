# EnterpriseRAG 离线评估

本目录不会把 EnterpriseRAG-Bench 数据集提交进 Git。数据已经下载到本机的 `eval/data/EnterpriseRAG-Bench/`，其中包含官方 `questions.jsonl`（500 道问题）和用于本地接入验证的 `github_slice_0001.zip`（5,000 篇 GitHub 文档）。完整 `all_documents.zip` 约 1.26 GB，下载速度较慢；当前未完成部分明确保存为 `all_documents.zip.partial`，不可用于导入。

官方 Hugging Face 页面是 [onyx-dot-app/EnterpriseRAG-Bench](https://huggingface.co/datasets/onyx-dot-app/EnterpriseRAG-Bench)，官方 release 和 `.txt` 导出格式见 [EnterpriseRAG-Bench GitHub repository](https://github.com/onyx-dot-app/EnterpriseRAG-Bench)。文件名带有 `dsid_...` 数据集文档 ID；问题集 `questions.jsonl` 使用 `question_id`、`question`、`expected_doc_ids`、`gold_answer`、`answer_facts` 等字段。

官方来源：

- [EnterpriseRAG-Bench](https://github.com/onyx-dot-app/EnterpriseRAG-Bench)
- [Quickstart / export data format](https://github.com/onyx-dot-app/EnterpriseRAG-Bench/blob/main/quickstart.md)

安装离线 worker 与评测依赖：

```powershell
python -m pip install -r .\eval\requirements.txt
```

完整 release 下载（支持断点续传；文件约 1.26 GB）：

```powershell
curl.exe -L --retry 5 --retry-delay 5 --continue-at - --fail --show-error `
  -o .\eval\data\EnterpriseRAG-Bench\all_documents.zip `
  https://github.com/onyx-dot-app/EnterpriseRAG-Bench/releases/download/v1.0.0/all_documents.zip
```

## 1. 导入开发规模数据

先人工下载官方 release 的 `all_documents.zip` 或 source slice，再设置后端 `ENTERPRISE_RAG_ADMIN_TOKEN`。适配器会按 source type 轮询抽样，支持 1000、5000、10000、50000 文档，不会自动下载完整 500k+ corpus。

先做本地读取验证，不会访问后端：

```powershell
python .\eval\import_enterprise_bench.py `
  --archive .\eval\data\EnterpriseRAG-Bench\github_slice_0001.zip `
  --max-documents 5000 `
  --dry-run
```

确认后再提交 embedding 入库：

```bash
python eval/import_enterprise_bench.py \
  --archive ./eval/data/EnterpriseRAG-Bench/all_documents.zip \
  --max-documents 5000 \
  --api-base http://localhost:8080 \
  --admin-token "$ENTERPRISE_RAG_ADMIN_TOKEN"
```

Windows PowerShell：

```powershell
python .\eval\import_enterprise_bench.py `
  --archive .\eval\data\EnterpriseRAG-Bench\all_documents.zip `
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
