<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useProductStore } from '@/stores/product'
import { useProductTypeStore } from '@/stores/productType'
import { useForm } from 'vee-validate'
import * as yup from 'yup'
import type { ProdutoDTO } from '@/api'

const productStore = useProductStore()
const productTypeStore = useProductTypeStore()

const dialog = ref(false)
const deleteDialog = ref(false)
const editMode = ref(false)
const selectedProduct = ref<ProdutoDTO | null>(null)
const search = ref('')

// Schema de validação
const schema = yup.object({
  descricao: yup.string().required('Descrição é obrigatória').min(3, 'Descrição deve ter pelo menos 3 caracteres'),
  valorFornecedor: yup.number().required('Valor do fornecedor é obrigatório').positive('Valor deve ser positivo'),
  tipoProdutoId: yup.number().required('Tipo de produto é obrigatório')
})

const { handleSubmit, resetForm, defineField, errors } = useForm({
  validationSchema: schema
})

const [descricao, descricaoProps] = defineField('descricao')
const [valorFornecedor, valorFornecedorProps] = defineField('valorFornecedor')
const [tipoProdutoId, tipoProdutoIdProps] = defineField('tipoProdutoId')

// Headers da tabela
const headers = [
  { title: 'ID', key: 'id', sortable: true },
  { title: 'Descrição', key: 'descricao', sortable: true },
  { title: 'Tipo', key: 'tipo', sortable: false },
  { title: 'Valor Fornecedor', key: 'valorFornecedor', sortable: true },
  { title: 'Estoque', key: 'quantidadeEmEstoque', sortable: true },
  { title: 'Ações', key: 'actions', sortable: false }
]

// Filtro de busca
const filteredProducts = computed(() => {
  if (!search.value) return productStore.products
  return productStore.products.filter(p =>
    p.descricao?.toLowerCase().includes(search.value.toLowerCase())
  )
})

onMounted(async () => {
  await Promise.all([
    productStore.fetchProducts(),
    productTypeStore.fetchProductTypes()
  ])
})

const openDialog = (product?: ProdutoDTO) => {
  if (product) {
    editMode.value = true
    selectedProduct.value = product
    descricao.value = product.descricao || ''
    valorFornecedor.value = product.valorFornecedor || 0
    tipoProdutoId.value = product.tipoProdutoId || 0
  } else {
    editMode.value = false
    selectedProduct.value = null
    resetForm()
  }
  dialog.value = true
}

const closeDialog = () => {
  dialog.value = false
  resetForm()
  selectedProduct.value = null
}

const openDeleteDialog = (product: ProdutoDTO) => {
  selectedProduct.value = product
  deleteDialog.value = true
}

const onSubmit = handleSubmit(async (values) => {
  try {
    if (editMode.value && selectedProduct.value?.id) {
      await productStore.updateProduct(selectedProduct.value.id, {
        id: selectedProduct.value.id,
        descricao: values.descricao,
        valorFornecedor: values.valorFornecedor,
        tipoProdutoId: values.tipoProdutoId,
        quantidadeEmEstoque: selectedProduct.value.quantidadeEmEstoque
      })
    } else {
      await productStore.createProduct({
        descricao: values.descricao,
        valorFornecedor: values.valorFornecedor,
        tipoProdutoId: values.tipoProdutoId
      })
    }
    closeDialog()
  } catch (error) {
    console.error('Erro ao salvar produto:', error)
  }
})

const deleteProduct = async () => {
  if (selectedProduct.value?.id) {
    try {
      await productStore.deleteProduct(selectedProduct.value.id)
      deleteDialog.value = false
      selectedProduct.value = null
    } catch (error) {
      console.error('Erro ao excluir produto:', error)
    }
  }
}

const formatCurrency = (value: number | undefined) => {
  if (value === undefined) return 'R$ 0,00'
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL'
  }).format(value)
}

const getProductTypeName = (typeId: number | undefined) => {
  if (!typeId) return 'N/A'
  const type = productTypeStore.productTypes.find(t => t.id === typeId)
  return type?.nome || 'N/A'
}
</script>

<template>
  <v-container fluid class="pa-4">
    <v-row>
      <v-col cols="12">
        <h1 class="text-h4 mb-6">Produtos</h1>
      </v-col>
    </v-row>

    <v-row>
      <v-col cols="12">
        <v-card>
          <v-card-title>
            <v-row align="center">
              <v-col cols="12" md="6">
                <v-text-field
                  v-model="search"
                  append-icon="mdi-magnify"
                  label="Pesquisar"
                  single-line
                  hide-details
                  variant="outlined"
                  density="compact"
                ></v-text-field>
              </v-col>
              <v-col cols="12" md="6" class="text-right">
                <v-btn
                  color="primary"
                  @click="openDialog()"
                  prepend-icon="mdi-plus"
                >
                  Novo Produto
                </v-btn>
              </v-col>
            </v-row>
          </v-card-title>

          <v-card-text>
            <v-data-table
              :headers="headers"
              :items="filteredProducts"
              :loading="productStore.loading"
              :search="search"
              class="elevation-1"
            >
              <template v-slot:item.tipo="{ item }">
                {{ getProductTypeName(item.tipoProdutoId) }}
              </template>
              <template v-slot:item.valorFornecedor="{ item }">
                {{ formatCurrency(item.valorFornecedor) }}
              </template>
              <template v-slot:item.quantidadeEmEstoque="{ item }">
                <v-chip
                  :color="(item.quantidadeEmEstoque || 0) < 10 ? 'error' : 'success'"
                  size="small"
                >
                  {{ item.quantidadeEmEstoque || 0 }}
                </v-chip>
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
          <span class="text-h5">{{ editMode ? 'Editar' : 'Novo' }} Produto</span>
        </v-card-title>
        <v-card-text>
          <v-form @submit.prevent="onSubmit">
            <v-text-field
              v-model="descricao"
              v-bind="descricaoProps"
              :error-messages="errors.descricao"
              label="Descrição do Produto"
              required
            ></v-text-field>

            <v-text-field
              v-model.number="valorFornecedor"
              v-bind="valorFornecedorProps"
              :error-messages="errors.valorFornecedor"
              label="Valor do Fornecedor"
              prefix="R$"
              type="number"
              step="0.01"
              required
            ></v-text-field>

            <v-select
              v-model="tipoProdutoId"
              v-bind="tipoProdutoIdProps"
              :error-messages="errors.tipoProdutoId"
              :items="productTypeStore.productTypes"
              item-title="nome"
              item-value="id"
              label="Tipo de Produto"
              required
            ></v-select>

            <v-alert
              v-if="editMode"
              type="info"
              variant="tonal"
              class="mt-3"
            >
              Estoque atual: {{ selectedProduct?.quantidadeEmEstoque || 0 }} unidades
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
            :loading="productStore.loading"
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
          Tem certeza que deseja excluir o produto "{{ selectedProduct?.descricao }}"?
          <v-alert type="warning" variant="tonal" class="mt-3">
            Esta ação não pode ser desfeita e todos os movimentos associados serão afetados.
          </v-alert>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn variant="text" @click="deleteDialog = false">Cancelar</v-btn>
          <v-btn
            color="error"
            variant="elevated"
            @click="deleteProduct"
            :loading="productStore.loading"
          >
            Excluir
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-container>
</template> 