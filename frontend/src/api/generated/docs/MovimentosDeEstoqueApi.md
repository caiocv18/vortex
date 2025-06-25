# MovimentosDeEstoqueApi

All URIs are relative to *http://localhost:8080*

|Method | HTTP request | Description|
|------------- | ------------- | -------------|
|[**atualizar2**](#atualizar2) | **PUT** /api/movimentos/{id} | Atualiza um movimento de estoque|
|[**buscarPorId2**](#buscarporid2) | **GET** /api/movimentos/{id} | Busca um movimento de estoque pelo ID|
|[**buscarTodos2**](#buscartodos2) | **GET** /api/movimentos | Busca todos os movimentos de estoque|
|[**criar2**](#criar2) | **POST** /api/movimentos | Cria um novo movimento de estoque|
|[**excluir2**](#excluir2) | **DELETE** /api/movimentos/{id} | Exclui um movimento de estoque|

# **atualizar2**
> MovimentoEstoque atualizar2(movimentoEstoque)

Atualiza um movimento de estoque existente com os dados fornecidos

### Example

```typescript
import {
    MovimentosDeEstoqueApi,
    Configuration,
    MovimentoEstoque
} from './api';

const configuration = new Configuration();
const apiInstance = new MovimentosDeEstoqueApi(configuration);

let id: number; //ID do movimento de estoque a ser atualizado (default to undefined)
let movimentoEstoque: MovimentoEstoque; //

const { status, data } = await apiInstance.atualizar2(
    id,
    movimentoEstoque
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **movimentoEstoque** | **MovimentoEstoque**|  | |
| **id** | [**number**] | ID do movimento de estoque a ser atualizado | defaults to undefined|


### Return type

**MovimentoEstoque**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | Movimento de estoque atualizado com sucesso |  -  |
|**400** | Dados inválidos fornecidos ou estoque insuficiente |  -  |
|**404** | Movimento de estoque ou produto não encontrado |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **buscarPorId2**
> MovimentoEstoque buscarPorId2()

Retorna um movimento de estoque específico pelo seu ID

### Example

```typescript
import {
    MovimentosDeEstoqueApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new MovimentosDeEstoqueApi(configuration);

let id: number; //ID do movimento de estoque a ser buscado (default to undefined)

const { status, data } = await apiInstance.buscarPorId2(
    id
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **id** | [**number**] | ID do movimento de estoque a ser buscado | defaults to undefined|


### Return type

**MovimentoEstoque**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | Operação bem-sucedida |  -  |
|**404** | Movimento de estoque não encontrado |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **buscarTodos2**
> MovimentoEstoque buscarTodos2()

Retorna uma lista com todos os movimentos de estoque cadastrados

### Example

```typescript
import {
    MovimentosDeEstoqueApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new MovimentosDeEstoqueApi(configuration);

const { status, data } = await apiInstance.buscarTodos2();
```

### Parameters
This endpoint does not have any parameters.


### Return type

**MovimentoEstoque**

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

# **criar2**
> MovimentoEstoque criar2(movimentoEstoque)

Cria um novo movimento de estoque com os dados fornecidos

### Example

```typescript
import {
    MovimentosDeEstoqueApi,
    Configuration,
    MovimentoEstoque
} from './api';

const configuration = new Configuration();
const apiInstance = new MovimentosDeEstoqueApi(configuration);

let movimentoEstoque: MovimentoEstoque; //

const { status, data } = await apiInstance.criar2(
    movimentoEstoque
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **movimentoEstoque** | **MovimentoEstoque**|  | |


### Return type

**MovimentoEstoque**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**201** | Movimento de estoque criado com sucesso |  -  |
|**400** | Dados inválidos fornecidos ou estoque insuficiente |  -  |
|**404** | Produto não encontrado |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **excluir2**
> excluir2()

Exclui um movimento de estoque existente pelo seu ID

### Example

```typescript
import {
    MovimentosDeEstoqueApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new MovimentosDeEstoqueApi(configuration);

let id: number; //ID do movimento de estoque a ser excluído (default to undefined)

const { status, data } = await apiInstance.excluir2(
    id
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **id** | [**number**] | ID do movimento de estoque a ser excluído | defaults to undefined|


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
|**204** | Movimento de estoque excluído com sucesso |  -  |
|**404** | Movimento de estoque não encontrado |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

