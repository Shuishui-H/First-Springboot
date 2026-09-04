// 全局配置：API 基址、是否使用内置 Mock、应用名
// - 合并进 erp-web 主工程 / 对接真实后端时，可通过 setApiBase 覆盖基址，
//   并把 useMock 置为 false（或通过 setApiConfig 动态调整）。
export const apiConfig = {
  baseURL: '/api',
  useMock: true, // true=使用内置 Mock 数据预览界面；false=请求真实后端
  appName: '系统设置',
  tokenKey: 'z_token',
  userKey: 'z_user'
}

export function setApiBase(base) {
  apiConfig.baseURL = base
}

export function setApiConfig(partial) {
  Object.assign(apiConfig, partial)
}

export function setAppName(name) {
  apiConfig.appName = name
}

export function getApiBase() {
  return apiConfig.baseURL
}
