-- Inserir tipos de produto
INSERT INTO tipo_produto (id, nome) VALUES (1, 'Eletrônicos');
INSERT INTO tipo_produto (id, nome) VALUES (2, 'Roupas');
INSERT INTO tipo_produto (id, nome) VALUES (3, 'Alimentos');

-- Inserir produtos
INSERT INTO produto (id, descricao, valor_fornecedor, quantidade_em_estoque, tipo_produto_id) VALUES (1, 'Smartphone Samsung Galaxy S21', 1500.00, 10, 1);
INSERT INTO produto (id, descricao, valor_fornecedor, quantidade_em_estoque, tipo_produto_id) VALUES (2, 'Notebook Dell Inspiron', 3000.00, 5, 1);
INSERT INTO produto (id, descricao, valor_fornecedor, quantidade_em_estoque, tipo_produto_id) VALUES (3, 'Camiseta Polo', 50.00, 20, 2);
INSERT INTO produto (id, descricao, valor_fornecedor, quantidade_em_estoque, tipo_produto_id) VALUES (4, 'Calça Jeans', 80.00, 15, 2);
INSERT INTO produto (id, descricao, valor_fornecedor, quantidade_em_estoque, tipo_produto_id) VALUES (5, 'Arroz 5kg', 20.00, 30, 3);
INSERT INTO produto (id, descricao, valor_fornecedor, quantidade_em_estoque, tipo_produto_id) VALUES (6, 'Feijão 1kg', 8.00, 25, 3);

-- Inserir movimentos de estoque (entradas)
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (1, '2023-01-01 10:00:00', 'ENTRADA', 10, NULL, 1);
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (2, '2023-01-01 11:00:00', 'ENTRADA', 5, NULL, 2);
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (3, '2023-01-02 09:00:00', 'ENTRADA', 20, NULL, 3);
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (4, '2023-01-02 10:00:00', 'ENTRADA', 15, NULL, 4);
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (5, '2023-01-03 08:00:00', 'ENTRADA', 30, NULL, 5);
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (6, '2023-01-03 09:00:00', 'ENTRADA', 25, NULL, 6);

-- Inserir movimentos de estoque (saídas)
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (7, '2023-01-04 14:00:00', 'SAIDA', 2, 2025.00, 1);
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (8, '2023-01-04 15:00:00', 'SAIDA', 1, 4050.00, 2);
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (9, '2023-01-05 13:00:00', 'SAIDA', 5, 67.50, 3);
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (10, '2023-01-05 14:00:00', 'SAIDA', 3, 108.00, 4);
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (11, '2023-01-06 12:00:00', 'SAIDA', 10, 27.00, 5);
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (12, '2023-01-06 13:00:00', 'SAIDA', 8, 10.80, 6);

-- Reiniciar sequências
ALTER SEQUENCE tipo_produto_seq RESTART WITH 4;
ALTER SEQUENCE produto_seq RESTART WITH 7;
ALTER SEQUENCE movimento_estoque_seq RESTART WITH 13;