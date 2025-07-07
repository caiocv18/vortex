import React, { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';

const DashboardPage: React.FC = () => {
  const { user, logout, isAuthenticated } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (!isAuthenticated) {
      navigate('/login');
    }
  }, [isAuthenticated, navigate]);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  if (!user) {
    return (
      <div className="dashboard-container">
        <div className="loading-spinner" />
      </div>
    );
  }

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString('pt-BR');
  };

  return (
    <div className="dashboard-container">
      <div className="dashboard-header">
        <h1 className="dashboard-title">🏢 VORTEX Dashboard</h1>
        <p className="dashboard-subtitle">Sistema de Autenticação</p>
      </div>

      <div className="user-info">
        <h2 style={{ color: 'var(--color-heading)', marginBottom: '1rem' }}>
          Informações do Usuário
        </h2>
        
        <div style={{ display: 'grid', gap: '0.75rem' }}>
          <div>
            <strong>Nome:</strong> {user.name}
          </div>
          <div>
            <strong>Email:</strong> {user.email}
          </div>
          <div>
            <strong>ID:</strong> {user.id}
          </div>
          <div>
            <strong>Provedor:</strong> {user.provider}
          </div>
          <div>
            <strong>Roles:</strong> {user.roles.join(', ')}
          </div>
          <div>
            <strong>Email Verificado:</strong> {user.emailVerified ? 'Sim' : 'Não'}
          </div>
          {user.lastLogin && (
            <div>
              <strong>Último Login:</strong> {formatDate(user.lastLogin)}
            </div>
          )}
        </div>
      </div>

      <div className="user-info">
        <h2 style={{ color: 'var(--color-heading)', marginBottom: '1rem' }}>
          Bem-vindo ao VORTEX!
        </h2>
        <p style={{ marginBottom: '1rem' }}>
          Você está autenticado com sucesso no sistema de autenticação do VORTEX.
          Este é um microsserviço de autenticação construído com Quarkus e integrado
          com o sistema principal de controle de estoque.
        </p>
        <p style={{ fontSize: '0.9rem', opacity: 0.8 }}>
          <strong>Funcionalidades disponíveis:</strong>
        </p>
        <ul style={{ marginTop: '0.5rem', paddingLeft: '1.5rem' }}>
          <li>Autenticação local com email e senha</li>
          <li>Autenticação OAuth com Google e GitHub</li>
          <li>Recuperação de senha por email</li>
          <li>Geração de tokens JWT seguros</li>
          <li>Auditoria de eventos de autenticação</li>
        </ul>
      </div>

      <div className="logout-section">
        <button onClick={handleLogout} className="btn-primary">
          Sair do Sistema
        </button>
      </div>
    </div>
  );
};

export default DashboardPage;