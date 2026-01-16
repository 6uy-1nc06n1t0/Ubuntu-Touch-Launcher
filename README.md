# Ubuntu Touch Launcher

Bem-vindo ao **Ubuntu Touch Launcher**.

Este é um projeto que permite mudar o launcher padrão do sistema para uma aparência inspirada no **Ubuntu Touch**, trazendo uma experiência alternativa ao padrão do Android.

---

## Como usar

Para utilizar o projeto, siga os passos abaixo:

1. Instale o aplicativo
2. Siga as recomendações do setup inicial
3. Conceda as permissões obrigatórias

---

## Permissões obrigatórias

Necessárias para que o launcher funcione corretamente:

* Definir como launcher padrão
* Acesso a configurações restritas (em alguns aparelhos)
* Sobreposição de tela
* Alterar configurações do sistema
* Acesso às notificações

---

## Permissões opcionais

Utilizadas apenas caso o sistema não permita navegação por gestos ao trocar o launcher padrão:

* Serviço de acessibilidade

---

## Ocultando a NavBar

Para dispositivos que **não permitem navegação por gestos** ao trocar o launcher padrão.

**ATENÇÃO:**
Em dispositivos **sem root**, este processo **não é persistente** e pode retornar ao padrão do sistema a qualquer momento.

### Requisitos

* Termux instalado e configurado
* Shizuku instalado e configurado
* Criação de script para ocultação da NavBar

### Script de ocultação da NavBar

* Salve o arquivo com extensão .sh e execute utilizando shizuku via termux ou ADB

```sh
#!/system/bin/sh

TARGET_MODE=2
SLEEP_TIME=1

apply_nav() {
    settings put secure navigation_mode $TARGET_MODE
    settings put global policy_control immersive.navigation=*
}

while true; do
    CURRENT_MODE=$(settings get secure navigation_mode)

    if [ "$CURRENT_MODE" != "$TARGET_MODE" ]; then
        apply_nav
    fi

    sleep $SLEEP_TIME
done
```

---
## Funções do Launcher

* Dock lateral que abre a gaveta de aplicativos
* Gaveta de aplicativos
* Dock secundário que abre sobre qualquer tela
* Gaveta secundária que abre sobre qualquer tela
* Suporte à navegação por gestos
* Opções de troca de wallpaper
* Widgets personalizados
* Suporte à troca de imagem dos ícones
* Suporte à troca de formato dos ícones
* Suporte à ocultação de aplicativos na gaveta

---

## Contribua com o projeto

Se este projeto foi útil para você, considere apoiar o desenvolvimento para garantir manutenção e atualizações contínuas.

### Contribua via PayPal

*Em breve...*

### Contribua via Bitcoin

**Endereço BTC:**

```
dapperparent02@walletofsatoshi.com
```

---

## Licença

Este projeto está licenciado sob a **MIT License**.
