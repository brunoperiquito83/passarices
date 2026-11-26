package pt.birds.meteo

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val bg=Color(0xFF0A1726);private val panel=Color(0xFF1A344B);internal val accent=Color(0xFF81D3F3);internal val subtle=Color(0xFFB3CADB)
private val pt=Locale.forLanguageTag("pt-PT")
private val clock=DateTimeFormatter.ofPattern("dd/MM HH:mm",pt)
private fun stamp(p:Packet?)=p?.saved?.takeIf{it>0}?.let{Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(clock)}?:"—"
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun BirdsApp(s:MeteoState,requestNotifications:()->Unit,locate:()->Unit){
    val list=s.all;val count=list.size+if(s.gps==null)1 else 0;val pager=rememberPagerState(initialPage=list.indexOfFirst{it.id==s.primary}.coerceAtLeast(0),pageCount={count});val scope=rememberCoroutineScope();var tab by remember{mutableIntStateOf(0)};val place=list.getOrNull(pager.currentPage)
    MaterialTheme(colorScheme=darkColorScheme(primary=accent,background=bg,surface=bg,onSurface=Color.White,surfaceVariant=panel,onSurfaceVariant=subtle)){
        Scaffold(containerColor=bg,modifier=Modifier.safeDrawingPadding(),topBar={Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){Text("BIRDS METEO",fontSize=15.sp,fontWeight=FontWeight.Bold,color=accent,modifier=Modifier.weight(1f));TextButton(onClick={s.refresh(true)}){Text(if(s.updating)"A atualizar" else "Atualizar")}}},bottomBar={NavigationBar(containerColor=Color(0xFF11283C)){listOf("Tempo","Mapas","Comparar","Locais","Opções").forEachIndexed{i,t->NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Text(listOf("◉","▧","≋","⌖","⚙")[i],fontSize=20.sp)},label={Text(t,fontSize=10.sp)})}}}){pad->
            Box(Modifier.fillMaxSize().padding(pad)) { when(tab){
                0->Column{LazyRow(contentPadding=PaddingValues(horizontal=14.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){items(count){i->FilterChip(selected=pager.currentPage==i,onClick={scope.launch{pager.animateScrollToPage(i)}},label={Text(list.getOrNull(i)?.name?:"GPS")})}};HorizontalPager(state=pager,modifier=Modifier.weight(1f),key={i->list.getOrNull(i)?.id?:"gps_pending"}){i->val p=list.getOrNull(i);if(p==null){Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Text("Localização atual",fontSize=24.sp);Text(s.gpsMessage,color=subtle);Button(onClick=locate){Text("Obter localização GPS")}}}else{PullToRefreshBox(isRefreshing=s.updating,onRefresh={s.refresh(true)},modifier=Modifier.fillMaxSize()){WeatherPage(p,s.data[p.id],s.warnings,s,locate)}}}}
                1->MapPage(place?:list.first(),s)
                2->ComparePage(list,place?:list.first(),s)
                3->PlacesPage(s,locate){id->tab=0;scope.launch{pager.scrollToPage(s.all.indexOfFirst{it.id==id}.coerceAtLeast(0))}}
                4->OptionsPage(s,requestNotifications)
            }}
        }
    }
}
@Composable internal fun Section(title:String,content:@Composable ColumnScope.()->Unit){Card(colors=CardDefaults.cardColors(containerColor=panel),shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(title.uppercase(pt),fontSize=12.sp,color=accent,fontWeight=FontWeight.Bold);content()}}}
@Composable internal fun Line(label:String,value:String){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){Text(label,color=subtle,fontSize=13.sp,modifier=Modifier.weight(1f));Text(value,fontSize=14.sp,fontWeight=FontWeight.Medium)}}
@Composable internal fun Source(p:Packet?,name:String,minutes:Long=60){Text("$name · Descarregado: ${stamp(p)}"+(if(p?.saved!=null&&expired(p.saved,System.currentTimeMillis(),minutes)) " · desatualizado" else ""),color=subtle,fontSize=11.sp);p?.error?.let{Text("Falha de atualização: $it",color=Color(0xFFFFCE8A),fontSize=12.sp)}}
@Composable private fun WeatherPage(p:Place,d:LocalData?,warn:Packet?,s:MeteoState,locate:()->Unit){val w=d?.weather?.obj();val h=w?.optJSONObject("hourly");val day=w?.optJSONObject("daily");val c=w?.optJSONObject("current");val i=w.hourIndex();val a=d?.air?.obj();val ah=a?.optJSONObject("hourly");val ai=a.hourIndex();val code=c.number("weather_code")?.toInt();val gradient=when{c.number("is_day")==0.0->listOf(Color(0xFF111C42),bg);code in listOf(61,63,65,80,81,82,95,96,99)->listOf(Color(0xFF435566),bg);else->listOf(Color(0xFF28618A),bg)}
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(gradient)).verticalScroll(rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Column(Modifier.fillMaxWidth().padding(vertical=18.dp),horizontalAlignment=Alignment.CenterHorizontally){WeatherAtmosphere(code,c.number("is_day")?.let{it==1.0});Text(p.name,fontSize=29.sp,fontWeight=FontWeight.Medium);Text(p.reference,color=subtle,fontSize=13.sp);if(p.gps)TextButton(onClick=locate){Text("Atualizar posição")};Text(formatValue(c.number("temperature_2m"),"°"),fontSize=76.sp,fontWeight=FontWeight.Light);Text(describeWeather(code),fontSize=19.sp);Text("Sensação ${formatValue(c.number("apparent_temperature"),"°")}  ·  Máx ${formatValue(day.at("temperature_2m_max",0),"°")}  ·  Mín ${formatValue(day.at("temperature_2m_min",0),"°")}",fontSize=13.sp);Spacer(Modifier.height(7.dp));Source(d?.weather,"Open-Meteo · modelo, não medição")}
        Alerts(p,warn)
        StationSection(p,s.observations)
        RiskSections(p,s)
        Section("Próximas 48 horas"){val indices=(i.coerceAtLeast(0) until minOf(i.coerceAtLeast(0)+48,h.times().size)).toList();if(indices.isEmpty())Text("Sem previsão disponível") else {Trend(indices.map{h.at("temperature_2m",it)});LazyRow(horizontalArrangement=Arrangement.spacedBy(16.dp)){items(indices){j->Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.width(77.dp)){Text(h.strAt("time",j)?.replace('T',' ')?.takeLast(5)?:"—",color=subtle,fontSize=12.sp);Text(formatValue(h.at("temperature_2m",j),"°"),fontSize=20.sp);Text(describeWeather(h.at("weather_code",j)?.toInt()),fontSize=10.sp,minLines=2);Text(formatValue(h.at("precipitation_probability",j),"%"),color=accent,fontSize=12.sp);Text(formatValue(h.at("wind_speed_10m",j),"km/h"),fontSize=11.sp)}}}}}
        Section("Previsão diária · até 16 dias"){val dates=day.times();if(dates.isEmpty())Text("Sem previsão disponível");dates.forEachIndexed{j,date->val label=runCatching{LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE dd/MM",pt))}.getOrDefault(date);Line(label,"${formatValue(day.at("temperature_2m_min",j),"°")} / ${formatValue(day.at("temperature_2m_max",j),"°")} · ${formatValue(day.at("precipitation_probability_max",j),"%")}");Text("${describeWeather(day.at("weather_code",j)?.toInt())} · ${formatValue(day.at("precipitation_sum",j),"mm")} · Rajadas ${formatValue(day.at("wind_gusts_10m_max",j),"km/h")}",color=subtle,fontSize=11.sp);if(j<dates.lastIndex)HorizontalDivider(color=Color.White.copy(alpha=.08f))}}
        Section("Previsão a cada 15 minutos"){Text("Valores de modelo; podem ser interpolados.",color=subtle,fontSize=12.sp);val q=w?.optJSONObject("minutely_15");val qi=w.hourIndex("minutely_15").coerceAtLeast(0);val indices=(qi until minOf(qi+24,q.times().size)).toList();if(indices.isEmpty())Text("Sem dados disponíveis") else LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){items(indices){j->Column{Text(q.strAt("time",j)?.takeLast(5)?:"—",color=subtle);Text(formatValue(q.at("temperature_2m",j),"°C"));Text(formatValue(q.at("precipitation",j),"mm"),color=accent)}}}}
        WeatherSpec.fields.groupBy{it.group}.forEach{(group,fields)->Section(group){fields.forEach{f->val v=c.number(f.key)?:h.at(f.key,i);Line(f.label,formatValue(v,f.unit)+(if(f.key=="wind_direction_10m"&&v!=null) " ${direction(v)}" else ""))};if(group=="Vento")Line("Escala de Beaufort",beaufort(c.number("wind_speed_10m")?:h.at("wind_speed_10m",i))?.toString()?:"—");Text("Valores atuais ou da hora em curso · — = indisponível",fontSize=11.sp,color=subtle)}}
        Section("Sol e Lua"){Line("Nascer do Sol",day.strAt("sunrise",0)?.takeLast(5)?:"—");Line("Pôr do Sol",day.strAt("sunset",0)?.takeLast(5)?:"—");Line("Duração do dia",day.at("daylight_duration",0)?.let{ "${it.toInt()/3600}h ${it.toInt()%3600/60}min"}?:"—");val phase=moonPhase(w?.localNow()?.toLocalDate()?:LocalDate.now());Line("Fase lunar (aprox.)",phase.first);Line("Iluminação (aprox.)","${phase.second}%")}
        AstronomySection(p,w,s)
        GeomagneticSection(s.kp)
        ClimateSection(p,s)
        Section("Qualidade do ar e pólen"){Source(d?.air,"CAMS / Open-Meteo");WeatherSpec.air.forEach{f->Line(f.label,formatValue(ah.at(f.key,ai),f.unit))};Text("Pólen pode estar indisponível fora da época.",fontSize=11.sp,color=subtle)}
        Section("Histórico meteorológico"){var date by remember(p.id){mutableStateOf(LocalDate.now().minusDays(7).toString())};OutlinedTextField(value=date,onValueChange={date=it},label={Text("Data AAAA-MM-DD")},singleLine=true);val dt=runCatching{LocalDate.parse(date)}.getOrNull();Button(enabled=dt!=null&&dt.isBefore(LocalDate.now())&&dt.year>=1940,onClick={dt?.let{s.loadExtra(p,"history",it)}}){Text("Consultar histórico")};val packet=dt?.let{s.extras[s.extraKey(p,"history",it)]};if(packet!=null){Source(packet,"Open-Meteo Archive · reanálise");val obj=packet.obj()?.optJSONObject("daily");Line("Máxima",formatValue(obj.at("temperature_2m_max",0),"°C"));Line("Mínima",formatValue(obj.at("temperature_2m_min",0),"°C"));Line("Precipitação",formatValue(obj.at("precipitation_sum",0),"mm"));Line("Vento máximo",formatValue(obj.at("wind_speed_10m_max",0),"km/h"))}}
        Section("Meteorologia marítima"){Text("Consulta da célula marítima disponível; pode ficar longe de localidades interiores.",fontSize=12.sp,color=subtle);Button(onClick={s.loadExtra(p,"marine")}){Text("Consultar mar")};s.extras[s.extraKey(p,"marine")]?.let{packet->Source(packet,"Open-Meteo Marine");val obj=packet.obj();val m=obj?.optJSONObject("hourly");val mi=obj.hourIndex();Line("Célula marítima","${formatValue(obj.number("latitude"))}, ${formatValue(obj.number("longitude"))}");listOf(Field("wave_height","Altura das ondas","m"),Field("wave_period","Período","s"),Field("wave_direction","Direção","°"),Field("swell_wave_height","Ondulação","m"),Field("swell_wave_period","Período da ondulação","s"),Field("sea_surface_temperature","Temperatura da água","°C"),Field("ocean_current_velocity","Corrente","km/h"),Field("sea_level_height_msl","Nível do mar estimado","m")).forEach{f->Line(f.label,formatValue(m.at(f.key,mi),f.unit))}}}
        Text("Dados: Open-Meteo, CAMS, IPMA. As previsões não são medições locais.",fontSize=11.sp,color=subtle);Spacer(Modifier.height(8.dp))
    }
}
@Composable private fun Trend(values:List<Double?>){val finite=values.filterNotNull();if(finite.size<2)return;val min=finite.min();val span=(finite.max()-min).coerceAtLeast(1.0);Canvas(Modifier.fillMaxWidth().height(55.dp)){values.zipWithNext().forEachIndexed{i,(a,b)->if(a!=null&&b!=null)drawLine(accent,Offset(size.width*i/(values.size-1),size.height*(1-((a-min)/span).toFloat())),Offset(size.width*(i+1)/(values.size-1),size.height*(1-((b-min)/span).toFloat())),strokeWidth=3.dp.toPx())}}}
@Composable private fun Alerts(p:Place,packet:Packet?){Section("Avisos oficiais IPMA"){Source(packet,"IPMA",15);val arr=packet?.array();val rows=if(arr==null)emptyList()else List(arr.length()){arr.optJSONObject(it)}.filterNotNull().filter{it.optString("idAreaAviso")==p.district&&it.optString("awarenessLevelID") in listOf("yellow","orange","red")&&runCatching{LocalDateTime.parse(it.optString("endTime")).toInstant(ZoneOffset.UTC).isAfter(Instant.now())}.getOrDefault(false)};when{p.district.isBlank()->Text("Distrito não identificado; consulta oficial necessária.");arr==null->Text("Avisos indisponíveis.");rows.isEmpty()->Text(if(packet?.error!=null||expired(packet?.saved?:0,System.currentTimeMillis(),15))"Sem avisos no último ficheiro disponível; confirmação necessária." else "Sem avisos ativos ou futuros no ficheiro consultado.");else->rows.forEach{r->val level=r.optString("awarenessLevelID");Text("${mapOf("yellow" to "Amarelo","orange" to "Laranja","red" to "Vermelho")[level]} · ${r.optString("awarenessTypeName")}",color=when(level){"red"->Color(0xFFFF9696);"orange"->Color(0xFFFFB36D);else->Color(0xFFFFDE79)},fontWeight=FontWeight.Bold);Text(r.optString("text"));Text("${r.optString("startTime")} → ${r.optString("endTime")} UTC",fontSize=11.sp,color=subtle)}}}}
@Composable private fun ComparePage(places:List<Place>,p:Place,s:MeteoState){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("Comparar",fontSize=28.sp);Section("Localidades · condições do modelo"){places.forEach{loc->val packet=s.data[loc.id]?.weather;val c=packet?.obj()?.optJSONObject("current");Line(loc.name,"${formatValue(c.number("temperature_2m"),"°C")} · ${formatValue(c.number("wind_speed_10m"),"km/h")}");Text("Dados: ${stamp(packet)}",color=subtle,fontSize=10.sp)}};Section("Modelos · ${p.name}"){val models=listOf("ecmwf_ifs025" to "ECMWF IFS 0,25°","gfs_seamless" to "GFS","icon_seamless" to "ICON");Button(onClick={models.forEach{s.loadExtra(p,it.first)}}){Text("Carregar modelos")};models.forEach{(id,label)->val packet=s.extras[s.extraKey(p,id)];val o=packet?.obj();val h=o?.optJSONObject("hourly");val i=o.hourIndex();Text(label,fontWeight=FontWeight.Bold);Line("Temperatura",formatValue(h.at("temperature_2m",i),"°C"));Line("Precipitação",formatValue(h.at("precipitation",i),"mm"));Line("Vento",formatValue(h.at("wind_speed_10m",i),"km/h"));if(packet!=null)Source(packet,"Open-Meteo");if(i>=0)Trend((i until minOf(i+24,h.times().size)).map{h.at("temperature_2m",it)})};Text("Divergências entre modelos não equivalem a probabilidades.",color=subtle,fontSize=12.sp)}}}
@Composable private fun MapPage(p:Place,s:MeteoState){val ctx=LocalContext.current;Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("Mapas · ${p.name}",fontSize=26.sp);Section("Radar de precipitação"){RadarPanel(p,s)};Section("Satélite e mapas externos"){Text("Satélite: acesso ao visualizador oficial; imagem nativa ainda indisponível.",color=subtle);TextButton(onClick={runCatching{ctx.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://view.eumetsat.int/")))}}){Text("Satélite EUMETView")};TextButton(onClick={ctx.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.ipma.pt/pt/otempo/obs.radar/")))}){Text("Radar oficial IPMA")};TextButton(onClick={ctx.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.windy.com/${p.latitude}/${p.longitude}")))}){Text("Abrir mapa Windy")}};Section("Coordenadas"){Line("Latitude",p.latitude.toString());Line("Longitude",p.longitude.toString())}}}
@Composable private fun PlacesPage(s:MeteoState,locate:()->Unit,select:(String)->Unit){var name by remember{mutableStateOf("")};var lat by remember{mutableStateOf("")};var lon by remember{mutableStateOf("")};Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("Localidades",fontSize=28.sp);Section("Predefinidas e personalizadas"){s.places.forEachIndexed{i,p->Column{Text(p.name,modifier=Modifier.clickable{select(p.id)},fontSize=17.sp);Text(p.reference,color=subtle,fontSize=12.sp);Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){TextButton(onClick={s.move(i,-1)},enabled=i>0){Text("↑")};TextButton(onClick={s.move(i,1)},enabled=i<s.places.lastIndex){Text("↓")};TextButton(onClick={s.setPrimary(p.id)}){Text(if(s.primary==p.id)"Principal ✓" else "Principal")};TextButton(onClick={s.remove(p.id)},enabled=s.places.size>1){Text("Remover")}};HorizontalDivider(color=Color.White.copy(alpha=.08f))}}};Section("Localização atual"){Text(s.gps?.name?:s.gpsMessage);Button(onClick=locate){Text("Obter GPS")};s.gps?.let{TextButton(onClick={select(it.id)}){Text("Abrir página GPS")}}};PlaceSearch(s);Section("Adicionar por coordenadas"){OutlinedTextField(name,{name=it},label={Text("Nome")},singleLine=true);OutlinedTextField(lat,{lat=it},label={Text("Latitude")},singleLine=true);OutlinedTextField(lon,{lon=it},label={Text("Longitude")},singleLine=true);val a=lat.replace(',','.').toDoubleOrNull();val b=lon.replace(',','.').toDoubleOrNull();Button(enabled=name.isNotBlank()&&a!=null&&b!=null&&a in -90.0..90.0&&b in -180.0..180.0,onClick={if(a!=null&&b!=null){s.add(name,a,b);name="";lat="";lon=""}}){Text("Adicionar")}}}}
@Composable private fun OptionsPage(s:MeteoState,requestNotifications:()->Unit){var confirm by remember{mutableStateOf(false)};Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("Opções",fontSize=28.sp);Section("Atualização"){Row(verticalAlignment=Alignment.CenterVertically){Text("Segundo plano (30 min, não garantido)",modifier=Modifier.weight(1f));Switch(checked=s.background,onCheckedChange=s::setBackground)};Text("Atualização ao abrir e ao puxar para baixo. Android pode adiar tarefas em segundo plano.",color=subtle,fontSize=12.sp);Button(onClick={s.refresh(true)}){Text("Atualizar todas")}};NotificationOptions(s,requestNotifications);Section("Configuração"){Line("Idioma","Português (Portugal)");Line("Unidades","Sistema métrico");TextButton(onClick={confirm=true}){Text("Restaurar localidades originais")}};Section("Versão de desenvolvimento"){Text("Birds Meteo 0.8-rc1 · versão de validação");Text("Notificações, widget, radar, estações IPMA, sismos, incêndios e caudal por validar no Android. Satélite integrado ainda pendente.",color=subtle);Text("Fontes: Open-Meteo / ECMWF / DWD / NOAA, CAMS ENSEMBLE, IPMA.",fontSize=12.sp,color=subtle)}};if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Restaurar localidades?")},text={Text("As localidades personalizadas serão removidas.")},confirmButton={TextButton(onClick={s.reset();confirm=false;s.refresh(true)}){Text("Restaurar")}},dismissButton={TextButton(onClick={confirm=false}){Text("Cancelar")}})}

@Composable private fun StationSection(p:Place,packet:Packet?){
    val station=remember(p,packet){nearestObservation(p,packet)}
    Section("Observações reais · IPMA"){
        Source(packet,"Estações automáticas IPMA")
        if(station==null)Text("Não há medições válidas de uma estação a menos de 50 km nas últimas 3 horas.")
        else {
            Line("Estação",station.name);Line("Distância",formatValue(station.distance,"km"));Line("Hora da medição",station.time.replace('T',' ')+" UTC")
            val o=station.values
            listOf(Field("temperatura","Temperatura (média horária)","°C"),Field("humidade","Humidade","%"),Field("pressao","Pressão ao nível do mar","hPa"),Field("intensidadeVentoKM","Vento","km/h"),Field("precAcumulada","Chuva acumulada na hora","mm"),Field("radiacao","Radiação solar","kJ/m²")).forEach{f->Line(f.label,formatValue(ipmaValue(o.number(f.key)),f.unit))}
            Line("Direção do vento",ipmaDirection(ipmaValue(o.number("idDireccVento"))?.toInt()))
            Text("Medições na estação indicada, não no ponto exato da localidade. Valores em falta não são substituídos.",fontSize=11.sp,color=subtle)
        }
    }
}

@Composable private fun RiskSections(p:Place,s:MeteoState){
    Section("Risco de incêndio rural · IPMA"){
        val code=municipalities[p.id]
        if(code==null)Text("Concelho não confirmado para esta posição. Risco indisponível, não é risco reduzido.")
        else {
            val today=LocalDate.now(ZoneId.of("Europe/Lisbon"))
            s.fire.forEachIndexed{index,packet->
                val root=packet?.obj();val date=root?.optString("dataPrev")?.take(10);val local=root?.optJSONObject("local")?.optJSONObject(code)
                val level=local?.optJSONObject("data")?.number("rcm")?.takeIf{it%1==0.0}?.toInt()
                val label=if(date==today.plusDays(index.toLong()).toString())fireRiskLabel(level) else null
                Line(listOf("Hoje","Amanhã","Depois de amanhã")[index],label?:"Indisponível")
                Source(packet,"IPMA · ${date?:"data desconhecida"}")
            }
            Text("Índice oficial por concelho; não é previsão de incêndios ativos.",color=subtle,fontSize=11.sp)
        }
    }
    Section("Sismicidade · últimos 30 dias"){
        Source(s.seismic,"IPMA · Continente e Madeira")
        val a=s.seismic?.obj()?.optJSONArray("data");val now=Instant.now()
        val events=if(a==null)emptyList() else (0 until a.length()).mapNotNull{i->
            val r=a.optJSONObject(i)?:return@mapNotNull null
            val d=quakeDistance(p,r.number("lat"),r.number("lon")?:r.number("long"))?:return@mapNotNull null
            val t=utcInstant(r.optString("time"))?:return@mapNotNull null
            if(d>200||t.isAfter(now)||t.isBefore(now.minusSeconds(30L*86400)))null else Triple(r,d,t)
        }.sortedByDescending{it.third}
        if(a==null)Text("Dados indisponíveis.") else if(events.isEmpty())Text("Nenhum evento elegível num raio de 200 km no ficheiro disponível.") else {
            Line("Eventos no raio de 200 km",events.size.toString())
            events.take(10).forEach{(r,d,t)->
                Text(r.optString("obsRegion").ifBlank{"Epicentro sem nome"},fontWeight=FontWeight.Medium)
                Line("Magnitude ${r.optString("magType")}",formatValue(ipmaValue(r.number("magnitud"))))
                Line("Distância ao epicentro",formatValue(d,"km"));Text(t.toString()+" · UTC",color=subtle,fontSize=11.sp)
                HorizontalDivider(color=Color.White.copy(alpha=.08f))
            }
        }
        Text("Registo de eventos, não previsão de sismos. Açores não incluídos nesta fonte.",color=subtle,fontSize=11.sp)
    }
    Section("Hidrologia · caudal fluvial"){
        Button(onClick={s.loadExtra(p,"flood")}){Text("Consultar caudal · 7 dias")}
        val packet=s.extras[s.extraKey(p,"flood")];val o=packet?.obj();val day=o?.optJSONObject("daily")
        if(packet!=null){Source(packet,"Copernicus GloFAS / Open-Meteo");if(day.times().isEmpty())Text("Sem dados de caudal disponíveis.") else day.times().forEachIndexed{i,date->Line(date,formatValue(day.at("river_discharge",i),"m³/s"));Line("Intervalo P25–P75","${formatValue(day.at("river_discharge_p25",i))} – ${formatValue(day.at("river_discharge_p75",i),"m³/s")}")}}
        Text("Modelo do curso de água selecionado pelo fornecedor numa área de 5 km. Pode não representar um rio específico. Caudal não equivale a aviso de inundação.",color=subtle,fontSize=11.sp)
    }
}
@Composable private fun NotificationOptions(s:MeteoState,requestNotifications:()->Unit){
    Section("Notificações meteorológicas"){
        Button(onClick=requestNotifications){Text("Permissões Android")}
        Text("Desativadas por defeito. Avisos oficiais por distrito e previsão de chuva nas próximas três horas. O Android pode atrasar a entrega.",color=subtle,fontSize=12.sp)
        Line("Probabilidade mínima de chuva","${s.rainThreshold}%")
        Slider(value=s.rainThreshold.toFloat(),onValueChange={s.setRainThreshold(it.toInt())},valueRange=10f..100f,steps=8)
        s.places.forEach{p->
            Text(p.name,fontWeight=FontWeight.Medium)
            Row(verticalAlignment=Alignment.CenterVertically){Text(if(p.district.isBlank())"IPMA · distrito desconhecido" else "Avisos oficiais IPMA",modifier=Modifier.weight(1f));Switch(checked=s.alertOn(p.id,"official"),enabled=p.district.isNotBlank(),onCheckedChange={s.setAlert(p.id,"official",it)})}
            Row(verticalAlignment=Alignment.CenterVertically){Text("Previsão de chuva",modifier=Modifier.weight(1f));Switch(checked=s.alertOn(p.id,"rain"),onCheckedChange={s.setAlert(p.id,"rain",it)})}
            HorizontalDivider(color=Color.White.copy(alpha=.08f))
        }
        Text("GPS não recebe notificações em segundo plano nesta versão. Um aviso IPMA partilhado por várias localidades do mesmo distrito não é repetido.",fontSize=11.sp,color=subtle)
    }
    Section("Widget Android"){
        Text("Adicionar Birds Meteo a partir do seletor de widgets do ecrã inicial. Mostra a localidade principal; redimensionável, com mais informação quando há espaço.")
        Text("Atualizado pela aplicação e pelas tarefas de segundo plano. Sem dados recentes, indica a antiguidade.",color=subtle,fontSize=12.sp)
    }
}
