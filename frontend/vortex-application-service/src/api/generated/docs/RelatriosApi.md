# RelatriosApi

All URIs are relative to *http://localhost:8080/api*

|Method | HTTP request | Description|
|------------- | ------------- | -------------|
|[**gerarRelatorioLucroPorProduto**](#gerarrelatoriolucroporproduto) | **GET** /relatorios/lucro-por-produto | Gera relatório de lucro por produto|
|[**gerarRelatorioProdutosPorTipo**](#gerarrelatorioprodutosportipo) | **GET** /relatorios/produtos-por-tipo | Gera relatório de produtos por tipo|

# **gerarRelatorioLucroPorProduto**
> LucroPorProdutoDTO gerarRelatorioLucroPorProduto()

Retorna uma lista com ID/descrição do produto, total de unidades vendidas e lucro total

### Example

```typescript
import {
    RelatriosApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new RelatriosApi(configuration);

const { status, data } = await apiInstance.gerarRelatorioLucroPorProduto();
```

### Parameters
This endpoint does not have any parameters.


### Return type

**LucroPorProdutoDTO**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | Operação bem-sucedida |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **gerarRelatorioProdutosPorTipo**
> ProdutoPorTipoDTO gerarRelatorioProdutosPorTipo()

Retorna uma lista de produtos do tipo especificado, com quantidade total de saídas e estoque atual

### Example

```typescript
import {
    RelatriosApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new RelatriosApi(configuration);

let tipoProdutoId: number; //ID do tipo de produto (default to undefined)

const { status, data } = await apiInstance.gerarRelatorioProdutosPorTipo(
    tipoProdutoId
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **tipoProdutoId** | [**number**] | ID do tipo de produto | defaults to undefined|


### Return type

**ProdutoPorTipoDTO**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | Operação bem-sucedida |  -  |
|**404** | Tipo de produto não encontrado |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

