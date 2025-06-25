-- Inserir tipos de produto
INSERT INTO tipo_produto (id, nome) VALUES (1, 'Eletrônicos');
INSERT INTO tipo_produto (id, nome) VALUES (2, 'Roupas');
INSERT INTO tipo_produto (id, nome) VALUES (3, 'Alimentos');
INSERT INTO tipo_produto (id, nome) VALUES (4, 'Móveis');
INSERT INTO tipo_produto (id, nome) VALUES (5, 'Livros');
INSERT INTO tipo_produto (id, nome) VALUES (6, 'Esportes');
INSERT INTO tipo_produto (id, nome) VALUES (7, 'Brinquedos');
INSERT INTO tipo_produto (id, nome) VALUES (8, 'Cosméticos');
INSERT INTO tipo_produto (id, nome) VALUES (9, 'Ferramentas');
INSERT INTO tipo_produto (id, nome) VALUES (10, 'Decoração');

-- Inserir produtos
INSERT INTO produto (id, descricao, valor_fornecedor, quantidade_em_estoque, tipo_produto_id) VALUES (1, 'Smartphone Samsung Galaxy S21', 1500.00, 10, 1);
INSERT INTO produto (id, descricao, valor_fornecedor, quantidade_em_estoque, tipo_produto_id) VALUES (2, 'Notebook Dell Inspiron', 3000.00, 5, 1);
INSERT INTO produto (id, descricao, valor_fornecedor, quantidade_em_estoque, tipo_produto_id) VALUES (3, 'Camiseta Polo', 50.00, 20, 2);
INSERT INTO produto (id, descricao, valor_fornecedor, quantidade_em_estoque, tipo_produto_id) VALUES (4, 'Calça Jeans', 80.00, 15, 2);
INSERT INTO produto (id, descricao, valor_fornecedor, quantidade_em_estoque, tipo_produto_id) VALUES (5, 'Arroz 5kg', 20.00, 30, 3);
INSERT INTO produto (id, descricao, valor_fornecedor, quantidade_em_estoque, tipo_produto_id) VALUES (6, 'Feijão 1kg', 8.00, 25, 3);
INSERT INTO produto (id, descricao, valor_fornecedor, quantidade_em_estoque, tipo_produto_id) VALUES (7, 'Sofá 3 Lugares', 1200.00, 3, 4);
INSERT INTO produto (id, descricao, valor_fornecedor, quantidade_em_estoque, tipo_produto_id) VALUES (8, 'Mesa de Jantar', 800.00, 4, 4);
INSERT INTO produto (id, descricao, valor_fornecedor, quantidade_em_estoque, tipo_produto_id) VALUES (9, 'Romance Bestseller', 45.00, 12, 5);
INSERT INTO produto (id, descricao, valor_fornecedor, quantidade_em_estoque, tipo_produto_id) VALUES (10, 'Livro Técnico', 120.00, 8, 5);
INSERT INTO produto (id, descricao, valor_fornecedor, quantidade_em_estoque, tipo_produto_id) VALUES (11, 'Bola de Futebol', 60.00, 15, 6);
INSERT INTO produto (id, descricao, valor_fornecedor, quantidade_em_estoque, tipo_produto_id) VALUES (12, 'Raquete de Tênis', 150.00, 6, 6);
INSERT INTO produto (id, descricao, valor_fornecedor, quantidade_em_estoque, tipo_produto_id) VALUES (13, 'Boneca Barbie', 80.00, 10, 7);
INSERT INTO produto (id, descricao, valor_fornecedor, quantidade_em_estoque, tipo_produto_id) VALUES (14, 'Lego Star Wars', 200.00, 7, 7);
INSERT INTO produto (id, descricao, valor_fornecedor, quantidade_em_estoque, tipo_produto_id) VALUES (15, 'Perfume Importado', 250.00, 8, 8);
INSERT INTO produto (id, descricao, valor_fornecedor, quantidade_em_estoque, tipo_produto_id) VALUES (16, 'Kit Maquiagem', 120.00, 12, 8);
INSERT INTO produto (id, descricao, valor_fornecedor, quantidade_em_estoque, tipo_produto_id) VALUES (17, 'Furadeira Elétrica', 180.00, 5, 9);
INSERT INTO produto (id, descricao, valor_fornecedor, quantidade_em_estoque, tipo_produto_id) VALUES (18, 'Jogo de Chaves', 90.00, 9, 9);
INSERT INTO produto (id, descricao, valor_fornecedor, quantidade_em_estoque, tipo_produto_id) VALUES (19, 'Vaso Decorativo', 45.00, 14, 10);
INSERT INTO produto (id, descricao, valor_fornecedor, quantidade_em_estoque, tipo_produto_id) VALUES (20, 'Quadro Artístico', 120.00, 6, 10);

-- Inserir movimentos de estoque (entradas)
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (1, '2023-01-01 10:00:00', 'ENTRADA', 10, NULL, 1);
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (2, '2023-01-01 11:00:00', 'ENTRADA', 5, NULL, 2);
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (3, '2023-01-02 09:00:00', 'ENTRADA', 20, NULL, 3);
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (4, '2023-01-02 10:00:00', 'ENTRADA', 15, NULL, 4);
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (5, '2023-01-03 08:00:00', 'ENTRADA', 30, NULL, 5);
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (6, '2023-01-03 09:00:00', 'ENTRADA', 25, NULL, 6);
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (7, '2023-01-04 10:00:00', 'ENTRADA', 3, NULL, 7);
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (8, '2023-01-04 11:00:00', 'ENTRADA', 4, NULL, 8);
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (9, '2023-01-05 09:00:00', 'ENTRADA', 12, NULL, 9);
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (10, '2023-01-05 10:00:00', 'ENTRADA', 8, NULL, 10);

-- Inserir movimentos de estoque (saídas)
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (11, '2023-01-06 14:00:00', 'SAIDA', 2, 2025.00, 1);
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (12, '2023-01-06 15:00:00', 'SAIDA', 1, 4050.00, 2);
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (13, '2023-01-07 13:00:00', 'SAIDA', 5, 67.50, 3);
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (14, '2023-01-07 14:00:00', 'SAIDA', 3, 108.00, 4);
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (15, '2023-01-08 12:00:00', 'SAIDA', 10, 27.00, 5);
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (16, '2023-01-08 13:00:00', 'SAIDA', 8, 10.80, 6);
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (17, '2023-01-09 14:00:00', 'SAIDA', 1, 1620.00, 7);
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (18, '2023-01-09 15:00:00', 'SAIDA', 2, 1080.00, 8);
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (19, '2023-01-10 13:00:00', 'SAIDA', 4, 60.75, 9);
INSERT INTO movimento_estoque (id, data_movimento, tipo_movimentacao, quantidade_movimentada, valor_venda, produto_id) VALUES (20, '2023-01-10 14:00:00', 'SAIDA', 2, 162.00, 10);

-- Reiniciar sequências
ALTER TABLE tipo_produto ALTER COLUMN id RESTART WITH 11;
ALTER TABLE produto ALTER COLUMN id RESTART WITH 21;
ALTER TABLE movimento_estoque ALTER COLUMN id RESTART WITH 21;
