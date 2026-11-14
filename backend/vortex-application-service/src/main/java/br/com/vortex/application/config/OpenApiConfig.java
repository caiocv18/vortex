package br.com.vortex.application.config;

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
                        .title("API de Controle de Estoque - Vortex")
                        .description("API RESTful para gerenciamento completo de estoque, incluindo cadastro de produtos, " +
                                "tipos de produtos, movimentações de entrada e saída, e geração de relatórios. " +
                                "Desenvolvida como TCC da pós-graduação em Desenvolvimento Full-Stack da PUCRS.")
                        .version("0.1.1")
                        .contact(new Contact()
                                .name("Vortex no GitHub")
                                .url("https://github.com/caiocv18/vortex"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .externalDocs(new ExternalDocumentation()
                        .description("README do projeto")
                        .url("https://github.com/caiocv18/vortex#readme"))
                .addServersItem(new Server()
                        .url("http://localhost:8080")
                        .description("Servidor de Desenvolvimento"));
    }
}
