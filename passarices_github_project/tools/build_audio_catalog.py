#!/usr/bin/env python3
import argparse, csv, hashlib, json, os, random, shutil, subprocess, sys
from pathlib import Path

TARGETS = [
    ("rain", "Chuva real", "Chuva e tempestades", "rain"),
    ("thunderstorm", "Trovoada real", "Chuva e tempestades", "thunderstorm"),
    ("wind", "Vento real", "Vento", "wind"),
    ("sea_waves", "Ondas do mar", "Mar e água", "sea_waves"),
    ("pouring_water", "Água corrente", "Mar e água", "pouring_water"),
    ("water_drops", "Gotas de água", "Mar e água", "water_drops"),
    ("crackling_fire", "Lareira e fogo", "Fogo", "crackling_fire"),
    ("chirping_birds", "Aves na natureza", "Animais", "chirping_birds"),
    ("crickets", "Grilos", "Animais", "crickets"),
    ("frog", "Rãs", "Animais", "frog"),
    ("dog", "Cães", "Animais", "dog"),
    ("cat", "Gatos", "Animais", "cat"),
    ("cow", "Vacas", "Animais", "cow"),
    ("sheep", "Ovelhas", "Animais", "sheep"),
    ("rooster", "Galos", "Animais", "rooster"),
    ("crow", "Corvos", "Animais", "crow"),
    ("train", "Comboio", "Transportes", "train"),
    ("airplane", "Avião", "Transportes", "airplane"),
    ("helicopter", "Helicóptero", "Transportes", "helicopter"),
    ("engine", "Motor", "Transportes", "engine"),
    ("car_horn", "Buzinas", "Cidade", "car_horn"),
    ("siren", "Sirenes", "Cidade", "siren"),
    ("church_bells", "Sinos", "Cidade", "church_bells"),
    ("washing_machine", "Máquina de lavar", "Casa e electrodomésticos", "washing_machine"),
    ("vacuum_cleaner", "Aspirador", "Casa e electrodomésticos", "vacuum_cleaner"),
    ("clock_tick", "Relógio", "Casa e electrodomésticos", "clock_tick"),
    ("keyboard_typing", "Teclado", "Tecnologia e trabalho", "keyboard_typing"),
    ("door_wood_creaks", "Porta de madeira", "Interiores", "door_wood_creaks"),
    ("footsteps", "Passos", "Interiores", "footsteps"),
    ("clapping", "Aplausos", "Humanos indistintos", "clapping"),
]

LICENSE = "CC BY-NC 4.0"
SOURCE = "ESC-50 dataset"
SOURCE_URL = "https://github.com/karolpiczak/ESC-50"
AUTHOR = "Karol J. Piczak and original Freesound contributors listed in ESC-50 metadata"

def sha256(path):
    h=hashlib.sha256()
    with open(path,'rb') as f:
        for chunk in iter(lambda:f.read(1024*1024), b''):
            h.update(chunk)
    return h.hexdigest()

def run(cmd):
    print("+", " ".join(map(str,cmd)))
    subprocess.check_call(cmd)

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--esc50-dir', required=True)
    ap.add_argument('--out-assets', default='app/src/main/assets')
    ap.add_argument('--docs-dir', default='docs')
    ap.add_argument('--clips-per-sound', type=int, default=8)
    args=ap.parse_args()
    esc=Path(args.esc50_dir)
    audio_dir=esc/'audio'
    meta=esc/'meta'/'esc50.csv'
    if not meta.exists():
        raise SystemExit(f'Metadata not found: {meta}')
    out_assets=Path(args.out_assets)
    out_audio=out_assets/'audio'
    docs=Path(args.docs_dir)
    out_audio.mkdir(parents=True, exist_ok=True)
    docs.mkdir(parents=True, exist_ok=True)

    rows=[]
    with open(meta, newline='', encoding='utf-8') as f:
        for r in csv.DictReader(f): rows.append(r)
    bycat={}
    for r in rows: bycat.setdefault(r['category'], []).append(r)

    manifest=[]; app_sounds=[]
    random.seed(4040)
    for sid, name, group, cat in TARGETS:
        candidates=bycat.get(cat, [])
        if not candidates:
            print(f'WARN no candidates for {cat}')
            continue
        # deterministic but varied: select across folds when possible
        candidates=sorted(candidates, key=lambda r:(r.get('fold',''), r['filename']))
        selected=candidates[:args.clips_per_sound]
        filelist=out_audio/f'{sid}_concat.txt'
        with open(filelist,'w',encoding='utf-8') as f:
            for r in selected:
                p=(audio_dir/r['filename']).resolve()
                if p.exists(): f.write(f"file '{p}'\n")
        out=out_audio/f'{sid}.ogg'
        # Concatenate real ESC-50 WAV clips. No synthetic replacement. Normalise softly and encode efficiently.
        run(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','concat','-safe','0','-i',str(filelist),
             '-ac','2','-ar','48000','-af','loudnorm=I=-18:LRA=12:TP=-1.5,afade=t=in:st=0:d=0.05,afade=t=out:st=39.85:d=0.15',
             '-c:a','libvorbis','-q:a','3',str(out)])
        filelist.unlink(missing_ok=True)
        duration = len(selected)*5.0
        originals=[]
        for r in selected:
            p=audio_dir/r['filename']
            originals.append({'filename':r['filename'], 'fold':r.get('fold'), 'target':r.get('target'), 'category':r.get('category'), 'esc10':r.get('esc10'), 'src_file':r.get('src_file'), 'take':r.get('take'), 'sha256':sha256(p) if p.exists() else None})
        entry={
            'id':sid, 'name':name, 'category':group, 'asset':f'audio/{sid}.ogg',
            'source':SOURCE, 'source_url':SOURCE_URL, 'author':AUTHOR, 'license':LICENSE,
            'duration_seconds_estimate':duration, 'format_final':'Ogg Vorbis, 48 kHz, stereo',
            'processing':'concatenated ESC-50 real WAV clips, loudness normalisation, short edge fades, Vorbis q3',
            'output_sha256':sha256(out), 'original_clips':originals
        }
        manifest.append(entry)
        app_sounds.append({'id':sid,'name':name,'category':group,'asset':f'audio/{sid}.ogg','license':LICENSE,'author':'ESC-50'})
    with open(out_assets/'sounds.json','w',encoding='utf-8') as f: json.dump(app_sounds,f,ensure_ascii=False,indent=2)
    with open(docs/'audio_manifest_0.4.0.json','w',encoding='utf-8') as f: json.dump(manifest,f,ensure_ascii=False,indent=2)
    with open(docs/'audio_report_0.4.0.md','w',encoding='utf-8') as f:
        f.write('# Passarices 0.4.0 — relatório de áudio\n\n')
        f.write(f'Sons aprovados e integrados: {len(manifest)}\n\n')
        f.write('Fonte principal: ESC-50. Todos os ficheiros integrados neste lote são derivados de gravações reais WAV do dataset, não de ruído estático sintetizado.\n\n')
        f.write('Licença: CC BY-NC 4.0. Uso recomendado: pessoal/não comercial. Créditos completos no manifesto JSON.\n\n')
        for m in manifest:
            f.write(f'- {m["name"]} (`{m["id"]}`): {len(m["original_clips"])} clips reais, ~{m["duration_seconds_estimate"]:.0f}s, {m["format_final"]}\n')
    print(f'Generated {len(manifest)} sounds')

if __name__=='__main__': main()
