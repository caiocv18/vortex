import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { ForgotPasswordPage } from '@/pages/ForgotPasswordPage';

// A página só depende de forgotPassword/isLoading do contexto. O forgotPassword de verdade
// (AuthContext) mostra o erro da API num toast e o relança; o mock rejeita do mesmo jeito
const auth = vi.hoisted(() => ({
  forgotPassword: vi.fn(),
}));

vi.mock('@/contexts/AuthContext', () => ({
  useAuth: () => ({ ...auth, isLoading: false }),
}));

const EMAIL = 'ana@vortex.dev';

function renderPage() {
  render(
    <MemoryRouter>
      <ForgotPasswordPage />
    </MemoryRouter>
  );
  return userEvent.setup();
}

async function requestResetLink(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Email Address'), EMAIL);
  await user.click(screen.getByRole('button', { name: 'Send Reset Link' }));
}

describe('ForgotPasswordPage', () => {
  beforeEach(() => {
    auth.forgotPassword.mockReset();
    vi.spyOn(console, 'log').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
    document.documentElement.removeAttribute('style');
  });

  it('mostra "Check Your Email" quando a API aceita o pedido', async () => {
    auth.forgotPassword.mockResolvedValue(undefined);
    const user = renderPage();

    await requestResetLink(user);

    expect(await screen.findByRole('heading', { name: 'Check Your Email' })).toBeInTheDocument();
    expect(auth.forgotPassword).toHaveBeenCalledExactlyOnceWith({ email: EMAIL });
  });

  it('fica no formulário, sem "Check Your Email", quando a API recusa (limite de tentativas)', async () => {
    let rejectRequest: (error: Error) => void = () => {};
    auth.forgotPassword.mockImplementation(
      () =>
        new Promise<void>((_resolve, reject) => {
          rejectRequest = reject;
        })
    );
    const user = renderPage();

    await requestResetLink(user);
    // Envio em andamento: só depois dele o resultado conta
    expect(await screen.findByRole('button', { name: /Sending email/ })).toBeDisabled();

    rejectRequest(new Error('Too many failed login attempts'));

    expect(await screen.findByRole('button', { name: 'Send Reset Link' })).toBeEnabled();
    expect(screen.getByRole('heading', { name: 'Forgot Password' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Check Your Email' })).not.toBeInTheDocument();
    expect(screen.getByLabelText('Email Address')).toHaveValue(EMAIL);
    expect(auth.forgotPassword).toHaveBeenCalledTimes(1);
  });
});
