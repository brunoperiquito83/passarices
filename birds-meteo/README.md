# Birds Meteo v0.8-rc1

**Candidato de validação, não APK.** Ler `VALIDACAO_RC.md` para alterações, testes e critérios em falta. A documentação histórica da v0.7 está abaixo.

# Birds Meteo v0.7 — projeto Android (pré-compilação)

**Não é um APK.** Preserva a v0.5 e acrescenta as integrações abaixo. A compilação continua bloqueada no ambiente de trabalho por falta de Android SDK/Gradle e falhas de DNS. Nenhuma execução Android foi comprovada.

## Novidades v0.7
- Pesquisa pelo nome/código postal com Open-Meteo/GeoNames, filtro Portugal por defeito ou pesquisa mundial, até 20 resultados; nome, região, país e coordenadas. Sem adivinhação do distrito IPMA; rejeita duplicados a menos de 50 m. O termo de pesquisa é enviado ao fornecedor.
- Crepúsculos civil (6°), náutico (12°), astronómico (18°), cálculo local aproximado NOAA com fuso IANA. Sem cruzamento calculável nas regiões polares, sem previsão inventada. Horários da Lua a pedido via USNO: nascer, trânsito, pôr e iluminação quando devolvidos; cache diária; desvio UTC do meio-dia local (limitação nos dias de mudança de hora). O serviço USNO foi confirmado documentalmente, mas a resposta ao pedido de teste não foi obtida.
- Kp global NOAA SWPC: JSON real consultado via web, formato de objetos com `time_tag` e `Kp` confirmado. Seleção da última observação válida, indicação de idade superior a 6 h, escala G. Ainda não testado no cliente Android.
- Normais climatológicas mensais 1991–2020: ERA5 diário, 10958 dias, média mensal da temperatura média diária e média dos totais mensais de precipitação. Exige 30 anos completos para cada mês; rejeita duplicados, lacunas e valores inválidos. Consulta manual (potencialmente pesada, sujeita às cotas do Open-Meteo), cache de 365 dias; não é normal oficial de estação nem comparação de anomalia mensal. Não foi obtida resposta real do endpoint neste ambiente.
- Animações desenhadas em Canvas: chuva, neve, nevoeiro, nuvens, estrelas, sol e trovoada com símbolo estático sem flashes; pausa individual no cartão. São representações das condições do modelo, não imagens observadas; não validadas visualmente em Android.
- Ligação ao visualizador oficial EUMETView no separador Mapas; **não é integração nativa de imagens de satélite**. Sem personalização individual dos cartões nesta versão.
- 58 verificações JVM (43 anteriores + 15 novas); testes de estrutura e preservação das localidades. Compilação e instalação continuam bloqueadas: Android SDK/Gradle/ADB ausentes e DNS indisponível. Nenhum APK gerado.
- Workflow GitHub Actions inclui verificação da assinatura debug, SHA-256 e arquivo do APK quando compilado. Não publicado nem executado. Requer um repositório privado do utilizador ou ambiente Windows conforme instruções. APK debug de máquinas diferentes pode ter assinatura diferente.

Fontes v0.7: https://open-meteo.com/en/docs/geocoding-api ; https://aa.usno.navy.mil/data/api.html ; https://aa.usno.navy.mil/faq/RST_defs ; https://gml.noaa.gov/grad/solcalc/glossary.html ; https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json ; https://open-meteo.com/en/docs/historical-weather-api ; https://view.eumetsat.int/ .

## Novidades v0.6
- Notificações opcionais por localidade: avisos oficiais IPMA amarelo/laranja/vermelho ativos ou a começar nas próximas 24 h; deduplicação por distrito, tipo, nível e intervalo. Previsão de chuva para três períodos horários (hora em curso e duas seguintes), probabilidade mínima configurável de 10–100% (70% por defeito), precipitação positiva e máximo de uma notificação por localidade e dia. Ambas desativadas de origem; permissão Android explícita. Sem notificações GPS nesta versão. Sem garantia de entrega atempada. Não constituem um sistema de emergência.
- Widget redimensionável baseado em RemoteViews: localidade principal, temperatura, estado, máximas/mínimas em formato largo, antiguidade dos dados. Atualiza ao abrir/atualizar a aplicação, mudar de localidade principal e no worker. Não há configuração independente por widget nem múltiplos providers.
- Risco rural IPMA: 3 dias e correspondência explícita DICO para as nove localidades. Dados de data diferente ou código desconhecido mostram indisponível; não se atribui o concelho mais próximo. GPS/localidades personalizadas ainda sem DICO. Sismos IPMA: continente e Madeira, raio de 200 km e últimos 30 dias, até 10 eventos mais recentes, magnitude, localização e distância; não é previsão. Caudal GloFAS/Open-Meteo a pedido: 7 dias, estimativa e quantis P25/P75; não calcula risco oficial de cheia.
- Decisões de produto: raio sísmico 200 km, limite de 10 eventos, chuva 70% por defeito, janela de aviso 24 h, widget da localidade principal. Os limites não representam garantias de precisão.
- 43 verificações JVM aprovadas (32 anteriores + 11 novas). Não exercitam JSON real, permissões Android, widgets, rede ou notificações reais. Sem SDK/Gradle; nenhum APK gerado.
- Fontes novas: https://api.ipma.pt/ ; https://open-meteo.com/en/docs/flood-api ; https://developer.android.com/develop/ui/views/appwidgets ; https://developer.android.com/develop/ui/views/notifications/notification-permission . As respostas IPMA de sismos e incêndios foram identificadas via consulta web; não validadas no cliente Android. A API de caudal foi validada documentalmente.
- Compilação: `Compilar-Windows.ps1` e workflow atualizados para nome de saída v0.6. Não executados.

## Novidades v0.5
- Radar no separador Mapas: mosaico de precipitação RainViewer sobre mosaico OpenStreetMap, marcador na localização, zoom 3–7, até 13 imagens históricas, reprodução e seleção temporal. A máscara indica áreas sem cobertura quando disponível; transparência não demonstra ausência de chuva. A resolução máxima e ausência de nowcast e satélite resultam da oferta gratuita RainViewer. Não há arrastamento livre; imagens dependem da rede/cache HTTP. Nenhum radar é simulado.
- Observações reais IPMA: feed `obs-surface.geojson`, seleciona estação mais próxima com dados válidos num raio de 50 km e menos de 3 horas. Hora interpretada como UTC; validar convenção com respostas reais. Mostra estação, distância, tempo e indicadores; -99 significa indisponível. O limite de 50 km é uma decisão de produto, não garantia de representatividade local.
- Script `Compilar-Windows.ps1`: requer Windows com Android Studio/SDK (incluindo Command-line Tools), JDK 17/21 e internet. Usa Gradle 8.9, verifica SHA-256 oficial, compila e executa lint, verifica assinatura debug e copia o resultado para `Birds_Meteo_v0.5_debug.apk`. Executar PowerShell na pasta do projeto: `powershell -NoProfile -ExecutionPolicy Bypass -File .\Compilar-Windows.ps1` (política limitada ao processo; não altera configuração do sistema). Não testado em Windows; não produz assinatura estável de distribuição. Alternativa: Android Studio ou workflow GitHub incluído (não executado).
- Testes: 32 verificações JVM, sem rede, incluindo as 22 anteriores e 10 adicionais. Não incluem interface, HTTP real nem análise de JSON Android.

Fontes: https://api.ipma.pt/ ; https://www.rainviewer.com/api/weather-maps-api.html ; https://www.rainviewer.com/api/transition-faq.html ; https://operations.osmfoundation.org/policies/tiles/ .

## Base v0.4 preservada (documentação histórica atualizada onde necessário)

Projeto nativo Kotlin + Jetpack Compose, destinado ao Xiaomi 15. Inclui as nove localidades aprovadas (coordenadas preservadas da v0.3) e a página de localização atual. Não é um APK, nem uma versão cuja instalação tenha sido confirmada.

## O que está implementado no código
- Cinco separadores: Tempo, Mapas, Comparar, Locais e Opções. Interface escura com fundo dependente das condições; previsão horária com gráfico e lista horizontal, diária até 16 dias e blocos de informação detalhada.
- Atualização ao abrir/reabrir e gesto de puxar para baixo. Pedidos concorrentes limitados a quatro; falha de uma fonte não impede a outra. WorkManager com período pretendido de 30 minutos, cache com validade de 60 minutos para previsão e qualidade do ar e 15 para avisos; Android pode adiar o trabalho. O período do worker não implica atualização de todos os dados a cada 30 minutos.
- Open-Meteo: previsões, catálogo atmosférico, intervalos de 15 minutos quando disponíveis, CAMS/qualidade do ar/pólen, histórico e consulta marítima a pedido; ECMWF, GFS e ICON para comparação a pedido. Pedido de previsão reduzido caso falhe o pedido completo. Não é uma fonte independente alternativa.
- IPMA: avisos por distrito, sem interpretar dados ausentes como ausência de risco; horas em UTC. Localização atual via Android LocationManager e geocodificação inversa, após permissão. Locais acrescentados manualmente por coordenadas, removidos, reordenados e escolha do principal.
- Cache persistente em ficheiros JSON atómicos privados da aplicação (não Room nesta versão); dados antigos identificados, ausência de valores indicada por travessão. Coordenadas GPS são enviadas ao fornecedor meteorológico quando o utilizador usa a página GPS; cache privada mantém coordenadas nas chaves. Não existe transmissão para um servidor próprio. Sem publicidade, conta obrigatória ou chaves API.
- Sol (dados do fornecedor), fase lunar e iluminação aproximadas (cálculo), escala Beaufort, direção do vento em PT.
- O separador Mapas inclui radar experimental v0.5 e ligações externas.

## Compilação (não executada neste ambiente)
Requisitos: JDK 17, Android SDK plataforma 35 e build-tools 35.0.0, Gradle 8.9, ligação aos repositórios Google/Maven. AGP 8.7.3, Kotlin 2.0.21. Abrir a pasta no Android Studio e sincronizar ou executar:

```sh
gradle --no-daemon :app:assembleDebug :app:lintDebug
```

Resultado esperado: `app/build/outputs/apk/debug/app-debug.apk` (assinatura de desenvolvimento, não é assinatura de distribuição). Incluída configuração GitHub Actions em `.github/workflows/build.yml`, para uso num repositório privado do utilizador se desejado; não foi publicada nem executada. Não existe Gradle Wrapper neste pacote porque não foi possível descarregar a distribuição. Para instalar a build de teste no Xiaomi, permitir a instalação de apps da origem usada e abrir o APK efetivamente compilado. Não desativar Play Protect.

## Testes e limites
`tests/run.sh` compila o núcleo Kotlin puro e executa 58 verificações sem rede. Resultados em `validation/core-test-report.txt`. `validation/structure-report.txt`: preservação de nove localidades e verificação XML. A validação original v0.3 está em `validation/validate_v03.py`. Os testes não exercitam a interface Compose, JSON real do Android, WorkManager, localização, permissões nem HTTP. O ambiente não tem SDK Android nem Gradle e o DNS para fornecedores e repositórios falha. Uma análise sintática Kotlin sem dependências não constitui compilação da aplicação. A disponibilidade dos campos, cobertura e cotas API em utilização real ainda necessitam de verificação.

Pendentes: compilação, assinatura estável de distribuição, instalação Xiaomi, testes online das APIs, localização GPS real, validação de todas as integrações e interface; risco de inundação oficial, imagens de satélite nativas, deteção de raios, marés oficiais, comparação de anomalias climatológicas, temas claro/dinâmico integral, personalização de cartões e fontes alternativas independentes. O nome dos locais definidos por coordenadas não deve ser interpretado como garantia de precisão do modelo. A fonte e unidade de cada série são previstas pelo catálogo; ainda requerem confronto com os dados devolvidos.

## Fontes documentais
- https://open-meteo.com/en/docs
- https://open-meteo.com/en/docs/air-quality-api (CAMS ENSEMBLE)
- https://open-meteo.com/en/docs/marine-weather-api
- https://open-meteo.com/en/docs/historical-weather-api
- https://api.ipma.pt/ e https://api.ipma.pt/open-data/distrits-islands.json
- https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler

## Critério de aceitação para a próxima versão
Compilar e instalar um APK assinado, confirmar 9 localidades + GPS em Xiaomi, testar cada fonte com internet e sem internet, comprovar que falhas preservam cache e que os dados sem cobertura não são inventados. Registar a versão Android, resposta das APIs, erros e tempo de carregamento. Não considerar a aplicação pronta antes destas verificações.
