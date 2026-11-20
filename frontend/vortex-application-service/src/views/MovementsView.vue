<script setup lang="ts">
import { ref, computed, onBeforeUnmount, watch } from 'vue'
import { useMovementStore } from '@/stores/movement'
import { useProductStore } from '@/stores/product'
import { useForm } from 'vee-validate'
import * as yup from 'yup'
import type { MovimentoEstoque, ProdutoDTO } from '@/api'
import {
  ITEMS_PER_PAGE_OPTIONS,
  SEARCH_DEBOUNCE_MS,
  isSameTableState,
  type TableOptions,
} from '@/stores/pagination'

const movementStore = useMovementStore()
const productStore = useProductStore()

const dialog = ref(false)
const deleteDialog = ref(false)
const editMode = ref(false)
const selectedMovement = ref<MovimentoEstoque | null>(null)
// search é o que está no campo; searchQuery, o texto que já foi para o servidor (com debounce).
// A tabela recebe searchQuery: ao mudar, ela volta para a página 1 e emite update:options.
const search = ref(movementStore.q)
const searchQuery = ref(movementStore.q)
const filterType = ref<'TODOS' | 'ENTRADA' | 'SAIDA'>(movementStore.tipo ?? 'TODOS')
let searchTimer: ReturnType<typeof setTimeout> | undefined
let productSearchTimer: ReturnType<typeof setTimeout> | undefined

watch(search, (text) => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    searchQuery.value = (text ?? '').trim()
  }, SEARCH_DEBOUNCE_MS)
})

// O filtro Todos/Entradas/Saídas vai como tipo e volta para a primeira página
watch(filterType, (type) => {
  void movementStore.fetchMovements({ page: 1, tipo: type === 'TODOS' ? null : type })
})

onBeforeUnmount(() => {
  clearTimeout(searchTimer)
  clearTimeout(productSearchTimer)
})

// Toda paginação, ordenação e busca passa por aqui. A primeira emissão (na montagem) sempre busca;
// depois, só quando as opções diferem da página que a store já pediu. Por isso a tabela recebe
// page, items-per-page e sort-by em mão única (sem v-model): com v-model, o Vuetify grava a store
// antes de emitir update:options, a comparação dá igual e nada é buscado. Assim a store só muda
// dentro do fetch; quando ela muda a página sozinha (filtro de tipo, volta depois de uma
// exclusão), a tabela reemite as opções, que já batem com a store e são ignoradas.
let firstLoad = true
const loadItems = (options: TableOptions) => {
  if (!firstLoad && isSameTableState(options, movementStore)) return
  firstLoad = false
  void movementStore.fetchMovements({
    page: options.page,
    itemsPerPage: options.itemsPerPage,
    sortBy: options.sortBy,
    q: options.search ?? '',
    tipo: filterType.value === 'TODOS' ? null : filterType.value,
  })
}

// Autocomplete de produto do formulário: busca no servidor (descrição, 20 por vez, com debounce).
// chosenProduct guarda o produto escolhido, de onde saem o estoque atual e o preço do aviso, e
// continua na lista mesmo quando uma busca nova não o traz, para o campo manter o rótulo.
const productOptions = ref<ProdutoDTO[]>([])
const productSearch = ref('')
const productSearchLoading = ref(false)
const chosenProduct = ref<ProdutoDTO | null>(null)

const withChosenProduct = (products: ProdutoDTO[]) => {
  const chosen = chosenProduct.value
  if (!chosen || products.some(p => p.id === chosen.id)) return products
  return [chosen, ...products]
}

// Cada busca ganha um número; só a última grava as opções e desliga o loading. Sem isso, uma
// resposta mais antiga que chegasse depois sobrescreveria a lista, e o primeiro finally apagaria
// o loading com outra requisição ainda pendente.
let latestProductSearch = 0
const loadProductOptions = async (text: string) => {
  const current = ++latestProductSearch
  productSearchLoading.value = true
  try {
    const products = await productStore.searchProducts(text)
    if (current === latestProductSearch) productOptions.value = withChosenProduct(products)
  } finally {
    if (current === latestProductSearch) productSearchLoading.value = false
  }
}

watch(productSearch, (text) => {
  clearTimeout(productSearchTimer)
  // Ao escolher um item, o campo passa a mostrar a descrição dele: isso não é uma busca nova
  if (!dialog.value || chosenProduct.value?.descricao === (text ?? '')) return
  productSearchTimer = setTimeout(() => loadProductOptions(text ?? ''), SEARCH_DEBOUNCE_MS)
})

// Schema de validação
const schema = yup.object({
  produtoId: yup.number().required('Produto é obrigatório'),
  tipoMovimentacao: yup.string().oneOf(['ENTRADA', 'SAIDA']).required('Tipo de movimentação é obrigatório'),
  quantidadeMovimentada: yup.number().required('Quantidade é obrigatória').positive('Quantidade deve ser positiva').integer('Quantidade deve ser um número inteiro')
})

const { handleSubmit, resetForm, defineField, errors } = useForm({
  validationSchema: schema
})

const [produtoId, produtoIdProps] = defineField('produtoId')
const [tipoMovimentacao, tipoMovimentacaoProps] = defineField('tipoMovimentacao')
const [quantidadeMovimentada, quantidadeMovimentadaProps] = defineField('quantidadeMovimentada')

// O produto escolhido no autocomplete acompanha o campo produtoId
watch(produtoId, (id) => {
  chosenProduct.value = productOptions.value.find(p => p.id === id) ?? null
})

const chosenProductSalePrice = computed(() => {
  const valorFornecedor = chosenProduct.value?.valorFornecedor
  return valorFornecedor ? valorFornecedor * 1.35 : undefined
})

// Headers da tabela. As keys ordenáveis são propriedades da entidade MovimentoEstoque (o sort vai
// para o servidor como key,asc|desc); a descrição do produto vem pronta da API e não ordena.
const headers = [
  { title: 'ID', key: 'id', sortable: true },
  { title: 'Data', key: 'dataMovimento', sortable: true },
  { title: 'Produto', key: 'produtoDescricao', sortable: false },
  { title: 'Tipo', key: 'tipoMovimentacao', sortable: true },
  { title: 'Quantidade', key: 'quantidadeMovimentada', sortable: true },
  { title: 'Valor Venda', key: 'valorVenda', sortable: true },
  { title: 'Ações', key: 'actions', sortable: false }
]

const openDialog = async (movement?: MovimentoEstoque) => {
  chosenProduct.value = null
  productOptions.value = []
  if (movement) {
    editMode.value = true
    selectedMovement.value = movement
    // O produto atual entra como item inicial, para o autocomplete exibir o rótulo
    if (movement.produtoId) {
      try {
        const product = await productStore.getProductById(movement.produtoId)
        chosenProduct.value = product
        productOptions.value = [product]
      } catch (error) {
        console.error('Erro ao buscar o produto do movimento:', error)
      }
    }
    produtoId.value = movement.produtoId || 0
    tipoMovimentacao.value = movement.tipoMovimentacao || 'ENTRADA'
    quantidadeMovimentada.value = movement.quantidadeMovimentada || 1
  } else {
    editMode.value = false
    selectedMovement.value = null
    resetForm()
    tipoMovimentacao.value = 'ENTRADA'
  }
  dialog.value = true
  await loadProductOptions('')
}

const closeDialog = () => {
  dialog.value = false
  resetForm()
  selectedMovement.value = null
  chosenProduct.value = null
}

const openDeleteDialog = (movement: MovimentoEstoque) => {
  selectedMovement.value = movement
  deleteDialog.value = true
}

const onSubmit = handleSubmit(async (values) => {
  try {
    if (editMode.value && selectedMovement.value?.id) {
      await movementStore.updateMovement(selectedMovement.value.id, {
        id: selectedMovement.value.id,
        produtoId: values.produtoId,
        tipoMovimentacao: values.tipoMovimentacao,
        quantidadeMovimentada: values.quantidadeMovimentada
      })
    } else {
      await movementStore.createMovement({
        produtoId: values.produtoId,
        tipoMovimentacao: values.tipoMovimentacao,
        quantidadeMovimentada: values.quantidadeMovimentada
      })
    }
    closeDialog()
  } catch (error) {
    console.error('Erro ao salvar movimento:', error)
  }
})

const deleteMovement = async () => {
  if (selectedMovement.value?.id) {
    try {
      await movementStore.deleteMovement(selectedMovement.value.id)
      deleteDialog.value = false
      selectedMovement.value = null
    } catch (error) {
      console.error('Erro ao excluir movimento:', error)
    }
  }
}

const formatCurrency = (value: number | undefined) => {
  if (value === undefined) return 'N/A'
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL'
  }).format(value)
}

const formatDate = (date: string | undefined) => {
  if (!date) return 'N/A'
  return new Date(date).toLocaleString('pt-BR')
}
</script>

<template>
  <!-- Sem margem no título: o gap do grid do Vuetify 4 (24px) já separa o título do card -->
  <v-container fluid class="pa-4">
    <v-row>
      <v-col cols="12">
        <h1 class="text-headline-large">Movimentação de Estoque</h1>
      </v-col>
    </v-row>

    <v-row>
      <v-col cols="12">
        <v-card>
          <v-card-title>
            <v-row class="align-center">
              <v-col cols="12" md="4">
                <v-text-field
                  v-model="search"
                  append-icon="mdi-magnify"
                  label="Pesquisar por produto"
                  single-line
                  hide-details
                  variant="outlined"
                  density="compact"
                ></v-text-field>
              </v-col>
              <v-col cols="12" md="4">
                <v-btn-toggle
                  v-model="filterType"
                  mandatory
                  divided
                  density="compact"
                  variant="outlined"
                >
                  <v-btn value="TODOS">Todos</v-btn>
                  <v-btn value="ENTRADA" color="success">Entradas</v-btn>
                  <v-btn value="SAIDA" color="warning">Saídas</v-btn>
                </v-btn-toggle>
              </v-col>
              <v-col cols="12" md="4" class="text-right">
                <v-btn
                  color="primary"
                  @click="openDialog()"
                  prepend-icon="mdi-plus"
                >
                  Nova Movimentação
                </v-btn>
              </v-col>
            </v-row>
          </v-card-title>

          <v-card-text>
            <v-data-table-server
              :page="movementStore.page"
              :items-per-page="movementStore.itemsPerPage"
              :sort-by="movementStore.sortBy"
              :headers="headers"
              :items="movementStore.items"
              :items-length="movementStore.totalItems"
              :items-per-page-options="ITEMS_PER_PAGE_OPTIONS"
              :loading="movementStore.loading"
              :search="searchQuery"
              class="elevation-1"
              @update:options="loadItems"
            >
              <template v-slot:item.dataMovimento="{ item }">
                {{ formatDate(item.dataMovimento) }}
              </template>
              <template v-slot:item.produtoDescricao="{ item }">
                {{ item.produtoDescricao || 'N/A' }}
              </template>
              <template v-slot:item.tipoMovimentacao="{ item }">
                <v-chip
                  :color="item.tipoMovimentacao === 'ENTRADA' ? 'success' : 'warning'"
                  size="small"
                >
                  <v-icon start size="x-small">
                    {{ item.tipoMovimentacao === 'ENTRADA' ? 'mdi-arrow-down' : 'mdi-arrow-up' }}
                  </v-icon>
                  {{ item.tipoMovimentacao }}
                </v-chip>
              </template>
              <template v-slot:item.valorVenda="{ item }">
                {{ item.tipoMovimentacao === 'SAIDA' ? formatCurrency(item.valorVenda) : '-' }}
              </template>
              <template v-slot:item.actions="{ item }">
                <v-btn
                  icon
                  size="small"
                  variant="text"
                  @click="openDialog(item)"
                >
                  <v-icon>mdi-pencil</v-icon>
                </v-btn>
                <v-btn
                  icon
                  size="small"
                  variant="text"
                  color="error"
                  @click="openDeleteDialog(item)"
                >
                  <v-icon>mdi-delete</v-icon>
                </v-btn>
              </template>
            </v-data-table-server>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <!-- Dialog de criação/edição -->
    <v-dialog v-model="dialog" max-width="600px">
      <v-card>
        <v-card-title>
          <span class="text-headline-small">{{ editMode ? 'Editar' : 'Nova' }} Movimentação</span>
        </v-card-title>
        <v-card-text>
          <v-form @submit.prevent="onSubmit">
            <!-- no-filter: a lista já vem filtrada do servidor -->
            <v-autocomplete
              v-model="produtoId"
              v-model:search="productSearch"
              v-bind="produtoIdProps"
              :error-messages="errors.produtoId"
              :items="productOptions"
              :loading="productSearchLoading"
              item-title="descricao"
              item-value="id"
              label="Produto"
              no-filter
              required
            >
              <template v-slot:item="{ props, item }">
                <v-list-item v-bind="props" :subtitle="`Estoque: ${item.quantidadeEmEstoque || 0}`">
                </v-list-item>
              </template>
            </v-autocomplete>

            <v-radio-group
              v-model="tipoMovimentacao"
              v-bind="tipoMovimentacaoProps"
              :error-messages="errors.tipoMovimentacao"
              inline
            >
              <v-radio label="Entrada" value="ENTRADA" color="success"></v-radio>
              <v-radio label="Saída" value="SAIDA" color="warning"></v-radio>
            </v-radio-group>

            <v-text-field
              v-model.number="quantidadeMovimentada"
              v-bind="quantidadeMovimentadaProps"
              :error-messages="errors.quantidadeMovimentada"
              label="Quantidade"
              type="number"
              min="1"
              required
            ></v-text-field>

            <v-alert
              v-if="tipoMovimentacao === 'SAIDA' && produtoId"
              type="info"
              variant="tonal"
              class="mt-3"
            >
              <div>Estoque atual: {{ chosenProduct?.quantidadeEmEstoque || 0 }} unidades</div>
              <div v-if="chosenProductSalePrice !== undefined">
                Valor de venda será: {{ formatCurrency(chosenProductSalePrice) }}
              </div>
            </v-alert>
          </v-form>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn variant="text" @click="closeDialog">Cancelar</v-btn>
          <v-btn
            color="primary"
            variant="elevated"
            @click="onSubmit"
            :loading="movementStore.loading"
          >
            {{ editMode ? 'Salvar' : 'Criar' }}
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Dialog de confirmação de exclusão -->
    <v-dialog v-model="deleteDialog" max-width="400px">
      <v-card>
        <v-card-title class="text-headline-small">Confirmar Exclusão</v-card-title>
        <v-card-text>
          Tem certeza que deseja excluir esta movimentação?
          <v-alert type="warning" variant="tonal" class="mt-3">
            Esta ação não pode ser desfeita e o estoque será ajustado.
          </v-alert>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn variant="text" @click="deleteDialog = false">Cancelar</v-btn>
          <v-btn
            color="error"
            variant="elevated"
            @click="deleteMovement"
            :loading="movementStore.loading"
          >
            Excluir
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-container>
</template> 