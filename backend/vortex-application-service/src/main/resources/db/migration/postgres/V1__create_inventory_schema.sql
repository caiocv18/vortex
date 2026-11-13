-- Esquema inicial do app no PostgreSQL (perfil postgres).
-- Espelha o que o Hibernate 7 gera para as entidades TipoProduto, Produto e MovimentoEstoque; o perfil roda
-- com ddl-auto=validate. O validate só confere tabela, coluna, tipo e sequence: CHECK, UNIQUE, FK e precisão
-- vivem aqui, então mudança de entidade (um novo TipoMovimentacao, por exemplo) pede uma V2.

-- allocationSize = 1 nas três entidades: o incremento tem de ser 1, ou o Hibernate recusa a sequence no boot
CREATE SEQUENCE tipo_produto_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE produto_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE movimento_estoque_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE tipo_produto (
    id BIGINT NOT NULL,
    nome VARCHAR(255) NOT NULL,
    CONSTRAINT pk_tipo_produto PRIMARY KEY (id),
    CONSTRAINT uk_tipo_produto_nome UNIQUE (nome)
);

CREATE TABLE produto (
    id BIGINT NOT NULL,
    descricao VARCHAR(255) NOT NULL,
    valor_fornecedor NUMERIC(38, 2) NOT NULL,
    quantidade_em_estoque INTEGER NOT NULL,
    tipo_produto_id BIGINT NOT NULL,
    CONSTRAINT pk_produto PRIMARY KEY (id),
    CONSTRAINT fk_produto_tipo_produto FOREIGN KEY (tipo_produto_id) REFERENCES tipo_produto (id)
);

CREATE TABLE movimento_estoque (
    id BIGINT NOT NULL,
    data_movimento TIMESTAMP(6) NOT NULL,
    tipo_movimentacao VARCHAR(255) NOT NULL,
    quantidade_movimentada INTEGER NOT NULL,
    valor_venda NUMERIC(38, 2),
    produto_id BIGINT NOT NULL,
    CONSTRAINT pk_movimento_estoque PRIMARY KEY (id),
    CONSTRAINT ck_movimento_estoque_tipo_movimentacao CHECK (tipo_movimentacao IN ('ENTRADA', 'SAIDA')),
    CONSTRAINT fk_movimento_estoque_produto FOREIGN KEY (produto_id) REFERENCES produto (id)
);

-- O PostgreSQL não indexa FK sozinho; os relatórios e a exclusão de produto filtram por elas
CREATE INDEX ix_produto_tipo_produto_id ON produto (tipo_produto_id);
CREATE INDEX ix_movimento_estoque_produto_id ON movimento_estoque (produto_id);
