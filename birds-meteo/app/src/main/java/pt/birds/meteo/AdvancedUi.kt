package pt.birds.meteo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

@Composable internal fun WeatherAtmosphere(code:Int?,isDay:Boolean?){
    val kind=weatherEffect(code,isDay)
    if(kind=="none")return
    var enabled by remember{mutableStateOf(true)}
    var tick by remember(kind){mutableIntStateOf(0)}
    LaunchedEffect(kind,enabled){while(enabled){delay(75);tick=(tick+1)%2400}}
    Canvas(Modifier.fillMaxWidth().height(82.dp)){
        val t=if(enabled)tick else 0
        val w=size.width;val h=size.height
        when(kind){
            "sun"->{val center=Offset(w*.5f,h*.49f);drawCircle(Color(0xFFFFD279).copy(alpha=.13f),h*.46f,center);drawCircle(Color(0xFFFFD279),h*.25f,center)}
            "stars"->{for(j in 0..29){val x=((j*73+17)%101)/101f*w;val y=((j*37+11)%97)/97f*h;drawCircle(Color.White.copy(alpha=(.36+.16*sin(t*.025+j)).toFloat()),1.6.dp.toPx(),Offset(x,y))}}
            "rain","storm","snow"->{
                for(j in 0..35){val x=((j*73+t*(if(kind=="snow")1 else 3))%191)/191f*w;val y=((j*43+t*(if(kind=="snow")2 else 11))%103)/103f*h
                    if(kind=="snow")drawCircle(Color.White.copy(alpha=.75f),2.dp.toPx(),Offset(x,y)) else drawLine(Color(0xFFA6D9F5).copy(alpha=.75f),Offset(x,y),Offset(x-6.dp.toPx(),y+15.dp.toPx()),1.7.dp.toPx())}
                if(kind=="storm"){val x=w*.52f;drawLine(Color(0xFFFFD279),Offset(x,h*.1f),Offset(x-h*.13f,h*.52f),3.dp.toPx());drawLine(Color(0xFFFFD279),Offset(x-h*.13f,h*.52f),Offset(x+h*.08f,h*.48f),3.dp.toPx());drawLine(Color(0xFFFFD279),Offset(x+h*.08f,h*.48f),Offset(x-h*.04f,h*.92f),3.dp.toPx())}
            }
            "fog","cloud"->{for(j in 0..4){val x=((t*(j%2+1)+j*91)%400)/400f*(w+h)-h/2;drawOval(Color(0xFFBCD4E5).copy(alpha=if(kind=="fog") .12f else .26f),Offset(x-w*.12f,h*(.12f+j*.1f)),Size(w*.36f,h*.35f))}}
        }
    }
    TextButton(onClick={enabled=!enabled}){Text(if(enabled)"Pausar animação" else "Animar estado do tempo",fontSize=10.sp)}
}

@Composable internal fun PlaceSearch(s:MeteoState){
    var q by remember{mutableStateOf("")};var ptOnly by remember{mutableStateOf(true)}
    Section("Pesquisar localidades"){
        OutlinedTextField(q,{q=it.take(100)},label={Text("Nome ou código postal")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Row(verticalAlignment=Alignment.CenterVertically){Text("Apenas Portugal",modifier=Modifier.weight(1f));Switch(checked=ptOnly,onCheckedChange={ptOnly=it})}
        Button(enabled=q.trim().length>=2&&!s.searching,onClick={s.searchPlaces(q,ptOnly)}){Text(if(s.searching)"A pesquisar…" else "Pesquisar")}
        val packet=s.searchResult;val arr=packet?.obj()?.optJSONArray("results")
        if(packet?.error!=null)Text("Pesquisa indisponível: ${packet.error}")
        else if(packet!=null&&(arr==null||arr.length()==0))Text("Sem resultados. Continua disponível a introdução por coordenadas.")
        if(arr!=null)for(i in 0 until arr.length()){
            val r=arr.optJSONObject(i)?:continue;val name=r.optString("name").takeIf{it.isNotBlank()}?:continue
            val lat=r.number("latitude")?.takeIf{it in -90.0..90.0}?:continue;val lon=r.number("longitude")?.takeIf{it in -180.0..180.0}?:continue
            val ref=listOf(r.optString("admin2"),r.optString("admin1"),r.optString("country")).filter{it.isNotBlank()}.distinct().joinToString(" · ")
            val duplicate=s.places.any{distanceKm(it.latitude,it.longitude,lat,lon)<.05}
            Text(name);Text(ref, color=subtle,fontSize=12.sp);Text("$lat, $lon",color=subtle,fontSize=11.sp)
            TextButton(enabled=!duplicate,onClick={s.addResult(name,ref,lat,lon)}){Text(if(duplicate)"Já adicionada" else "Adicionar localidade")}
            HorizontalDivider()
        }
        Text("Fonte: Open-Meteo / GeoNames. Resultados podem não abranger lugares muito pequenos. Distrito IPMA de novas localidades não é inferido.",fontSize=11.sp,color=subtle)
    }
}

@Composable internal fun AstronomySection(p:Place,w:JSONObject?,s:MeteoState){
    val zone=runCatching{ZoneId.of(w?.optString("timezone").orEmpty())}.getOrNull()?:if(p.id in WeatherSpec.districts)ZoneId.of("Europe/Lisbon")else null
    Section("Astronomia avançada"){
        if(zone==null){Text("Fuso horário indisponível. Atualiza a previsão para obter os horários locais.");return@Section}
        val date=LocalDate.now(zone);val formatter=DateTimeFormatter.ofPattern("HH:mm")
        Text("$date · ${zone.id}",color=subtle,fontSize=12.sp)
        listOf("Civil" to 6.0,"Náutico" to 12.0,"Astronómico" to 18.0).forEach{(label,angle)->
            val t=remember(p,date,zone,angle){twilight(p,date,zone,angle)}
            Line("$label · manhã",t?.first?.format(formatter)?:"Sem cruzamento calculável")
            Line("$label · noite",t?.second?.format(formatter)?:"Sem cruzamento calculável")
        }
        Text("Crepúsculos: aproximação astronómica NOAA, horizonte ideal. Sem correção por relevo.",fontSize=11.sp,color=subtle)
        Button(onClick={s.loadAstronomy(p,date,zone)}){Text("Consultar horários da Lua")}
        val packet=s.extras[s.extraKey(p,"astro",date)];if(packet!=null){
            Source(packet,"US Naval Observatory",1440)
            val data=packet.obj()?.optJSONObject("properties")?.optJSONObject("data")
            val moon=data?.optJSONArray("moondata")
            fun event(name:String):String=(0 until (moon?.length()?:0)).mapNotNull{i->moon?.optJSONObject(i)?.takeIf{it.optString("phen").equals(name,true)}?.optString("time")?.takeIf{it.isNotBlank()}}.joinToString(" / ").ifBlank{"—"}
            Line("Nascer da Lua",event("Rise"));Line("Trânsito lunar",event("Upper Transit"));Line("Pôr da Lua",event("Set"));Line("Iluminação USNO",data?.optString("fracillum")?.takeIf{it.isNotBlank()}?:"—")
            Text("Horários com o desvio UTC ao meio-dia local; em dias de mudança de hora podem diferir. — = indisponível ou sem evento nessa data.",fontSize=11.sp,color=subtle)
        }
    }
}
@Composable internal fun GeomagneticSection(packet:Packet?){
    Section("Atividade geomagnética · global"){
        Source(packet,"NOAA SWPC",60)
        val arr=packet?.array();val now=Instant.now();val latest=if(arr==null)null else (0 until arr.length()).mapNotNull{i->val r=arr.optJSONObject(i)?:return@mapNotNull null;val time=utcInstant(r.optString("time_tag"))?:return@mapNotNull null;val kp=kpValue(r.number("Kp"))?:return@mapNotNull null;if(time.isAfter(now))null else time to kp}.maxByOrNull{it.first}
        Line("Kp",formatValue(latest?.second));Line("Escala",kpScale(latest?.second)?:"—");Line("Instante UTC",latest?.first?.toString()?:"—")
        if(latest!=null&&latest.first.isBefore(now.minusSeconds(6*3600)))Text("Observação antiga; não representa atividade atual.",color=subtle)
        Text("Índice planetário estimado de 3 horas. Não é uma medição local nem uma previsão de auroras.",fontSize=11.sp,color=subtle)
    }
}
@Composable internal fun ClimateSection(p:Place,s:MeteoState){
    Section("Climatologia · 1991–2020"){
        Text("Normais mensais calculadas a partir da reanálise ERA5, não de uma estação local. Consulta pesada, efetuada apenas a pedido.",color=subtle,fontSize=12.sp)
        Button(onClick={s.loadClimate(p)}){Text("Consultar normais climatológicas")}
        val packet=s.extras[s.extraKey(p,"climate")]
        if(packet!=null){Source(packet,"ERA5 / Open-Meteo",525600)
            val normals=remember(packet){val d=packet.obj()?.optJSONObject("daily");val rows=d.times().mapIndexedNotNull{i,raw->runCatching{LocalDate.parse(raw)}.getOrNull()?.let{ClimateDay(it,d.at("temperature_2m_mean",i),d.at("precipitation_sum",i))}};climateNormals(rows)}
            if(normals.isEmpty())Text("Sem série completa de 30 anos; não são calculadas normais parciais.")
            else normals.forEach{n->Line(Month.of(n.month).getDisplayName(java.time.format.TextStyle.FULL,Locale.forLanguageTag("pt-PT")),"${formatValue(n.temp,"°C")} · ${formatValue(n.rain,"mm")}")}
            Text("Temperatura média mensal e média dos totais mensais de precipitação. Só meses com 30 anos completos.",fontSize=11.sp,color=subtle)
        }
    }
}
