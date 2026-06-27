import { describe, expect, it } from 'vitest'
import { toPage } from '../types'

describe('toPage', () => {
  it('slices array data by page and size', () => {
    const page = toPage([1, 2, 3, 4, 5], 2, 2)
    expect(page).toEqual({ records: [3, 4], total: 5, current: 2, size: 2 })
  })
})
