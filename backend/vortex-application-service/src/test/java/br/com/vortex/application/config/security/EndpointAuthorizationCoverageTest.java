package br.com.vortex.application.config.security;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.access.expression.ExpressionUtils;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.SecurityAnnotationScanner;
import org.springframework.security.core.annotation.SecurityAnnotationScanners;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.util.SimpleMethodInvocation;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guarda da matriz de papéis: percorre todos os handlers registrados no Spring MVC e, para cada um em
 * {@code /api/**} (menos {@code /api/test/**}), resolve o {@code @PreAuthorize} efetivo com o mesmo scanner
 * da segurança de método (anotação do método vence a da classe; meta-anotações como {@code @CanWrite}
 * contam) e avalia a expressão. Falha quando um handler não tem {@code @PreAuthorize}, quando um usuário
 * autenticado sem papel passa, ou quando um handler de escrita deixa VIEWER passar.
 */
@SpringBootTest
@ActiveProfiles("test")
class EndpointAuthorizationCoverageTest {

    /** Handlers que existiam quando a matriz foi escrita (tipos, produtos e movimentos 5 cada, relatórios 2, filas 5). */
    private static final int KNOWN_API_HANDLERS = 22;

    private static final Set<RequestMethod> READ_METHODS = EnumSet.of(RequestMethod.GET, RequestMethod.HEAD, RequestMethod.OPTIONS);

    private final SecurityAnnotationScanner<PreAuthorize> preAuthorizeScanner =
            SecurityAnnotationScanners.requireUnique(PreAuthorize.class);

    private final DefaultMethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();

    private final ExpressionParser expressionParser = new SpelExpressionParser();

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void everyApiHandlerRequiresARoleAndNoWriteHandlerAdmitsViewer() {
        List<String> violations = new ArrayList<>();
        int checked = 0;

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo mapping = entry.getKey();
            HandlerMethod handler = entry.getValue();
            Set<String> paths = mapping.getPatternValues();
            if (paths.stream().noneMatch(path -> path.startsWith("/api/"))
                    || paths.stream().allMatch(path -> path.startsWith("/api/test/"))) {
                continue;
            }
            checked++;

            String name = handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName()
                    + " " + mapping.getMethodsCondition().getMethods() + " " + paths;
            PreAuthorize preAuthorize = preAuthorizeScanner.scan(handler.getMethod(), handler.getBeanType());
            if (preAuthorize == null) {
                violations.add(name + ": sem @PreAuthorize");
                continue;
            }
            if (grants(preAuthorize.value(), handler)) {
                violations.add(name + ": admite usuário autenticado sem papel (" + preAuthorize.value() + ")");
            }
            if (isWrite(mapping) && grants(preAuthorize.value(), handler, "ROLE_VIEWER")) {
                violations.add(name + ": handler de escrita admite VIEWER (" + preAuthorize.value() + ")");
            }
        }

        assertTrue(checked >= KNOWN_API_HANDLERS,
                "esperava ao menos " + KNOWN_API_HANDLERS + " handlers em /api/**, achou " + checked);
        assertEquals(List.of(), violations);
    }

    /** Sem método declarado, o mapeamento aceita qualquer verbo: conta como escrita. */
    private static boolean isWrite(RequestMappingInfo mapping) {
        Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
        return methods.isEmpty() || !READ_METHODS.containsAll(methods);
    }

    private boolean grants(String expression, HandlerMethod handler, String... authorities) {
        Authentication user = UsernamePasswordAuthenticationToken.authenticated("probe", null,
                Set.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
        MethodInvocation invocation = new SimpleMethodInvocation(null, handler.getMethod());
        EvaluationContext context = expressionHandler.createEvaluationContext(() -> user, invocation);
        return ExpressionUtils.evaluateAsBoolean(expressionParser.parseExpression(expression), context);
    }
}
