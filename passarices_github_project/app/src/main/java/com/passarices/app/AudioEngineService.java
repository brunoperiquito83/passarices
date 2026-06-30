package com.passarices.app;

import android.app.*;
import android.content.*;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.*;

public class AudioEngineService extends Service {
    public static final String ACTION_ADD = "com.passarices.ADD";
    public static final String ACTION_REMOVE = "com.passarices.REMOVE";
    public static final String ACTION_PAUSE = "com.passarices.PAUSE";
    public static final String ACTION_RESUME = "com.passarices.RESUME";
    public static final String ACTION_STOP = "com.passarices.STOP";
    public static final String ACTION_VOLUME = "com.passarices.VOLUME";
    public static final String EXTRA_ID = "id";
    public static final String EXTRA_ASSET = "asset";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_VOLUME = "volume";
    private static final String CHANNEL_ID = "passarices_playback";
    private final Map<String, PlayerState> players = new LinkedHashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean paused = false;

    static class PlayerState {
        MediaPlayer player; float targetVolume = 0.3f; String name; String asset;
        PlayerState(MediaPlayer p, String n, String a) { player=p; name=n; asset=a; }
    }

    @Override public void onCreate() { super.onCreate(); createChannel(); }
    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        String action = intent.getAction();
        if (ACTION_ADD.equals(action)) addSound(intent);
        else if (ACTION_REMOVE.equals(action)) removeSound(intent.getStringExtra(EXTRA_ID));
        else if (ACTION_PAUSE.equals(action)) fadeAll(false, false);
        else if (ACTION_RESUME.equals(action)) fadeAll(true, false);
        else if (ACTION_STOP.equals(action)) fadeAll(false, false);
        else if (ACTION_VOLUME.equals(action)) setVolume(intent.getStringExtra(EXTRA_ID), intent.getFloatExtra(EXTRA_VOLUME, 0.3f));
        startForeground(40, buildNotification());
        return START_STICKY;
    }

    private void addSound(Intent intent) {
        String id = intent.getStringExtra(EXTRA_ID);
        String asset = intent.getStringExtra(EXTRA_ASSET);
        String name = intent.getStringExtra(EXTRA_NAME);
        if (id == null || asset == null || players.containsKey(id)) return;
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            AssetFileDescriptor afd = getAssets().openFd(asset);
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp.setLooping(true);
            mp.prepare();
            PlayerState st = new PlayerState(mp, name == null ? id : name, asset);
            st.targetVolume = 0.3f;
            players.put(id, st);
            mp.setVolume(0f, 0f);
            mp.start();
            fadeOne(st, 0f, st.targetVolume, 5000, null);
            paused = false;
        } catch (Exception e) {
            writeCrash("addSound", e);
        }
    }

    private void removeSound(String id) {
        if (id == null) return;
        PlayerState st = players.remove(id);
        if (st == null) return;
        fadeOne(st, st.targetVolume, 0f, 5000, () -> { try { st.player.stop(); st.player.release(); } catch(Exception ignored){} });
    }

    private void setVolume(String id, float vol) {
        PlayerState st = players.get(id);
        if (st == null) return;
        st.targetVolume = Math.max(0f, Math.min(1f, vol));
        if (!paused) st.player.setVolume(st.targetVolume, st.targetVolume);
    }

    private void fadeAll(boolean resume, boolean release) {
        paused = !resume;
        for (PlayerState st: new ArrayList<>(players.values())) {
            if (resume) {
                try { if (!st.player.isPlaying()) st.player.start(); } catch(Exception ignored) {}
                fadeOne(st, 0f, st.targetVolume, 5000, null);
            } else {
                fadeOne(st, st.targetVolume, 0f, 5000, () -> { try { st.player.pause(); } catch(Exception ignored){} });
            }
        }
    }

    private void fadeOne(PlayerState st, float from, float to, long ms, Runnable end) {
        int steps = 25;
        for (int i=0;i<=steps;i++) {
            int k=i;
            handler.postDelayed(() -> {
                try {
                    float v = from + (to-from) * (k/(float)steps);
                    st.player.setVolume(v, v);
                    if (k==steps && end != null) end.run();
                } catch(Exception ignored) {}
            }, (ms * i)/steps);
        }
    }

    private Notification buildNotification() {
        Intent pause = new Intent(this, AudioEngineService.class).setAction(paused ? ACTION_RESUME : ACTION_PAUSE);
        PendingIntent pausePi = PendingIntent.getService(this, 1, pause, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop = new Intent(this, AudioEngineService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 2, stop, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        b.setContentTitle("Passarices")
         .setContentText(players.size() + " sons carregados")
         .setSmallIcon(android.R.drawable.ic_media_play)
         .setOngoing(true)
         .addAction(android.R.drawable.ic_media_pause, paused ? "Retomar" : "Pausar", pausePi)
         .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Parar tudo", stopPi);
        return b.build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Reprodução Passarices", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private void writeCrash(String where, Exception e) {
        try {
            java.io.File f = new java.io.File(getFilesDir(), "passarices-error.log");
            java.io.FileWriter w = new java.io.FileWriter(f, true);
            w.write(new java.util.Date()+" "+where+" "+e.toString()+"\n");
            w.close();
        } catch(Exception ignored) {}
    }

    @Override public void onDestroy() {
        for (PlayerState st: players.values()) { try { st.player.release(); } catch(Exception ignored){} }
        players.clear();
        super.onDestroy();
    }
}
