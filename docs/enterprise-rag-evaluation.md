# EnterpriseRAG evaluation

Use `eval/data/EnterpriseRAG-Bench/questions.jsonl` as the only benchmark source. Keep raw JSONL predictions and a run manifest containing commit SHA, corpus ID, embedding/chat model, retrieval configuration and timestamp.

Compare at least `KEYWORD`, `VECTOR`, `HYBRID` and `HYBRID_RERANK`, plus three chunk configurations. Report HitRate, Recall, Precision, MRR and nDCG at K. Report answer correctness, citation correctness, faithfulness and insufficient-evidence behavior when the evaluator/provider is available; otherwise write `NOT_EXECUTED` with the reason.

```powershell
python .\eval\run_eval.py `
  --questions .\eval\data\EnterpriseRAG-Bench\questions.jsonl `
  --api-base $env:API_BASE --strategy HYBRID `
  --output .\eval\results\hybrid.jsonl

python .\eval\retrieval_eval.py `
  --questions .\eval\data\EnterpriseRAG-Bench\questions.jsonl `
  --predictions .\eval\results\hybrid.jsonl `
  --output .\eval\results\hybrid-metrics.json
```

Do not display a benchmark card in the frontend until a real result file exists. A missing metric is `Not measured yet`, never zero or a guessed score.
