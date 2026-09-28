#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
匹配效果实验：ground truth（xlsx 82 个匹配对） vs matches 表实际产出

用法（需 lnf-postgres 容器在跑，导入已完成）：
    python scripts/evaluate_matches.py
输出：各阈值指标表 + 个案分析数据（供 docs/research/匹配实验结果.md 引用）

编号映射：导入是单线程顺序执行的（先 LOST 后 FOUND、xlsx 行序），
故 demo 用户的 items 按 id 排序后与 xlsx 同类行序一一对应。
"""
import json
import subprocess
from pathlib import Path

import openpyxl

ROOT = Path(__file__).resolve().parent.parent
XLSX = ROOT / "dataset" / "sim_dataset.xlsx"
DOCKER = r"C:\Program Files\Docker\Docker\resources\bin\docker.exe"
THRESHOLDS = [0.50, 0.55, 0.60, 0.65]
FLOOR = 0.40  # 导入时 server 运行的 match.threshold（matches 表只存 ≥0.40 的对）


def psql(sql, tuples=True):
    cmd = [DOCKER, "exec", "lnf-postgres", "psql", "-U", "lnf", "-d", "lost_and_found", "-t", "-c", sql]
    out = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if out.returncode != 0:
        raise RuntimeError(out.stderr)
    return out.stdout.strip()


def main():
    # ---------- ground truth ----------
    ws = openpyxl.load_workbook(XLSX)["数据集"]
    lost_order, found_order = [], []
    info = {}       # 编号 -> dict
    pair_of = {}    # 编号 -> P 编号（可能 None）
    gt = {}         # P -> (L编号, F编号)
    for r in ws.iter_rows(min_row=2, values_only=True):
        if r[0] is None:
            continue
        no, typ, name = r[0], r[1], r[2]
        info[no] = {"name": name, "type": typ, "date": str(r[8]), "loc": r[7], "note": r[11]}
        pair_of[no] = r[10] or None
        (lost_order if typ == "失物" else found_order).append(no)
        if r[10]:
            gt.setdefault(r[10], {})[typ] = no
    gt = {p: (d["失物"], d["招领"]) for p, d in gt.items()}
    print(f"ground truth: {len(gt)} 对")

    # ---------- DB ----------
    demo_filter = "user_id IN (SELECT id FROM users WHERE username LIKE 'demo%')"
    items = psql(f"SELECT id, type FROM items WHERE {demo_filter} ORDER BY id")
    no_of = {}  # item_id -> 数据编号
    li, fi = 0, 0
    for line in items.splitlines():
        iid, typ = (x.strip() for x in line.split("|"))
        if typ == "LOST":
            no_of[int(iid)] = lost_order[li]; li += 1
        else:
            no_of[int(iid)] = found_order[fi]; fi += 1
    assert li == len(lost_order) and fi == len(found_order), "条目数与 xlsx 不一致"

    rows = []
    for line in psql("SELECT lost_item_id, found_item_id, text_score, image_score, "
                     "time_score, location_score, total_score FROM matches").splitlines():
        l, f, ts, is_, ms, ls, tot = (x.strip() for x in line.split("|"))
        rows.append({
            "l": int(l), "f": int(f),
            "lno": no_of[int(l)], "fno": no_of[int(f)],
            "text": float(ts), "image": None if is_ == "" else float(is_),
            "time": float(ms), "loc": float(ls), "total": float(tot),
        })
    print(f"matches 表（≥{FLOOR}）: {len(rows)} 行")

    def is_true(row):
        p = pair_of.get(row["lno"])
        return p is not None and p == pair_of.get(row["fno"])

    # ---------- 阈值扫描 ----------
    print("\n阈值 | 匹配对总数 | 命中真实对 | 召回率(÷82) | 误报对 | 误报率")
    metrics = {}
    for t in THRESHOLDS:
        sel = [r for r in rows if r["total"] >= t]
        hit_pairs = {pair_of[r["lno"]] for r in sel if is_true(r)}
        wrong = [r for r in sel if not is_true(r)]
        recall = len(hit_pairs) / len(gt)
        fp_rate = len(wrong) / len(sel) if sel else 0
        metrics[t] = {"total": len(sel), "hit": len(hit_pairs), "recall": recall,
                      "wrong": len(wrong), "fp": fp_rate}
        print(f"{t:.2f} | {len(sel)} | {len(hit_pairs)} | {recall:.1%} | {len(wrong)} | {fp_rate:.1%}")

    # ---------- 漏配分析（0.40 地板都没进的真对） ----------
    hit_floor = {pair_of[r["lno"]] for r in rows if is_true(r)}
    missed = [p for p in gt if p not in hit_floor]
    print(f"\n漏配对（<{FLOOR} 或未进候选）: {len(missed)} 个: {missed}")
    missed_detail = []
    for p in missed:
        lno, fno = gt[p]
        lid = [k for k, v in no_of.items() if v == lno][0]
        fid = [k for k, v in no_of.items() if v == fno][0]
        # 离线算一遍这对的四因子（绕过粗筛 Top20，判断是否被粗筛截断）
        sc = psql(f"""SELECT 1-(a.text_vector <=> b.text_vector),
                             1-(a.image_vector <=> b.image_vector),
                             a.event_time, b.event_time, a.location_id, b.location_id
                      FROM items a, items b WHERE a.id={lid} AND b.id={fid}""")
        ts, isv, et_a, et_b, la, lb = (x.strip() for x in sc.split("|"))
        ts, isv = float(ts), float(isv)
        days = abs((__import__("datetime").datetime.fromisoformat(et_b)
                    - __import__("datetime").datetime.fromisoformat(et_a)).total_seconds()) / 86400
        ms = max(0.3, 1.0 - 0.1 * days)
        campus = psql(f"SELECT (SELECT campus FROM locations WHERE id={la}), "
                      f"(SELECT campus FROM locations WHERE id={lb})") if la and lb else "|"
        ca, cb = (x.strip() for x in campus.split("|"))
        if not la or not lb:
            ls = 0.4
        elif la == lb:
            ls = 1.0
        elif ca and ca == cb:
            ls = 0.6
        else:
            ls = 0.2
        total = 0.5 * ts + 0.2 * isv + 0.15 * ms + 0.15 * ls
        missed_detail.append({
            "pair": p, "lno": lno, "fno": fno,
            "lname": info[lno]["name"], "fname": info[fno]["name"],
            "note": info[lno]["note"],
            "text": ts, "image": isv, "time": ms, "loc": ls, "total": total,
        })
        print(f"  {p}: {lno}「{info[lno]['name']}」× {fno}「{info[fno]['name']}」 "
              f"离线分 text={ts:.4f} image={isv:.4f} time={ms:.2f} loc={ls} total={total:.4f} 备注={info[lno]['note']}")

    # ---------- 个案素材 ----------
    true_rows = sorted([r for r in rows if is_true(r)], key=lambda r: -r["total"])
    wrong_rows = sorted([r for r in rows if not is_true(r)], key=lambda r: -r["total"])
    print("\n成功对 Top2（各因子）:")
    for r in true_rows[:2]:
        print(f"  {pair_of[r['lno']]}: {r['lno']}「{info[r['lno']]['name']}」× {r['fno']}「{info[r['fno']]['name']}」"
              f" text={r['text']} image={r['image']} time={r['time']} loc={r['loc']} total={r['total']}")
    print("误配对 Top3（各因子）:")
    for r in wrong_rows[:3]:
        print(f"  {r['lno']}「{info[r['lno']]['name']}」({info[r['lno']]['date']}@{info[r['lno']]['loc']}) × "
              f"{r['fno']}「{info[r['fno']]['name']}」({info[r['fno']]['date']}@{info[r['fno']]['loc']})"
              f" text={r['text']} image={r['image']} time={r['time']} loc={r['loc']} total={r['total']}")

    out = {"metrics": {str(k): v for k, v in metrics.items()},
           "missed": missed_detail,
           "true_top": true_rows[:5], "wrong_top": wrong_rows[:5]}
    (ROOT / "docs" / "research").mkdir(parents=True, exist_ok=True)
    with open(ROOT / "docs" / "research" / "eval_raw.json", "w", encoding="utf-8") as fp:
        json.dump(out, fp, ensure_ascii=False, indent=2, default=str)
    print("\n原始数据已存 docs/research/eval_raw.json")


if __name__ == "__main__":
    main()
