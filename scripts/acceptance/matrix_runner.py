# -*- coding: utf-8 -*-
"""§9 接口验收矩阵 自动执行器（真实后端）。

用法（在本目录执行）：
  py matrix_main.py              # 主跑：需后端 MANAGER_CAN_READ_SUBORDINATE=true
  py matrix_toggle.py            # 开关关闭场景：需后端以 env=false 重启

环境变量：
  BLUEBIRD_API   后端基址，默认 http://localhost:8080/api/v1
  BLUEBIRD_DB    SQLite 路径（用于清理测试授权行），默认 ../../backend/data/bluebird.db
  MATRIX_OUT     结果 JSON 输出目录，默认 ./matrix-out
  MATRIX_PWD     测试账号统一密码，默认 Test@1234

前置：先执行 seed_accounts.py 建立验收账号（见 README.md）。
"""
import io
import json
import os
import sys
import traceback
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import date, timedelta

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

HERE = os.path.dirname(os.path.abspath(__file__))
BASE = os.environ.get('BLUEBIRD_API', 'http://localhost:8080/api/v1')
DB_PATH = os.environ.get('BLUEBIRD_DB', os.path.normpath(os.path.join(HERE, '..', '..', 'backend', 'data', 'bluebird.db')))
OUT_DIR = os.environ.get('MATRIX_OUT', os.path.join(HERE, 'matrix-out'))
PWD = os.environ.get('MATRIX_PWD', 'Test@1234')
PHASE = sys.argv[1] if len(sys.argv) > 1 else 'main'

U = {
    'zhangli': '2106100000000000001',
    'lisi': '2106100000000000002',
    'wangwu': '2106100000000000003',
    'auditor': '2106100000000000004',
    'outsider': '2106100000000000005',
    'useradmin': '2106100000000000006',
    'noteadmin': '2106100000000000007',
    'nodept': '2106100000000000008',
    'mtxviewer': '2106100000000000009',
    'mtxsub1': '2106100000000000010',
    'mtxsub2': '2106100000000000011',
}

TODAY = date.today()
results = []


class Resp:
    def __init__(self, status, body, raw=None):
        self.status = status
        self.body = body if isinstance(body, dict) else {}
        self.raw = raw

    @property
    def code(self):
        return self.body.get('code')

    @property
    def data(self):
        return self.body.get('data')

    def __repr__(self):
        return f'{self.status}/{self.code}'


class Api:
    def __init__(self):
        self.tokens = {}

    def token(self, user):
        if user not in self.tokens:
            r = self.req('POST', '/auth/login', None, {'username': user, 'password': PWD})
            assert r.code == 0, f'login {user} failed: {r.body}'
            self.tokens[user] = r.data['accessToken']
        return self.tokens[user]

    def req(self, method, path, user=None, body=None, form=None):
        url = BASE + urllib.parse.quote(path, safe='/?=&%,:')
        data = None
        headers = {}
        if form is not None:
            boundary = '----mtx' + uuid.uuid4().hex
            parts = []
            for k, v in form.items():
                if isinstance(v, tuple):
                    fname, content, ctype = v
                    parts.append(
                        f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"; filename="{fname}"\r\n'
                        f'Content-Type: {ctype}\r\n\r\n'.encode() + content + b'\r\n'
                    )
                else:
                    parts.append(
                        f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode()
                    )
            data = b''.join(parts) + f'--{boundary}--\r\n'.encode()
            headers['Content-Type'] = f'multipart/form-data; boundary={boundary}'
        elif body is not None:
            data = json.dumps(body).encode()
            headers['Content-Type'] = 'application/json'
        req = urllib.request.Request(url, data=data, method=method, headers=headers)
        if user:
            req.add_header('Authorization', 'Bearer ' + self.token(user))
        try:
            with urllib.request.urlopen(req) as r:
                payload = r.read()
                try:
                    return Resp(r.status, json.loads(payload), payload)
                except Exception:
                    return Resp(r.status, {}, payload)
        except urllib.error.HTTPError as e:
            payload = e.read()
            try:
                return Resp(e.code, json.loads(payload), payload)
            except Exception:
                return Resp(e.code, {}, payload)

    # ---- 便捷 ----
    def clear_dept(self, user):
        """把账号置为「无部门」（50017 场景 fixture）：系统会在登录/同步时回填默认部门，故用例前重置。"""
        import sqlite3

        uid = int(U[user])
        con = sqlite3.connect(DB_PATH, timeout=10)
        con.execute('UPDATE sys_user SET dept_id = NULL WHERE id = ?', (uid,))
        con.execute('DELETE FROM sys_user_department WHERE user_id = ?', (uid,))
        con.commit()
        con.close()
        self.tokens.pop(user, None)

    def new_note(self, user='zhangli', content=None, visibility='PRIVATE', log_date=None, tags=None):
        r = self.req(
            'POST',
            '/notes',
            user,
            {
                'content': content or f'MTX-{uuid.uuid4().hex[:8]} 验收矩阵用例',
                'logDate': log_date or TODAY.isoformat(),
                'visibility': visibility,
                'tagIds': tags or [],
            },
        )
        assert r.code == 0, f'new_note failed: {r.body}'
        return r.data


api = Api()


def record(cid, section, desc, expect_http, expect_code, resp, checks, extra=None):
    ok_http = resp.status == expect_http
    if expect_code is None:
        ok_code = True
    else:
        ok_code = resp.code == expect_code
    ok_extra, details = True, []
    if checks:
        try:
            checks(resp)
        except AssertionError as e:
            ok_extra = False
            details.append(str(e))
    status = 'PASS' if (ok_http and ok_code and ok_extra) else 'FAIL'
    results.append(
        {
            'id': cid,
            'section': section,
            'desc': desc,
            'expect': f'{expect_http}/{expect_code if expect_code is not None else "-"}',
            'actual': f'{resp.status}/{resp.code if resp.code is not None else "-"}',
            'status': status,
            'detail': '; '.join(details),
            'extra': extra or {},
        }
    )
    mark = 'ok  ' if status == 'PASS' else 'FAIL'
    print(f'{mark} {cid:<5} {section} {resp.status}/{resp.code}  expect {expect_http}/{expect_code}  {desc[:46]}')


def run(cid, section, desc, expect_http, expect_code, fn, checks=None):
    try:
        resp = fn()
    except AssertionError as e:
        results.append(
            {
                'id': cid,
                'section': section,
                'desc': desc,
                'expect': f'{expect_http}/{expect_code}',
                'actual': 'setup-error',
                'status': 'FAIL',
                'detail': str(e),
                'extra': {},
            }
        )
        print(f'FAIL {cid:<5} {section} setup-error: {str(e)[:80]}')
        return None
    except Exception:
        results.append(
            {
                'id': cid,
                'section': section,
                'desc': desc,
                'expect': f'{expect_http}/{expect_code}',
                'actual': 'exception',
                'status': 'FAIL',
                'detail': traceback.format_exc(limit=1),
                'extra': {},
            }
        )
        print(f'FAIL {cid:<5} {section} exception')
        return None
    record(cid, section, desc, expect_http, expect_code, resp, checks)
    return resp


# ============ 共享 fixture ============
def reset_test_grants():
    """清理测试账号的授权行，保证 PENDING/升级用例可重复执行。"""
    import sqlite3

    ids = [int(U[k]) for k in ('mtxviewer', 'mtxsub1', 'mtxsub2')]
    q = ','.join('?' * len(ids))
    con = sqlite3.connect(DB_PATH, timeout=10)
    con.execute(f'DELETE FROM note_user_grant WHERE grantor_id IN ({q}) OR grantee_id IN ({q})', ids + ids)
    con.commit()
    con.close()


def setup():
    fx = {}
    tag_raw = api.req('POST', '/tags', 'zhangli', {'name': 'MTX-T' + uuid.uuid4().hex[:4]}).data
    fx['tag'] = {'id': tag_raw if isinstance(tag_raw, int) else (tag_raw or {}).get('id')}
    fx['z_note'] = api.new_note('zhangli', visibility='INTERNAL')
    fx['z_private'] = api.new_note('zhangli', visibility='PRIVATE')
    fx['z_public'] = api.new_note('zhangli', visibility='PUBLIC')
    fx['l_note'] = api.new_note('lisi', visibility='INTERNAL')

    def grant(body, creator='zhangli'):
        r = api.req('POST', '/note-grants', creator, body)
        if r.code == 0:
            return r.data
        # 已存在同组合（50007）时复用现有授权，保证后续用例有 fixture
        lst = api.req('GET', '/note-grants?tab=granted', creator)
        for g in lst.data or []:
            if (
                g.get('grantorId') == int(U[creator])
                and (body.get('granteeId') is None or g.get('granteeId') == body.get('granteeId'))
                and g.get('mode') == body.get('mode')
                and g.get('status') in ('PENDING', 'APPROVED')
            ):
                return g
        print(f'  [setup] grant failed {body} -> {r.code} {r.body.get("message")}')
        return None

    # EDITOR 授权（zhangli 的日志 → lisi 可编辑）
    fx['g_editor'] = grant({'mode': 'OWNER', 'granteeId': int(U['lisi']), 'role': 'EDITOR'})
    # VIEWER 只读授权（→ mtxviewer 只读，供 5a / 字段级受限用例）
    fx['g_viewer'] = grant({'mode': 'OWNER', 'granteeId': int(U['mtxviewer']), 'role': 'VIEWER'})

    # SUPERIOR 待确认（creator=zhangli，grantor=mtxsub1/2 轮流用于不同流程分支）
    p1 = api.req('POST', '/note-grants', 'zhangli', {'mode': 'SUPERIOR', 'grantorId': int(U['mtxsub1']), 'role': 'VIEWER'})
    p2 = api.req('POST', '/note-grants', 'zhangli', {'mode': 'SUPERIOR', 'grantorId': int(U['mtxsub2']), 'role': 'VIEWER'})
    fx['g_pending'] = p1.data if p1.code == 0 else None
    fx['g_pending2'] = p2.data if p2.code == 0 else None
    if fx['g_pending'] is None or fx['g_pending2'] is None:
        print(f"  [setup] pending grant failed: p1={p1.code} {p1.body.get('message')} p2={p2.code} {p2.body.get('message')}")
    # SUPERIOR 已生效（复用 seed 的 lisi→zhangli 已批准授权）
    approved = None
    for g in api.req('GET', '/note-grants?tab=created', 'zhangli').data or []:
        if g.get('mode') == 'SUPERIOR' and g.get('status') == 'APPROVED':
            approved = g
            break
    fx['g_sup_approved'] = approved
    # 附件
    up = api.req('POST', '/files', 'zhangli', form={'file': ('mtx.txt', b'matrix attachment', 'text/plain')})
    fx['file'] = up.data
    # 任务（zhangli 可写）
    tasks = api.req('GET', '/tasks?scope=all&size=5', 'zhangli')
    fx['tasks'] = (tasks.data or {}).get('list') or []
    return fx


reset_test_grants()
FX = setup()
print('fixture ready:', {k: (v if isinstance(v, (int, str)) else type(v).__name__) for k, v in FX.items()})
print('=' * 100)
