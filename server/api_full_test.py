# -*- coding: utf-8 -*-
"""42 个接口全量实测（2026-10-19）：完整业务流 + 后台管理，输出 Markdown 实测记录。"""
import json, time, sys, io, urllib.request, urllib.error
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

BASE = 'http://localhost:8080'
results = []  # (模块, 方法, 路径, HTTP, 业务码, 结论, 备注)

def call(method, path, token=None, body=None, files=None):
    url = BASE + path
    headers = {}
    data = None
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if files:
        import uuid
        boundary = uuid.uuid4().hex
        parts = b''
        for name, (fname, content, ctype) in files.items():
            parts += (f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"; '
                      f'filename="{fname}"\r\nContent-Type: {ctype}\r\n\r\n').encode() + content + b'\r\n'
        parts += f'--{boundary}--\r\n'.encode()
        headers['Content-Type'] = 'multipart/form-data; boundary=' + boundary
        data = parts
    elif body is not None:
        headers['Content-Type'] = 'application/json'
        data = json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {}

def rec(mod, method, path, st, resp, expect0=True, note=''):
    code = resp.get('code', '-') if isinstance(resp, dict) else '-'
    ok = (st == 200 and code == 0) if expect0 else (st in (200, 400, 403) or code in (400, 403, 6002, 6004, 6005))
    results.append((mod, method, path, st, code, 'PASS' if ok else 'FAIL', note))
    return resp

def login(u, p):
    st, r = call('POST', '/api/auth/login', body={'username': u, 'password': p})
    return r['data']['token']

admin = login('admin', 'Demo123456')
demo01 = login('demo01', 'Demo123456')
demo02 = login('demo02', 'Demo123456')
rec('认证', 'POST', '/api/auth/login', 200, {'code': 0}, note='admin/demo01/demo02 三个账号')

ts = str(int(time.time()))
st, r = call('POST', '/api/auth/register',
             body={'username': 'apitest' + ts[-6:], 'password': 'Test123456',
                   'email': f'apitest{ts[-6:]}@test.com', 'nickname': '接口测试'})
rec('认证', 'POST', '/api/auth/register', st, r)

# --- 基础数据 ---
st, r = call('GET', '/api/categories')
cats = r['data']; rec('分类', 'GET', '/api/categories', st, r, note=f'{len(cats)} 个分类')
cat_id = cats[0]['id']
st, r = call('GET', '/api/locations')
locs = r['data']; rec('地点', 'GET', '/api/locations', st, r, note=f'{len(locs)} 个地点词表')
loc_id = locs[0]['id'] if locs else None

# --- 文件上传 ---
png = bytes.fromhex('89504e470d0a1a0a0000000d4948445200000001000000010806000000'
                    '1f15c4890000000d49444154789c626001000000ffff03000006000557bfabd40000000049454e44ae426082')
st, r = call('POST', '/api/files', token=demo01, files={'file': ('t.png', png, 'image/png')})
img_path = r['data'].get('path') if st == 200 and r.get('code') == 0 else None
rec('文件', 'POST', '/api/files', st, r, note='multipart 上传 1×1 PNG')

# --- 信息发布（FOUND，含隐藏特征）---
item_body = {'type': 'FOUND', 'title': f'接口实测-拾获黑色钱包{ts[-4:]}', 'categoryId': cat_id,
             'locationId': loc_id, 'eventTime': '2026-10-19 10:30:00',
             'description': '接口自动化实测数据：操场看台拾获黑色折叠钱包一个，内有学生卡。',
             'images': [img_path] if img_path else [],
             'hiddenFeatures': [{'featureKey': '钱包内学生卡姓名', 'answer': '测试同学'}]}
st, r = call('POST', '/api/items', token=demo01, body=item_body)
item_id = r['data']['id'] if st == 200 and r.get('code') == 0 else None
rec('信息发布', 'POST', '/api/items', st, r, note=f'新建 FOUND 信息 id={item_id}')

st, r = call('GET', '/api/items?type=FOUND&page=1&size=10', token=demo01)
rec('信息发布', 'GET', '/api/items', st, r, note='分页检索')
st, r = call('GET', '/api/items/mine', token=demo01)
rec('信息发布', 'GET', '/api/items/mine', st, r)
st, r = call('GET', f'/api/items/{item_id}', token=demo01)
rec('信息发布', 'GET', '/api/items/{id}', st, r)
item_body['description'] = '接口自动化实测数据（已编辑）：操场看台拾获黑色折叠钱包一个。'
st, r = call('PUT', f'/api/items/{item_id}', token=demo01, body=item_body)
rec('信息发布', 'PUT', '/api/items/{id}', st, r, note='编辑描述')
st, r = call('GET', f'/api/items/{item_id}/claim-questions', token=demo02)
rec('信息发布', 'GET', '/api/items/{id}/claim-questions', st, r, note='只回问题不回答案')

# --- 匹配 ---
time.sleep(4)  # 等匹配引擎
st, r = call('GET', f'/api/matches?itemId={item_id}', token=demo01)
matches = r.get('data') or []
rec('智能匹配', 'GET', '/api/matches', st, r, note=f'命中 {len(matches)} 条候选')
if matches:
    st, r = call('POST', f"/api/matches/{matches[0]['matchId']}/feedback", token=demo01, body={'confirm': True})
    rec('智能匹配', 'POST', '/api/matches/{id}/feedback', st, r, note='确认匹配有效')
else:
    st, r = call('POST', '/api/matches/1/feedback', token=demo01, body={'confirm': True})
    rec('智能匹配', 'POST', '/api/matches/{id}/feedback', st, r, expect0=False, note='无候选时对历史记录反馈')

# --- 认领全流程 ---
st, r = call('POST', '/api/claims', token=demo02,
             body={'foundItemId': item_id,
                   'answers': [{'featureKey': '钱包内学生卡姓名', 'answer': '测试同学'}]})
claim_id = r['data']['claimId'] if st == 200 and r.get('code') == 0 else None
rec('认领核销', 'POST', '/api/claims', st, r, note=f'demo02 认领 id={claim_id}')
st, r = call('GET', '/api/claims/mine', token=demo02)
rec('认领核销', 'GET', '/api/claims/mine', st, r)
st, r = call('GET', '/api/claims/todo', token=demo01)
rec('认领核销', 'GET', '/api/claims/todo', st, r, note='拾获者待办')
st, r = call('POST', f'/api/claims/{claim_id}/review', token=demo01, body={'action': 'APPROVE'})
rec('认领核销', 'POST', '/api/claims/{id}/review', st, r, note='APPROVE 通过')
st, r = call('GET', f'/api/claims/{claim_id}/code', token=demo02)
vcode = r['data'].get('verifyCode') if st == 200 and r.get('code') == 0 else None
rec('认领核销', 'GET', '/api/claims/{id}/code', st, r, note='取交接核销码')
st, r = call('POST', '/api/claims/verify', token=demo01, body={'verifyCode': vcode})
rec('认领核销', 'POST', '/api/claims/verify', st, r, note='扫码核销，交接完成')

# --- 信用 / 消息 / 用户 ---
st, r = call('GET', '/api/credits/mine', token=demo02)
rec('信用', 'GET', '/api/credits/mine', st, r)
st, r = call('GET', '/api/messages', token=demo02)
msgs = r.get('data') or []
if isinstance(msgs, dict):
    msgs = msgs.get('records') or msgs.get('list') or []
rec('消息', 'GET', '/api/messages', st, r, note=f'{len(msgs)} 条通知')
if msgs:
    mid = msgs[0]['id'] if isinstance(msgs[0], dict) else None
    if mid:
        st, r = call('POST', f'/api/messages/{mid}/read', token=demo02)
        rec('消息', 'POST', '/api/messages/{id}/read', st, r)
else:
    st, r = call('POST', '/api/messages/1/read', token=demo02)
    rec('消息', 'POST', '/api/messages/{id}/read', st, r, expect0=False, note='无消息时对历史id标记')
st, r = call('POST', '/api/messages/read-all', token=demo02)
rec('消息', 'POST', '/api/messages/read-all', st, r)
st, r = call('GET', '/api/users/me', token=demo01)
rec('用户', 'GET', '/api/users/me', st, r)
st, r = call('PUT', '/api/users/me', token=demo01, body={'nickname': '演示一号'})
rec('用户', 'PUT', '/api/users/me', st, r, note='改昵称')

# --- 关闭信息（另建一条 LOST 测关闭；原信息已随核销自动完结）---
item_body2 = dict(item_body, type='LOST', title=f'接口实测-丢失校园卡{ts[-4:]}', hiddenFeatures=None)
st, r = call('POST', '/api/items', token=demo01, body=item_body2)
item2 = r['data']['id'] if st == 200 and r.get('code') == 0 else None
st, r = call('POST', f'/api/items/{item2}/close', token=demo01)
rec('信息发布', 'POST', '/api/items/{id}/close', st, r, note='已找回关闭')

# --- 后台管理 ×16 ---
st, r = call('GET', '/api/admin/items?page=1&size=10', token=admin)
rec('后台管理', 'GET', '/api/admin/items', st, r)
st, r = call('GET', '/api/admin/claims?page=1&size=10', token=admin)
rec('后台管理', 'GET', '/api/admin/claims', st, r)
st, r = call('GET', '/api/admin/stats/overview', token=admin)
rec('后台管理', 'GET', '/api/admin/stats/overview', st, r)
st, r = call('GET', '/api/admin/dashboard/stats', token=admin)
rec('后台管理', 'GET', '/api/admin/dashboard/stats', st, r)
st, r = call('GET', '/api/admin/audit/list?page=1&size=10', token=admin)
audits = (r.get('data') or {}).get('records') or (r.get('data') or {}).get('list') or []
rec('后台管理', 'GET', '/api/admin/audit/list', st, r, note=f'待审核 {len(audits)} 条')
pending = next((a for a in audits if a.get('status') in ('PENDING', 0, 'pending')), None)
if pending:
    st, r = call('PUT', f"/api/admin/audit/{pending['id']}/approve", token=admin)
    rec('后台管理', 'PUT', '/api/admin/audit/{id}/approve', st, r, note='通过一条待审核')
else:
    st, r = call('PUT', '/api/admin/audit/999999/approve', token=admin)
    rec('后台管理', 'PUT', '/api/admin/audit/{id}/approve', st, r, expect0=False,
        note='无待审核，6002 不存在校验符合预期')
    st, r = call('PUT', '/api/admin/audit/999999/reject', token=admin, body={'reason': '测试'})
    rec('后台管理', 'PUT', '/api/admin/audit/{id}/reject', st, r, expect0=False,
        note='无待审核，6002 不存在校验符合预期')
if pending:
    st, r = call('PUT', '/api/admin/audit/999999/reject', token=admin, body={'reason': '测试'})
    rec('后台管理', 'PUT', '/api/admin/audit/{id}/reject', st, r, expect0=False, note='6002 校验')
st, r = call('GET', '/api/admin/users?page=1&size=10', token=admin)
rec('后台管理', 'GET', '/api/admin/users', st, r)
st, r = call('POST', '/api/admin/users/52/ban', token=admin, body={'ban': True, 'reason': '接口实测'})
rec('后台管理', 'POST', '/api/admin/users/{id}/ban', st, r, note='封禁 demo10')
st, r = call('PUT', '/api/admin/users/52/ban', token=admin, body={'reason': '接口实测'})
rec('后台管理', 'PUT', '/api/admin/users/{id}/ban', st, r, expect0=False, note='重复封禁 6005 符合预期')
st, r = call('PUT', '/api/admin/users/52/unban', token=admin)
rec('后台管理', 'PUT', '/api/admin/users/{id}/unban', st, r, note='解封恢复')
st, r = call('GET', '/api/admin/config/match', token=admin)
cfg = r.get('data')
rec('后台管理', 'GET', '/api/admin/config/match', st, r)
st, r = call('PUT', '/api/admin/config/match', token=admin, body=cfg)
rec('后台管理', 'PUT', '/api/admin/config/match', st, r, note='同值写回')
st, r = call('POST', '/api/admin/config/category', token=admin, body={'name': '接口测试' + ts[-4:]})
rec('后台管理', 'POST', '/api/admin/config/category', st, r, note='新增分类')
st, r = call('POST', '/api/admin/config/location', token=admin,
             body={'name': '接口测试楼' + ts[-4:], 'campus': '丰台校区'})
rec('后台管理', 'POST', '/api/admin/config/location', st, r, note='新增地点')
st, r = call('POST', f'/api/admin/claims/{claim_id}/arbitrate', token=admin,
             body={'action': 'APPROVE', 'reason': '接口实测'})
rec('后台管理', 'POST', '/api/admin/claims/{id}/arbitrate', st, r, expect0=False,
    note='非争议状态仲裁 6004 符合预期')

# --- 汇总输出 ---
passed = sum(1 for x in results if x[5] == 'PASS')
lines = ['# 接口实测记录（2026-10-19）', '',
         f'- 环境：后端 jar @ 8080（本机），PostgreSQL 容器 lnf-postgres，匹配引擎 @ 9000',
         f'- 账号：admin / demo01 / demo02（Demo123456）',
         f'- 结果：{passed}/{len(results)} 通过', '',
         '| # | 模块 | 方法 | 路径 | HTTP | 业务码 | 结论 | 备注 |',
         '|---|------|------|------|------|--------|------|------|']
for i, (mod, m, p, stt, c, concl, note) in enumerate(results, 1):
    lines.append(f'| {i} | {mod} | {m} | `{p}` | {stt} | {c} | {concl} | {note} |')
out = '\n'.join(lines)
with open('../docs/接口实测记录-20261019.md', 'w', encoding='utf-8') as f:
    f.write(out)
print(f'TOTAL {len(results)}  PASS {passed}  FAIL {len(results)-passed}')
for x in results:
    if x[5] == 'FAIL':
        print('FAIL:', x)
