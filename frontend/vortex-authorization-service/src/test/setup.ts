// Setup do Vitest: matchers do jest-dom (toBeInTheDocument etc.) e limpeza do DOM entre testes
// (sem `globals: true`, o Testing Library não registra o cleanup sozinho)
import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

afterEach(() => {
  cleanup();
});
