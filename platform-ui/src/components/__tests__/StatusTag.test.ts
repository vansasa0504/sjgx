import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import StatusTag from '../StatusTag.vue'

describe('StatusTag', () => {
  it('renders default status mapping', () => {
    const wrapper = mount(StatusTag, { props: { status: 'ONLINE' }, global: { plugins: [ElementPlus] } })
    expect(wrapper.text()).toContain('在线')
  })
})
