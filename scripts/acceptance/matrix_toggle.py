# -*- coding: utf-8 -*-
"""§9 矩阵 · 开关关闭场景（后端需以 MANAGER_CAN_READ_SUBORDINATE=false 启动）。

覆盖：12a（subordinate 403）/ 12c（visible 静默省略，仍 200）/ 25（calendar subordinate 403）。
"""
import json
import os
import sys

import matrix_runner as R

Z = 'zhangli'


def main():
    R.reset_test_grants()
    R.run('12a', 'B', 'scope=subordinate 开关关闭', 403, None,
          lambda: R.api.req('GET', '/notes?scope=subordinate', Z))
    R.run('12c', 'B', 'scope=visible 开关关闭（静默省略）', 200, 0,
          lambda: R.api.req('GET', '/notes?scope=visible&size=100', Z),
          lambda r: None if isinstance(r.data.get('list'), list) else (_ for _ in ()).throw(AssertionError('list 结构缺失')))
    R.run('25', 'E', 'calendar scope=subordinate 开关关闭', 403, None,
          lambda: R.api.req('GET', f'/notes/calendar?year={R.TODAY.year}&month={R.TODAY.month}&scope=subordinate', Z))
    R.run('25x', 'E', '对照组 calendar mine 仍 200', 200, 0,
          lambda: R.api.req('GET', f'/notes/calendar?year={R.TODAY.year}&month={R.TODAY.month}&scope=mine', Z))

    total = len(R.results)
    passed = sum(1 for x in R.results if x['status'] == 'PASS')
    print('=' * 100)
    print(f'[toggle] 总用例 {total}  通过 {passed}  失败 {total - passed}')
    os.makedirs(R.OUT_DIR, exist_ok=True)
    with open(os.path.join(R.OUT_DIR, 'matrix_result_toggle.json'), 'w', encoding='utf-8') as fh:
        json.dump({'phase': 'toggle', 'total': total, 'passed': passed,
                   'failed': total - passed, 'results': R.results}, fh, ensure_ascii=False, indent=2)
    sys.exit(0 if passed == total else 1)


if __name__ == '__main__':
    main()
