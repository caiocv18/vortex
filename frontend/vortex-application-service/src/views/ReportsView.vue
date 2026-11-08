<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useTheme } from 'vuetify'
import { useReportStore } from '@/stores/report'
import { useProductTypeStore } from '@/stores/productType'
import { chartThemeColors, foldTail } from '@/themes/chartPalette'
import { Bar, Pie } from 'vue-chartjs'
import {
  Chart as ChartJS,
  Title,
  Tooltip,
  Legend,
  BarElement,
  CategoryScale,
  LinearScale,
  ArcElement
} from 'chart.js'

ChartJS.register(Title, Tooltip, Legend, BarElement, CategoryScale, LinearScale, ArcElement)

const reportStore = useReportStore()
const productTypeStore = useProductTypeStore()

const selectedTypeId = ref<number | null>(null)
const loadingProductsByType = ref(false)
const loadingProfitByProduct = ref(false)

// Cores dos gráficos a partir do tema ativo do Vuetify (ver themes/chartPalette.ts). A store de
// tema troca o tema por theme.change(); theme.current muda, os computeds abaixo recalculam e o
// vue-chartjs redesenha os gráficos com as cores novas.
const theme = useTheme()
const chartTheme = computed(() => chartThemeColors(theme.current.value))

// Dados para o gráfico de produtos por tipo
const productsByTypeChartData = computed(() => {
  const labels = reportStore.productsByType.map(p => p.descricao || '')
  const stockData = reportStore.productsByType.map(p => p.quantidadeEmEstoque || 0)
  const salesData = reportStore.productsByType.map(p => p.totalSaidas || 0)
  const [stockColor, salesColor] = chartTheme.value.series

  return {
    labels,
    datasets: [
      {
        label: 'Estoque Atual',
        backgroundColor: stockColor,
        data: stockData
      },
      {
        label: 'Total de Saídas',
        backgroundColor: salesColor,
        data: salesData
      }
    ]
  }
})

// Dados para o gráfico de lucro por produto: uma cor por fatia, sem repetir; passando das cores
// da paleta, os produtos de menor lucro viram uma fatia "Outros" (a tabela ao lado lista todos)
const profitByProductChartData = computed(() => {
  const sortedProducts = [...reportStore.profitByProduct].sort((a, b) => (b.lucroTotal || 0) - (a.lucroTotal || 0))
  const slices = foldTail(sortedProducts.map(p => ({ label: p.descricao || '', value: p.lucroTotal || 0 })))
  const { series, other, surface } = chartTheme.value

  return {
    labels: slices.map(s => s.label),
    datasets: [{
      label: 'Lucro Total (R$)',
      backgroundColor: slices.map((s, i) => (s.other ? other : series[i])),
      // Contorno na cor do card: separa as fatias sem o traço branco padrão do Chart.js,
      // que aparecia como linha clara no tema escuro
      borderColor: surface,
      borderWidth: 2,
      data: slices.map(s => s.value)
    }]
  }
})

// Opções dos gráficos: legenda, rótulos e grade na cor de texto do tema (no tema escuro o cinza
// padrão do Chart.js ficava quase ilegível sobre o card)
const legendLabels = computed(() => ({ color: chartTheme.value.text }))

const barChartOptions = computed(() => {
  const axis = {
    ticks: { color: chartTheme.value.text },
    grid: { color: chartTheme.value.grid },
    border: { color: chartTheme.value.grid }
  }
  return {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'top' as const,
        labels: legendLabels.value
      },
      title: {
        display: false
      }
    },
    scales: {
      x: axis,
      y: axis
    }
  }
})

const pieChartOptions = computed(() => ({
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: {
      position: 'right' as const,
      labels: legendLabels.value
    },
    title: {
      display: false
    }
  }
}))

onMounted(async () => {
  await productTypeStore.fetchProductTypes()
  await loadProfitReport()
})

const loadProductsByTypeReport = async () => {
  if (!selectedTypeId.value) return
  
  loadingProductsByType.value = true
  try {
    await reportStore.fetchProductsByType(selectedTypeId.value)
  } catch (error) {
    console.error('Erro ao carregar relatório:', error)
  } finally {
    loadingProductsByType.value = false
  }
}

const loadProfitReport = async () => {
  loadingProfitByProduct.value = true
  try {
    await reportStore.fetchProfitByProduct()
  } catch (error) {
    console.error('Erro ao carregar relatório de lucro:', error)
  } finally {
    loadingProfitByProduct.value = false
  }
}

const formatCurrency = (value: number) => {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL'
  }).format(value)
}

// Estatísticas
const totalRevenue = computed(() => {
  return reportStore.profitByProduct.reduce((total, product) => {
    const units = product.totalUnidadesVendidas || 0
    const profit = product.lucroTotal || 0
    const revenue = units > 0 ? (profit / 0.35) * 1.35 : 0
    return total + revenue
  }, 0)
})

const totalCost = computed(() => {
  return totalRevenue.value - reportStore.totalProfit
})

const profitMargin = computed(() => {
  if (totalRevenue.value === 0) return 0
  return (reportStore.totalProfit / totalRevenue.value) * 100
})
</script>

<template>
  <!-- Espaçamento vertical: só o gap do grid do Vuetify 4 (24px entre linhas); sem margem no
       título nem mt-* nas linhas, que davam 48px num lugar e 16px no outro -->
  <v-container fluid class="pa-4">
    <v-row>
      <v-col cols="12">
        <h1 class="text-headline-large">Relatórios</h1>
      </v-col>
    </v-row>

    <!-- Cards de estatísticas -->
    <v-row>
      <v-col cols="12" md="3">
        <v-card>
          <v-card-text>
            <div class="text-label-medium">Receita Total</div>
            <div class="text-headline-small font-weight-bold">{{ formatCurrency(totalRevenue) }}</div>
          </v-card-text>
        </v-card>
      </v-col>
      <v-col cols="12" md="3">
        <v-card>
          <v-card-text>
            <div class="text-label-medium">Custo Total</div>
            <div class="text-headline-small font-weight-bold">{{ formatCurrency(totalCost) }}</div>
          </v-card-text>
        </v-card>
      </v-col>
      <v-col cols="12" md="3">
        <v-card>
          <v-card-text>
            <div class="text-label-medium">Lucro Total</div>
            <div class="text-headline-small font-weight-bold text-success">{{ formatCurrency(reportStore.totalProfit) }}</div>
          </v-card-text>
        </v-card>
      </v-col>
      <v-col cols="12" md="3">
        <v-card>
          <v-card-text>
            <div class="text-label-medium">Margem de Lucro</div>
            <div class="text-headline-small font-weight-bold">{{ profitMargin.toFixed(1) }}%</div>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <!-- Relatório de Produtos por Tipo -->
    <v-row>
      <v-col cols="12">
        <v-card>
          <!-- text-wrap: no celular o título quebra em vez de cortar -->
          <v-card-title class="text-wrap">
            <v-icon class="mr-2">mdi-package-variant</v-icon>
            Relatório de Produtos por Tipo
          </v-card-title>
          <v-card-text>
            <v-row>
              <v-col cols="12" md="6">
                <v-select
                  v-model="selectedTypeId"
                  :items="productTypeStore.productTypes"
                  item-title="nome"
                  item-value="id"
                  label="Selecione um tipo de produto"
                  @update:model-value="loadProductsByTypeReport"
                ></v-select>
              </v-col>
            </v-row>

            <v-progress-linear
              v-if="loadingProductsByType"
              indeterminate
              color="primary"
            ></v-progress-linear>

            <div v-if="reportStore.productsByType.length > 0 && !loadingProductsByType">
              <v-row>
                <v-col cols="12" md="8">
                  <div style="height: 400px;">
                    <Bar
                      :data="productsByTypeChartData"
                      :options="barChartOptions"
                    />
                  </div>
                </v-col>
                <v-col cols="12" md="4">
                  <v-list density="compact">
                    <v-list-subheader>Detalhes</v-list-subheader>
                    <v-list-item
                      v-for="product in reportStore.productsByType"
                      :key="product.id"
                    >
                      <v-list-item-title>{{ product.descricao }}</v-list-item-title>
                      <v-list-item-subtitle>
                        Estoque: {{ product.quantidadeEmEstoque }} | Saídas: {{ product.totalSaidas }}
                      </v-list-item-subtitle>
                    </v-list-item>
                  </v-list>
                </v-col>
              </v-row>
            </div>

            <v-alert
              v-else-if="!loadingProductsByType && selectedTypeId"
              type="info"
              variant="tonal"
            >
              Nenhum produto encontrado para este tipo.
            </v-alert>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <!-- Relatório de Lucro por Produto -->
    <v-row>
      <v-col cols="12">
        <v-card>
          <v-card-title class="text-wrap">
            <v-icon class="mr-2">mdi-cash-multiple</v-icon>
            Relatório de Lucro por Produto
          </v-card-title>
          <v-card-text>
            <v-progress-linear
              v-if="loadingProfitByProduct"
              indeterminate
              color="primary"
            ></v-progress-linear>

            <div v-if="reportStore.profitByProduct.length > 0 && !loadingProfitByProduct">
              <v-row>
                <v-col cols="12" md="6">
                  <div style="height: 400px;">
                    <Pie
                      :data="profitByProductChartData"
                      :options="pieChartOptions"
                    />
                  </div>
                </v-col>
                <v-col cols="12" md="6">
                  <v-data-table
                    :headers="[
                      { title: 'Produto', key: 'descricao', sortable: true },
                      { title: 'Unidades Vendidas', key: 'totalUnidadesVendidas', sortable: true },
                      { title: 'Lucro Total', key: 'lucroTotal', sortable: true }
                    ]"
                    :items="reportStore.profitByProduct"
                    :items-per-page="10"
                    density="compact"
                  >
                    <template v-slot:item.lucroTotal="{ item }">
                      <span class="text-success font-weight-bold">
                        {{ formatCurrency(item.lucroTotal || 0) }}
                      </span>
                    </template>
                  </v-data-table>
                </v-col>
              </v-row>
            </div>

            <v-alert
              v-else-if="!loadingProfitByProduct"
              type="info"
              variant="tonal"
            >
              Nenhuma venda realizada ainda.
            </v-alert>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template> 