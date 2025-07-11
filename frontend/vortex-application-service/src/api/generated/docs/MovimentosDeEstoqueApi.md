# MovimentosDeEstoqueApi

All URIs are relative to *http://localhost:8080/api*

|Method | HTTP request | Description|
|------------- | ------------- | -------------|
|[**createMovimento**](#createmovimento) | **POST** /movimentos | Cria um novo movimento de estoque|
|[**deleteMovimento**](#deletemovimento) | **DELETE** /movimentos/{id} | Exclui um movimento de estoque|
|[**findAllMovimentos**](#findallmovimentos) | **GET** /movimentos | Busca todos os movimentos de estoque|
|[**findMovimentoById**](#findmovimentobyid) | **GET** /movimentos/{id} | Busca um movimento de estoque pelo ID|
|[**updateMovimento**](#updatemovimento) | **PUT** /movimentos/{id} | Atualiza um movimento de estoque|

# **createMovimento**
> MovimentoEstoque createMovimento(movimentoEstoque)

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

const { status, data } = await apiInstance.createMovimento(
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

# **deleteMovimento**
> deleteMovimento()

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

const { status, data } = await apiInstance.deleteMovimento(
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

# **findAllMovimentos**
> MovimentoEstoque findAllMovimentos()

Retorna uma lista com todos os movimentos de estoque cadastrados

### Example

```typescript
import {
    MovimentosDeEstoqueApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new MovimentosDeEstoqueApi(configuration);

const { status, data } = await apiInstance.findAllMovimentos();
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

# **findMovimentoById**
> MovimentoEstoque findMovimentoById()

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

const { status, data } = await apiInstance.findMovimentoById(
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

# **updateMovimento**
> MovimentoEstoque updateMovimento(movimentoEstoque)

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

const { status, data } = await apiInstance.updateMovimento(
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

