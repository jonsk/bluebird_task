import { describe, expect, it } from 'vitest'
import { PRIORITY, PRIORITY_LABEL, ROLE_CODE, ROUTE_SCOPE_MAP, TASK_SCOPE } from '@/utils/constants'

describe('constants（契约一致性）', () => {
  it('优先级为四值且标签完整', () => {
    expect(PRIORITY).toEqual(['LOW', 'MEDIUM', 'HIGH', 'URGENT'])
    expect(Object.keys(PRIORITY_LABEL).sort()).toEqual([...PRIORITY].sort())
  })

  it('路由名映射六大视图 scope（R9-契约）', () => {
    expect(ROUTE_SCOPE_MAP).toEqual({
      index: TASK_SCOPE.day,
      myWeek: TASK_SCOPE.week,
      myJoin: TASK_SCOPE.joined,
      myDo: TASK_SCOPE.assigned,
      myCollect: TASK_SCOPE.collect,
      allTask: TASK_SCOPE.all,
    })
  })

  it('角色为四值枚举（02 §2.4）', () => {
    expect(Object.values(ROLE_CODE)).toEqual(['ADMIN', 'AUDITOR', 'USER_MANAGER', 'COMMON'])
  })
})
