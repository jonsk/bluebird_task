/** 通用分页响应体：字段与各模块本地分页接口保持一致。 */
export interface Page<T> {
  list: T[]
  total: number
  page: number
  size: number
}
