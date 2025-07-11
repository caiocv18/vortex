# ProdutosApi

All URIs are relative to *http://localhost:8080/api*

|Method | HTTP request | Description|
|------------- | ------------- | -------------|
|[**createProduto**](#createproduto) | **POST** /produtos | Cria um novo produto|
|[**deleteProduto**](#deleteproduto) | **DELETE** /produtos/{id} | Exclui um produto|
|[**findAllProdutos**](#findallprodutos) | **GET** /produtos | Busca todos os produtos|
|[**findProdutoById**](#findprodutobyid) | **GET** /produtos/{id} | Busca um produto pelo ID|
|[**updateProduto**](#updateproduto) | **PUT** /produtos/{id} | Atualiza um produto|

# **createProduto**
> ProdutoDTO createProduto(produtoDTO)

Cria um novo produto com os dados fornecidos

### Example

```typescript
import {
    ProdutosApi,
    Configuration,
    ProdutoDTO
} from './api';

const configuration = new Configuration();
const apiInstance = new ProdutosApi(configuration);

let produtoDTO: ProdutoDTO; //

const { status, data } = await apiInstance.createProduto(
    produtoDTO
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **produtoDTO** | **ProdutoDTO**|  | |


### Return type

**ProdutoDTO**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**201** | Produto criado com sucesso |  -  |
|**400** | Dados inválidos fornecidos |  -  |
|**404** | Tipo de produto não encontrado |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **deleteProduto**
> deleteProduto()

Exclui um produto existente pelo seu ID

### Example

```typescript
import {
    ProdutosApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new ProdutosApi(configuration);

let id: number; //ID do produto a ser excluído (default to undefined)

const { status, data } = await apiInstance.deleteProduto(
    id
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **id** | [**number**] | ID do produto a ser excluído | defaults to undefined|


### Return type

void (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: Not defined


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**204** | Produto excluído com sucesso |  -  |
|**404** | Produto não encontrado |  -  |
|**400** | Não é possível excluir um produto que possui movimentos de estoque associados |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **findAllProdutos**
> ProdutoDTO findAllProdutos()

Retorna uma lista com todos os produtos cadastrados

### Example

```typescript
import {
    ProdutosApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new ProdutosApi(configuration);

const { status, data } = await apiInstance.findAllProdutos();
```

### Parameters
This endpoint does not have any parameters.


### Return type

**ProdutoDTO**

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

# **findProdutoById**
> ProdutoDTO findProdutoById()

Retorna um produto específico pelo seu ID

### Example

```typescript
import {
    ProdutosApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new ProdutosApi(configuration);

let id: number; //ID do produto a ser buscado (default to undefined)

const { status, data } = await apiInstance.findProdutoById(
    id
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **id** | [**number**] | ID do produto a ser buscado | defaults to undefined|


### Return type

**ProdutoDTO**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | Operação bem-sucedida |  -  |
|**404** | Produto não encontrado |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **updateProduto**
> ProdutoDTO updateProduto(produtoDTO)

Atualiza um produto existente com os dados fornecidos

### Example

```typescript
import {
    ProdutosApi,
    Configuration,
    ProdutoDTO
} from './api';

const configuration = new Configuration();
const apiInstance = new ProdutosApi(configuration);

let id: number; //ID do produto a ser atualizado (default to undefined)
let produtoDTO: ProdutoDTO; //

const { status, data } = await apiInstance.updateProduto(
    id,
    produtoDTO
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **produtoDTO** | **ProdutoDTO**|  | |
| **id** | [**number**] | ID do produto a ser atualizado | defaults to undefined|


### Return type

**ProdutoDTO**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | Produto atualizado com sucesso |  -  |
|**400** | Dados inválidos fornecidos |  -  |
|**404** | Produto ou tipo de produto não encontrado |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

