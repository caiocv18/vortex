# MovimentoEstoque

Representa um movimento de estoque (entrada ou saída)

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **number** | Identificador único do movimento de estoque (gerado automaticamente) | [optional] [readonly] [default to undefined]
**dataMovimento** | **string** | Data e hora em que o movimento foi registrado (gerado automaticamente) | [optional] [readonly] [default to undefined]
**tipoMovimentacao** | **string** | Tipo de movimentação (ENTRADA aumenta o estoque, SAIDA diminui o estoque) | [default to undefined]
**quantidadeMovimentada** | **number** | Quantidade de itens movimentados (deve ser maior que zero) | [default to undefined]
**valorVenda** | **number** | Valor de venda unitário (calculado automaticamente para SAIDA como 1.35 * valorFornecedor, nulo para ENTRADA) | [optional] [readonly] [default to undefined]
**produtoId** | **number** | Identificador do produto associado ao movimento | [default to undefined]

## Example

```typescript
import { MovimentoEstoque } from './api';

const instance: MovimentoEstoque = {
    id,
    dataMovimento,
    tipoMovimentacao,
    quantidadeMovimentada,
    valorVenda,
    produtoId,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
