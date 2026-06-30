package com.passarices.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.*;
import android.widget.*;

import org.json.*;

import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    private LinearLayout root, content, nav;
    private final ArrayList<SoundItem> library = new ArrayList<>();
    private final LinkedHashMap<String, ActiveSound> active = new LinkedHashMap<>();
    private SharedPreferences prefs;
    private String currentTab = "Início";
    private String greeting;
    private int bg = Color.rgb(8,17,13), card = Color.rgb(18,35,27), fg = Color.rgb(238,244,235), muted = Color.rgb(166,180,169), accent = Color.rgb(70,178,117);
    private static final int MAX_SOUNDS = 10;

    static class ActiveSound { SoundItem item; float vol=0.3f; boolean muted=false; boolean solo=false; ActiveSound(SoundItem i){item=i;} }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("passarices", MODE_PRIVATE);
        applyThemePrefs();
        greeting = makeGreeting();
        requestNotifications();
        loadLibrary();
        buildRoot();
        showHome();
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 44);
        }
    }

    private void applyThemePrefs() {
        String theme = prefs.getString("theme", "Verde Floresta");
        if (theme.contains("Azul")) accent = Color.rgb(71,145,220);
        else if (theme.contains("Roxo")) accent = Color.rgb(161,104,225);
        else if (theme.contains("Âmbar")) accent = Color.rgb(220,160,70);
        else if (theme.contains("Cinza")) accent = Color.rgb(150,155,160);
        else if (theme.contains("Teal")) accent = Color.rgb(43,184,174);
        else accent = Color.rgb(70,178,117);
        boolean light = prefs.getBoolean("light", false);
        if (light) { bg=Color.rgb(241,246,241); card=Color.WHITE; fg=Color.rgb(20,30,25); muted=Color.rgb(75,88,80); }
        else { bg=Color.rgb(8,17,13); card=Color.rgb(18,35,27); fg=Color.rgb(238,244,235); muted=Color.rgb(166,180,169); }
    }

    private String makeGreeting() {
        String[] a = {"Bom dia", "Boa tarde", "Boa noite", "Olhá aí", "Boas", "Buenas", "Mekié", "Ide co caralho", "Morre"};
        String[] b = {"Bruno", "Periquito", "Caralho", "Filho da Puta", "Inútil da Merda", "Cabrão do Caralho", "Xico Trevas", "Xico Couves", "Zé Nabiças", "Paneleirote", "Zé Chunga"};
        Random r = new Random(System.currentTimeMillis());
        return a[r.nextInt(a.length)] + " " + b[r.nextInt(b.length)];
    }

    private void loadLibrary() {
        library.clear();
        try {
            InputStream is = getAssets().open("sounds.json");
            String txt = readAll(is);
            JSONArray arr = new JSONArray(txt);
            for (int i=0;i<arr.length();i++) {
                JSONObject o = arr.getJSONObject(i);
                library.add(new SoundItem(o.getString("id"), o.getString("name"), o.optString("category","Sons"), o.getString("asset"), o.optString("license",""), o.optString("author","")));
            }
        } catch(Exception e) {
            library.add(new SoundItem("white_noise", "Ruído branco", "Sintético", "audio/white_noise.ogg", "generated", "Passarices"));
        }
    }

    private String readAll(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n; while((n=is.read(buf))>0) bos.write(buf,0,n); return bos.toString("UTF-8");
    }

    private void buildRoot() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(bg); root.setPadding(dp(16), dp(44), dp(16), 0);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this); scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
        nav = new LinearLayout(this); nav.setOrientation(LinearLayout.HORIZONTAL); nav.setPadding(0, dp(8),0,dp(8)); root.addView(nav, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        drawNav();
    }

    private void drawNav() {
        nav.removeAllViews();
        String[] tabs = {"Início","Ambientes","Misturador","Biblioteca","Guardados"};
        for (String t: tabs) {
            Button b = new Button(this); b.setText(t); b.setTextSize(11); b.setTextColor(t.equals(currentTab)?Color.WHITE:fg); b.setBackgroundColor(t.equals(currentTab)?accent:card); b.setAllCaps(false);
            b.setOnClickListener(v -> { currentTab=t; if(t.equals("Início")) showHome(); else if(t.equals("Ambientes")) showEnvironments(); else if(t.equals("Misturador")) showMixer(); else if(t.equals("Biblioteca")) showLibrary(); else showSaved(); });
            nav.addView(b, new LinearLayout.LayoutParams(0, dp(52), 1));
        }
    }

    private TextView tv(String text, int sp, int color, int style) { TextView v=new TextView(this); v.setText(text); v.setTextSize(sp); v.setTextColor(color); v.setGravity(Gravity.CENTER); v.setTypeface(null, style); v.setPadding(4,6,4,6); return v; }
    private TextView left(String text, int sp, int color, int style) { TextView v=tv(text,sp,color,style); v.setGravity(Gravity.START); return v; }
    private Button btn(String text) { Button b=new Button(this); b.setText(text); b.setTextColor(fg); b.setAllCaps(false); b.setBackgroundColor(card); return b; }
    private void clear(String title) { content.removeAllViews(); drawNav(); if(!title.equals("Início")) content.addView(tv(title,26,fg,1)); }

    private void showHome() {
        currentTab="Início"; clear("Início");
        content.addView(tv("Passarices", 34, fg, 1));
        content.addView(tv(greeting, 20, accent, 1));
        content.addView(tv("Que sangres dos olhos até sufocares no teu próprio vómito auditivo.", 15, muted, 0));
        TextView info = tv("Sons carregados: "+active.size()+"/"+MAX_SOUNDS, 16, fg, 1); content.addView(info);
        Button m = btn("Abrir Misturador"); m.setOnClickListener(v -> { currentTab="Misturador"; showMixer(); }); content.addView(m, new LinearLayout.LayoutParams(-1, dp(56)));
        Button b = btn("Escolher sons reais"); b.setOnClickListener(v -> { currentTab="Biblioteca"; showLibrary(); }); content.addView(b, new LinearLayout.LayoutParams(-1, dp(56)));
        content.addView(left("Última mistura não é carregada ao abrir. Layout, tema e idioma ficam guardados.", 14, muted,0));
    }

    private void showLibrary() {
        currentTab="Biblioteca"; clear("Biblioteca por Famílias");
        Map<String, ArrayList<SoundItem>> groups = new LinkedHashMap<>();
        for (SoundItem s: library) groups.computeIfAbsent(s.category, k->new ArrayList<>()).add(s);
        for (String g: groups.keySet()) {
            content.addView(left(g, 20, accent, 1));
            for (SoundItem s: groups.get(g)) {
                Button b=btn(s.name + "  ·  +"); b.setOnClickListener(v -> addSound(s)); content.addView(b, new LinearLayout.LayoutParams(-1, dp(50)));
            }
        }
    }

    private void addSound(SoundItem s) {
        if (active.containsKey(s.id)) { alert("Som já carregado", "Este som já está no misturador."); return; }
        if (active.size() >= MAX_SOUNDS) { alert("Limite atingido", "Limite atingido. Só podes usar 10 sons em simultâneo."); return; }
        active.put(s.id, new ActiveSound(s));
        Intent i = new Intent(this, AudioEngineService.class).setAction(AudioEngineService.ACTION_ADD);
        i.putExtra(AudioEngineService.EXTRA_ID, s.id); i.putExtra(AudioEngineService.EXTRA_NAME, s.name); i.putExtra(AudioEngineService.EXTRA_ASSET, s.asset);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        showMixer();
    }

    private void showMixer() {
        currentTab="Misturador"; clear("Misturador Central");
        content.addView(left("Ativos: "+active.size()+"/"+MAX_SOUNDS+" · volume inicial 30%", 14, muted, 0));
        LinearLayout controls = new LinearLayout(this); controls.setOrientation(LinearLayout.HORIZONTAL);
        Button pause=btn("Pausar"); pause.setOnClickListener(v -> startService(new Intent(this, AudioEngineService.class).setAction(AudioEngineService.ACTION_PAUSE)));
        Button resume=btn("Retomar"); resume.setOnClickListener(v -> startService(new Intent(this, AudioEngineService.class).setAction(AudioEngineService.ACTION_RESUME)));
        controls.addView(pause,new LinearLayout.LayoutParams(0,dp(52),1)); controls.addView(resume,new LinearLayout.LayoutParams(0,dp(52),1)); content.addView(controls);
        for (ActiveSound a: active.values()) drawActive(a);
        Button add=btn("Adicionar mais sons"); add.setOnClickListener(v->{currentTab="Biblioteca";showLibrary();}); content.addView(add,new LinearLayout.LayoutParams(-1,dp(56)));
    }

    private void drawActive(ActiveSound a) {
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setBackgroundColor(card); box.setPadding(dp(10),dp(8),dp(10),dp(8));
        box.addView(left(a.item.name,18,fg,1)); box.addView(left(a.item.category+" · "+a.item.license+" · "+a.item.author,12,muted,0));
        SeekBar sb=new SeekBar(this); sb.setMax(100); sb.setProgress((int)(a.vol*100)); sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){ public void onProgressChanged(SeekBar b,int p,boolean f){ if(f){a.vol=p/100f; Intent i=new Intent(MainActivity.this,AudioEngineService.class).setAction(AudioEngineService.ACTION_VOLUME); i.putExtra(AudioEngineService.EXTRA_ID,a.item.id); i.putExtra(AudioEngineService.EXTRA_VOLUME,a.vol); startService(i);} } public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){} }); box.addView(sb);
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        Button solo=btn("Solo"); solo.setOnClickListener(v -> Toast.makeText(this,"Solo registado na interface; motor simples nesta build.",Toast.LENGTH_SHORT).show());
        Button remove=btn("Remover"); remove.setOnClickListener(v -> { active.remove(a.item.id); Intent i=new Intent(this,AudioEngineService.class).setAction(AudioEngineService.ACTION_REMOVE); i.putExtra(AudioEngineService.EXTRA_ID,a.item.id); startService(i); showMixer(); });
        row.addView(solo,new LinearLayout.LayoutParams(0,dp(48),1)); row.addView(remove,new LinearLayout.LayoutParams(0,dp(48),1)); box.addView(row);
        content.addView(box, new LinearLayout.LayoutParams(-1, -2));
    }

    private void showEnvironments() {
        currentTab="Ambientes"; clear("Ambientes Imersivos");
        String[][] envs = {{"Tempestade real", "rain,thunderstorm,wind"},{"Viagem de comboio", "train,rain,wind"},{"Casa em ruína auditiva", "washing_machine,vacuum_cleaner,clock_tick"},{"Floresta e bicharada", "chirping_birds,crickets,frog"},{"Costa nocturna", "sea_waves,wind,thunderstorm"}};
        for (String[] e: envs) { Button b=btn(e[0]); b.setOnClickListener(v -> addEnvironment(e[1])); content.addView(b, new LinearLayout.LayoutParams(-1, dp(58))); }
        Button surprise=btn("Surpreende-me"); surprise.setOnClickListener(v -> surprise()); content.addView(surprise,new LinearLayout.LayoutParams(-1,dp(58)));
    }
    private void addEnvironment(String ids) {
        ArrayList<String> skipped = new ArrayList<>(); int added=0;
        for(String id: ids.split(",")) { SoundItem s=find(id); if(s==null || active.containsKey(id)) continue; if(active.size()<MAX_SOUNDS){ active.put(s.id,new ActiveSound(s)); Intent i=new Intent(this,AudioEngineService.class).setAction(AudioEngineService.ACTION_ADD); i.putExtra(AudioEngineService.EXTRA_ID,s.id); i.putExtra(AudioEngineService.EXTRA_NAME,s.name); i.putExtra(AudioEngineService.EXTRA_ASSET,s.asset); if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i); added++; } else skipped.add(s.name); }
        if(!skipped.isEmpty()) alert("Ambiente parcial", "Adicionados: "+added+"\nFicaram de fora: "+skipped);
        showMixer();
    }
    private void surprise(){ Collections.shuffle(library); int n=Math.min(3,MAX_SOUNDS-active.size()); for(int i=0;i<n;i++) addSound(library.get(i)); }
    private SoundItem find(String id){ for(SoundItem s:library) if(s.id.equals(id)) return s; return null; }

    private void showSaved() {
        currentTab="Guardados"; clear("Guardados e Definições");
        content.addView(left("Layout, tema e idioma são manuais e ficam guardados.",14,muted,0));
        String[] themes={"Verde Floresta","Azul Nocturno","Roxo Tempestade","Âmbar Quente","Cinza Grafite","Teal Oceânico"};
        for(String t:themes){ Button b=btn("Tema: "+t); b.setOnClickListener(v->{ prefs.edit().putString("theme",t).apply(); applyThemePrefs(); buildRoot(); showSaved(); }); content.addView(b,new LinearLayout.LayoutParams(-1,dp(48))); }
        Button mode=btn("Alternar claro/escuro"); mode.setOnClickListener(v->{ prefs.edit().putBoolean("light",!prefs.getBoolean("light",false)).apply(); applyThemePrefs(); buildRoot(); showSaved(); }); content.addView(mode,new LinearLayout.LayoutParams(-1,dp(48)));
        Button credits=btn("Créditos e licenças"); credits.setOnClickListener(v->showCredits()); content.addView(credits,new LinearLayout.LayoutParams(-1,dp(48)));
        Button log=btn("Exportar relatório local de erros"); log.setOnClickListener(v->shareLog()); content.addView(log,new LinearLayout.LayoutParams(-1,dp(48)));
    }

    private void showCredits(){ clear("Créditos e Licenças"); content.addView(left("Áudio real: ESC-50, Karol J. Piczak, licença CC BY-NC 4.0. Subconjuntos e metadados ficam registados no manifesto da build. Ruídos sintéticos, quando existirem, são assinalados como gerados.",14,fg,0)); }
    private void shareLog(){ try{ File f=new File(getFilesDir(),"passarices-error.log"); Toast.makeText(this, f.exists()?f.getAbsolutePath():"Sem erros registados", Toast.LENGTH_LONG).show(); }catch(Exception e){} }

    private void alert(String title, String msg){ new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("Fechar", null).show(); }
    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
}
