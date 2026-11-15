# Quiz King question model

A small open model (Qwen3-4B) fine-tuned with [Unsloth](https://github.com/unslothai/unsloth) to write
new quiz questions in Quiz King's format. Generated questions are **drafts**: an admin reviews them on the
admin page before they join any game, because small models can get facts wrong.

## 1. Export the training data

With the stack running and the question bank imported:

```bash
python3 export_dataset.py            # writes data/train.jsonl and data/valid.jsonl
```

Each example is a conversation in the exact format the app uses at run time ([prompt.json](prompt.json)).
The files are git-ignored: the imported questions are Open Trivia DB content (CC BY-SA 4.0), so keep that
attribution with the data and with any model trained on it.

## 2. Train on a free GPU (Google Colab)

Unsloth trains on NVIDIA GPUs; a free Colab T4 is enough for a 4B model.

1. Open [train_question_model.ipynb](train_question_model.ipynb) in Colab and pick **Runtime → T4 GPU**.
2. Run the cells; upload `train.jsonl`, `valid.jsonl` and `prompt.json` when asked.
3. Training takes about 40-60 minutes. Cell 6 prints sample questions and how many are valid.
4. The last cell downloads `quiz-king-q4_k_m.gguf` (~2.5 GB). Put it in `models/` next to this file.

## 3. Run it with Quiz King

From `QuizKing/`:

```bash
AI_MODEL_URL=http://question-model:8080 docker compose --profile ai up -d question-model question-generator
```

`question-model` is llama.cpp's server running the GGUF on the CPU (about 2 GB RAM). Then, on the
**Create** page, use **AI question drafts**: pick a topic, difficulty and count, review the drafts, approve
(optionally after editing) or reject them. Approved questions join the topic's games straight away.

## Changing the prompt

Edit `prompt.json`, copy it to `questionGenerator/src/main/resources/ai/prompt.json` (a test fails if the
two differ), re-export the data and retrain: the model only works well with the wording it was trained on.
