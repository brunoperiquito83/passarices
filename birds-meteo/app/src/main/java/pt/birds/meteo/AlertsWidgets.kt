package pt.birds.meteo

import android.Manifest
import android.app.*
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.widget.RemoteViews
import java.time.*

object AlertSettings {
    fun prefs(c:Context)=c.getSharedPreferences("alerts",Context.MODE_PRIVATE)
    fun enabled(c:Context,id:String,kind:String)=prefs(c).getBoolean("${kind}_$id",false)
    fun set(c:Context,id:String,kind:String,value:Boolean){prefs(c).edit().putBoolean("${kind}_$id",value).apply()}
    fun threshold(c:Context)=prefs(c).getInt("rain_threshold",70).coerceIn(10,100)
    fun threshold(c:Context,v:Int){prefs(c).edit().putInt("rain_threshold",v.coerceIn(10,100)).apply()}
    fun permitted(c:Context)= (Build.VERSION.SDK_INT<33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) && c.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    @Synchronized fun notify(c:Context,places:List<Place>,repo:Repo){
        if(!permitted(c))return
        val manager=c.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("ipma_alerts","Avisos oficiais IPMA",NotificationManager.IMPORTANCE_HIGH))
        manager.createNotificationChannel(NotificationChannel("rain_forecast","Previsão de chuva",NotificationManager.IMPORTANCE_DEFAULT))
        val now=Instant.now();val saved=prefs(c);val seen=saved.getStringSet("seen",emptySet()).orEmpty().toMutableSet()
        val date=LocalDate.now(ZoneId.of("Europe/Lisbon")).toString()
        val retained=seen.filter{it.substringBefore('|')>=LocalDate.parse(date).minusDays(2).toString()}.toMutableSet()
        val warnings=repo.read("warnings")?.takeUnless{expired(it.saved,now.toEpochMilli(),30)}?.array()
        fun send(key:String,channel:String,title:String,message:String){
            if(key in retained)return
            val intent=PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n=Notification.Builder(c,channel).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(message).setStyle(Notification.BigTextStyle().bigText(message)).setContentIntent(intent).setAutoCancel(true).build()
            if(runCatching{manager.notify(key.hashCode(),n)}.isSuccess)retained.add(key)
        }
        for(p in places.filterNot{it.gps}){
            if(enabled(c,p.id,"official")&&p.district.isNotBlank()&&warnings!=null){
                for(i in 0 until warnings.length()){
                    val w=warnings.optJSONObject(i)?:continue
                    val level=w.optString("awarenessLevelID");val start=w.optString("startTime");val end=w.optString("endTime");val type=w.optString("awarenessTypeName")
                    if(w.optString("idAreaAviso")!=p.district||!alertEligible(level,start,end,now))continue
                    val label=mapOf("yellow" to "Amarelo","orange" to "Laranja","red" to "Vermelho")[level]?:continue
                    // Same alert is deduplicated across the three Lisbon pages.
                    val key="${end.take(10)}|official|${p.district}|$type|$level|$start|$end"
                    send(key,"ipma_alerts","IPMA · $label · ${p.name}","$type. ${w.optString("text")} Válido até $end UTC.")
                }
            }
            if(enabled(c,p.id,"rain")){
                val packet=repo.cached(p).weather?.takeUnless{expired(it.saved,now.toEpochMilli(),90)}?:continue
                val o=packet.obj()?:continue;val h=o.optJSONObject("hourly");val idx=o.hourIndex();if(idx<0)continue
                val hit=(idx until minOf(idx+3,h.times().size)).firstOrNull{rainEligible(h.at("precipitation_probability",it),h.at("precipitation",it),threshold(c))}?:continue
                val hour=h.strAt("time",hit)?:continue
                send("${hour.take(10)}|rain|${p.id}","rain_forecast","Possibilidade de chuva · ${p.name}","Previsão Open-Meteo para ${hour.replace('T',' ')}: ${formatValue(h.at("precipitation_probability",hit),"%")} · ${formatValue(h.at("precipitation",hit),"mm")}. Não é deteção por radar.")
            }
        }
        saved.edit().putStringSet("seen",retained).apply()
    }
}
class WeatherWidget:AppWidgetProvider(){
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray){render(context,manager,ids)}
    override fun onAppWidgetOptionsChanged(context:Context,manager:AppWidgetManager,id:Int,options:android.os.Bundle){render(context,manager,intArrayOf(id))}
    companion object {
        fun updateAll(c:Context){val m=AppWidgetManager.getInstance(c);render(c,m,m.getAppWidgetIds(ComponentName(c,WeatherWidget::class.java)))}
        private fun render(c:Context,m:AppWidgetManager,ids:IntArray){
            if(ids.isEmpty())return
            val repo=Repo(c);val places=repo.places();val primary=c.getSharedPreferences("options",Context.MODE_PRIVATE).getString("primary",null);val p=places.firstOrNull{it.id==primary}?:places.firstOrNull()?:return
            val packet=repo.cached(p).weather;val o=packet?.obj();val cur=o?.optJSONObject("current");val day=o?.optJSONObject("daily")
            val stale=packet==null||expired(packet.saved,System.currentTimeMillis(),60)
            for(id in ids){
                val v=RemoteViews(c.packageName,R.layout.weather_widget)
                v.setTextViewText(R.id.widget_place,p.name)
                v.setTextViewText(R.id.widget_temp,formatValue(cur.number("temperature_2m"),"°C"))
                v.setTextViewText(R.id.widget_condition,describeWeather(cur.number("weather_code")?.toInt()))
                val wide=m.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)>=180
                v.setViewVisibility(R.id.widget_detail,if(wide)android.view.View.VISIBLE else android.view.View.GONE)
                v.setTextViewText(R.id.widget_detail,"Máx ${formatValue(day.at("temperature_2m_max",0),"°")} · Mín ${formatValue(day.at("temperature_2m_min",0),"°")}")
                v.setTextViewText(R.id.widget_status,if(packet==null||packet.saved<=0)"Abre a aplicação" else (if(stale)"Dados antigos · " else "")+Instant.ofEpochMilli(packet.saved).atZone(ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm")))
                v.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(c,1,Intent(c,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                m.updateAppWidget(id,v)
            }
        }
    }
}
