# ProdutoDTO

DTO para transferência de dados de Produto

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**descricao** | **string** | Descrição do produto | [default to undefined]
**id** | **number** | ID do produto | [optional] [default to undefined]
**quantidadeEmEstoque** | **number** | Quantidade em estoque | [optional] [default to 0]
**tipoProdutoId** | **number** | ID do tipo de produto | [default to undefined]
**valorFornecedor** | **number** | Valor do fornecedor | [default to undefined]

## Example

```typescript
import { ProdutoDTO } from './api';

const instance: ProdutoDTO = {
    descricao,
    id,
    quantidadeEmEstoque,
    tipoProdutoId,
    valorFornecedor,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
