package br.com.nexdom.desafio.backend.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração do OpenAPI/Swagger para documentação da API.
 * Esta classe configura as informações básicas da API, como título, descrição,
 * versão, contato, licença e servidores disponíveis.
 */
@Configuration
public class OpenApiConfig {

    /**
     * Configura o OpenAPI com informações detalhadas sobre a API.
     *
     * @return Configuração do OpenAPI
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de Controle de Estoque - Nexdom")
                        .description("API RESTful para gerenciamento completo de estoque, incluindo cadastro de produtos, " +
                                "tipos de produtos, movimentações de entrada e saída, e geração de relatórios. " +
                                "Desenvolvida como parte do desafio FullStack da Nexdom.")
                        .version("1.0.0")
                        .termsOfService("https://www.nexdom.com.br/termos")
                        .contact(new Contact()
                                .name("Equipe de Desenvolvimento Nexdom")
                                .email("dev@nexdom.com.br")
                                .url("https://www.nexdom.com.br/contato"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .externalDocs(new ExternalDocumentation()
                        .description("Documentação completa da API")
                        .url("https://www.nexdom.com.br/docs"))
                .addServersItem(new Server()
                        .url("http://localhost:8080")
                        .description("Servidor de Desenvolvimento"))
                .addServersItem(new Server()
                        .url("https://api.nexdom.com.br")
                        .description("Servidor de Produção"));
    }
}
