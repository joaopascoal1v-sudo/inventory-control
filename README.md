# Controle de estoque em Java

Aplicacao simples de controle de estoque com uma janela grafica em Java Swing.
Os produtos ficam salvos em um banco MySQL e continuam disponiveis depois que
o programa e encerrado.

## Requisitos

- JDK 21 ou superior
- Maven 3.9 ou superior

O Maven usa o JDK definido em `JAVA_HOME`; o projeto compila para Java 21.

O Maven baixa automaticamente o driver MySQL JDBC declarado no `pom.xml`.
Tambem e necessario ter um servidor MySQL em execucao e configurar o usuario
de acesso. Ao abrir o projeto no VS Code, instale as extensoes recomendadas:
o pacote Java Extension Pack para desenvolvimento Java/Maven e uma extensao
MySQL para consultar o banco.

## Executar

Na raiz do projeto, execute:

```bash
mvn compile exec:java
```

Na primeira execucao, o banco `estoque` e criado automaticamente, junto com
as tabelas `produtos`, `fornecedores` e `usuarios`. A tabela `fornecedores` armazena
nome, CNPJ (opcional e unico), telefone, e-mail e endereco. O usuario MySQL
precisa ter permissao para criar o banco; se nao tiver, crie-o antes
(`CREATE DATABASE estoque;`). A janela permite
cadastrar, listar, atualizar, remover produtos e registrar entradas e saidas
do estoque. Clique em um produto da tabela para preencher os campos.

Na primeira abertura, cadastre o administrador inicial na tela apresentada e
crie uma senha com pelo menos 8 caracteres. Nas proximas aberturas, entre com
esse e-mail e senha. O administrador pode cadastrar funcionarios pelo botao
no topo da janela; ambos os perfis podem operar o estoque e cadastrar
fornecedores na aba correspondente.

Configure o usuario e a senha do MySQL nas variaveis de ambiente do Windows,
sem grava-los no codigo. No PowerShell, use:

```bash
$env:DB_USER = 'estoque_app'
$env:DB_PASSWORD = 'sua_senha'
mvn compile exec:java
```

Se ainda nao houver um banco ou usuario para a aplicacao, conecte ao MySQL
com uma conta administradora e execute:

```sql
CREATE DATABASE estoque;
CREATE USER 'estoque_app'@'localhost' IDENTIFIED BY 'sua_senha';
GRANT ALL PRIVILEGES ON estoque.* TO 'estoque_app'@'localhost';
```

Por padrao, a aplicacao conecta em `jdbc:mysql://localhost:3306/estoque`. Para
usar outro host, porta ou banco, defina `DB_URL`, por exemplo:

```bash
$env:DB_URL = 'jdbc:mysql://localhost:3306/estoque?createDatabaseIfNotExist=true'
mvn compile exec:java
```

O arquivo `estoque.db` do SQLite nao e importado automaticamente; os dados
existentes nele precisam ser migrados para o MySQL separadamente.

## Estrutura

- `entities`: entidade e regras basicas do produto.
- `repository`: contrato e implementacao JDBC para persistencia.
- `service`: operacoes de negocio do estoque.
- `security`: hash e verificacao de senhas dos usuarios.
- `database`: conexao e inicializacao do esquema MySQL.
- `app`: `Main` inicializa a aplicacao e `UI` mostra a janela grafica.

## Fornecedores e usuarios

O cadastro de fornecedores esta disponivel por `FornecedorService`, com nome
obrigatorio e CNPJ, telefone, e-mail e endereco opcionais. O CNPJ e unico no
banco quando informado.

O primeiro administrador e criado pela tela inicial. Depois, um administrador
autenticado pode cadastrar funcionarios pelo botao da janela. O login e feito
por e-mail e senha; as senhas precisam ter pelo menos 8 caracteres e sao
armazenadas como hash PBKDF2, nunca em texto simples. Ambos os perfis podem
operar o estoque e cadastrar fornecedores; somente administradores podem
cadastrar funcionarios.
