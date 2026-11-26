package pt.birds.meteo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.*
import android.os.Bundle
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import java.time.*
import java.util.UUID
import org.json.JSONObject

class MeteoState(private val ctx:Context):ViewModel(){
    val repo=Repo(ctx);private val prefs=ctx.getSharedPreferences("options",Context.MODE_PRIVATE)
    var places:List<Place> by mutableStateOf(repo.places());private set
    private var gpsState by mutableStateOf<Place?>(null)
    val gps:Place? get()=gpsState
    var gpsMessage by mutableStateOf("Ativa a localização para consultar o tempo no local atual.")
    val all:List<Place> get()=places+listOfNotNull(gps)
    val data=mutableStateMapOf<String,LocalData>()
    var warnings by mutableStateOf(repo.read("warnings"));private set
    var observations by mutableStateOf(repo.read("ipma_surface"));private set
    var seismic by mutableStateOf(repo.read("ipma_seismic"));private set
    var fire by mutableStateOf((0..2).map{repo.read("ipma_fire_$it")});private set
    var alertRevision by mutableIntStateOf(0);private set
    fun alertOn(id:String,kind:String):Boolean { alertRevision; return AlertSettings.enabled(ctx,id,kind) }
    fun setAlert(id:String,kind:String,value:Boolean){AlertSettings.set(ctx,id,kind,value);alertRevision++}
    private var rainThresholdState by mutableIntStateOf(AlertSettings.threshold(ctx))
    val rainThreshold:Int get()=rainThresholdState
    fun setRainThreshold(value:Int){rainThresholdState=value;AlertSettings.threshold(ctx,value)}
    var kp by mutableStateOf(repo.read("noaa_kp"));private set
    var searchResult by mutableStateOf<Packet?>(null);private set
    var searching by mutableStateOf(false);private set
    private var searchJob:Job?=null
    private var searchGeneration=0
    fun searchPlaces(query:String,portugalOnly:Boolean){
        val generation=++searchGeneration;searchJob?.cancel();searchResult=null;searching=true
        searchJob=viewModelScope.launch{try{val url=searchUrl(query,portugalOnly);val raw=repo.request(url);val o=JSONObject(raw);require(!o.optBoolean("error"));if(generation==searchGeneration)searchResult=Packet(raw,System.currentTimeMillis())}catch(e:CancellationException){throw e}catch(e:Exception){if(generation==searchGeneration)searchResult=Packet("",0,e.message?:"Pesquisa indisponível")}finally{if(generation==searchGeneration)searching=false}}
    }
    fun addResult(name:String,reference:String,lat:Double,lon:Double){
        if(places.any{distanceKm(it.latitude,it.longitude,lat,lon)<0.05})return
        val p=Place(UUID.randomUUID().toString(),name,reference,lat,lon);places=places+p;persist();viewModelScope.launch{data[p.id]=repo.refresh(p,true)}
    }
    fun loadAstronomy(p:Place,date:LocalDate,zone:ZoneId){
        val key=extraKey(p,"astro",date);if(extraJobs[key]?.isActive==true)return
        extraJobs[key]=viewModelScope.launch{extras=extras+(key to (repo.read(key)?:Packet("",0)));val result=repo.fetch(key,astronomicalUrl(p,date,zone),false,1440,{r->val o=JSONObject(r);!o.optBoolean("error")&&o.optJSONObject("properties")?.optJSONObject("data")?.optJSONArray("moondata")!=null});extras=extras+(key to result)}
    }
    fun loadClimate(p:Place){
        val key=extraKey(p,"climate");if(extraJobs[key]?.isActive==true)return
        extraJobs[key]=viewModelScope.launch{extras=extras+(key to (repo.read(key)?:Packet("",0)));val result=repo.fetch(key,climateUrl(p),false,525600,{r->JSONObject(r).optJSONObject("daily").times().size==10958});extras=extras+(key to result)}
    }
    var radar by mutableStateOf(repo.read("radar_metadata"));private set
    private var radarJob:Job?=null
    fun loadRadar(force:Boolean=false){if(radarJob?.isActive==true)return;radarJob=viewModelScope.launch{radar=repo.radar(force)}}
    var updating by mutableStateOf(false);private set
    private var backgroundState by mutableStateOf(prefs.getBoolean("background",true))
    val background:Boolean get()=backgroundState
    private var primaryState by mutableStateOf(prefs.getString("primary",places.firstOrNull()?.id.orEmpty()).orEmpty())
    val primary:String get()=primaryState
    var extras by mutableStateOf<Map<String,Packet>>(emptyMap());private set
    private var refreshJob:Job?=null
    private var gpsJob:Job?=null
    private val extraJobs=mutableMapOf<String,Job>()
    init {places.forEach{data[it.id]=repo.cached(it)};repo.background(background);refresh(true)}
    fun refresh(force:Boolean){if(refreshJob?.isActive==true)return;refreshJob=viewModelScope.launch{updating=true;try{supervisorScope{launch{warnings=repo.warnings(force)};launch{observations=repo.observations(force)};launch{seismic=repo.seismic(force)};launch{kp=repo.geomagnetic(force)};launch{fire=(0..2).map{async{repo.fire(it,force)}}.awaitAll()};all.map{p->launch{val result=repo.refresh(p,force);if(all.any{it==p})data[p.id]=result}}.joinAll()};AlertSettings.notify(ctx,all,repo);WeatherWidget.updateAll(ctx)}finally{updating=false}}}
    fun setGps(p:Place){if(gps?.latitude==p.latitude&&gps?.longitude==p.longitude)return;gpsJob?.cancel();data[p.id]=repo.cached(p);gpsState=p;gpsMessage="";gpsJob=viewModelScope.launch{val v=repo.refresh(p,true);if(gps==p)data[p.id]=v}}
    fun setBackground(value:Boolean){backgroundState=value;prefs.edit().putBoolean("background",value).apply();repo.background(value)}
    fun setPrimary(id:String){primaryState=id;prefs.edit().putString("primary",id).apply();WeatherWidget.updateAll(ctx)}
    fun reset(){places=runCatching{val a=org.json.JSONArray(ctx.assets.open("locations.json").bufferedReader().use{it.readText()});List(a.length()){val o=a.getJSONObject(it);val id=o.getString("id");Place(id,o.getString("name"),o.getString("reference"),o.getDouble("latitude"),o.getDouble("longitude"),WeatherSpec.districts[id].orEmpty())}}.getOrDefault(places);persist()}
    fun move(i:Int,delta:Int){val j=i+delta;if(j !in places.indices)return;val reordered=places.toMutableList();val moved=reordered.removeAt(i);reordered.add(j,moved);places=reordered;persist()}
    fun remove(id:String){if(places.size<=1)return;places=places.filterNot{it.id==id};if(primary==id)setPrimary(places.first().id);persist()}
    fun add(name:String,lat:Double,lon:Double){val p=Place(UUID.randomUUID().toString(),name.trim(),"Localidade personalizada",lat,lon);places=places+p;persist();viewModelScope.launch{data[p.id]=repo.refresh(p,true)}}
    private fun persist(){repo.savePlaces(places);if(places.none{it.id==primary})setPrimary(places.first().id);places.forEach{if(!data.containsKey(it.id))data[it.id]=repo.cached(it)}}
    fun loadExtra(p:Place,type:String,date:LocalDate?=null){val key=extraKey(p,type,date);if(extraJobs[key]?.isActive==true)return;extraJobs[key]=viewModelScope.launch{extras=extras+(key to Packet("",0));val url=when(type){"history"->WeatherSpec.historical(p,requireNotNull(date));"marine"->WeatherSpec.marine(p);"flood"->floodUrl(p);else->WeatherSpec.model(p,type)};val result=repo.fetch(key,url,true,60,{r-> val o=JSONObject(r);o.optJSONObject(if(type in listOf("history","flood"))"daily" else "hourly").times().isNotEmpty()});extras=extras+(key to result)}}
    fun extraKey(p:Place,type:String,date:LocalDate?=null)=repo.cacheKey(p)+"_"+type+(date?.toString()?:"")
}
class MainActivity:ComponentActivity(){
    private lateinit var state:MeteoState
    private var cancellation:CancellationSignal?=null
    private val permissions=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){if(hasLocation())locate()else state.gpsMessage="Permissão de localização recusada."}
    private val notificationPermission=registerForActivityResult(ActivityResultContracts.RequestPermission()){ }
    private fun requestNotifications(){if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,packageName))}
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);runCatching{if(android.net.http.HttpResponseCache.getInstalled()==null)android.net.http.HttpResponseCache.install(java.io.File(cacheDir,"http"),32L*1024*1024)};state=ViewModelProvider(this,object:ViewModelProvider.Factory{override fun <T:ViewModel> create(modelClass:Class<T>):T { @Suppress("UNCHECKED_CAST") return MeteoState(applicationContext) as T }}).get(MeteoState::class.java);setContent{BirdsApp(state,::requestNotifications){if(hasLocation())locate()else permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))}};if(hasLocation())locate()}
    override fun onRestart(){super.onRestart();state.refresh(true);if(hasLocation())locate()}
    private fun hasLocation()=checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
    @Suppress("MissingPermission") private fun locate(){if(!hasLocation())return;val manager=getSystemService(LOCATION_SERVICE) as LocationManager;val provider=listOf(LocationManager.NETWORK_PROVIDER,LocationManager.GPS_PROVIDER).filter{manager.isProviderEnabled(it)}.let{if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED&&LocationManager.GPS_PROVIDER in it)LocationManager.GPS_PROVIDER else it.firstOrNull()};if(provider==null){state.gpsMessage="Localização do sistema desligada.";return};state.gpsMessage="A obter localização…";cancellation?.cancel();val signal=CancellationSignal();cancellation=signal
        fun useLocation(loc:Location?){if(loc==null){state.gpsMessage="Não foi possível obter uma posição atual.";return};state.viewModelScope.launch{val address=withContext(Dispatchers.IO){runCatching{@Suppress("DEPRECATION") Geocoder(this@MainActivity,java.util.Locale.forLanguageTag("pt-PT")).getFromLocation(loc.latitude,loc.longitude,1)?.firstOrNull()}.getOrNull()};if(signal.isCanceled)return@launch;val label=address?.subLocality?:address?.locality?:address?.subAdminArea?:"Localização atual";val district=mapOf("Lisboa" to "LSB","Setúbal" to "STB","Porto" to "PTO","Beja" to "BJA","Guarda" to "GDA","Aveiro" to "AVR","Braga" to "BRG","Bragança" to "BGC","Castelo Branco" to "CBO","Coimbra" to "CBR","Évora" to "EVR","Faro" to "FAR","Leiria" to "LRA","Portalegre" to "PTG","Santarém" to "STM","Viana do Castelo" to "VCT","Vila Real" to "VRL","Viseu" to "VIS")[address?.adminArea].orEmpty();state.setGps(Place("gps",label,"Localização atual · precisão ${loc.accuracy.toInt()} m",loc.latitude,loc.longitude,district,true))}}
        try{if(android.os.Build.VERSION.SDK_INT>=30)manager.getCurrentLocation(provider,signal,mainExecutor){useLocation(it)} else { @Suppress("DEPRECATION") val last=manager.getLastKnownLocation(provider);useLocation(last?.takeIf{System.currentTimeMillis()-it.time<300000}) }}catch(e:Exception){state.gpsMessage="Localização indisponível."}}
    override fun onDestroy(){cancellation?.cancel();super.onDestroy()}
}
