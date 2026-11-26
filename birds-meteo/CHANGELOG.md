# v0.8-rc1
- Revisão de cache, pesquisa e GPS; cinco testes instrumentados criados; pipeline Android com emulador e fontes reais; 58 verificações Kotlin passaram. Sem compilação ou execução Android.

# v0.7 — 26/11/2026
- Pesquisa geográfica, crepúsculos, horários lunares USNO a pedido, atividade geomagnética NOAA, normais ERA5 1991–2020 a pedido.
- Efeitos Canvas com pausa; ligação ao visualizador EUMETView (satélite nativo pendente).
- 58 testes JVM aprovados. Nove localidades preservadas. Não houve compilação ou teste Android.
- Workflow e script Windows atualizados; validação de assinaturas e SHA-256 prevista no workflow, não executado.

# v0.6 — 26/11/2026
- Notificações de avisos IPMA e previsão de chuva configuráveis por localidade; deduplicação e permissão explícita.
- Widget Android redimensionável, com dados da localidade principal e indicação de antiguidade.
- Sismicidade IPMA, risco de incêndio rural por DICO e previsão de caudal GloFAS.
- 43 verificações JVM aprovadas; compilação Android e testes de APIs no dispositivo pendentes.
- Nenhum APK gerado. Preservadas as nove localidades e a base v0.5.

# Birds Meteo v0.5 — 26/11/2026

- Mantido o projeto Android v0.4, ID e nove localidades, GPS, navegação e atualizações.
- Radar RainViewer no separador Mapas: mosaico XYZ com mapa base OpenStreetMap, marcador, zoom 3–7, cursor temporal, reprodução, máscara de cobertura, carimbo da imagem, mensagens de falha e cache HTTP. Um único mosaico por instante; sem previsão futura, sem imagens de satélite e sem mapa com arrastamento livre.
- Medições de estações IPMA: GeoJSON agregado, cache partilhada, estação com dados válidos mais próxima até 50 km e observação de menos de 180 minutos; dados -99 tratados como ausentes. Medição não substitui previsão local.
- Script Windows para preparar Gradle 8.9 com checksum, instalar componentes SDK se necessário, compilar, lint e verificar assinatura de desenvolvimento. Não executado no Windows.
- 32 testes JVM aprovados (22 anteriores + 10 novos). Nenhuma build Android executada; SDK/Gradle ausentes e falhas DNS.
- Pendentes: teste real da aplicação e fontes; notificações, widgets, satélite, sismos, incêndios, hidrologia, marés e restantes funcionalidades da especificação.
