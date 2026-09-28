#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
模拟数据集批量导入脚本（走真实 API，不直接写库）

用法：
    python scripts/import_dataset.py           # 幂等：demo01 已存在则跳过
    python scripts/import_dataset.py --clean   # 先删演示数据（docker exec psql），再重新导入

前置：docker(lnf-postgres) → matcher(:9000) → server(:8080) 均已启动。
"""
import argparse
import json
import subprocess
import sys
import time
from pathlib import Path

import openpyxl
import requests

ROOT = Path(__file__).resolve().parent.parent
XLSX = ROOT / "dataset" / "sim_dataset.xlsx"
PHOTOS = ROOT / "dataset" / "photos"
BASE = "http://localhost:8080"

DOCKER = r"C:\Program Files\Docker\Docker\resources\bin\docker.exe"
DB_CONTAINER = "lnf-postgres"

USERS = [
    {"username": f"demo{i:02d}", "password": "Demo123456",
     "email": f"demo{i:02d}@muc.edu.cn", "nickname": f"演示用户{i:02d}"}
    for i in range(1, 11)
]

TIME_SUFFIX = " 12:00:00"
FOUND_NO_ANSWER = "（招领方不填）"


def psql(sql: str) -> str:
    out = subprocess.run(
        [DOCKER, "exec", DB_CONTAINER, "psql", "-U", "lnf", "-d", "lost_and_found", "-t", "-c", sql],
        capture_output=True, text=True, encoding="utf-8", errors="replace")
    if out.returncode != 0:
        raise RuntimeError(f"psql 失败: {out.stderr}")
    return out.stdout.strip()


def clean_demo_data():
    """删除 demo% 用户的全部数据（依赖序）。仅用于本地开发重来。"""
    print("[clean] 删除演示数据 ...")
    psql("""
    DELETE FROM messages WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'demo%');
    DELETE FROM credit_logs WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'demo%');
    DELETE FROM audit_logs WHERE operator_id IN (SELECT id FROM users WHERE username LIKE 'demo%');
    DELETE FROM matches WHERE lost_item_id IN (SELECT id FROM items WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'demo%'))
       OR found_item_id IN (SELECT id FROM items WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'demo%'));
    DELETE FROM claims WHERE claimant_id IN (SELECT id FROM users WHERE username LIKE 'demo%')
       OR found_item_id IN (SELECT id FROM items WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'demo%'));
    DELETE FROM item_features WHERE item_id IN (SELECT id FROM items WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'demo%'));
    DELETE FROM items WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'demo%');
    DELETE FROM users WHERE username LIKE 'demo%';
    """)
    print("[clean] 完成")


def api(method: str, path: str, token: str = None, **kw):
    headers = kw.pop("headers", {})
    if token:
        headers["Authorization"] = f"Bearer {token}"
    r = requests.request(method, BASE + path, headers=headers, timeout=60, **kw)
    r.raise_for_status()
    return r.json()


def register_and_login():
    """注册（已存在则忽略 1001）并登录，返回 token 列表"""
    tokens = []
    for u in USERS:
        r = requests.post(f"{BASE}/api/auth/register", json=u, timeout=30).json()
        if r["code"] not in (0, 1001):  # 1001 = 用户名已存在
            raise RuntimeError(f"注册 {u['username']} 失败: {r}")
        login = requests.post(f"{BASE}/api/auth/login",
                              json={"username": u["username"], "password": u["password"]},
                              timeout=30).json()
        if login["code"] != 0:
            raise RuntimeError(f"登录 {u['username']} 失败: {login}")
        tokens.append(login["data"]["token"])
    print(f"[users] {len(tokens)} 个演示用户就绪")
    return tokens


def demo_already_imported() -> bool:
    r = requests.post(f"{BASE}/api/auth/login",
                      json={"username": "demo01", "password": "Demo123456"}, timeout=30).json()
    return r["code"] == 0


def load_rows():
    ws = openpyxl.load_workbook(XLSX)["数据集"]
    rows = []
    for r in ws.iter_rows(min_row=2, values_only=True):
        if r[0] is None:
            continue
        rows.append({
            "no": r[0], "type": r[1], "name": r[2], "category": r[3],
            "desc": r[4], "hidden": r[5], "campus": r[6], "location": r[7],
            "date": str(r[8]), "photo": r[9], "pair": r[10],
        })
    return rows


def build_category_map(token):
    data = api("GET", "/api/categories", token)["data"]
    return {c["name"]: c["id"] for c in data}


def build_location_mapper(token):
    """包含匹配：词表按名字长度降序，命中即映射；操场/食堂用校区消歧"""
    data = api("GET", "/api/locations", token)["data"]
    # data 结构：level1 节点含 children（level2）
    level2 = []
    for l1 in data:
        for l2 in l1.get("children", []) or []:
            level2.append({"id": l2["id"], "name": l2["name"], "campus": l2["campus"]})
    level2.sort(key=lambda x: -len(x["name"]))

    def map_location(text, campus):
        if not text:
            return None
        for entry in level2:
            name = entry["name"]
            if name in ("操场", "食堂"):  # 歧义词，单独按校区处理
                continue
            if name in text:
                return entry["id"]
        if "班车" in text:
            return 11 if "调度" in text else (12 if ("海淀-丰台" in text or "五棵松" in text) else 11)
        if "清真" in text:
            return 4
        if "荟贤" in text:
            return 5
        if "操场" in text:
            return 6 if campus == "海淀" else 10
        if "食堂" in text:
            return 4 if campus == "海淀" else 9
        return None

    return map_location


def upload_photo(token, photo_name):
    path = PHOTOS / photo_name
    if not path.exists():
        return None, f"照片不存在 {photo_name}"
    with open(path, "rb") as f:
        r = requests.post(f"{BASE}/api/files",
                          headers={"Authorization": f"Bearer {token}"},
                          files={"file": (photo_name, f, "image/jpeg")}, timeout=60)
    data = r.json()
    if data["code"] != 0:
        return None, f"上传失败 {data}"
    return data["data"]["path"], None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--clean", action="store_true", help="删除演示数据后重新导入")
    args = ap.parse_args()

    if args.clean:
        clean_demo_data()
    elif demo_already_imported():
        print("[skip] demo01 已存在，数据集已导入过；如需重来请加 --clean")
        return

    rows = load_rows()
    lost_rows = [r for r in rows if r["type"] == "失物"]
    found_rows = [r for r in rows if r["type"] == "招领"]
    # 匹配对 → LOST 行（FOUND 隐藏特征兜底用）
    pair_lost = {r["pair"]: r for r in lost_rows if r["pair"]}

    tokens = register_and_login()
    cat_map = build_category_map(tokens[0])
    loc_map = build_location_mapper(tokens[0])

    ok, fail = 0, []
    t0 = time.time()

    for seq, row in enumerate(lost_rows + found_rows):
        token = tokens[seq % len(tokens)]
        photo_path, err = upload_photo(token, row["photo"])
        if err:
            fail.append((row["no"], err))
            continue

        item = {
            "type": "LOST" if row["type"] == "失物" else "FOUND",
            "title": row["name"],
            "categoryId": cat_map.get(row["category"]),
            "locationId": loc_map(row["location"], row["campus"]),
            "eventTime": row["date"] + TIME_SUFFIX,
            "description": row["desc"],
            "images": [photo_path],
        }
        if item["type"] == "FOUND":
            if row["hidden"] and row["hidden"] != FOUND_NO_ANSWER:
                answer = row["hidden"]
            elif row["pair"] and row["pair"] in pair_lost:
                answer = pair_lost[row["pair"]]["hidden"]  # 拾获者看到实物可登记特征
            else:
                answer = row["desc"][:20]
            item["hiddenFeatures"] = [{"featureKey": "物品细节特征", "answer": answer}]

        resp = api("POST", "/api/items", token, json=item)
        if resp["code"] == 0:
            ok += 1
        else:
            fail.append((row["no"], f"发布失败 code={resp['code']} {resp['message']}"))
        if (seq + 1) % 50 == 0:
            print(f"[progress] {seq + 1}/{len(rows)}  成功 {ok}  失败 {len(fail)}")

    print(f"\n[done] 总计 {len(rows)} 行：成功 {ok}，失败 {len(fail)}，耗时 {time.time()-t0:.0f}s")
    for no, err in fail:
        print(f"  FAIL {no}: {err}")

    print("\n[verify] 等待异步向量化（每 20s 检查一次，最多 8 分钟）...")
    deadline = time.time() + 480
    while time.time() < deadline:
        time.sleep(20)
        stats = psql("SELECT count(*), count(text_vector), count(image_vector) FROM items "
                     "WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'demo%')")
        total, tv, iv = (int(x) for x in stats.split("|"))
        print(f"  items={total} text_vector={tv} image_vector={iv}")
        if total > 0 and tv == total and iv == total:
            break
    mcnt = psql("""SELECT count(*) FROM matches m JOIN items l ON l.id=m.lost_item_id
                   WHERE l.user_id IN (SELECT id FROM users WHERE username LIKE 'demo%')""")
    print(f"[verify] matches 记录数: {mcnt}")


if __name__ == "__main__":
    main()
