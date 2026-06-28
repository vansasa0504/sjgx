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
  const safePage = page > 0 ? page : 1
  const safeSize = size > 0 ? size : 10
  if (Array.isArray(value)) {
    const start = (safePage - 1) * safeSize
    return { records: value.slice(start, start + safeSize), total: value.length, current: safePage, size: safeSize }
  }
  return {
    records: value.records ?? [],
    total: value.total ?? 0,
    current: value.current ?? safePage,
    size: value.size ?? safeSize
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
  id?: number
  billNo: string
  billType?: string
  billPeriod?: string
  periodStart?: string
  periodEnd?: string
  totalAmount?: number
  status?: string
  items?: BillItem[]
}

export interface BillItem {
  id?: number
  itemType: string
  refId: string
  quantity: number
  unitPrice: number
  amount: number
  period: string
  serviceCode?: string
  consumerCode?: string
  partnerCode?: string
}

export interface BillingStats {
  totalAmount: number
  invokeCount: number
  billCount: number
  itemCount: number
  amountByItemType: Record<string, number>
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
