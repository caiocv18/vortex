package br.com.vortex.application.config;

import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Normalização do OpenAPI para o snapshot {@code docs/application/openapi-backend.json}, que é a entrada do
 * {@code npm run generate-api} do front. O mesmo texto sai do {@code /v3/api-docs} real e do snapshot, então a
 * comparação só acusa mudança de contrato, nunca de formatação. A normalização é idempotente: aplicada ao
 * próprio resultado, devolve o mesmo texto.
 *
 * <ul>
 *     <li>o base path {@code /api} sai dos paths e entra no {@code servers}, que é o {@code BASE_PATH} do cliente
 *     gerado; a porta do servidor fica fixa em 8080;</li>
 *     <li>só a API de estoque entra (tipos de produto, produtos, movimentos e relatórios): filas, {@code /api/test}
 *     e health não fazem parte do cliente do front, e os schemas e tags que só eles usavam saem junto;</li>
 *     <li>as chaves dos objetos ficam em ordem alfabética e as tags pelo nome, para a ordem não depender da ordem
 *     em que o Spring registrou os controllers;</li>
 *     <li>JSON com recuo de 2 espaços, {@code "chave": valor}, UTF-8 sem escapar acento e quebra de linha no fim.</li>
 * </ul>
 */
final class OpenApiSnapshot {

    static final String BASE_PATH = "/api";
    static final List<String> RECURSOS = List.of("/tipos-produto", "/produtos", "/movimentos", "/relatorios");

    private static final String RECUO = "  ";
    private static final String PREFIXO_SCHEMA = "#/components/schemas/";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private OpenApiSnapshot() {
    }

    /**
     * @param json OpenAPI em JSON, do {@code /v3/api-docs} ou do snapshot
     * @return o JSON normalizado, pronto para comparar ou gravar
     */
    @SuppressWarnings("unchecked")
    static String normalizar(String json) {
        Map<String, Object> spec = new LinkedHashMap<>(MAPPER.readValue(json, Map.class));
        fixarServidores(spec);
        Map<String, Object> paths = soAApiDeEstoque((Map<String, Object>) spec.getOrDefault("paths", Map.of()));
        spec.put("paths", paths);
        podarSchemas(spec, paths);
        podarTags(spec, paths);

        StringBuilder out = new StringBuilder();
        escrever(ordenar(spec), out, "");
        return out.append('\n').toString();
    }

    @SuppressWarnings("unchecked")
    private static void fixarServidores(Map<String, Object> spec) {
        Object servidores = spec.get("servers");
        if (!(servidores instanceof List<?> lista)) {
            return;
        }
        List<Object> fixos = new ArrayList<>();
        for (Object item : lista) {
            Map<String, Object> servidor = new LinkedHashMap<>((Map<String, Object>) item);
            String url = String.valueOf(servidor.get("url"))
                    .replaceFirst("^(https?://localhost):\\d+", "$1:8080")
                    .replaceFirst("/+$", "");
            servidor.put("url", url.endsWith(BASE_PATH) ? url : url + BASE_PATH);
            fixos.add(servidor);
        }
        spec.put("servers", fixos);
    }

    private static Map<String, Object> soAApiDeEstoque(Map<String, Object> paths) {
        Map<String, Object> mantidos = new LinkedHashMap<>();
        paths.forEach((path, operacoes) -> {
            String semBase = path.startsWith(BASE_PATH + "/") ? path.substring(BASE_PATH.length()) : path;
            if (RECURSOS.stream().anyMatch(r -> semBase.equals(r) || semBase.startsWith(r + "/"))) {
                mantidos.put(semBase, operacoes);
            }
        });
        return mantidos;
    }

    /** Mantém só os schemas que os paths mantidos alcançam, direta ou indiretamente, por {@code $ref}. */
    @SuppressWarnings("unchecked")
    private static void podarSchemas(Map<String, Object> spec, Map<String, Object> paths) {
        if (!(spec.get("components") instanceof Map<?, ?> componentesLidos)
                || !(componentesLidos.get("schemas") instanceof Map<?, ?> schemasLidos)) {
            return;
        }
        Map<String, Object> componentes = new LinkedHashMap<>((Map<String, Object>) componentesLidos);
        Map<String, Object> schemas = (Map<String, Object>) schemasLidos;

        Set<String> alcancados = new HashSet<>();
        List<String> pendentes = new ArrayList<>();
        refs(paths, pendentes);
        while (!pendentes.isEmpty()) {
            String nome = pendentes.removeLast();
            if (alcancados.add(nome) && schemas.containsKey(nome)) {
                refs(schemas.get(nome), pendentes);
            }
        }

        Map<String, Object> podados = new LinkedHashMap<>();
        schemas.forEach((nome, schema) -> {
            if (alcancados.contains(nome)) {
                podados.put(nome, schema);
            }
        });
        componentes.put("schemas", podados);
        spec.put("components", componentes);
    }

    private static void refs(Object no, List<String> saida) {
        if (no instanceof Map<?, ?> mapa) {
            mapa.forEach((chave, valor) -> {
                if ("$ref".equals(chave) && valor instanceof String ref && ref.startsWith(PREFIXO_SCHEMA)) {
                    saida.add(ref.substring(PREFIXO_SCHEMA.length()));
                } else {
                    refs(valor, saida);
                }
            });
        } else if (no instanceof Collection<?> lista) {
            lista.forEach(item -> refs(item, saida));
        }
    }

    /** Mantém só as tags que alguma operação mantida usa, ordenadas pelo nome. */
    private static void podarTags(Map<String, Object> spec, Map<String, Object> paths) {
        if (!(spec.get("tags") instanceof List<?> tags)) {
            return;
        }
        Set<Object> usadas = new HashSet<>();
        paths.values().forEach(operacoes -> {
            if (operacoes instanceof Map<?, ?> porMetodo) {
                porMetodo.values().forEach(operacao -> {
                    if (operacao instanceof Map<?, ?> op && op.get("tags") instanceof Collection<?> nomes) {
                        usadas.addAll(nomes);
                    }
                });
            }
        });
        spec.put("tags", tags.stream()
                .filter(tag -> tag instanceof Map<?, ?> m && usadas.contains(m.get("name")))
                .sorted((a, b) -> String.valueOf(((Map<?, ?>) a).get("name"))
                        .compareTo(String.valueOf(((Map<?, ?>) b).get("name"))))
                .toList());
    }

    private static Object ordenar(Object no) {
        if (no instanceof Map<?, ?> mapa) {
            Map<String, Object> ordenado = new TreeMap<>();
            mapa.forEach((chave, valor) -> ordenado.put(String.valueOf(chave), ordenar(valor)));
            return ordenado;
        }
        if (no instanceof Collection<?> lista) {
            return lista.stream().map(OpenApiSnapshot::ordenar).toList();
        }
        return no;
    }

    private static void escrever(Object no, StringBuilder out, String recuo) {
        if (no instanceof Map<?, ?> mapa) {
            if (mapa.isEmpty()) {
                out.append("{}");
                return;
            }
            out.append("{\n");
            Iterator<? extends Map.Entry<?, ?>> entradas = mapa.entrySet().iterator();
            while (entradas.hasNext()) {
                Map.Entry<?, ?> entrada = entradas.next();
                out.append(recuo).append(RECUO).append(MAPPER.writeValueAsString(String.valueOf(entrada.getKey())))
                        .append(": ");
                escrever(entrada.getValue(), out, recuo + RECUO);
                out.append(entradas.hasNext() ? ",\n" : "\n");
            }
            out.append(recuo).append('}');
        } else if (no instanceof List<?> lista) {
            if (lista.isEmpty()) {
                out.append("[]");
                return;
            }
            out.append("[\n");
            Iterator<?> itens = lista.iterator();
            while (itens.hasNext()) {
                out.append(recuo).append(RECUO);
                escrever(itens.next(), out, recuo + RECUO);
                out.append(itens.hasNext() ? ",\n" : "\n");
            }
            out.append(recuo).append(']');
        } else {
            out.append(MAPPER.writeValueAsString(no));
        }
    }
}
