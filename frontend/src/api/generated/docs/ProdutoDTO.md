# ProdutoDTO

DTO para transferência de dados de Produto

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **number** | ID do produto | [optional] [default to undefined]
**descricao** | **string** | Descrição do produto | [default to undefined]
**valorFornecedor** | **number** | Valor do fornecedor | [default to undefined]
**quantidadeEmEstoque** | **number** | Quantidade em estoque | [optional] [default to 0]
**tipoProdutoId** | **number** | ID do tipo de produto | [default to undefined]

## Example

```typescript
import { ProdutoDTO } from './api';

const instance: ProdutoDTO = {
    id,
    descricao,
    valorFornecedor,
    quantidadeEmEstoque,
    tipoProdutoId,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
