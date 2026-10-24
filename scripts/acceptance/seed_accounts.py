# -*- coding: utf-8 -*-
"""建立 §9 验收矩阵所需的本地账号（幂等）。

前置：后端库已有基础部门与 zhangli/lisi/wangwu/auditor/outsider/useradmin
（由项目既有的本地种子脚本建立，非本仓库职责）。

本脚本补齐矩阵专用账号（固定 ID，供 matrix_*.py 直接引用）：
  noteadmin  2106100000000000007  ADMIN         （scope=all / tab=all / 代撤销）
  nodept     2106100000000000008  COMMON 无部门  （50017 / 空 facet）
  mtxviewer  2106100000000000009  COMMON        （纯 VIEWER 授权：5a / 22c）
  mtxsub1    2106100000000000010  COMMON 前端组  （SUPERIOR PENDING：21 / 22a）
  mtxsub2    2106100000000000011  COMMON 前端组  （PENDING 撤销/升级：23a / 22d）

用法：BLUEBIRD_DB=<...>/bluebird.db MATRIX_PWD=Test@1234 py seed_accounts.py
"""
import os
import sqlite3
import sys

import bcrypt

HERE = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.environ.get('BLUEBIRD_DB', os.path.normpath(os.path.join(HERE, '..', '..', 'backend', 'data', 'bluebird.db')))
PWD = os.environ.get('MATRIX_PWD', 'Test@1234')
HASH = bcrypt.hashpw(PWD.encode(), bcrypt.gensalt(rounds=10)).decode()

ACCOUNTS = [
    (2106100000000000007, 'noteadmin', '验收管理员', 'ADMIN', None),
    (2106100000000000008, 'nodept', '无部门用户', 'COMMON', None),
    (2106100000000000009, 'mtxviewer', '只读验收员', 'COMMON', '市场部'),
    (2106100000000000010, 'mtxsub1', '验收下属甲', 'COMMON', '前端组'),
    (2106100000000000011, 'mtxsub2', '验收下属乙', 'COMMON', '前端组'),
]


def main():
    con = sqlite3.connect(DB_PATH, timeout=10)
    cur = con.cursor()
    dept = {name: did for did, name in cur.execute('SELECT id, name FROM sys_department WHERE deleted = 0')}

    for uid, username, name, role, dept_name in ACCOUNTS:
        dept_id = dept.get(dept_name) if dept_name else None
        if dept_name and dept_id is None:
            raise SystemExit(f'缺少部门「{dept_name}」，请先建立基础部门数据')
        cur.execute(
            "INSERT OR IGNORE INTO sys_user (id,username,password,name,dept_id,role_code,status,must_change_password,deleted,created_at)"
            " VALUES (?,?,?,?,?,?,'ACTIVE',0,0,strftime('%Y-%m-%dT%H:%M:%fZ','now'))",
            (uid, username, HASH, name, dept_id, role),
        )
        cur.execute(
            "UPDATE sys_user SET password=?, name=?, dept_id=?, role_code=?, status='ACTIVE',"
            " must_change_password=0, deleted=0 WHERE username=?",
            (HASH, name, dept_id, role, username),
        )
        cur.execute('DELETE FROM sys_user_department WHERE user_id=?', (uid,))
        if dept_id is not None:
            cur.execute(
                'INSERT INTO sys_user_department (id,user_id,dept_id,is_primary) VALUES (?,?,?,1)',
                (uid + 5000, uid, dept_id),
            )

    # 清理矩阵账号的历史授权，保证 PENDING / 升级用例可重复执行
    ids = [a[0] for a in ACCOUNTS]
    q = ','.join('?' * len(ids))
    cur.execute(f'DELETE FROM note_user_grant WHERE grantor_id IN ({q}) OR grantee_id IN ({q})', ids + ids)
    con.commit()
    con.close()
    print(f'ok: {len(ACCOUNTS)} 个验收账号就绪，授权行已重置')


if __name__ == '__main__':
    main()
