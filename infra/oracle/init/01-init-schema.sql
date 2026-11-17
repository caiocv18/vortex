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

-- Conectar como o usuário da aplicação
-- ALTER SESSION SET CURRENT_SCHEMA = vortex_user;

-- Criar sequências para as tabelas (caso não existam)
DECLARE
    seq_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO seq_count FROM user_sequences WHERE sequence_name = 'TIPO_PRODUTO_SEQ';
    IF seq_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE tipo_produto_seq START WITH 1 INCREMENT BY 1';
    END IF;
    
    SELECT COUNT(*) INTO seq_count FROM user_sequences WHERE sequence_name = 'PRODUTO_SEQ';
    IF seq_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE produto_seq START WITH 1 INCREMENT BY 1';
    END IF;
    
    SELECT COUNT(*) INTO seq_count FROM user_sequences WHERE sequence_name = 'MOVIMENTO_ESTOQUE_SEQ';
    IF seq_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE movimento_estoque_seq START WITH 1 INCREMENT BY 1';
    END IF;
END;
/ 