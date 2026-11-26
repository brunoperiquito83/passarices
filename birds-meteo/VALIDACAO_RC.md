# Birds Meteo 0.8-rc1 — candidato de validação, NÃO versão final

A base v0.7 foi preservada. Esta revisão concentra-se na compilação e qualidade, não em acrescentar integrações ainda não testadas.

## Alterações
- Sincronização da leitura e escrita de cache entre instâncias do repositório, proteção contra substituição por uma gravação mais antiga.
- Pesquisa: resultados e estado de carregamento protegidos contra conclusão de pedido cancelado.
- GPS: limpar/substituir os dados anteriores antes de publicar a nova posição.
- Cinco testes instrumentados: arranque/navegação, JSON Android, configuração das nove localidades, previsão e ar reais nas nove localidades, serviços partilhados reais. São testes criados, NÃO executados. A verificação de previsões admite a resposta reduzida do fornecedor; não garante todos os campos avançados.
- Pipeline CI: compilar, lint, compilar testes Android, executar em emulador API 35 (incluindo chamadas reais), verificar assinatura debug, publicar APK e SHA-256 apenas se todas as etapas passarem. Relatórios de teste devem ser publicados mesmo em falha. A execução pode falhar por indisponibilidade de fornecedores; isso deve ser registado, não mascarado.

## Evidências locais
`validation/core-test-report.txt`: 58/58 verificações do núcleo Kotlin executadas.
`validation/structure-report.txt`: nove localidades preservadas; seis XML analisados; ausência de SDK/Gradle/ADB, falhas DNS.

## Compilação
Em repositório privado GitHub com Actions disponíveis, colocar os conteúdos deste ZIP na raiz da branch main. O workflow `.github/workflows/build.yml` é disparado por push ou manualmente. Em caso de sucesso, descarregar o artefacto `Birds_Meteo_v0.8_RC_debug` na execução, não um ZIP do código-fonte. A assinatura debug não é a assinatura final de distribuição. Não foi feito upload a nenhum repositório.

Alternativamente, com Android Studio/SDK no Windows, executar `Compilar-Windows.ps1`. Para executar os testes instrumentados num emulador ou dispositivo ligado, executar `gradle --no-daemon :app:connectedDebugAndroidTest` (Gradle 8.9, JDK 17). O script Windows verifica compilação/lint/assinatura, mas não executa os testes instrumentados automaticamente.

## Critérios ainda não cumpridos
- Compilação, lint, testes instrumentados, validação real da rede e instalação no Xiaomi 15.
- Teste de GPS com permissões concedidas/recusadas, atualização manual/automática, offline, widget, notificações, radar, todos os campos avançados e desempenho/bateria. O emulador não comprova comportamento HyperOS.
- Funcionalidades pendentes: satélite nativo (há link externo), raios, marés oficiais, personalização de cartões, anomalias climatológicas, temas completos e fallback independente.
- Assinatura estável para atualização/distribuição.

Não existe APK nesta entrega. O estado é RC para validação; não é uma aplicação concluída.
