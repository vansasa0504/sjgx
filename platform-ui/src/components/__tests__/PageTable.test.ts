import { describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import PageTable from '../PageTable.vue'

describe('PageTable', () => {
  it('loads data on mount and refreshes on page change', async () => {
    const fetchData = vi.fn().mockResolvedValue({ records: [{ name: 'row1' }], total: 1 })
    const wrapper = mount(PageTable, {
      props: { columns: [{ prop: 'name', label: '名称' }], fetchData },
      global: { plugins: [ElementPlus] }
    })

    await flushPromises()
    expect(fetchData).toHaveBeenCalledWith({ page: 1, size: 10 })
    expect(wrapper.text()).toContain('row1')

    await wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('current-change', 2)
    await flushPromises()
    expect(fetchData).toHaveBeenLastCalledWith({ page: 2, size: 10 })
  })

  it('renders empty state', async () => {
    const wrapper = mount(PageTable, {
      props: { columns: [{ prop: 'name', label: '名称' }], fetchData: vi.fn().mockResolvedValue({ records: [], total: 0 }) },
      global: { plugins: [ElementPlus] }
    })

    await flushPromises()
    expect(wrapper.text()).toContain('暂无数据')
  })

  it('renders actions slot', async () => {
    const wrapper = mount(PageTable, {
      props: {
        columns: [{ prop: 'name', label: '名称' }, { prop: 'actions', label: '操作' }],
        fetchData: vi.fn().mockResolvedValue({ records: [{ name: 'row1' }], total: 1 })
      },
      slots: { actions: '<button class="row-action">操作</button>' },
      global: { plugins: [ElementPlus] }
    })

    await flushPromises()
    expect(wrapper.find('.row-action').exists()).toBe(true)
  })
})
