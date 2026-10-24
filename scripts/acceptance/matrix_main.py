# -*- coding: utf-8 -*-
"""§9 矩阵执行入口：装载 A–L 用例并汇总。"""
import json
import os
import sys

import matrix_runner as R  # noqa: E402  (infra + fixture + record/run)
import matrix_cases_a  # noqa: E402
import matrix_cases_b  # noqa: E402

matrix_cases_a.build(R.api, R.U, R.FX, R.run, R.TODAY)
matrix_cases_b.build(R.api, R.U, R.FX, R.run, R.TODAY)

total = len(R.results)
passed = sum(1 for x in R.results if x['status'] == 'PASS')
failed = [x for x in R.results if x['status'] != 'PASS']

print('=' * 100)
print(f'总用例 {total}  通过 {passed}  失败 {len(failed)}')
for f in failed:
    print(f"  FAIL {f['id']:<5} {f['section']} expect {f['expect']} actual {f['actual']} | {f['detail'][:160]}")

os.makedirs(R.OUT_DIR, exist_ok=True)
out = os.path.join(R.OUT_DIR, 'matrix_result_%s.json' % R.PHASE)
with open(out, 'w', encoding='utf-8') as fh:
    json.dump({'phase': R.PHASE, 'total': total, 'passed': passed, 'failed': len(failed),
               'results': R.results}, fh, ensure_ascii=False, indent=2)
print('written:', out)
sys.exit(0 if not failed else 1)
