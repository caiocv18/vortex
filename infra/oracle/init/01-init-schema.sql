-- Script de inicialização para Oracle
-- Conectar ao PDB (Pluggable Database)
ALTER SESSION SET CONTAINER = ORCLPDB1;

-- Criar usuário para a aplicação (caso não exista)
BEGIN
   EXECUTE IMMEDIATE 'CREATE USER vortex_user IDENTIFIED BY vortex_password';
EXCEPTION
   WHEN OTHERS THEN
      IF SQLCODE != -1920 THEN -- Ignorar erro se usuário já existe
         RAISE;
      END IF;
END;
/

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