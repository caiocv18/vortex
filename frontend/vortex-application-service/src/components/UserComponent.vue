<template>
  <v-card>
    <v-card-title>
      <v-icon left>mdi-account-circle</v-icon>
      Informações do Usuário
    </v-card-title>
    
    <v-card-text v-if="authStore.user">
      <v-list dense>
        <v-list-item>
          <v-list-item-content>
            <v-list-item-title>Nome</v-list-item-title>
            <v-list-item-subtitle>{{ authStore.user.name }}</v-list-item-subtitle>
          </v-list-item-content>
        </v-list-item>
        
        <v-list-item>
          <v-list-item-content>
            <v-list-item-title>Email</v-list-item-title>
            <v-list-item-subtitle>{{ authStore.user.email }}</v-list-item-subtitle>
          </v-list-item-content>
        </v-list-item>
        
        <v-list-item>
          <v-list-item-content>
            <v-list-item-title>Provedor</v-list-item-title>
            <v-list-item-subtitle>{{ authStore.user.provider || 'Local' }}</v-list-item-subtitle>
          </v-list-item-content>
        </v-list-item>
        
        <v-list-item>
          <v-list-item-content>
            <v-list-item-title>Perfis</v-list-item-title>
            <v-list-item-subtitle>
              <v-chip
                v-for="role in authStore.user.roles"
                :key="role"
                small
                class="ma-1"
                color="primary"
              >
                {{ role }}
              </v-chip>
            </v-list-item-subtitle>
          </v-list-item-content>
        </v-list-item>
        
        <v-list-item v-if="authStore.user.lastLogin">
          <v-list-item-content>
            <v-list-item-title>Último Login</v-list-item-title>
            <v-list-item-subtitle>
              {{ formatDate(authStore.user.lastLogin) }}
            </v-list-item-subtitle>
          </v-list-item-content>
        </v-list-item>
      </v-list>
    </v-card-text>
    
    <v-card-actions>
      <v-spacer></v-spacer>
      <v-btn
        color="error"
        variant="text"
        @click="handleLogout"
      >
        <v-icon left>mdi-logout</v-icon>
        Sair
      </v-btn>
    </v-card-actions>
  </v-card>
</template>

<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

function formatDate(dateString: string): string {
  return new Date(dateString).toLocaleString('pt-BR')
}

function handleLogout() {
  authStore.logout()
}
</script>