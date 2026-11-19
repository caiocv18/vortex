-- Script de inicialização para Oracle Database Free (gvenzl/oracle-free)
-- Roda uma vez, na primeira inicialização do volume, como SYS no CDB (/container-entrypoint-initdb.d).
-- Conectar ao PDB (Pluggable Database) padrão do Oracle Free
ALTER SESSION SET CONTAINER = FREEPDB1;

-- O usuário da aplicação (vortex_user) não é criado aqui: a imagem o cria no FREEPDB1 antes deste
-- script, a partir de APP_USER e APP_USER_PASSWORD do compose (a senha vem de ORACLE_APP_USER_PASSWORD).

-- Conceder privilégios necessários
GRANT CONNECT, RESOURCE, DBA TO vortex_user;
GRANT CREATE SESSION TO vortex_user;
GRANT CREATE TABLE TO vortex_user;
GRANT CREATE SEQUENCE TO vortex_user;
GRANT UNLIMITED TABLESPACE TO vortex_user;

-- Tabelas e sequences não são criadas aqui: o Hibernate (ddl-auto=update) as cria no schema do vortex_user quando o
-- backend sobe. Um CREATE SEQUENCE neste script rodaria como SYS e criaria a sequence no schema do SYS, que o
-- vortex_user não enxerga (ORA-02289, medido em 19/11/2026 com gvenzl/oracle-free:23.26.3-slim-faststart). 