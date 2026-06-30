# Passarices 0.4.0

Aplicação Android adaptativa para Xiaomi 15 e ChromeOS, com sons ambientes reais processados a partir do dataset ESC-50.

## Como compilar no GitHub

1. Criar um repositório público no GitHub.
2. Fazer upload de todos os ficheiros deste projecto para a raiz do repositório.
3. Abrir o separador **Actions**.
4. Aguardar pelo workflow **Build Passarices APK**.
5. Descarregar o artefacto **Passarices-0.4.0-APK-and-reports**.
6. Dentro do ZIP estará o ficheiro `Passarices-0.4.0.apk`.

## Áudio

O workflow descarrega o ESC-50, selecciona 30 classes, concatena gravações reais WAV, normaliza e converte para Ogg Vorbis 48 kHz.

O lote 0.4.0 usa a licença do ESC-50: CC BY-NC 4.0. Uso recomendado: pessoal/não comercial. Os créditos ficam em `docs/audio_manifest_0.4.0.json` e no artefacto de build.

## Importante

Este projecto inclui uma chave de assinatura local para simplificar a instalação pessoal. Como o repositório será público, esta chave não deve ser usada para uma aplicação comercial ou distribuída publicamente fora deste contexto. Para uso pessoal, mantém as actualizações consistentes desde que a mesma chave seja preservada.
