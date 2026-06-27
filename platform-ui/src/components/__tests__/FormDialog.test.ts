import { describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import FormDialog from '../FormDialog.vue'

describe('FormDialog', () => {
  it('renders fields and submits form', async () => {
    const submit = vi.fn().mockResolvedValue(undefined)
    const wrapper = mount(FormDialog, {
      props: {
        modelValue: true,
        title: '新建',
        fields: [{ prop: 'name', label: '名称', type: 'input' }],
        initial: { name: 'demo' },
        submit
      },
      global: { plugins: [ElementPlus] },
      attachTo: document.body
    })

    await flushPromises()
    expect(document.body.textContent).toContain('新建')
    await (wrapper.vm as unknown as { submitForm: () => Promise<void> }).submitForm()
    await flushPromises()
    expect(submit).toHaveBeenCalledWith({ name: 'demo' })
  })
})
