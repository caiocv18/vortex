Chaves RSA 2048 SOMENTE PARA TESTES.

privateKey.pem e publicKey.pem foram geradas com openssl só para a suíte de testes deste
módulo (src/test/resources/application.properties aponta para elas). Não protegem nada:
nunca use este par em dev, leve ou produção.

As chaves reais do serviço ficam em src/main/resources/jwt/ e continuam fora do git
(o .gitignore do módulo libera apenas src/test/resources/jwt-test/*.pem).

Para regerar:
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out privateKey.pem
  openssl pkey -in privateKey.pem -pubout -out publicKey.pem
