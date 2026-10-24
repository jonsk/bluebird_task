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
                                             'visibility': 'PRIVATE', 'tagIds': [FX['tag']['id']]})

    run('1', 'A', 'owner 创建日志', 200, 0, a1,
        lambda r: need(r, 0, lambda x: all(k in x.data for k in
                                           ('id', 'version', 'contentSummary', 'ownerTags', 'attachments', 'relations'))))

    run('1a', 'A', '未登录创建', 401, None, lambda: api.req('POST', '/notes', None,
                                                       {'content': 'x', 'logDate': TODAY.isoformat()}))

    def a1b():
        return api.req('POST', '/notes', OUT, {'content': 'MTX-A1b outsider 自建', 'logDate': TODAY.isoformat(),
                                               'visibility': 'PRIVATE', 'tagIds': []})

    run('1b', 'A', '任意登录用户创建自己的日志', 200, 0, a1b, lambda r: need(r, 0, lambda x: x.data.get('id') is not None))

    run('1c', 'A', 'content 超 20000', 200, 50002,
        lambda: api.req('POST', '/notes', Z, {'content': 'x' * 20001, 'logDate': TODAY.isoformat(),
                                              'visibility': 'PRIVATE', 'tagIds': []}),
        lambda r: need(r, 50002))

    run('1d', 'A', 'logDate=2026-13-15', 400, None,
        lambda: api.req('POST', '/notes', Z, {'content': 'x', 'logDate': '2026-13-15',
                                              'visibility': 'PRIVATE', 'tagIds': []}))
    run('1d-1', 'A', 'logDate=abc', 400, None,
        lambda: api.req('POST', '/notes', Z, {'content': 'x', 'logDate': 'abc',
                                              'visibility': 'PRIVATE', 'tagIds': []}))
    run('1d-2', 'A', 'logDate 未来超 7 天', 200, 50012,
        lambda: api.req('POST', '/notes', Z, {'content': 'x', 'logDate': (TODAY + timedelta(days=30)).isoformat(),
                                              'visibility': 'PRIVATE', 'tagIds': []}),
        lambda r: need(r, 50012))

    def a1e():
        other_tag = api.req('POST', '/tags', L, {'name': 'MTX-L' + uuid.uuid4().hex[:4]}).data
        tag_id = other_tag if isinstance(other_tag, int) else other_tag['id']
        return api.req('POST', '/notes', Z, {'content': 'MTX-A1e', 'logDate': TODAY.isoformat(),
                                             'visibility': 'PRIVATE', 'tagIds': [tag_id]})

    run('1e', 'A', '引用他人标签', 200, 50003, a1e, lambda r: need(r, 50003))
    def a2():
        api.clear_dept(NODEPT)
        return api.req('POST', '/notes', NODEPT, {'content': 'MTX-A2', 'logDate': TODAY.isoformat(),
                                                  'visibility': 'INTERNAL', 'tagIds': []})

    run('2', 'A', '无部门用户 INTERNAL', 200, 50017, a2, lambda r: need(r, 50017))

    def a4():
        n = api.new_note(Z)
        return api.req('PUT', f"/notes/{n['id']}", Z, {'content': '改', 'version': n['version'] - 1,
                                                       'logDate': n['logDate'], 'visibility': n['visibility'],
                                                       'tagIds': []})

    run('4', 'A', 'version 过期冲突', 200, 10006, a4, lambda r: need(r, 10006))

    def a4a():
        n = api.new_note(Z)
        r = api.req('PUT', f"/notes/{n['id']}", Z, {'content': 'MTX-4a 新内容', 'version': n['version'],
                                                    'logDate': n['logDate'], 'visibility': n['visibility'],
                                                    'tagIds': []})
        assert r.code == 0 and r.data['version'] == n['version'] + 1, f"version 未递增: {r.data.get('version')}"
        return r

    run('4a', 'A', 'version 正确更新并递增', 200, 0, a4a)

    def a5():
        n = api.new_note(Z)
        return api.req('PUT', f"/notes/{n['id']}", L, {'visibility': 'PUBLIC', 'version': n['version'],
                                                       'content': n['contentSummary'], 'logDate': n['logDate'],
                                                       'tagIds': []})

    run('5', 'A', 'EDITOR 改 visibility 被拒', 200, 50013, a5, lambda r: need(r, 50013))

    def a5a():
        n = api.new_note(Z, visibility='INTERNAL')
        return api.req('PUT', f"/notes/{n['id']}", 'mtxviewer', {'content': 'VIEWER 越权', 'version': n['version'],
                                                                 'logDate': n['logDate'], 'visibility': 'INTERNAL',
                                                                 'tagIds': []})

    run('5a', 'A', 'VIEWER 编辑被拒', 403, None, a5a)
    run('6', 'A', '不可见用户读详情', 404, None, lambda: api.req('GET', f"/notes/{FX['z_private']['id']}", OUT))
    run('6a', 'A', 'owner 读详情', 200, 0, lambda: api.req('GET', f"/notes/{FX['z_note']['id']}", Z),
        lambda r: need(r, 0, lambda x: all(k in x.data for k in ('content', 'ownerTags', 'canEdit', 'canManage'))))

    def a7():
        n = api.new_note(Z)
        return api.req('DELETE', f"/notes/{n['id']}", L)

    run('7', 'A', 'EDITOR 删除被拒', 403, None, a7)

    def a7a():
        n = api.new_note(Z)
        r = api.req('DELETE', f"/notes/{n['id']}", Z)
        # 附加断言：grant 不级联清理，lisi 仍能读 zhangli 的其他日志
        g = api.req('GET', '/note-grants?tab=granted', Z)
        still = [x for x in (g.data or []) if x.get('status') == 'APPROVED' and int(x.get('grantorId')) == int(U[Z])]
        assert still, '删除单条 note 后 grant 被误级联清理'
        other = api.req('GET', f"/notes/{FX['z_note']['id']}", L)
        assert other.status == 200, f'grantee 失去访问: {other.status}'
        return r

    run('7a', 'A', 'owner 软删除（grant 不级联）', 200, 0, a7a)
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
    run('11', 'B', 'mine+archived', 200, 0, lambda: api.req('GET', '/notes?scope=mine&archived=true', Z))
    run('12', 'B', 'shared+archived 忽略归档', 200, 0, lambda: api.req('GET', '/notes?scope=shared&archived=true', W))
    run('12b', 'B', 'internal 同部门可见', 200, 0, lambda: api.req('GET', '/notes?scope=internal&size=100', L),
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

    run('13a', 'C', 'EDITOR 置顶被拒', 403, None, c13a)
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

    run('14a', 'C', 'EDITOR 取消置顶被拒', 403, None, c14a)

    def c15():
        n = api.new_note(Z)
        r = api.req('POST', f"/notes/{n['id']}/archive", Z)
        d = api.req('GET', f"/notes/{n['id']}", Z)
        assert d.data['archived'] is True, 'archived 未置 true'
        return r

    run('15', 'C', '归档', 200, 0, c15)

    def c15a():
        n = api.new_note(Z)
        return api.req('POST', f"/notes/{n['id']}/archive", L)

    run('15a', 'C', 'EDITOR 归档被拒', 403, None, c15a)

    def c16():
        n = api.new_note(Z)
        api.req('POST', f"/notes/{n['id']}/archive", Z)
        r = api.req('POST', f"/notes/{n['id']}/unarchive", Z)
        d = api.req('GET', f"/notes/{n['id']}", Z)
        assert d.data['archived'] is False, 'archived 未置 false'
        return r

    run('16', 'C', '取消归档', 200, 0, c16)

    def c16a():
        n = api.new_note(Z)
        api.req('POST', f"/notes/{n['id']}/archive", Z)
        return api.req('POST', f"/notes/{n['id']}/unarchive", L)

    # 实现语义：归档后非 owner 不可见 → 404（不泄露存在性），非 403
    run('16a', 'C', 'EDITOR 取消归档（归档即不可见）', 404, None, c16a)
    return locals()
