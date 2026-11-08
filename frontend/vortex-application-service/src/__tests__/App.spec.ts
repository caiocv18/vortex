import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory, type Router } from 'vue-router'
import { createVuetify } from 'vuetify'
import App from '@/App.vue'
import { createMemoryStorage } from '@/__tests__/memoryStorage'

// Drawer de navegação do App.vue nos breakpoints padrão do Vuetify 4 (mobile abaixo de 1145px):
// fixo no desktop; temporário, fechado e fechando ao navegar no mobile.

const Page = defineComponent({ render: () => h('div', 'página') })

let wrapper: VueWrapper | undefined
let router: Router
const originalWidth = window.innerWidth

async function mountApp(width: number) {
  // O display do Vuetify lê window.innerWidth ao ser criado (o jsdom não tem matchMedia)
  Object.defineProperty(window, 'innerWidth', { value: width, configurable: true, writable: true })

  router = createRouter({
    history: createMemoryHistory(),
    // As rotas do menu do drawer, todas com a mesma página vazia
    routes: ['/', '/product-types', '/products', '/movements', '/reports'].map((path) => ({
      path,
      component: Page,
    })),
  })
  await router.push('/')
  await router.isReady()

  wrapper = mount(App, {
    global: { plugins: [createPinia(), router, createVuetify()] },
  })
  await flushPromises()
  return wrapper
}

const drawerEl = () => wrapper!.find('.v-navigation-drawer')
const isOpen = () => drawerEl().classes().includes('v-navigation-drawer--active')
const isTemporary = () => drawerEl().classes().includes('v-navigation-drawer--temporary')
const headerButton = () => wrapper!.find('.v-navigation-drawer .v-list-item__append .v-btn')
const navIcon = () => wrapper!.find('.v-app-bar-nav-icon')
const scrim = () => wrapper!.find('.v-navigation-drawer__scrim')

describe('App navigation drawer', () => {
  beforeEach(() => {
    // Sessão já gravada: a store de auth considera o usuário logado e o App mostra o shell
    const storage = createMemoryStorage()
    storage.setItem('accessToken', 'access')
    storage.setItem('refreshToken', 'refresh')
    storage.setItem('vortex_user', JSON.stringify({ id: '1', email: 'ana@vortex.dev', username: 'ana' }))
    vi.stubGlobal('localStorage', storage)
    vi.spyOn(console, 'log').mockImplementation(() => {})
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    Object.defineProperty(window, 'innerWidth', { value: originalWidth, configurable: true, writable: true })
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('is permanent and open on desktop, with the rail toggle', async () => {
    await mountApp(1366)

    expect(isOpen()).toBe(true)
    expect(isTemporary()).toBe(false)
    expect(scrim().exists()).toBe(false)
    // Como antes: com o drawer aberto e sem rail, o app bar não mostra o ícone de menu
    expect(navIcon().exists()).toBe(false)

    await headerButton().trigger('click')
    expect(drawerEl().classes()).toContain('v-navigation-drawer--rail')
    expect(isOpen()).toBe(true)
    expect(navIcon().exists()).toBe(true)
  })

  it('stays open on desktop when navigating', async () => {
    await mountApp(1366)
    await router.push('/products')
    await flushPromises()
    expect(isOpen()).toBe(true)
  })

  // 390: celular; 768: tablet (faixa sm, 600–840); 1000: faixa md (840–1145)
  it.each([390, 768, 1000])('is temporary and starts closed at %ipx', async (width) => {
    await mountApp(width)

    expect(isTemporary()).toBe(true)
    expect(isOpen()).toBe(false)
    expect(scrim().exists()).toBe(false)
    expect(navIcon().exists()).toBe(true)
  })

  it('opens over the content from the app bar icon and closes on navigation (mobile)', async () => {
    await mountApp(390)

    await navIcon().trigger('click')
    await flushPromises()
    expect(isOpen()).toBe(true)
    expect(scrim().exists()).toBe(true)
    // Sobreposto: o drawer temporário não fica em modo rail nem empurra o conteúdo
    expect(drawerEl().classes()).not.toContain('v-navigation-drawer--rail')

    await router.push('/products')
    await flushPromises()
    expect(isOpen()).toBe(false)
  })

  it('closes from the drawer header button on mobile instead of toggling the rail', async () => {
    await mountApp(390)
    await navIcon().trigger('click')
    await flushPromises()
    expect(isOpen()).toBe(true)

    await headerButton().trigger('click')
    await flushPromises()
    expect(isOpen()).toBe(false)
    expect(drawerEl().classes()).not.toContain('v-navigation-drawer--rail')
  })

  it('closes when the scrim is clicked (mobile)', async () => {
    await mountApp(390)
    await navIcon().trigger('click')
    await flushPromises()

    await scrim().trigger('click')
    await flushPromises()
    expect(isOpen()).toBe(false)
  })
})
