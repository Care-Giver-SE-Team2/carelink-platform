import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import LandingHome from './index'
import FamilySignUp from './FamilySignUp'

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function open(path = '/apply') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/" element={<LandingHome />} />
        <Route path="/apply" element={<FamilySignUp />} />
        <Route path="/family/family-bindings" element={<h1>Family bindings</h1>} />
      </Routes>
    </MemoryRouter>,
  )
}

/** Answers csrf, sign-up and login; `signUpStatus` lets a test make sign-up fail. */
function stubServer(signUpStatus = 201) {
  const fetchMock = vi.fn().mockImplementation((url: string, init: RequestInit = {}) => {
    if (url === '/api/auth/csrf') {
      document.cookie = 'XSRF-TOKEN=signup-token; path=/'
      return Promise.resolve(new Response(null))
    }
    if (url === '/api/family-registrations' && init.method === 'POST') {
      return Promise.resolve(signUpStatus === 201
        ? json({ familyMemberId: 5, username: 'lim.weiling', fullName: 'Lim Wei Ling' }, 201)
        : json({ status: signUpStatus, detail: 'That username is already taken', code: 'USERNAME_TAKEN' }, signUpStatus))
    }
    if (url === '/api/auth/login' && init.method === 'POST') {
      return Promise.resolve(json({ id: 9, username: 'lim.weiling', displayName: 'Lim Wei Ling', roles: ['FAMILY'] }))
    }
    return Promise.resolve(json({ status: 401 }, 401))
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

async function fillIn() {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Your full name'), '  Lim Wei Ling ')
  await user.type(screen.getByLabelText('Mobile number'), '9123 4567')
  await user.type(screen.getByLabelText('Choose a username'), 'Lim.WeiLing')
  await user.type(screen.getByLabelText('Choose a password'), 'chosen-password')
  return user
}

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
})

describe('Family sign-up', () => {
  it('is reached from the landing page without a session', async () => {
    const fetchMock = stubServer()
    open('/')

    await userEvent.setup().click(screen.getByRole('link', { name: /Register as a family member/ }))

    expect(screen.getByRole('heading', { name: 'Create your family account' })).toBeTruthy()
    expect(fetchMock).not.toHaveBeenCalledWith('/api/auth/me', expect.anything())
  })

  it('creates the account, signs in with it and continues to family bindings', async () => {
    const fetchMock = stubServer()
    open()

    const user = await fillIn()
    await user.click(screen.getByRole('button', { name: 'Create account and continue' }))

    expect(await screen.findByRole('heading', { name: 'Family bindings' })).toBeTruthy()
    const signUp = fetchMock.mock.calls.find(([url]) => url === '/api/family-registrations')!
    expect(JSON.parse(signUp[1].body as string)).toEqual({
      username: 'lim.weiling',
      password: 'chosen-password',
      fullName: 'Lim Wei Ling',
      phone: '+6591234567',
    })
    expect(new Headers(signUp[1].headers).get('X-XSRF-TOKEN')).toBe('signup-token')
    const login = fetchMock.mock.calls.find(([url]) => url === '/api/auth/login')!
    expect(JSON.parse(login[1].body as string)).toEqual({ username: 'lim.weiling', password: 'chosen-password' })
  })

  it('checks the details before sending anything', async () => {
    const fetchMock = stubServer()
    open()

    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Choose a password'), 'short')
    await user.click(screen.getByRole('button', { name: 'Create account and continue' }))

    expect(screen.getByText('Enter your full name.')).toBeTruthy()
    expect(screen.getByText('Enter your mobile number.')).toBeTruthy()
    expect(screen.getByText('Use at least 8 characters.')).toBeTruthy()
    expect(screen.getByLabelText('Choose a username').getAttribute('aria-invalid')).toBe('true')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('stops the mobile number at the longest way it can be written', async () => {
    stubServer()
    open()

    // user-event does not apply maxlength to type="tel", so check the attribute browsers enforce.
    expect(screen.getByLabelText('Mobile number')).toHaveAttribute('maxlength', '15')
  })

  it('asks for another username when the chosen one is taken, and stays on the page', async () => {
    const fetchMock = stubServer(409)
    open()

    const user = await fillIn()
    await user.click(screen.getByRole('button', { name: 'Create account and continue' }))

    expect(await screen.findByText('That username is taken. Choose another.')).toBeTruthy()
    expect(screen.getByRole('heading', { name: 'Create your family account' })).toBeTruthy()
    expect(fetchMock.mock.calls.some(([url]) => url === '/api/auth/login')).toBe(false)
  })
})
