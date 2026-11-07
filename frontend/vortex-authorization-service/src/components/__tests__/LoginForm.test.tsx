import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { LoginForm } from '@/components/LoginForm';

// O formulário só depende de login/forceReset/isLoading do contexto; o resto do AuthProvider
// (axios, localStorage, redirect) fica fora deste teste
const auth = vi.hoisted(() => ({
  login: vi.fn(),
  forceReset: vi.fn(),
}));

vi.mock('@/contexts/AuthContext', () => ({
  useAuth: () => ({ ...auth, isLoading: false }),
}));

function renderLoginForm() {
  render(
    <MemoryRouter>
      <LoginForm />
    </MemoryRouter>
  );
  return userEvent.setup();
}

describe('LoginForm (validação yup via @hookform/resolvers)', () => {
  beforeEach(() => {
    auth.login.mockReset();
    auth.forceReset.mockReset();
    vi.spyOn(console, 'log').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('mostra as mensagens de obrigatório ao enviar vazio e não chama o login', async () => {
    const user = renderLoginForm();

    await user.click(screen.getByRole('button', { name: 'Sign In' }));

    expect(await screen.findByText('Email or username is required')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();
    expect(auth.login).not.toHaveBeenCalled();
  });

  it('exige ao menos 3 caracteres no identificador', async () => {
    const user = renderLoginForm();

    await user.type(screen.getByLabelText('Email or Username'), 'ab');
    await user.type(screen.getByLabelText('Password'), 'segredo');
    await user.click(screen.getByRole('button', { name: 'Sign In' }));

    expect(await screen.findByText('Must be at least 3 characters')).toBeInTheDocument();
    expect(screen.queryByText('Password is required')).not.toBeInTheDocument();
    expect(auth.login).not.toHaveBeenCalled();
  });

  it('envia identifier, password e rememberMe quando o formulário é válido', async () => {
    const user = renderLoginForm();

    await user.type(screen.getByLabelText('Email or Username'), 'ana@vortex.dev');
    await user.type(screen.getByLabelText('Password'), 'segredo');
    await user.click(screen.getByLabelText('Remember me'));
    await user.click(screen.getByRole('button', { name: 'Sign In' }));

    await vi.waitFor(() => expect(auth.login).toHaveBeenCalledTimes(1));
    expect(auth.login).toHaveBeenCalledWith({
      identifier: 'ana@vortex.dev',
      password: 'segredo',
      rememberMe: true,
    });
    expect(screen.queryByText('Email or username is required')).not.toBeInTheDocument();
    expect(screen.queryByText('Must be at least 3 characters')).not.toBeInTheDocument();
  });
});
