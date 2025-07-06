# ProdutosApi

All URIs are relative to *http://localhost:8080*

|Method | HTTP request | Description|
|------------- | ------------- | -------------|
|[**atualizar1**](#atualizar1) | **PUT** /api/produtos/{id} | Atualiza um produto|
|[**buscarPorId1**](#buscarporid1) | **GET** /api/produtos/{id} | Busca um produto pelo ID|
|[**buscarTodos1**](#buscartodos1) | **GET** /api/produtos | Busca todos os produtos|
|[**criar1**](#criar1) | **POST** /api/produtos | Cria um novo produto|
|[**excluir1**](#excluir1) | **DELETE** /api/produtos/{id} | Exclui um produto|

# **atualizar1**
> ProdutoDTO atualizar1(produtoDTO)

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

const { status, data } = await apiInstance.atualizar1(
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

# **buscarPorId1**
> ProdutoDTO buscarPorId1()

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

const { status, data } = await apiInstance.buscarPorId1(
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

# **buscarTodos1**
> ProdutoDTO buscarTodos1()

Retorna uma lista com todos os produtos cadastrados

### Example

```typescript
import {
    ProdutosApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new ProdutosApi(configuration);

const { status, data } = await apiInstance.buscarTodos1();
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

# **criar1**
> ProdutoDTO criar1(produtoDTO)

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

const { status, data } = await apiInstance.criar1(
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

# **excluir1**
> excluir1()

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

const { status, data } = await apiInstance.excluir1(
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

