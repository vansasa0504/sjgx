export interface Page<T> {
  records: T[]
  total: number
  current: number
  size: number
}

export interface PageQuery {
  page?: number
  size?: number
  [key: string]: unknown
}

export function toPage<T>(value: T[] | Page<T>, page = 1, size = 10): Page<T> {
  if (Array.isArray(value)) {
    return { records: value, total: value.length, current: page, size }
  }
  return {
    records: value.records ?? [],
    total: value.total ?? 0,
    current: value.current ?? page,
    size: value.size ?? size
  }
}

export interface Partner {
  id: number
  partnerCode?: string
  name: string
  dataType?: string
  industry?: string
  complianceLevel?: string
  status?: string
  rating?: string
  createdAt?: string
}

export interface Consumer {
  id: number
  consumerCode?: string
  code?: string
  name: string
  bizLine?: string
  systemType?: string
  complianceLevel?: string
  status?: string
}

export interface IngestTask {
  id: number
  partnerId: number
  endpoint?: string
  protocol?: string
  format?: string
  syncMode?: string
  cron?: string
  status?: string
  fieldMapping?: Record<string, string>
  qualityRules?: string[]
}

export interface DataServiceDefinition {
  serviceCode: string
  name: string
  routeKey: string
  status?: string
  version?: number
}

export interface CatalogItem {
  id: number
  name?: string
  subject?: string
  partnerId?: number
  dataType?: string
  scenario?: string
  fieldDefinitions?: string
}

export interface QualityRule {
  id?: number
  ruleCode: string
  dimension: string
  field: string
  expression?: Record<string, unknown>
  weight: number
}

export interface BillingRule {
  id?: number
  ruleCode: string
  ruleName: string
  billingModel: string
  targetType: string
  targetId?: number
  unitPrice: number
  currency?: string
  effectiveFrom?: string
  effectiveTo?: string
  packageAllowance?: number
}

export interface Bill {
  billNo: string
  billType?: string
  period?: string
  amount?: number
  status?: string
}

export interface UserAccount {
  username: string
  password?: string
  permissions: string[]
}

export interface Role {
  name: string
  permissions: string[]
}
