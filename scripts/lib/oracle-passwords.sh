#!/bin/bash
# Senhas do Oracle local (ambiente prd), carregado pelo start-vortex.sh e pelo
# infra/scripts/start-oracle.sh; quem carrega define print_color e as cores.
#
# O Oracle grava ORACLE_PASSWORD (SYS e SYSTEM) e ORACLE_APP_USER_PASSWORD (vortex_user) na primeira
# subida do volume vortex-oracle-data, e trocar a variável depois não muda o banco. Os compose não
# têm padrão para nenhuma das duas, e o backend no perfil prd recusa senha de banco fraca.

# Imprime por que uma senha de banco é fraca, ou nada quando ela serve. Mesma regra do
# isWeakPassword do SecurityConfigurationValidator: menos de 12 caracteres, só letras, só números,
# ou com password ou admin em qualquer caixa.
#
# Corpo em subshell com LC_ALL=C, para a regra não mudar com o locale de quem roda: em C, ${#} conta
# bytes e [a-zA-Z] é só ASCII. Por isso o caractere fora do ASCII imprimível é recusado antes, já que
# o Java conta caracteres e uma senha curta com acento passaria aqui e cairia no backend, com o
# volume já criado com ela.
db_password_weakness() (
    LC_ALL=C
    local value=$1
    local lower=${value,,}
    if [[ -z "$value" ]]; then
        echo "não está definida"
    elif [[ "$value" =~ [^[:print:]] ]]; then
        echo "tem caractere fora do ASCII imprimível (gere com openssl rand -hex 24)"
    elif (( ${#value} < 12 )); then
        echo "tem ${#value} caracteres (o mínimo é 12)"
    elif [[ "$lower" == *password* || "$lower" == *admin* ]]; then
        echo "contém password ou admin"
    elif [[ "$value" =~ ^[a-zA-Z]+$ || "$value" =~ ^[0-9]+$ ]]; then
        echo "só tem letras ou só tem números"
    fi
)

# Confere as duas senhas antes de qualquer container: imprime cada problema e a dica, e devolve 1
# se alguma não serve. A dica gera só as que falharam: regerar uma senha boa de um volume já criado
# daria ORA-01017 na subida seguinte.
check_oracle_passwords() {
    local var reason
    local -a failed=()
    for var in ORACLE_PASSWORD ORACLE_APP_USER_PASSWORD; do
        reason=$(db_password_weakness "${!var:-}")
        [[ -z "$reason" ]] && continue
        print_color "$RED" "❌ Oracle: $var $reason."
        failed+=("$var=\"\$(openssl rand -hex 24)\"")
    done
    (( ${#failed[@]} == 0 )) && return 0
    print_color "$YELLOW" "💡 O Oracle grava as duas senhas na primeira subida do volume, e o backend no perfil prd recusa senha de banco fraca. Gere e exporte antes de subir:"
    print_color "$YELLOW" "   export ${failed[*]}"
    print_color "$YELLOW" "   Volume já criado: o banco guarda as senhas com que nasceu. Se forem os antigos padrões (Oracle_1234, vortex_password), troque no banco como no troubleshooting do infra/docs/README-ORACLE.md ou recrie com ./start-vortex.sh --clean."
    return 1
}
