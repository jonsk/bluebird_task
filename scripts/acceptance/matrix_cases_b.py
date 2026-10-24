# -*- coding: utf-8 -*-
"""§9 矩阵用例定义（D–L）。"""


def build(api, U, FX, run, TODAY):
    import uuid

    Z, L, W, OUT, ADM, NODEPT = 'zhangli', 'lisi', 'wangwu', 'outsider', 'noteadmin', 'nodept'

    def need(resp, code, cond=None, msg=''):
        assert resp.code == code, f'expect code {code}, got {resp.code} ({resp.body.get("message")}) {msg}'
        if cond:
            assert cond(resp), f'field check failed {msg}'

    class _Shim:
        def __init__(self, data):
            self.code = 0
            self.data = data

    def new_pending():
        """取/建一条 SUPERIOR 待确认（creator=zhangli，grantor=wangwu）。"""
        for g in api.req('GET', '/note-grants?tab=created', Z).data or []:
            if g.get('mode') == 'SUPERIOR' and g.get('grantorId') == int(U[W]) and g.get('status') == 'PENDING':
                return _Shim(g)
        return api.req('POST', '/note-grants', Z, {'mode': 'SUPERIOR', 'grantorId': int(U[W]), 'role': 'VIEWER'})

    def grant_of(grantee, role='VIEWER'):
        """确保存在 OWNER 授权（不存在则建），返回 grant dict。"""
        for g in api.req('GET', '/note-grants?tab=granted', Z).data or []:
            if g.get('mode') == 'OWNER' and g.get('granteeId') == int(U[grantee]) and g.get('status') == 'APPROVED':
                return g
        r = api.req('POST', '/note-grants', Z, {'mode': 'OWNER', 'granteeId': int(U[grantee]), 'role': role})
        assert r.code == 0, r.body
        return r.data

    def new_file(user=Z, name=None, content=b'matrix-file'):
        r = api.req('POST', '/files', user, form={'file': (name or f'mtx-{uuid.uuid4().hex[:6]}.txt', content, 'text/plain')})
        assert r.code == 0, f'upload failed {r.body}'
        return r.data

    # ---------------- D. 授权 ----------------
    run('17', 'D', 'OWNER 自授权', 200, 50008,
        lambda: api.req('POST', '/note-grants', Z, {'mode': 'OWNER', 'granteeId': int(U[Z]), 'role': 'VIEWER'}),
        lambda r: need(r, 50008))
    run('18', 'D', 'SUPERIOR 带 granteeId', 400, None,
        lambda: api.req('POST', '/note-grants', Z, {'mode': 'SUPERIOR', 'granteeId': int(U[L]), 'role': 'VIEWER'}))
    run('20', 'D', '重复 active 授权', 200, 50007,
        lambda: api.req('POST', '/note-grants', Z, {'mode': 'OWNER', 'granteeId': int(U['mtxviewer']), 'role': 'VIEWER'}),
        lambda r: need(r, 50007))
    run('21', 'D', '非 grantor 确认', 403, None,
        lambda: api.req('POST', f"/note-grants/{FX['g_pending']['id']}/approve", L))
    run('22', 'D', '非 PENDING 确认', 200, 50014,
        lambda: api.req('POST', f"/note-grants/{FX['g_sup_approved']['id']}/approve", W),
        lambda r: need(r, 50014))

    def d22a():
        r = api.req('POST', f"/note-grants/{FX['g_pending']['id']}/reject", 'mtxsub1')
        assert r.data and r.data.get('status') == 'REJECTED', f"status={r.data.get('status') if r.data else None}"
        return r

    run('22a', 'D', 'grantor 拒绝 PENDING', 200, 0, d22a)
    run('22b', 'D', '非 PENDING 拒绝', 200, 50014,
        lambda: api.req('POST', f"/note-grants/{FX['g_sup_approved']['id']}/reject", W),
        lambda r: need(r, 50014))

    def d22c():
        g = FX['g_viewer'] or grant_of('mtxviewer', 'VIEWER')
        r = api.req('POST', f"/note-grants/{g['id']}/upgrade", Z)
        assert r.data and r.data.get('role') == 'EDITOR', f"role={r.data.get('role') if r.data else None} code={r.code}"
        return r

    run('22c', 'D', 'VIEWER 升级为 EDITOR', 200, 0, d22c)

    def d22d():
        return api.req('POST', f"/note-grants/{FX['g_pending2']['id']}/upgrade", 'mtxsub2')

    run('22d', 'D', '非 APPROVED 升级', 200, 50014, d22d, lambda r: need(r, 50014))
    run('23', 'D', 'created_by=me 撤 APPROVED（非 grantor）', 403, None,
        lambda: api.req('DELETE', f"/note-grants/{FX['g_sup_approved']['id']}", Z))

    def d23a():
        r = api.req('DELETE', f"/note-grants/{FX['g_pending2']['id']}", Z)
        return r

    run('23a', 'D', 'created_by=me 撤 PENDING', 200, 0, d23a)

    def d23b():
        g = grant_of('useradmin', 'VIEWER')
        return api.req('DELETE', f"/note-grants/{g['id']}", Z)

    run('23b', 'D', 'grantor 撤 APPROVED', 200, 0, d23b)

    def d23c():
        g = grant_of('wangwu', 'EDITOR')
        return api.req('DELETE', f"/note-grants/{g['id']}", ADM)

    run('23c', 'D', 'ADMIN 代撤销', 200, 0, d23c)
    run('24', 'D', 'tab=all 非 ADMIN', 403, None, lambda: api.req('GET', '/note-grants?tab=all', Z))

    def d24a():
        r = api.req('GET', '/note-grants?tab=created', Z)
        assert isinstance(r.data, list), 'created tab 应为数组'
        return r

    run('24a', 'D', 'tab=created', 200, 0, d24a)

    def d24b():
        r = api.req('GET', '/note-grants?tab=granted', Z)
        assert isinstance(r.data, list), 'granted tab 应为数组'
        return r

    run('24b', 'D', 'tab=granted', 200, 0, d24b)
    run('24c', 'D', 'tab=incoming', 200, 0, lambda: api.req('GET', '/note-grants?tab=incoming', Z))
    run('24d', 'D', 'tab=all ADMIN', 200, 0, lambda: api.req('GET', '/note-grants?tab=all', ADM),
        lambda r: need(r, 0, lambda x: isinstance(x.data, list)))

    # ---------------- E. 日历 ----------------
    run('25a', 'E', 'calendar scope=all 非 ADMIN', 403, None,
        lambda: api.req('GET', f'/notes/calendar?year={TODAY.year}&month={TODAY.month}&scope=all', Z))
    run('26', 'E', 'calendar year=abc', 400, None,
        lambda: api.req('GET', '/notes/calendar?year=abc&month=1&scope=mine', Z))

    def e26a():
        r = api.req('GET', f'/notes/calendar?year={TODAY.year}&month={TODAY.month}&scope=mine', Z)
        assert isinstance(r.data, list) and all('date' in d and 'count' in d for d in r.data), '结构应为 [{date,count}]'
        return r

    run('26a', 'E', 'calendar mine 聚合', 200, 0, e26a)

    # ---------------- F. Note 附件 ----------------
    run('27', 'F', '不可见用户绑定附件', 404, None,
        lambda: api.req('POST', f"/notes/{FX['z_private']['id']}/attachments", OUT,
                        {'attachmentId': FX['file']['id']}))

    def f27a():
        n = api.new_note(Z)
        api.req('POST', f"/notes/{n['id']}/archive", Z)
        return api.req('POST', f"/notes/{n['id']}/attachments", L, {'attachmentId': FX['file']['id']})

    # 实现语义：归档后该日志退出共享/授权视图，非 owner 一律 404（不泄露存在性）
    run('27a', 'F', 'EDITOR 写已归档日志（归档即不可见）', 404, None, f27a)

    def f27b():
        n = api.new_note(Z)
        f = new_file()
        r = api.req('POST', f"/notes/{n['id']}/attachments", Z, {'attachmentId': f['id']})
        assert r.data and r.data.get('attachmentId') == f['id'] and r.data.get('createdAt'), 'NoteAttachmentVO 字段缺失'
        return r

    run('27b', 'F', 'owner 绑定附件', 200, 0, f27b)

    def f27c():
        n = api.new_note(Z)
        other_file = new_file(L)
        return api.req('POST', f"/notes/{n['id']}/attachments", Z, {'attachmentId': other_file['id']})

    run('27c', 'F', '绑定他人附件', 403, None, f27c)
    run('28', 'F', '不可见用户列附件', 404, None,
        lambda: api.req('GET', f"/notes/{FX['z_private']['id']}/attachments", OUT))
    run('29', 'F', '不可见用户下载附件', 404, None,
        lambda: api.req('GET', f"/notes/{FX['z_private']['id']}/attachments/{FX['file']['id']}/download", OUT))
    run('28a', 'F', '可见用户列附件', 200, 0,
        lambda: api.req('GET', f"/notes/{FX['z_note']['id']}/attachments", Z),
        lambda r: need(r, 0, lambda x: isinstance(x.data, list)))

    def f29a():
        n = api.new_note(Z)
        f = new_file()
        b = api.req('POST', f"/notes/{n['id']}/attachments", Z, {'attachmentId': f['id']})
        assert b.code == 0, b.body
        r = api.req('GET', f"/notes/{n['id']}/attachments/{f['id']}/download", Z)
        assert r.status == 200 and r.raw == b'matrix-file', f'blob 内容不符: {r.status}'
        return r

    run('29a', 'F', '下载 note 附件（blob）', 200, None, f29a)

    def f30():
        n = api.new_note(Z)
        f = new_file()
        b = api.req('POST', f"/notes/{n['id']}/attachments", Z, {'attachmentId': f['id']})
        return api.req('DELETE', f"/notes/{n['id']}/attachments/{f['id']}", L)

    run('30', 'F', 'EDITOR 移除附件', 403, None, f30)

    def f30a():
        n = api.new_note(Z)
        f = new_file()
        b = api.req('POST', f"/notes/{n['id']}/attachments", Z, {'attachmentId': f['id']})
        r = api.req('DELETE', f"/notes/{n['id']}/attachments/{f['id']}", Z)
        lst = api.req('GET', f"/notes/{n['id']}/attachments", Z)
        assert all(int(a['attachmentId']) != int(f['id']) for a in lst.data), '关联未解除'
        return r

    run('30a', 'F', 'owner 解除附件关联', 200, 0, f30a)

    # ---------------- G. 通用 files ----------------
    run('31', 'G', 'POST /files 携带 taskId', 400, None,
        lambda: api.req('POST', '/files', Z, form={'taskId': '1', 'file': ('a.txt', b'a', 'text/plain')}))
    run('31a', 'G', 'POST /files 无 taskId', 200, 0,
        lambda: api.req('POST', '/files', Z, form={'file': ('b.txt', b'b', 'text/plain')}),
        lambda r: need(r, 0, lambda x: all(k in x.data for k in ('id', 'downloadUrl', 'createdBy', 'canDelete'))))
    run('32', 'G', 'GET /files/config', 200, 0, lambda: api.req('GET', '/files/config', Z),
        lambda r: need(r, 0, lambda x: isinstance(x.data.get('maxSize'), int) and isinstance(x.data.get('allowedExt'), list)))

    def g_file():
        return new_file(Z)

    def g33():
        f = g_file()
        return api.req('GET', f"/files/{f['id']}", L)

    run('33', 'G', '非创建者读文件', 403, None, g33)

    def g33a():
        f = g_file()
        r = api.req('GET', f"/files/{f['id']}", Z)
        assert r.status == 200 and r.raw, 'GET /files/{id} 未返回文件流'
        return r

    run('33a', 'G', '创建者读文件（文件流）', 200, None, g33a)

    def g33b():
        f = g_file()
        return api.req('GET', f"/files/{f['id']}/preview", L)

    run('33b', 'G', '非创建者预览', 403, None, g33b)

    def g33c():
        f = g_file()
        r = api.req('GET', f"/files/{f['id']}/preview", Z)
        assert r.status == 200 and r.raw, 'preview 未返回二进制'
        return r

    run('33c', 'G', '创建者预览（blob）', 200, None, g33c)

    def g34():
        n = api.new_note(Z)
        f = new_file()
        api.req('POST', f"/notes/{n['id']}/attachments", Z, {'attachmentId': f['id']})
        return api.req('DELETE', f"/files/{f['id']}", Z)

    run('34', 'G', '删除被引用文件', 409, None, g34)

    def g34a():
        f = new_file()
        return api.req('DELETE', f"/files/{f['id']}", Z)

    run('34a', 'G', '删除无引用文件', 200, 0, g34a)

    def g34b():
        f = new_file(Z)
        return api.req('DELETE', f"/files/{f['id']}", L)

    run('34b', 'G', '非创建者删除文件', 403, None, g34b)

    # ---------------- H. Task 附件 ----------------
    def task_id():
        if FX.get('task_own'):
            return FX['task_own']
        r = api.req('POST', '/tasks', Z, {'title': 'MTX 任务 ' + uuid.uuid4().hex[:5], 'content': 'matrix'})
        assert r.code == 0, f'create task failed {r.body}'
        FX['task_own'] = r.data if isinstance(r.data, int) else r.data['id']
        return FX['task_own']

    def task_of(user, assignee=None):
        """user 创建的任务；assignee 指定时该用户可写（｢可见但非上传者｣场景）。"""
        body = {'title': 'MTX-' + user + uuid.uuid4().hex[:4], 'content': 'matrix', 'visibility': 'DEPT'}
        if assignee:
            body['assigneeId'] = int(U[assignee])
        r = api.req('POST', '/tasks', user, body)
        assert r.code == 0, f'create task({user}) failed {r.body}'
        return r.data if isinstance(r.data, int) else r.data['id']

    def h35():
        tid = task_id()
        r = api.req('POST', f'/tasks/{tid}/attachments', Z, form={'file': ('t.txt', b'task-att', 'text/plain')})
        assert r.code == 0 and r.data.get('downloadUrl'), f'FileVO 缺 downloadUrl: {r.body}'
        FX['task_file'] = r.data
        return r

    run('35', 'H', 'task 可写方上传附件', 200, 0, h35)

    def h35a():
        # lisi 创建的任务，zhangli 作为上级可见但不可写
        tid = task_of(L)
        return api.req('POST', f'/tasks/{tid}/attachments', Z, form={'file': ('x.txt', b'x', 'text/plain')})

    run('35a', 'H', 'task 不可写方上传', 403, None, h35a)

    def h35b():
        tid = task_id()
        return api.req('POST', f'/tasks/{tid}/attachments', OUT, form={'file': ('x.txt', b'x', 'text/plain')})

    run('35b', 'H', 'task 不可见方上传', 404, None, h35b)

    def h36():
        tid = task_id()
        aid = FX['task_file']['id']
        r = api.req('GET', f'/tasks/{tid}/attachments/{aid}/download', Z)
        assert r.status == 200 and r.raw, 'task 附件下载非二进制'
        return r

    run('36', 'H', 'task 附件下载', 200, None, h36)

    def h36a():
        tid = task_id()
        return api.req('GET', f"/tasks/{tid}/attachments/{FX['task_file']['id']}/download", OUT)

    run('36a', 'H', 'task 附件下载（不可见）', 404, None, h36a)

    def h36b():
        tid = task_id()
        return api.req('GET', f"/tasks/{tid + 999}/attachments/{FX['task_file']['id']}/download", Z)

    run('36b', 'H', 'task_id 不匹配下载', 404, None, h36b)

    def h36c():
        tid = task_id()
        r = api.req('GET', f"/tasks/{tid}/attachments/{FX['task_file']['id']}/preview", Z)
        assert r.status == 200 and r.raw, 'preview 非二进制'
        return r

    run('36c', 'H', 'task 附件预览', 200, None, h36c)

    def h36d():
        tid = task_id()
        return api.req('GET', f"/tasks/{tid}/attachments/{FX['task_file']['id']}/preview", OUT)

    run('36d', 'H', 'task 附件预览（不可见）', 404, None, h36d)

    def h37():
        tid = task_id()
        n = api.new_note(Z)
        f = new_file()
        api.req('POST', f"/notes/{n['id']}/attachments", Z, {'attachmentId': f['id']})
        up = api.req('POST', f'/tasks/{tid}/attachments', Z, form={'file': ('r.txt', b'r', 'text/plain')})
        # 让该 task 附件同时被 note 引用
        api.req('POST', f"/notes/{n['id']}/attachments", Z, {'attachmentId': up.data['id']})
        r = api.req('DELETE', f"/tasks/{tid}/attachments/{up.data['id']}", Z)
        notes = api.req('GET', f"/notes/{n['id']}/attachments", Z)
        assert any(int(a['attachmentId']) == int(up.data['id']) for a in notes.data), '有 note 引用时 note 关联被误删'
        return r

    run('37', 'H', 'task 附件解绑（有 note 引用）', 200, 0, h37)

    def h37a():
        tid = task_id()
        up = api.req('POST', f'/tasks/{tid}/attachments', Z, form={'file': ('q.txt', b'q', 'text/plain')})
        return api.req('DELETE', f"/tasks/{tid}/attachments/{up.data['id']}", Z)

    run('37a', 'H', 'task 附件删除（无引用）', 200, 0, h37a)

    def h37b():
        tid = task_of(L)
        up = api.req('POST', f'/tasks/{tid}/attachments', L, form={'file': ('w.txt', b'w', 'text/plain')})
        assert up.code == 0, up.body
        return api.req('DELETE', f"/tasks/{tid}/attachments/{up.data['id']}", Z)

    run('37b', 'H', 'task 不可写方删除附件', 403, None, h37b)

    def h37c():
        # zhangli 可写（被指派）但不是上传者
        tid = task_of(L, assignee=Z)
        up = api.req('POST', f'/tasks/{tid}/attachments', L, form={'file': ('e.txt', b'e', 'text/plain')})
        assert up.code == 0, up.body
        return api.req('DELETE', f"/tasks/{tid}/attachments/{up.data['id']}", Z)

    run('37c', 'H', '可写但非上传者删除', 403, None, h37c)

    def h37d():
        tid = task_id()
        up = api.req('POST', f'/tasks/{tid}/attachments', Z, form={'file': ('z.txt', b'z', 'text/plain')})
        return api.req('DELETE', f"/tasks/{tid + 999}/attachments/{up.data['id']}", Z)

    run('37d', 'H', 'task_id 不匹配删除', 404, None, h37d)

    # ---------------- I. 引用 / backlinks ----------------
    def i38():
        n = api.new_note(Z)
        return api.req('POST', f"/notes/{n['id']}/relations", Z, {'targetType': 'NOTE', 'targetId': n['id']})

    run('38', 'I', '自引用', 400, None, i38)

    def i39():
        n = api.new_note(Z)
        hidden = api.new_note(OUT, visibility='PRIVATE')
        return api.req('POST', f"/notes/{n['id']}/relations", Z, {'targetType': 'NOTE', 'targetId': hidden['id']})

    run('39', 'I', '引用不可见 note', 200, 50006, i39, lambda r: need(r, 50006))

    def i39a():
        n = api.new_note(Z)
        r = api.req('POST', f"/notes/{n['id']}/relations", Z, {'targetType': 'TASK', 'targetId': task_id()})
        assert r.code == 0 and r.data, '引用任务应返回 relation 列表'
        FX['rel_note'] = n['id']
        FX['rel_id'] = r.data[0]['id'] if isinstance(r.data, list) else None
        return r

    run('39a', 'I', '引用任务', 200, 0, i39a)

    def i39b():
        n = api.new_note(Z)
        tid = task_id()
        api.req('POST', f"/notes/{n['id']}/relations", Z, {'targetType': 'TASK', 'targetId': tid})
        r = api.req('POST', f"/notes/{n['id']}/relations", Z, {'targetType': 'TASK', 'targetId': tid})
        assert r.code == 0, f'重复引用应幂等: {r.code}'
        return r

    run('39b', 'I', '重复引用幂等', 200, 0, i39b)

    def i39c():
        n = api.new_note(Z)
        api.req('POST', f"/notes/{n['id']}/archive", Z)
        return api.req('POST', f"/notes/{n['id']}/relations", L, {'targetType': 'TASK', 'targetId': task_id()})

    run('39c', 'I', 'EDITOR 给已归档日志加引用（归档即不可见）', 404, None, i39c)
    run('40', 'I', '列引用', 200, 0, lambda: api.req('GET', f"/notes/{FX['z_note']['id']}/relations", Z),
        lambda r: need(r, 0, lambda x: isinstance(x.data, list)))
    run('40a', 'I', '不可见用户列引用', 404, None,
        lambda: api.req('GET', f"/notes/{FX['z_private']['id']}/relations", OUT))
    run('41', 'I', '不可见用户列被引用', 404, None,
        lambda: api.req('GET', f"/notes/{FX['z_private']['id']}/backlinks", OUT))
    run('41a', 'I', '可见用户列被引用', 200, 0,
        lambda: api.req('GET', f"/notes/{FX['z_note']['id']}/backlinks", Z),
        lambda r: need(r, 0, lambda x: isinstance(x.data, list)))

    def i42():
        n = api.new_note(Z)
        api.req('POST', f"/notes/{n['id']}/relations", Z, {'targetType': 'TASK', 'targetId': task_id()})
        rels = api.req('GET', f"/notes/{n['id']}/relations", Z).data
        return api.req('DELETE', f"/notes/{n['id']}/relations/{rels[0]['id']}", L)

    run('42', 'I', 'EDITOR 删引用', 403, None, i42)

    def i42a():
        n = api.new_note(Z)
        api.req('POST', f"/notes/{n['id']}/relations", Z, {'targetType': 'TASK', 'targetId': task_id()})
        rels = api.req('GET', f"/notes/{n['id']}/relations", Z).data
        r = api.req('DELETE', f"/notes/{n['id']}/relations/{rels[0]['id']}", Z)
        after = api.req('GET', f"/notes/{n['id']}/relations", Z).data
        assert all(int(x['id']) != int(rels[0]['id']) for x in after), '引用未解除'
        return r

    run('42a', 'I', 'owner 删引用', 200, 0, i42a)

    # ---------------- J. 任务搜索 ----------------
    run('43', 'J', 'keyword 1 字符', 200, 0, lambda: api.req('GET', '/tasks/search?keyword=a', Z),
        lambda r: need(r, 0, lambda x: isinstance(x.data.get('list'), list) and x.data.get('total') == 0))
    run('44', 'J', 'keyword 大小写不敏感', 200, 0, lambda: api.req('GET', '/tasks/search?keyword=Task', Z),
        lambda r: need(r, 0, lambda x: isinstance(x.data.get('list'), list)))
    run('44a', 'J', 'keyword 为空', 200, 0, lambda: api.req('GET', '/tasks/search?keyword=', Z),
        lambda r: need(r, 0, lambda x: x.data.get('total') == 0))
    run('44b', 'J', 'keyword 未登录', 401, None, lambda: api.req('GET', '/tasks/search?keyword=Task', None))

    # ---------------- K. Tag 清理 ----------------
    def tag_id_of(resp):
        return resp.data if isinstance(resp.data, int) else (resp.data or {}).get('id')

    def k45():
        tag_id = tag_id_of(api.req('POST', '/tags', Z, {'name': 'MTX-K' + uuid.uuid4().hex[:4]}))
        n = api.req('POST', '/notes', Z, {'content': 'MTX-K45', 'logDate': TODAY.isoformat(),
                                          'visibility': 'PRIVATE', 'tagIds': [tag_id]})
        assert n.code == 0, n.body
        r = api.req('DELETE', f"/tags/{tag_id}", Z)
        d = api.req('GET', f"/notes/{n.data['id']}", Z)
        assert all(int(t['id']) != int(tag_id) for t in d.data.get('tags') or []), 'note_tag 未级联清理'
        return r

    run('45', 'K', '删除有引用的标签（级联）', 200, 0, k45)

    def k45a():
        tag_id = tag_id_of(api.req('POST', '/tags', Z, {'name': 'MTX-KA' + uuid.uuid4().hex[:4]}))
        return api.req('DELETE', f"/tags/{tag_id}", Z)

    run('45a', 'K', '删除无引用标签', 200, 0, k45a)
    # 实现语义：tag 不存在/非本人 → HTTP 200 + 10004「标签不存在」（信息隐藏，不区分 403/404）
    run('45b', 'K', '删除不存在标签', 200, 10004, lambda: api.req('DELETE', '/tags/99999999', Z),
        lambda r: need(r, 10004))
    run('45c', 'K', '删除他人标签（信息隐藏）', 200, 10004,
        lambda: api.req('DELETE', f"/tags/{FX['tag']['id']}", L), lambda r: need(r, 10004))

    # ---------------- L. facets ----------------
    run('46', 'L', 'facet scope=mine', 200, 0, lambda: api.req('GET', '/notes/tags?scope=mine', Z),
        lambda r: need(r, 0, lambda x: isinstance(x.data, list) and all('id' in i for i in x.data)))
    run('46a', 'L', 'facet scope=shared（无 id）', 200, 0, lambda: api.req('GET', '/notes/tags?scope=shared', W),
        lambda r: need(r, 0, lambda x: isinstance(x.data, list)))
    run('46b', 'L', 'facet 空标签用户', 200, 0, lambda: api.req('GET', '/notes/tags?scope=mine', NODEPT),
        lambda r: need(r, 0, lambda x: x.data == []))
    run('46c', 'L', 'facet scope=all 非 ADMIN', 403, None, lambda: api.req('GET', '/notes/tags?scope=all', Z))
    run('46d', 'L', 'facet scope 非法', 400, None, lambda: api.req('GET', '/notes/tags?scope=xxx', Z))

    return locals()
