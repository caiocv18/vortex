1. Autenticação e Autorização

  Qual método de autenticação você prefere?
  - JWT (JSON Web Tokens) - Stateless, escalável, ideal para microserviços
  - Session-based - Tradicional, stateful, mais simples
  - OAuth2/OpenID Connect - Para integração com provedores externos

  Minha recomendação: JWT, pois é mais adequado para arquiteturas de microserviços e permite melhor escalabilidade.

  2. Banco de Dados - Estrutura

  Como você gostaria de organizar as tabelas de usuários?
  - Tabela única com todos os dados do usuário
  - Tabelas separadas (users, profiles, credentials, roles)
  - Schema específico para o serviço de autorização

  Minha recomendação: Tabelas separadas com schema próprio (ex: auth.users, auth.roles), para melhor organização e segurança.

  3. Funcionalidades de Segurança

  Quais recursos de segurança são importantes?
  - Rate limiting para prevenir brute force
  - 2FA (Two-Factor Authentication)
  - Captcha para formulários públicos
  - Logs de auditoria para rastreamento
  - Refresh tokens para renovação de sessão
  - Blacklist de tokens para logout seguro

  Minha recomendação: Todos, exceto 2FA inicialmente (pode ser adicionado depois).

  4. Integração com Sistema Principal

  Como o serviço de autorização deve se comunicar com o sistema principal?
  - REST API com endpoints específicos
  - Mensageria (Kafka/RabbitMQ) para eventos
  - Ambos - API para operações síncronas, mensageria para eventos

  Minha recomendação: Ambos - REST para login/validação e mensageria para eventos como "usuário criado", "senha alterada".

  5. Recuperação de Senha

  Qual método para recuperação?
  - Email com link temporário
  - Código via SMS
  - Perguntas de segurança
  - Combinação de métodos

  Minha recomendação: Email com link temporário (mais seguro e comum).

  6. Estrutura do Frontend

  Como organizar os componentes React?
  - Páginas separadas para cada função (login, registro, recuperação)
  - Modal único com abas/steps
  - Rotas dedicadas com layouts próprios

  Minha recomendação: Páginas separadas com rotas dedicadas para melhor UX e SEO.

  7. Validações

  Quais validações implementar?
  - Email válido e único
  - Senha forte (mínimo de caracteres, complexidade)
  - CPF/CNPJ válido
  - Termos de uso aceitos
  - Idade mínima

  Minha recomendação: Todas, exceto idade mínima (depende do negócio).

  8. Testes

  Que tipo de cobertura de testes?
  - Unitários (services, validators)
  - Integração (API endpoints)
  - E2E (fluxo completo)
  - Performance (carga)

  Minha recomendação: Unitários e integração obrigatórios, E2E para fluxos críticos.

  9. Documentação

  O que documentar?
  - API com Swagger/OpenAPI
  - README com instruções
  - Diagramas de fluxo
  - Guia de contribuição

  Minha recomendação: Todos, com foco principal no Swagger para API.

  10. Features Adicionais

  Alguma dessas é necessária agora?
  - Login social (Google, Facebook)
  - Single Sign-On (SSO)
  - Gerenciamento de sessões (ver dispositivos logados)
  - Perfis de usuário com foto
  - Configurações de privacidade

  Minha recomendação: Deixar preparado para login social, mas não implementar agora.

  Por favor, me informe suas preferências para que eu possa criar um prompt completo e detalhado!

Minha resposta:
1. JWT
2. Tabelas separadas com schema próprio
3. Todos, exceto 2FA inicialmente
4. Ambos - REST para login/validação e mensageria para eventos como "usuário criado", "senha alterada", "usuário logado".
5. Email com link temporário
6. Páginas separadas com rotas dedicadas para melhor UX e SEO.
7. Email válido e único, Senha forte (mínimo de caracteres, complexidade). Faça a parte de senha ser algo parametrizável (Se precisa ter número, caracter especial, letra maiúscula, letra minúscula e etc)
8. Unitários e integração obrigatórios, E2E para fluxos críticos.
9. Todos, com foco principal no Swagger para API. Colocar tudo em docs/authentication
10. Quero apenas que seja adicionado na aplicação um componente no canto superior direito mostrando o usuário que está logado e um botão para sair.

Faça com que o design da tela de login seja muito bonito e que converse com o design da aplicação principal
Faça com que as cores sejam parametrizáveis num arquivo CSS
Ao final do prompt adicione todos os arquivos que já existem e que você considera que precisam ser considerados nessa implementação, coloque antes de cada arquivo o caracter "@" e a path do arquivo a partir da raiz do projeto