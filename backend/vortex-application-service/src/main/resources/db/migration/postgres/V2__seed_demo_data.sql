-- Dados de exemplo do perfil postgres: os mesmos 10 tipos, 20 produtos e 20 movimentos do import.sql do H2.
-- Migração versionada: o Flyway aplica uma vez só, logo depois da V1, num banco novo. Nenhum boot seguinte repõe
-- o que a demo apagou, porque um movimento reposto voltaria sem o efeito no estoque que o DELETE desfez.
-- Para recomeçar a demo do zero, recrie o banco.
-- Rodar o script de novo à mão não duplica nem rebobina nada:
--   ON CONFLICT DO NOTHING pula as linhas que já existem;
--   o JOIN com a tabela-pai pula a linha cujo pai não existe, em vez de quebrar por FK;
--   o setval só avança cada sequence até o MAX(id), nunca para trás (para trás reemitiria ids já usados);
--   numa tabela vazia o MAX(id) é NULL, o WHERE não casa e a sequence fica como está.
-- Os literais de data levam TIMESTAMP: dentro de um VALUES numa subconsulta eles virariam text.

INSERT INTO tipo_produto (id, nome)
VALUES
    (1, 'Eletrônicos'),
    (2, 'Roupas'),
    (3, 'Alimentos'),
    (4, 'Móveis'),
    (5, 'Livros'),
    (6, 'Esportes'),
    (7, 'Brinquedos'),
    (8, 'Cosméticos'),
    (9, 'Ferramentas'),
    (10, 'Decoração')
ON CONFLICT DO NOTHING;

INSERT INTO produto (id, descricao, valor_fornecedor, quantidade_em_estoque, tipo_produto_id)
SELECT v.id, v.descricao, v.valor_fornecedor, v.quantidade_em_estoque, v.tipo_produto_id
FROM (
    VALUES
        (1, 'Smartphone Samsung Galaxy S21', 1500.00, 10, 1),
        (2, 'Notebook Dell Inspiron', 3000.00, 5, 1),
        (3, 'Camiseta Polo', 50.00, 20, 2),
        (4, 'Calça Jeans', 80.00, 15, 2),
        (5, 'Arroz 5kg', 20.00, 30, 3),
        (6, 'Feijão 1kg', 8.00, 25, 3),
        (7, 'Sofá 3 Lugares', 1200.00, 3, 4),
        (8, 'Mesa de Jantar', 800.00, 4, 4),
        (9, 'Romance Bestseller', 45.00, 12, 5),
        (10, 'Livro Técnico', 120.00, 8, 5),
        (11, 'Bola de Futebol', 60.00, 15, 6),
        (12, 'Raquete de Tênis', 150.00, 6, 6),
        (13, 'Boneca Barbie', 80.00, 10, 7),
        (14, 'Lego Star Wars', 200.00, 7, 7),
        (15, 'Perfume Importado', 250.00, 8, 8),
        (16, 'Kit Maquiagem', 120.00, 12, 8),
        (17, 'Furadeira Elétrica', 180.00, 5, 9),
        (18, 'Jogo de Chaves', 90.00, 9, 9),
        (19, 'Vaso Decorativo', 45.00, 14, 10),
        (20, 'Quadro Artístico', 120.00, 6, 10)
) AS v (id, descricao, valor_fornecedor, quantidade_em_estoque, tipo_produto_id)
JOIN tipo_produto t ON t.id = v.tipo_produto_id
ON CONFLICT DO NOTHING;

INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id)
SELECT v.id, v.data_movimento, v.tipo_movimentacao, v.quantidade_movimentada, v.valor_venda, v.produto_id
FROM (
    VALUES
        (1, TIMESTAMP '2023-01-01 10:00:00', 'ENTRADA', 10, NULL, 1),
        (2, TIMESTAMP '2023-01-01 11:00:00', 'ENTRADA', 5, NULL, 2),
        (3, TIMESTAMP '2023-01-02 09:00:00', 'ENTRADA', 20, NULL, 3),
        (4, TIMESTAMP '2023-01-02 10:00:00', 'ENTRADA', 15, NULL, 4),
        (5, TIMESTAMP '2023-01-03 08:00:00', 'ENTRADA', 30, NULL, 5),
        (6, TIMESTAMP '2023-01-03 09:00:00', 'ENTRADA', 25, NULL, 6),
        (7, TIMESTAMP '2023-01-04 10:00:00', 'ENTRADA', 3, NULL, 7),
        (8, TIMESTAMP '2023-01-04 11:00:00', 'ENTRADA', 4, NULL, 8),
        (9, TIMESTAMP '2023-01-05 09:00:00', 'ENTRADA', 12, NULL, 9),
        (10, TIMESTAMP '2023-01-05 10:00:00', 'ENTRADA', 8, NULL, 10),
        (11, TIMESTAMP '2023-01-06 14:00:00', 'SAIDA', 2, 2025.00, 1),
        (12, TIMESTAMP '2023-01-06 15:00:00', 'SAIDA', 1, 4050.00, 2),
        (13, TIMESTAMP '2023-01-07 13:00:00', 'SAIDA', 5, 67.50, 3),
        (14, TIMESTAMP '2023-01-07 14:00:00', 'SAIDA', 3, 108.00, 4),
        (15, TIMESTAMP '2023-01-08 12:00:00', 'SAIDA', 10, 27.00, 5),
        (16, TIMESTAMP '2023-01-08 13:00:00', 'SAIDA', 8, 10.80, 6),
        (17, TIMESTAMP '2023-01-09 14:00:00', 'SAIDA', 1, 1620.00, 7),
        (18, TIMESTAMP '2023-01-09 15:00:00', 'SAIDA', 2, 1080.00, 8),
        (19, TIMESTAMP '2023-01-10 13:00:00', 'SAIDA', 4, 60.75, 9),
        (20, TIMESTAMP '2023-01-10 14:00:00', 'SAIDA', 2, 162.00, 10)
) AS v (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id)
JOIN produto p ON p.id = v.produto_id
ON CONFLICT DO NOTHING;

SELECT setval('tipo_produto_seq', m) FROM (SELECT MAX(id) AS m FROM tipo_produto) x
WHERE m >= (SELECT last_value FROM tipo_produto_seq);
SELECT setval('produto_seq', m) FROM (SELECT MAX(id) AS m FROM produto) x
WHERE m >= (SELECT last_value FROM produto_seq);
SELECT setval('movimento_estoque_seq', m) FROM (SELECT MAX(id) AS m FROM movimento_estoque) x
WHERE m >= (SELECT last_value FROM movimento_estoque_seq);
