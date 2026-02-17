# Anti-Bet 💰

**Anti-Bet** é um aplicativo Android gratuito que ajuda brasileiros que lutam contra a dependência em apostas esportivas e jogos de azar a:

1. **Registrar quanto dinheiro economizaram** ao resistir à vontade de apostar
2. **Receber alertas gentis** quando visitam sites de apostas
3. **Acompanhar seu progresso** com gráficos e estatísticas

## 🌟 Funcionalidades Principais

### 📊 Registro de Economia
- Registre o valor que você **quase apostou** mas resistiu
- Adicione notas opcionais (ex: "Jogo do Flamengo")
- Acompanhe o total economizado ao longo do tempo
- Veja gráficos diário, semanal e mensal

### 🛡️ Proteção via VPN
- Sistema de VPN local que detecta tentativas de acesso a sites de apostas
- Notificação imediata quando um site de aposta é detectado
- Possibilidade de **bloquear** o acesso (funcionalidade opcional)
- Lista de domínios de apostas brasileiros e internacionais

### ⏰ Lembretes Diários
- Notificações periódicas para registrar economias
- Acompanhamento de sequência (streak) de dias sem aposta
- Mensagens motivacionais

## 🔒 Privacidade e Segurança

- **Dados armazenados localmente** no dispositivo
- VPN funciona **100% no dispositivo** - nenhum dado é enviado para servidores externos
- Inspeção de tráfego limitada apenas a domínios de apostas conhecidos
- Total conformidade com políticas do Google Play

## 📱 Tecnologias

- **Linguagem**: Kotlin
- **UI**: Jetpack Compose
- **Banco de dados**: Room (SQLite)
- **Navegação**: Navigation Compose
- **Background**: WorkManager
- **VPN**: Android VpnService API

## 📋 Requisitos

- Android 8.0 (API 26) ou superior
- Sem necessidade de root

## 📥 Instalação

O APK de debug está disponível em:
```
app/build/outputs/apk/debug/app-debug.apk
```

Para instalar no dispositivo:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 📁 Estrutura do Projeto

```
app/src/main/java/com/antibet/
├── data/
│   ├── local/
│   │   ├── dao/         # Room DAOs
│   │   ├── database/    # Room Database
│   │   └── entity/     # Room Entities
│   └── repository/     # Repositories
├── domain/
│   ├── model/          # Domain models
│   └── usecase/        # Use cases
├── presentation/
│   ├── add/            # Tela de registro
│   ├── home/          # Tela principal
│   ├── navigation/    # Navegação
│   ├── protection/    # Tela de proteção VPN
│   └── theme/         # Tema do app
├── service/
│   ├── notification/   # Workers de notificação
│   └── vpn/            # Serviço VPN
└── util/               # Utilitários
```

## 📄 Políticas

Este app foi desenvolvido seguindo as diretrizes do Google Play para uso de VpnService, sendo categorizado como **controle parental / segurança de dispositivo**. O app:

- Não monetiza dados de tráfego
- Não colet histórico de navegação
- Usa inspeção de DNS local apenas para domínios de apostas
- Fornece transparência total ao usuário

## 📝 Licença

Este projeto é de uso pessoal/educacional.
