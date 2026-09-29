import { setupWorker } from 'msw/browser'
import { handlers } from './handlers'

/** 浏览器端 MSW worker（仅 dev/test 由 main.ts 动态装载）。 */
export const worker = setupWorker(...handlers)
