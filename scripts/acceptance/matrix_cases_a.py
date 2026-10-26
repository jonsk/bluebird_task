# -*- coding: utf-8 -*-
"""§9 矩阵用例定义（A–L）。由 matrix_runner.py import 执行。"""


def build(api, U, FX, run, TODAY):
    import uuid
    from datetime import timedelta

    Z, L, W, OUT, ADM, NODEPT = 'zhangli', 'lisi', 'wangwu', 'outsider', 'noteadmin', 'nodept'

    def need(resp, code, cond=None, msg=''):
        assert resp.code == code, f'expect code {code}, got {resp.code} ({resp.body.get("message")}) {msg}'
        if cond:
            assert cond(resp), f'field check failed {msg}'

    # ---------------- A. Note CRUD ----------------
    def a1():
        return api.req('POST', '/notes', Z, {'content': 'MTX-A1 创建', 'logDate': TODAY.isoformat(),
                                             'tagIds': [FX['tag']['id']]})

    run('1', 'A', 'owner 创建日志', 200, 0, a1,
        lambda r: need(r, 0, lambda x: all(k in x.data for k in
                                           ('id', 'version', 'contentSummary', 'ownerTags', 'attachments', 'relations'))))

    run('1a', 'A', '未登录创建', 401, None, lambda: api.req('POST', '/notes', None,
                                                       {'content': 'x', 'logDate': TODAY.isoformat()}))

    def a1b():
        return api.req('POST', '/notes', OUT, {'content': 'MTX-A1b outsider 自建', 'logDate': TODAY.isoformat(),
                                               'tagIds': []})

    run('1b', 'A', '任意登录用户创建自己的日志', 200, 0, a1b, lambda r: need(r, 0, lambda x: x.data.get('id') is not None))

    run('1c', 'A', 'content 超 20000', 200, 50002,
        lambda: api.req('POST', '/notes', Z, {'content': 'x' * 20001, 'logDate': TODAY.isoformat(),
                                              'tagIds': []}),
        lambda r: need(r, 50002))

    run('1d', 'A', 'logDate=2026-13-15', 400, None,
        lambda: api.req('POST', '/notes', Z, {'content': 'x', 'logDate': '2026-13-15',
                                              'tagIds': []}))
    run('1d-1', 'A', 'logDate=abc', 400, None,
        lambda: api.req('POST', '/notes', Z, {'content': 'x', 'logDate': 'abc',
                                              'tagIds': []}))
    run('1d-2', 'A', 'logDate 未来超 7 天', 200, 50012,
        lambda: api.req('POST', '/notes', Z, {'content': 'x', 'logDate': (TODAY + timedelta(days=30)).isoformat(),
                                              'tagIds': []}),
        lambda r: need(r, 50012))

    def a1e():
        other_tag = api.req('POST', '/tags', L, {'name': 'MTX-L' + uuid.uuid4().hex[:4]}).data
        tag_id = other_tag if isinstance(other_tag, int) else other_tag['id']
        return api.req('POST', '/notes', Z, {'content': 'MTX-A1e', 'logDate': TODAY.isoformat(),
                                             'tagIds': [tag_id]})

    run('1e', 'A', '引用他人标签', 200, 50003, a1e, lambda r: need(r, 50003))
    def a2():
        api.clear_dept(NODEPT)
        return api.req('POST', '/notes', NODEPT, {'content': 'MTX-A2', 'logDate': TODAY.isoformat(),
                                                  'tagIds': []})

    # 减法后 visibility 已下线：无部门用户也能创建；未知字段被忽略，详情不再回传 visibility/archived
    run('2', 'A', 'visibility 字段已废弃（传了也被忽略）', 200, 0, a2,
        lambda r: need(r, 0, lambda x: 'visibility' not in x.data and 'archived' not in x.data))

    def a4():
        n = api.new_note(Z)
        return api.req('PUT', f"/notes/{n['id']}", Z, {'content': '改', 'version': n['version'] - 1,
                                                       'logDate': n['logDate'],
                                                       'tagIds': []})

    run('4', 'A', 'version 过期冲突', 200, 10006, a4, lambda r: need(r, 10006))

    def a4a():
        n = api.new_note(Z)
        r = api.req('PUT', f"/notes/{n['id']}", Z, {'content': 'MTX-4a 新内容', 'version': n['version'],
                                                    'logDate': n['logDate'],
                                                    'tagIds': []})
        assert r.code == 0 and r.data['version'] == n['version'] + 1, f"version 未递增: {r.data.get('version')}"
        return r

    run('4a', 'A', 'version 正确更新并递增', 200, 0, a4a)

    def a5():
        n = api.new_note(Z)
        return api.req('PUT', f"/notes/{n['id']}", Z, {'version': n['version'],
                                                      'content': n['contentSummary'], 'logDate': n['logDate'],
                                                      'tagIds': [999999]})

    run('5', 'A', 'owner 改 tagIds 为不存在标签', 200, 50003, a5, lambda r: need(r, 50003))

    def a5a():
        n = api.new_note(Z)
        return api.req('PUT', f"/notes/{n['id']}", 'mtxviewer', {'content': 'VIEWER 越权', 'version': n['version'],
                                                                 'logDate': n['logDate'],
                                                                 'tagIds': []})

    run('5a', 'A', '无关系用户编辑 → 404（授权已下线）', 404, None, a5a)
    run('6', 'A', '不可见用户读详情', 404, None, lambda: api.req('GET', f"/notes/{FX['z_private']['id']}", OUT))
    run('6a', 'A', 'owner 读详情', 200, 0, lambda: api.req('GET', f"/notes/{FX['z_note']['id']}", Z),
        lambda r: need(r, 0, lambda x: all(k in x.data for k in ('content', 'ownerTags', 'canEdit', 'canManage'))))

    def a7():
        n = api.new_note(Z)
        return api.req('DELETE', f"/notes/{n['id']}", L)

    run('7', 'A', '无关系用户删除 → 404', 404, None, a7)

    def a7a():
        n = api.new_note(Z)
        r = api.req('DELETE', f"/notes/{n['id']}", Z)
        # 附加断言：授权能力已下线，他人始终读不到
        other = api.req('GET', f"/notes/{FX['z_note']['id']}", L)
        assert other.status == 404, f'他人不应可见: {other.status}'
        return r

    run('7a', 'A', 'owner 软删除（他人始终不可见）', 200, 0, a7a)
    run('7b', 'A', '不可见用户删除', 404, None,
        lambda: api.req('DELETE', f"/notes/{FX['z_private']['id']}", OUT))

    # ---------------- B. 列表与 scope ----------------
    run('8', 'B', 'scope=all 非 ADMIN', 403, None, lambda: api.req('GET', '/notes?scope=all', OUT))
    run('8a', 'B', 'scope=all ADMIN', 200, 0, lambda: api.req('GET', '/notes?scope=all&size=100', ADM),
        lambda r: need(r, 0, lambda x: x.data.get('total', 0) >= 1))
    run('9', 'B', 'date 与 start/end 同时传', 400, None,
        lambda: api.req('GET', '/notes?scope=mine&date=2026-10-01&start=2026-09-01&end=2026-09-30', Z))

    def b10():
        r = api.req('GET', '/notes?scope=mine&size=200', Z)
        assert r.code == 0 and r.data['size'] == 100, f"size 未截断到 100: {r.data.get('size')}"
        return r

    run('10', 'B', 'size>100 截断', 200, 0, b10)
    # 减法后 explore / internal 两个 scope 已下线，archive 查询参数被忽略
    run('11', 'B', 'scope=explore 已下线', 400, None, lambda: api.req('GET', '/notes?scope=explore', Z))
    run('12', 'B', 'scope=internal 已下线', 400, None, lambda: api.req('GET', '/notes?scope=internal', Z))
    run('12b', 'B', 'scope=shared 已下线（授权摘除）', 400, None,
        lambda: api.req('GET', '/notes?scope=shared&size=100', L))
    run('12c', 'B', 'archived 参数已废弃（被忽略）', 200, 0,
        lambda: api.req('GET', '/notes?scope=mine&archived=true', Z),
        lambda r: need(r, 0, lambda x: isinstance(x.data.get('list'), list)))
    run('12d', 'B', 'tagName 不存在', 200, 0, lambda: api.req('GET', '/notes?scope=mine&tagName=不存在的标签xyz', Z),
        lambda r: need(r, 0, lambda x: x.data.get('total') == 0))
    run('12e', 'B', 'tagId 不存在', 200, 0, lambda: api.req('GET', '/notes?scope=mine&tagId=999999', Z),
        lambda r: need(r, 0, lambda x: x.data.get('total') == 0))
    run('12f', 'B', 'tagId 非数值', 400, None, lambda: api.req('GET', '/notes?scope=mine&tagId=abc', Z))
    run('12g', 'B', 'keyword 模糊匹配', 200, 0, lambda: api.req('GET', '/notes?scope=mine&keyword=MTX', Z),
        lambda r: need(r, 0, lambda x: x.data.get('total', 0) >= 1))

    # ---------------- C. pin / archive ----------------
    def c13():
        n = api.new_note(Z)
        r = api.req('POST', f"/notes/{n['id']}/pin", Z)
        d = api.req('GET', f"/notes/{n['id']}", Z)
        assert d.data['pinned'] is True, 'pinned 未置 true'
        return r

    run('13', 'C', 'owner 置顶', 200, 0, c13)

    def c13a():
        n = api.new_note(Z)
        return api.req('POST', f"/notes/{n['id']}/pin", L)

    run('13a', 'C', '他人置顶 → 404（授权已下线）', 404, None, c13a)
    run('13b', 'C', '不可见用户置顶', 404, None,
        lambda: api.req('POST', f"/notes/{FX['z_private']['id']}/pin", OUT))

    def c14():
        n = api.new_note(Z)
        api.req('POST', f"/notes/{n['id']}/pin", Z)
        r = api.req('DELETE', f"/notes/{n['id']}/pin", Z)
        d = api.req('GET', f"/notes/{n['id']}", Z)
        assert d.data['pinned'] is False, 'pinned 未置 false'
        return r

    run('14', 'C', '取消置顶', 200, 0, c14)

    def c14a():
        n = api.new_note(Z)
        api.req('POST', f"/notes/{n['id']}/pin", Z)
        return api.req('DELETE', f"/notes/{n['id']}/pin", L)

    run('14a', 'C', '他人取消置顶 → 404', 404, None, c14a)

    # 减法：归档能力整体下线 → 两个端点都不再映射（统一 200 + 10004「资源不存在」）
    run('15', 'C', 'archive 端点已下线', 200, 10004,
        lambda: api.req('POST', f"/notes/{api.new_note(Z)['id']}/archive", Z), lambda r: need(r, 10004))
    run('15a', 'C', 'unarchive 端点已下线', 200, 10004,
        lambda: api.req('POST', f"/notes/{api.new_note(Z)['id']}/unarchive", Z), lambda r: need(r, 10004))
    run('16', 'C', '详情不含 archived 字段', 200, 0,
        lambda: api.req('GET', f"/notes/{api.new_note(Z)['id']}", Z),
        lambda r: need(r, 0, lambda x: 'archived' not in x.data))
    run('16a', 'C', '列表不含 visibility / archived', 200, 0,
        lambda: api.req('GET', '/notes?scope=mine', Z),
        lambda r: need(r, 0, lambda x: all('visibility' not in i and 'archived' not in i for i in x.data['list'])))
    return locals()
