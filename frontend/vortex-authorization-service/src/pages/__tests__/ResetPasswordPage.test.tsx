import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { ResetPasswordPage } from '@/pages/ResetPasswordPage';

// A página só depende de resetPassword/isLoading do contexto. O resetPassword de verdade
// (AuthContext) mostra o erro da API num toast e o relança; o mock rejeita do mesmo jeito
const auth = vi.hoisted(() => ({
  resetPassword: vi.fn(),
}));

vi.mock('@/contexts/AuthContext', () => ({
  useAuth: () => ({ ...auth, isLoading: false }),
}));

const NEW_PASSWORD = 'NovaSenha1!';

function renderPage() {
  render(
    <MemoryRouter initialEntries={['/reset-password/token-123']}>
      <Routes>
        <Route path="/reset-password/:token" element={<ResetPasswordPage />} />
        <Route path="/login" element={<p>Tela de login</p>} />
      </Routes>
    </MemoryRouter>
  );
  return userEvent.setup();
}

async function submitNewPassword(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('New Password'), NEW_PASSWORD);
  await user.type(screen.getByLabelText('Confirm New Password'), NEW_PASSWORD);
  await user.click(screen.getByRole('button', { name: 'Reset Password' }));
}

describe('ResetPasswordPage', () => {
  beforeEach(() => {
    auth.resetPassword.mockReset();
    vi.spyOn(console, 'log').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
    document.documentElement.removeAttribute('style');
  });

  it('mostra a tela de sucesso e volta para o login quando a API aceita a nova senha', async () => {
    auth.resetPassword.mockResolvedValue(undefined);
    const user = renderPage();

    await submitNewPassword(user);

    expect(await screen.findByRole('heading', { name: 'Password Reset Successful' })).toBeInTheDocument();
    expect(auth.resetPassword).toHaveBeenCalledExactlyOnceWith({
      token: 'token-123',
      password: NEW_PASSWORD,
      confirmPassword: NEW_PASSWORD,
    });
    // Redirecionamento de 3 s da tela de sucesso
    expect(await screen.findByText('Tela de login', {}, { timeout: 5000 })).toBeInTheDocument();
  }, 15000);

  it('fica no formulário, sem tela de sucesso, quando a API recusa (token expirado ou já usado)', async () => {
    let rejectReset: (error: Error) => void = () => {};
    auth.resetPassword.mockImplementation(
      () =>
        new Promise<void>((_resolve, reject) => {
          rejectReset = reject;
        })
    );
    const user = renderPage();

    await submitNewPassword(user);
    // Envio em andamento: só depois dele o resultado conta
    expect(await screen.findByRole('button', { name: /Resetting password/ })).toBeDisabled();

    rejectReset(new Error('Invalid or expired reset token'));

    expect(await screen.findByRole('button', { name: 'Reset Password' })).toBeEnabled();
    expect(screen.getByRole('heading', { name: 'Reset Password' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Password Reset Successful' })).not.toBeInTheDocument();
    expect(screen.queryByText(/You can now log in/)).not.toBeInTheDocument();
    expect(screen.getByLabelText('New Password')).toHaveValue(NEW_PASSWORD);
    expect(auth.resetPassword).toHaveBeenCalledTimes(1);
  });
});
