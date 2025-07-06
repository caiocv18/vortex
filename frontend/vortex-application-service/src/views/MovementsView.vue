<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useMovementStore } from '@/stores/movement'
import { useProductStore } from '@/stores/product'
import { useForm } from 'vee-validate'
import * as yup from 'yup'
import type { MovimentoEstoque } from '@/api'

const movementStore = useMovementStore()
const productStore = useProductStore()

const dialog = ref(false)
const deleteDialog = ref(false)
const editMode = ref(false)
const selectedMovement = ref<MovimentoEstoque | null>(null)
const search = ref('')
const filterType = ref<'TODOS' | 'ENTRADA' | 'SAIDA'>('TODOS')

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

// Headers da tabela
const headers = [
  { title: 'ID', key: 'id', sortable: true },
  { title: 'Data', key: 'dataMovimento', sortable: true },
  { title: 'Produto', key: 'produto', sortable: false },
  { title: 'Tipo', key: 'tipoMovimentacao', sortable: true },
  { title: 'Quantidade', key: 'quantidadeMovimentada', sortable: true },
  { title: 'Valor Venda', key: 'valorVenda', sortable: true },
  { title: 'Ações', key: 'actions', sortable: false }
]

// Filtro de busca e tipo
const filteredMovements = computed(() => {
  let movements = movementStore.movements

  // Filtro por tipo
  if (filterType.value !== 'TODOS') {
    movements = movements.filter(m => m.tipoMovimentacao === filterType.value)
  }

  // Filtro por busca (produto)
  if (search.value) {
    movements = movements.filter(m => {
      const product = productStore.products.find(p => p.id === m.produtoId)
      return product?.descricao?.toLowerCase().includes(search.value.toLowerCase())
    })
  }

  return movements
})

onMounted(async () => {
  await Promise.all([
    movementStore.fetchMovements(),
    productStore.fetchProducts()
  ])
})

const openDialog = (movement?: MovimentoEstoque) => {
  if (movement) {
    editMode.value = true
    selectedMovement.value = movement
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
}

const closeDialog = () => {
  dialog.value = false
  resetForm()
  selectedMovement.value = null
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
    // Atualizar produtos para refletir mudanças no estoque
    await productStore.fetchProducts()
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
      // Atualizar produtos para refletir mudanças no estoque
      await productStore.fetchProducts()
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

const getProductName = (productId: number | undefined) => {
  if (!productId) return 'N/A'
  const product = productStore.products.find(p => p.id === productId)
  return product?.descricao || 'N/A'
}

const formatDate = (date: string | undefined) => {
  if (!date) return 'N/A'
  return new Date(date).toLocaleString('pt-BR')
}
</script>

<template>
  <v-container fluid class="pa-4">
    <v-row>
      <v-col cols="12">
        <h1 class="text-h4 mb-6">Movimentação de Estoque</h1>
      </v-col>
    </v-row>

    <v-row>
      <v-col cols="12">
        <v-card>
          <v-card-title>
            <v-row align="center">
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
            <v-data-table
              :headers="headers"
              :items="filteredMovements"
              :loading="movementStore.loading"
              class="elevation-1"
            >
              <template v-slot:item.dataMovimento="{ item }">
                {{ formatDate(item.dataMovimento) }}
              </template>
              <template v-slot:item.produto="{ item }">
                {{ getProductName(item.produtoId) }}
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
            </v-data-table>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <!-- Dialog de criação/edição -->
    <v-dialog v-model="dialog" max-width="600px">
      <v-card>
        <v-card-title>
          <span class="text-h5">{{ editMode ? 'Editar' : 'Nova' }} Movimentação</span>
        </v-card-title>
        <v-card-text>
          <v-form @submit.prevent="onSubmit">
            <v-select
              v-model="produtoId"
              v-bind="produtoIdProps"
              :error-messages="errors.produtoId"
              :items="productStore.products"
              item-title="descricao"
              item-value="id"
              label="Produto"
              required
            >
              <template v-slot:item="{ props, item }">
                <v-list-item v-bind="props" :subtitle="`Estoque: ${item.raw.quantidadeEmEstoque || 0}`">
                </v-list-item>
              </template>
            </v-select>

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
              <div>Estoque atual: {{ productStore.products.find(p => p.id === produtoId)?.quantidadeEmEstoque || 0 }} unidades</div>
              <div v-if="productStore.products.find(p => p.id === produtoId)?.valorFornecedor">
                Valor de venda será: {{ formatCurrency((productStore.products.find(p => p.id === produtoId)?.valorFornecedor || 0) * 1.35) }}
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
        <v-card-title class="text-h5">Confirmar Exclusão</v-card-title>
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