# TiposDeProdutoApi

All URIs are relative to *http://localhost:8080*

|Method | HTTP request | Description|
|------------- | ------------- | -------------|
|[**atualizar**](#atualizar) | **PUT** /api/tipos-produto/{id} | Atualiza um tipo de produto|
|[**buscarPorId**](#buscarporid) | **GET** /api/tipos-produto/{id} | Busca um tipo de produto pelo ID|
|[**buscarTodos**](#buscartodos) | **GET** /api/tipos-produto | Busca todos os tipos de produto|
|[**criar**](#criar) | **POST** /api/tipos-produto | Cria um novo tipo de produto|
|[**excluir**](#excluir) | **DELETE** /api/tipos-produto/{id} | Exclui um tipo de produto|

# **atualizar**
> TipoProdutoDTO atualizar(tipoProdutoDTO)

Atualiza um tipo de produto existente com os dados fornecidos

### Example

```typescript
import {
    TiposDeProdutoApi,
    Configuration,
    TipoProdutoDTO
} from './api';

const configuration = new Configuration();
const apiInstance = new TiposDeProdutoApi(configuration);

let id: number; //ID do tipo de produto a ser atualizado (default to undefined)
let tipoProdutoDTO: TipoProdutoDTO; //

const { status, data } = await apiInstance.atualizar(
    id,
    tipoProdutoDTO
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **tipoProdutoDTO** | **TipoProdutoDTO**|  | |
| **id** | [**number**] | ID do tipo de produto a ser atualizado | defaults to undefined|


### Return type

**TipoProdutoDTO**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | Tipo de produto atualizado com sucesso |  -  |
|**400** | Dados inválidos fornecidos |  -  |
|**404** | Tipo de produto não encontrado |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **buscarPorId**
> TipoProdutoDTO buscarPorId()

Retorna um tipo de produto específico pelo seu ID

### Example

```typescript
import {
    TiposDeProdutoApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new TiposDeProdutoApi(configuration);

let id: number; //ID do tipo de produto a ser buscado (default to undefined)

const { status, data } = await apiInstance.buscarPorId(
    id
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **id** | [**number**] | ID do tipo de produto a ser buscado | defaults to undefined|


### Return type

**TipoProdutoDTO**

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

# **buscarTodos**
> TipoProdutoDTO buscarTodos()

Retorna uma lista com todos os tipos de produto cadastrados

### Example

```typescript
import {
    TiposDeProdutoApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new TiposDeProdutoApi(configuration);

const { status, data } = await apiInstance.buscarTodos();
```

### Parameters
This endpoint does not have any parameters.


### Return type

**TipoProdutoDTO**

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

# **criar**
> TipoProdutoDTO criar(tipoProdutoDTO)

Cria um novo tipo de produto com os dados fornecidos

### Example

```typescript
import {
    TiposDeProdutoApi,
    Configuration,
    TipoProdutoDTO
} from './api';

const configuration = new Configuration();
const apiInstance = new TiposDeProdutoApi(configuration);

let tipoProdutoDTO: TipoProdutoDTO; //

const { status, data } = await apiInstance.criar(
    tipoProdutoDTO
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **tipoProdutoDTO** | **TipoProdutoDTO**|  | |


### Return type

**TipoProdutoDTO**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**201** | Tipo de produto criado com sucesso |  -  |
|**400** | Dados inválidos fornecidos |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **excluir**
> excluir()

Exclui um tipo de produto existente pelo seu ID

### Example

```typescript
import {
    TiposDeProdutoApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new TiposDeProdutoApi(configuration);

let id: number; //ID do tipo de produto a ser excluído (default to undefined)

const { status, data } = await apiInstance.excluir(
    id
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **id** | [**number**] | ID do tipo de produto a ser excluído | defaults to undefined|


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
|**204** | Tipo de produto excluído com sucesso |  -  |
|**404** | Tipo de produto não encontrado |  -  |
|**400** | Não é possível excluir um tipo de produto que possui produtos associados |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

