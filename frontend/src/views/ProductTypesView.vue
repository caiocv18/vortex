<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useProductTypeStore } from '@/stores/productType'
import { useForm } from 'vee-validate'
import * as yup from 'yup'
import type { TipoProdutoDTO } from '@/api'

const productTypeStore = useProductTypeStore()

const dialog = ref(false)
const deleteDialog = ref(false)
const editMode = ref(false)
const selectedProductType = ref<TipoProdutoDTO | null>(null)
const search = ref('')

// Schema de validação
const schema = yup.object({
  nome: yup.string().required('Nome é obrigatório').min(3, 'Nome deve ter pelo menos 3 caracteres')
})

const { handleSubmit, resetForm, defineField, errors } = useForm({
  validationSchema: schema
})

const [nome, nomeProps] = defineField('nome')

// Headers da tabela
const headers = [
  { title: 'ID', key: 'id', sortable: true },
  { title: 'Nome', key: 'nome', sortable: true },
  { title: 'Ações', key: 'actions', sortable: false }
]

// Filtro de busca
const filteredProductTypes = computed(() => {
  if (!search.value) return productTypeStore.productTypes
  return productTypeStore.productTypes.filter(pt =>
    pt.nome?.toLowerCase().includes(search.value.toLowerCase())
  )
})

onMounted(() => {
  productTypeStore.fetchProductTypes()
})

const openDialog = (productType?: TipoProdutoDTO) => {
  if (productType) {
    editMode.value = true
    selectedProductType.value = productType
    nome.value = productType.nome || ''
  } else {
    editMode.value = false
    selectedProductType.value = null
    resetForm()
  }
  dialog.value = true
}

const closeDialog = () => {
  dialog.value = false
  resetForm()
  selectedProductType.value = null
}

const openDeleteDialog = (productType: TipoProdutoDTO) => {
  selectedProductType.value = productType
  deleteDialog.value = true
}

const onSubmit = handleSubmit(async (values) => {
  try {
    if (editMode.value && selectedProductType.value?.id) {
      await productTypeStore.updateProductType(selectedProductType.value.id, {
        id: selectedProductType.value.id,
        nome: values.nome
      })
    } else {
      await productTypeStore.createProductType({
        nome: values.nome
      })
    }
    closeDialog()
  } catch (error) {
    console.error('Erro ao salvar tipo de produto:', error)
  }
})

const deleteProductType = async () => {
  if (selectedProductType.value?.id) {
    try {
      await productTypeStore.deleteProductType(selectedProductType.value.id)
      deleteDialog.value = false
      selectedProductType.value = null
    } catch (error) {
      console.error('Erro ao excluir tipo de produto:', error)
    }
  }
}
</script>

<template>
  <v-container fluid class="pa-4">
    <v-row>
      <v-col cols="12">
        <h1 class="text-h4 mb-6">Tipos de Produto</h1>
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
                  Novo Tipo de Produto
                </v-btn>
              </v-col>
            </v-row>
          </v-card-title>

          <v-card-text>
            <v-data-table
              :headers="headers"
              :items="filteredProductTypes"
              :loading="productTypeStore.loading"
              :search="search"
              class="elevation-1"
            >
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
    <v-dialog v-model="dialog" max-width="500px">
      <v-card>
        <v-card-title>
          <span class="text-h5">{{ editMode ? 'Editar' : 'Novo' }} Tipo de Produto</span>
        </v-card-title>
        <v-card-text>
          <v-form @submit.prevent="onSubmit">
            <v-text-field
              v-model="nome"
              v-bind="nomeProps"
              :error-messages="errors.nome"
              label="Nome do Tipo de Produto"
              required
            ></v-text-field>
          </v-form>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn variant="text" @click="closeDialog">Cancelar</v-btn>
          <v-btn
            color="primary"
            variant="elevated"
            @click="onSubmit"
            :loading="productTypeStore.loading"
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
          Tem certeza que deseja excluir o tipo de produto "{{ selectedProductType?.nome }}"?
          <v-alert type="warning" variant="tonal" class="mt-3">
            Esta ação não pode ser desfeita e todos os produtos associados serão afetados.
          </v-alert>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn variant="text" @click="deleteDialog = false">Cancelar</v-btn>
          <v-btn
            color="error"
            variant="elevated"
            @click="deleteProductType"
            :loading="productTypeStore.loading"
          >
            Excluir
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-container>
</template> 