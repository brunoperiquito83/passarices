package pt.birds.meteo

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.*
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.time.*
import androidx.work.*
import java.util.concurrent.TimeUnit

data class Packet(val raw:String,val saved:Long,val error:String?=null) {
    fun obj()=runCatching{JSONObject(raw)}.getOrNull()
    fun array()=runCatching{JSONArray(raw)}.getOrNull()
}
data class LocalData(val weather:Packet?=null,val air:Packet?=null)
fun JSONObject?.number(k:String):Double? = this?.optDouble(k)?.takeIf{it.isFinite()}
fun JSONObject?.at(k:String,i:Int):Double? = if(i<0)null else this?.optJSONArray(k)?.optDouble(i)?.takeIf{it.isFinite()}
fun JSONObject?.strAt(k:String,i:Int):String? = if(i<0)null else this?.optJSONArray(k)?.let{if(it.isNull(i))null else it.optString(i).takeIf(String::isNotBlank)}
fun JSONObject?.times():List<String> = this?.optJSONArray("time")?.let{a->List(a.length()){a.optString(it)}}?:emptyList()
fun JSONObject.localNow():LocalDateTime = LocalDateTime.now(runCatching{ZoneId.of(optString("timezone"))}.getOrDefault(ZoneId.of("Europe/Lisbon")))
fun JSONObject?.hourIndex(section:String="hourly"):Int = this?.let{timeIndex(it.optJSONObject(section).times(),it.localNow())}?:-1
class Repo(private val context:Context) {
    private val prefs=context.getSharedPreferences("places",Context.MODE_PRIVATE)
    private val dir=File(context.filesDir,"meteorology").apply{mkdirs()}
    companion object { private val gate=Semaphore(4); private val cacheLock=Any(); const val WARNINGS="https://api.ipma.pt/open-data/forecast/warnings/warnings_www.json" }
    fun places():List<Place> {
        val arr=runCatching{JSONArray(prefs.getString("list",null)?:context.assets.open("locations.json").bufferedReader().use{it.readText()})}.getOrElse{JSONArray(context.assets.open("locations.json").bufferedReader().use{it.readText()})}
        return List(arr.length()){val a=arr.getJSONObject(it); val id=a.getString("id");Place(id,a.getString("name"),a.optString("reference"),a.getDouble("latitude"),a.getDouble("longitude"),a.optString("district",WeatherSpec.districts[id].orEmpty()))}
    }
    fun savePlaces(places:List<Place>){ val a=JSONArray();places.forEach{p->a.put(JSONObject().put("id",p.id).put("name",p.name).put("reference",p.reference).put("latitude",p.latitude).put("longitude",p.longitude).put("district",p.district))};prefs.edit().putString("list",a.toString()).apply() }
    fun cacheKey(p:Place)= "${p.id}_${"%.4f".format(java.util.Locale.ROOT,p.latitude)}_${"%.4f".format(java.util.Locale.ROOT,p.longitude)}"
    fun read(key:String):Packet? = synchronized(cacheLock){runCatching{val o=JSONObject(AtomicFile(File(dir,key+".json")).readFully().decodeToString());Packet(o.getString("raw"),o.getLong("saved"))}.getOrNull()}
    private fun write(key:String,p:Packet)=synchronized(cacheLock){ if((read(key)?.saved?:0)>p.saved)return@synchronized; val file=AtomicFile(File(dir,key+".json"));val out=file.startWrite();try{out.write(JSONObject().put("raw",p.raw).put("saved",p.saved).toString().toByteArray());file.finishWrite(out)}catch(e:Exception){file.failWrite(out);throw e} }
    suspend fun request(url:String):String = withContext(Dispatchers.IO){gate.withPermit{val c=URL(url).openConnection() as HttpsURLConnection; try { c.connectTimeout=12000;c.readTimeout=if(url.contains("start_date=1991-01-01"))90000 else 22000;c.setRequestProperty("User-Agent","BirdsMeteo/0.8-rc1 (personal weather application)");c.setRequestProperty("Accept","application/json"); val status=c.responseCode;if(status !in 200..299)throw java.io.IOException("HTTP $status");c.inputStream.bufferedReader().use{it.readText()} }finally{c.disconnect()} }}
    suspend fun fetch(key:String,url:String,force:Boolean,ttl:Long,valid:(String)->Boolean,fallback:String?=null):Packet {
        val old=withContext(Dispatchers.IO){read(key)};if(!force&&old!=null&&!expired(old.saved,System.currentTimeMillis(),ttl))return old
        return try { val raw=try{request(url).also{require(valid(it)){"Resposta inesperada"}}}catch(e:Exception){if(e is CancellationException)throw e;if(fallback==null)throw e;request(fallback).also{require(valid(it)){"Resposta alternativa inesperada"}}};val packet=Packet(raw,System.currentTimeMillis());withContext(Dispatchers.IO){write(key,packet)};packet }
        catch(e:Exception){if(e is CancellationException)throw e;old?.copy(error=e.message?:"Falha de ligação")?:Packet("",0,e.message?:"Falha de ligação")}
    }
    suspend fun weather(p:Place,force:Boolean)=fetch(cacheKey(p)+"_weather",WeatherSpec.forecast(p),force,60,{r->val o=JSONObject(r);o.optJSONObject("current")?.number("temperature_2m")!=null && o.optJSONObject("hourly").times().isNotEmpty()},WeatherSpec.forecast(p,false))
    suspend fun air(p:Place,force:Boolean)=fetch(cacheKey(p)+"_air",WeatherSpec.air(p),force,60,{r->JSONObject(r).optJSONObject("hourly").times().isNotEmpty()})
    suspend fun observations(force:Boolean)=fetch("ipma_surface","https://api.ipma.pt/open-data/observation/meteorology/stations/obs-surface.geojson",force,60,{r->JSONObject(r).optJSONArray("features")?.length()?.let{it>0}==true})
    suspend fun geomagnetic(force:Boolean)=fetch("noaa_kp","https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json",force,60,{r->val a=JSONArray(r);(0 until a.length()).any{i->val o=a.optJSONObject(i);kpValue(o.number("Kp"))!=null&&utcInstant(o?.optString("time_tag").orEmpty())!=null}})
    suspend fun seismic(force:Boolean)=fetch("ipma_seismic","https://api.ipma.pt/open-data/observation/seismic/7.json",force,60,{r->JSONObject(r).optJSONArray("data")!=null})
    suspend fun fire(day:Int,force:Boolean)=fetch("ipma_fire_$day","https://api.ipma.pt/open-data/forecast/meteorology/rcm/rcm-d$day.json",force,60,{r->JSONObject(r).optJSONObject("local")!=null})
    suspend fun radar(force:Boolean)=fetch("radar_metadata","https://api.rainviewer.com/public/weather-maps.json",force,10,{r->JSONObject(r).optJSONObject("radar")?.optJSONArray("past")?.length()?.let{it>0}==true})
    suspend fun warnings(force:Boolean)=fetch("warnings",WARNINGS,force,15,{r->JSONArray(r);true})
    suspend fun refresh(p:Place,force:Boolean)=coroutineScope{val w=async{weather(p,force)};val a=async{air(p,force)};LocalData(w.await(),a.await())}
    fun cached(p:Place)=LocalData(read(cacheKey(p)+"_weather"),read(cacheKey(p)+"_air"))
    fun background(enabled:Boolean){val wm=WorkManager.getInstance(context);if(enabled)wm.enqueueUniquePeriodicWork("birds-weather",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<RefreshWorker>(30,TimeUnit.MINUTES).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()) else wm.cancelUniqueWork("birds-weather")}
}
class RefreshWorker(ctx:Context,params:WorkerParameters):CoroutineWorker(ctx,params){override suspend fun doWork():ListenableWorker.Result {val repo=Repo(applicationContext);return try{coroutineScope{val a=async{repo.warnings(false)};val o=async{repo.observations(false)};repo.places().map{async{repo.refresh(it,false)}}.awaitAll();a.await();o.await()};AlertSettings.notify(applicationContext,repo.places(),repo);WeatherWidget.updateAll(applicationContext);ListenableWorker.Result.success()}catch(e:CancellationException){throw e}catch(e:Exception){ListenableWorker.Result.retry()}}}
