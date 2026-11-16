#!/usr/bin/env python3
"""
Exports the QuizCadia question bank as a chat dataset for fine-tuning (train.jsonl, valid.jsonl).

Each example is one conversation in the exact format the question generator uses at run time
(see prompt.json): a system prompt, a user turn naming topic / difficulty / format, and the
assistant's JSON answer. Only hand-written and imported questions are used, never AI drafts.

Usage:
    python3 export_dataset.py [--url http://localhost:4280] [--out data] [--valid-share 0.05]

Data licence: imported questions come from Open Trivia DB under CC BY-SA 4.0. The exported files
and anything trained on them should keep that attribution; they are git-ignored on purpose.
"""
import argparse
import hashlib
import json
import os
import random
import sys
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
PROMPT = json.load(open(os.path.join(HERE, "prompt.json"), encoding="utf-8"))
LEVELS = {1: "easy", 2: "medium", 3: "hard"}


def options_of(q):
    opts = [o.strip() for o in (q.get("option1"), q.get("option2"), q.get("option3"), q.get("option4")) if o and o.strip()]
    return list(dict.fromkeys(opts))  # drop duplicates, keep order


def to_example(topic, q, rng):
    opts = options_of(q)
    answer = (q.get("correctAnswer") or "").strip()
    stem = (q.get("questionStem") or "").strip()
    if not stem or len(opts) < 2 or not any(o.lower() == answer.lower() for o in opts):
        return None
    answer = next(o for o in opts if o.lower() == answer.lower())
    true_false = sorted(o.lower() for o in opts) == ["false", "true"]
    if true_false:
        opts, fmt = ["True", "False"], "true/false"
        answer = answer.capitalize()
    elif len(opts) == 4:
        rng.shuffle(opts)  # the model should not learn that the answer sits in a fixed slot
        fmt = "multiple choice"
    else:
        return None
    user = PROMPT["user"].format(topic=topic, difficulty=LEVELS.get(q.get("questionLevel"), "easy"), format=fmt)
    reply = json.dumps({"question": stem, "options": opts, "answer": answer}, ensure_ascii=False)
    return {"conversations": [
        {"role": "system", "content": PROMPT["system"]},
        {"role": "user", "content": user},
        {"role": "assistant", "content": reply},
    ]}


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[1])
    ap.add_argument("--url", default="http://localhost:4280", help="QuizCadia base URL (nginx entry point)")
    ap.add_argument("--out", default=os.path.join(HERE, "data"))
    ap.add_argument("--valid-share", type=float, default=0.05)
    args = ap.parse_args()

    with urllib.request.urlopen(args.url.rstrip("/") + "/api/v1/question-generator/category", timeout=60) as r:
        categories = json.load(r)

    rng = random.Random(3407)
    train, valid, skipped = [], [], 0
    for c in categories:
        for t in c.get("topic") or []:
            name = t["topicName"] if t["topicName"] == c["categoryName"] else f"{c['categoryName']}: {t['topicName']}"
            for q in t.get("questions") or []:
                if (q.get("questionType") or "").lower() == "ai":
                    continue
                ex = to_example(name, q, rng)
                if ex is None:
                    skipped += 1
                    continue
                # stable split by question text, so re-exporting never moves a question between sets
                bucket = int(hashlib.sha1(q["questionStem"].encode("utf-8")).hexdigest(), 16) % 1000
                (valid if bucket < args.valid_share * 1000 else train).append(ex)

    os.makedirs(args.out, exist_ok=True)
    for name, rows in (("train.jsonl", train), ("valid.jsonl", valid)):
        with open(os.path.join(args.out, name), "w", encoding="utf-8") as f:
            for row in rows:
                f.write(json.dumps(row, ensure_ascii=False) + "\n")
    print(f"train: {len(train)}  valid: {len(valid)}  skipped: {skipped}  -> {args.out}")
    if not train:
        sys.exit("No questions exported: is the question bank empty?")


if __name__ == "__main__":
    main()
