Crie um script bash que exibe uma animação ASCII de um vórtex espiral clássico girando no sentido horário com centro fixo. O script deve ser compatível com WSL Ubuntu 24 e terminal integrado do VS Code.
Parâmetros obrigatórios:

--size <valor> ou -s <valor>: tamanho do vórtex (1-10, onde 1=muito pequeno, 10=muito grande)
--speed <valor> ou -v <valor>: velocidade da rotação (1-10, onde 1=muito lento, 10=muito rápido)
--color <cor> ou -c <cor>: cor da animação (blue, green)
--duration <segundos> ou -t <segundos>: duração em segundos (0 = infinito até Ctrl+C)

Características técnicas:

Use caracteres ASCII clássicos para espiral: *, +, o, ., -, |, /, \
Implemente cores usando códigos ANSI (azul: \033[34m, verde: \033[32m)
Centro fixo na tela com rotação em camadas concêntricas
Função de limpeza que restaura cursor e limpa tela ao pressionar Ctrl+C
Validação de parâmetros com mensagens de erro específicas
Opção --help ou -h com exemplos de uso

Exemplo de uso:
bash./vortex.sh -s 5 -v 3 -c blue -t 30
./vortex.sh --size 8 --speed 7 --color green --duration 0
Tratamento de erros:

Verificar se todos os parâmetros obrigatórios foram fornecidos
Validar intervalos numéricos (1-10 para size e speed)
Validar cores disponíveis (blue, green)
Exibir mensagem de ajuda para parâmetros inválidos

O script deve funcionar suavemente em ambos os ambientes de terminal especificados.