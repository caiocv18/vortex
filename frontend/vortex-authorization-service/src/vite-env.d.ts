/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_URL?: string;
  readonly VITE_THEME_COLOR?: string;
  // Aplicação principal (Vue): destino do hand-off e única origem aceita em ?returnUrl=
  readonly VITE_MAIN_APP_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
