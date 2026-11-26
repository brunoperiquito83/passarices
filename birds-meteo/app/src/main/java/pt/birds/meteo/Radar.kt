package pt.birds.meteo

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.time.*
import java.time.format.DateTimeFormatter

internal data class StationObservation(val name:String,val distance:Double,val time:String,val values:JSONObject)
internal fun nearestObservation(p:Place,packet:Packet?,now:Instant=Instant.now()):StationObservation? {
    val features=packet?.obj()?.optJSONArray("features")?:return null
    val candidates=(0 until features.length()).mapNotNull{i->
        val f=features.optJSONObject(i)?:return@mapNotNull null
        val c=f.optJSONObject("geometry")?.optJSONArray("coordinates")?:return@mapNotNull null
        val lat=c.optDouble(1);val lon=c.optDouble(0);if(!lat.isFinite()||!lon.isFinite()||lat !in -90.0..90.0||lon !in -180.0..180.0)return@mapNotNull null
        val o=f.optJSONObject("properties")?:return@mapNotNull null;val time=o.optString("time");val age=observationAgeMinutes(time,now)?:return@mapNotNull null
        val dist=distanceKm(p.latitude,p.longitude,lat,lon)
        if(age>=180||dist>50||listOf("temperatura","humidade","pressao","intensidadeVentoKM","precAcumulada","radiacao").none{ipmaValue(o.number(it))!=null})return@mapNotNull null
        StationObservation(o.optString("localEstacao").ifBlank{"Estação ${o.optString("idEstacao")}"},dist,time,o)
    }
    return candidates.sortedWith(compareBy<StationObservation>{it.distance}.thenByDescending{it.time}).firstOrNull()
}
private data class Frame(val time:Long,val url:String)
private suspend fun image(url:String):ImageBitmap?=withContext(Dispatchers.IO){
    try{val c=URL(url).openConnection() as HttpsURLConnection;try{c.connectTimeout=10000;c.readTimeout=15000;c.setRequestProperty("User-Agent","BirdsMeteo/0.7 (personal Android weather app)");if(c.responseCode!=200)null else c.inputStream.use{BitmapFactory.decodeStream(it)?.asImageBitmap()}}finally{c.disconnect()}}catch(e:CancellationException){throw e}catch(_:Exception){null}
}
@Composable internal fun RadarPanel(p:Place,s:MeteoState){
    LaunchedEffect(Unit){s.loadRadar()}
    var retry by remember{mutableIntStateOf(0)}
    var zoom by remember(p.id){mutableIntStateOf(6)}
    val tile=remember(p.latitude,p.longitude,zoom){radarTile(p.latitude,p.longitude,zoom)}
    val packet=s.radar
    val frames=remember(packet,tile){val root=packet?.obj();val a=root?.optJSONObject("radar")?.optJSONArray("past");if(a==null)emptyList()else (0 until a.length()).mapNotNull{i->val o=a.optJSONObject(i)?:return@mapNotNull null;val time=o.optLong("time",0);val url=radarUrl(root?.optString("host").orEmpty(),o.optString("path"),tile);if(time>0&&url!=null)Frame(time,url)else null}.distinctBy{it.time}.sortedBy{it.time}.takeLast(13)}
    var selected by remember(frames){mutableIntStateOf((frames.size-1).coerceAtLeast(0))}
    var playing by remember(tile,frames){mutableStateOf(false)}
    var base by remember(tile){mutableStateOf<ImageBitmap?>(null)}
    var coverage by remember(tile){mutableStateOf<ImageBitmap?>(null)}
    var frameImage by remember(tile,frames){mutableStateOf<ImageBitmap?>(null)}
    var displayed by remember(tile,frames){mutableStateOf<Frame?>(null)}
    var failure by remember(tile,frames){mutableStateOf(false)}
    val cache=remember(tile,frames){mutableMapOf<String,ImageBitmap>()}
    LaunchedEffect(tile,retry){coroutineScope{launch{base=image("https://tile.openstreetmap.org/${tile.z}/${tile.x}/${tile.y}.png")};launch{coverage=image("https://tilecache.rainviewer.com/v2/coverage/0/512/${tile.z}/${tile.x}/${tile.y}/0/0_0.png")}}}
    LaunchedEffect(selected,frames,tile,retry){val f=frames.getOrNull(selected)?:return@LaunchedEffect;failure=false;val result=cache[f.url]?:image(f.url)?.also{cache[f.url]=it};if(result==null){failure=true;frameImage=null;displayed=null;playing=false}else{frameImage=result;displayed=f}}
    LaunchedEffect(playing,displayed,selected){if(playing&&frames.size>1&&displayed==frames.getOrNull(selected)){delay(850);selected=(selected+1)%frames.size}}
    Row(verticalAlignment=Alignment.CenterVertically){Text("Zoom $zoom/7",modifier=Modifier.weight(1f));TextButton(enabled=zoom>3,onClick={zoom--}){Text("−")};TextButton(enabled=zoom<7,onClick={zoom++}){Text("+")};TextButton(onClick={retry++;s.loadRadar(true)}){Text("Atualizar")}}
    Box(Modifier.fillMaxWidth().aspectRatio(1f),contentAlignment=Alignment.Center){
        val b=base;if(b!=null)Image(b,"Mapa base",Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds) else Text("Mapa base indisponível")
        frameImage?.let{Image(it,"Radar de precipitação",Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds,alpha=.8f)}
        coverage?.let{Image(it,"Máscara de cobertura do radar",Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds,alpha=.55f)}
        Canvas(Modifier.fillMaxSize()){val pos=Offset((tile.markerX*size.width).toFloat(),(tile.markerY*size.height).toFloat());drawCircle(Color.White,8.dp.toPx(),pos);drawCircle(Color(0xFF16548E),5.dp.toPx(),pos)}
    }
    Text("© OpenStreetMap contributors · Radar: RainViewer",fontSize=11.sp)
    Text("Áreas escurecidas: sem cobertura, quando a máscara está disponível. Ausência de eco não garante ausência de chuva.",fontSize=11.sp)
    if(coverage==null)Text("Máscara de cobertura indisponível.",fontSize=12.sp)
    if(frames.isEmpty())Text(packet?.error?:"A obter imagens de radar…") else {
        Text(displayed?.let{Instant.ofEpochSecond(it.time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))}?:if(failure)"Imagem indisponível" else "A carregar imagem…")
        if(failure)Text("Falha no carregamento da imagem. Atualiza o radar ou escolhe outro instante.")
        if(frames.size>1)Slider(value=selected.toFloat(),onValueChange={playing=false;selected=it.toInt()},valueRange=0f..frames.lastIndex.toFloat(),steps=(frames.size-2).coerceAtLeast(0))
        Button(enabled=frames.size>1&&displayed!=null,onClick={playing=!playing}){Text(if(playing)"Pausar" else "Reproduzir")}
        Text("${frames.size} instantes históricos · sem previsão futura de radar. Hora de geração das imagens, não da medição exata.",fontSize=12.sp)
        if(packet!=null&&expired(packet.saved,System.currentTimeMillis(),10))Text("Metadados desatualizados.",fontSize=12.sp)
        packet?.error?.let{Text("Falha de atualização: $it",fontSize=12.sp)}
    }
}
