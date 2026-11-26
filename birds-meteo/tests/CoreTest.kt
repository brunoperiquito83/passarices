package pt.birds.meteo
import java.time.*
import java.net.URI
import java.net.URLDecoder
fun main(){
    var n=0
    fun test(label:String,block:()->Boolean){check(block()){label};println("PASS $label");n++}
    val p=Place("palmela","Palmela","Vale de Touros",38.58977,-8.92003,"STB")
    fun params(u:String)=URI(u).rawQuery.split('&').associate{val (k,v)=it.split('=',limit=2);k to URLDecoder.decode(v,"UTF-8")}
    val f=params(WeatherSpec.forecast(p));val basic=params(WeatherSpec.forecast(p,false))
    test("coordenadas preservadas"){f["latitude"]=="38.58977"&&f["longitude"]=="-8.92003"}
    test("previsao 16 dias e fuso automatico"){f["forecast_days"]=="16"&&f["timezone"]=="auto"}
    test("15 minutos: 24 intervalos"){f["forecast_minutely_15"]=="24"&&f["minutely_15"]!!.contains("rain")}
    test("catalogo horario sem duplicados"){val k=f["hourly"]!!.split(',');k.size==k.distinct().size&&k.size>=35}
    test("fallback sem campos experimentais"){basic["minutely_15"]==null&&basic["hourly"]!!.contains("temperature_2m")}
    test("ar e polen"){params(WeatherSpec.air(p))["hourly"]!!.contains("olive_pollen")}
    test("historico data exata"){params(WeatherSpec.historical(p,LocalDate.of(2020,2,29)))["end_date"]=="2020-02-29"}
    test("mar com celula maritima"){params(WeatherSpec.marine(p))["cell_selection"]=="sea"}
    test("modelo explicito"){params(WeatherSpec.model(p,"icon_seamless"))["models"]=="icon_seamless"}
    test("WMO conhecidos e desconhecidos"){describeWeather(0)=="Céu limpo"&&describeWeather(95)=="Trovoada"&&describeWeather(999)=="Sem informação"}
    test("vento direcoes circular e oeste"){direction(360.0)=="N"&&direction(-90.0)=="O"&&direction(45.0)=="NE"}
    test("Beaufort fronteiras"){beaufort(0.0)==0&&beaufort(1.0)==1&&beaufort(118.0)==12&&beaufort(-1.0)==null}
    test("ausencia nao equivale a zero"){formatValue(null)=="—"&&formatValue(Double.NaN)=="—"&&formatValue(0.0,"mm")=="0 mm"}
    test("formato portugues"){formatValue(12.5,"°C")=="12,5 °C"}
    val t=listOf("2026-11-26T10:00","2026-11-26T11:00","2026-11-26T12:00")
    test("hora mais recente anterior"){timeIndex(t,LocalDateTime.parse("2026-11-26T11:20"))==1}
    test("sem hora passada"){timeIndex(t,LocalDateTime.parse("2026-11-26T09:00"))==-1}
    test("tempo invalido ignorado"){timeIndex(listOf("bad",t[0]),LocalDateTime.parse("2026-11-26T10:30"))==1}
    test("cache fresca"){!expired(1000,61000,2)}
    test("cache expirada"){expired(1000,61000,1)&&expired(0,61000,1)&&expired(62000,61000,1)}
    test("lua nova aproximada"){moonPhase(LocalDate.of(2000,1,6)).first=="Lua nova"&&moonPhase(LocalDate.of(2000,1,6)).second==0}
    test("coordenadas invalidas rejeitadas"){runCatching{p.copy(latitude=91.0)}.isFailure&&runCatching{p.copy(longitude=Double.NaN)}.isFailure}
    test("nove distritos configurados"){WeatherSpec.districts.size==9&&WeatherSpec.districts["almeida"]=="GDA"}
    test("radar zoom limite"){runCatching{radarTile(38.0,-9.0,8)}.isFailure}
    val tile=radarTile(0.0,0.0,6)
    test("radar equador e Greenwich"){tile.x==32&&tile.y==32&&tile.markerX==0.0&&tile.markerY==0.0}
    test("radar limites polares e antimeridiano"){radarTile(90.0,180.0,7).x==127&&radarTile(-90.0,-180.0,7).y==127}
    test("radar URL autorizada"){radarUrl("https://tilecache.rainviewer.com","/v2/radar/123",tile)=="https://tilecache.rainviewer.com/v2/radar/123/512/6/32/32/2/1_1.png"}
    test("radar host e path indevidos rejeitados"){radarUrl("https://example.com","/v2/radar/123",tile)==null&&radarUrl("https://tilecache.rainviewer.com","/v2/radar/../../bad",tile)==null}
    test("distancia identica"){distanceKm(38.0,-9.0,38.0,-9.0)==0.0}
    test("distancia de um grau no equador"){distanceKm(0.0,0.0,0.0,1.0) in 111.0..112.0}
    test("IPMA sentinel e zero"){ipmaValue(-99.0)==null&&ipmaValue(0.0)==0.0&&ipmaValue(Double.NaN)==null}
    test("IPMA direcao"){ipmaDirection(6)=="SO"&&ipmaDirection(9)=="N"&&ipmaDirection(0)=="—"}
    test("idade UTC e futuro"){observationAgeMinutes("2026-11-26T10:00",Instant.parse("2026-11-26T11:00:00Z"))==60L&&observationAgeMinutes("2026-11-26T12:00",Instant.parse("2026-11-26T11:00:00Z"))==null}
    val now=Instant.parse("2026-11-26T12:00:00Z")
    test("codigos municipais nove localidades"){municipalities.keys==WeatherSpec.districts.keys&&municipalities["oriente"]=="1106"&&municipalities["almeida"]=="0902"&&municipalities["castro_verde"]=="0206"}
    test("risco oficial 1-5"){fireRiskLabel(1)=="Reduzido"&&fireRiskLabel(5)=="Máximo"&&fireRiskLabel(0)==null&&fireRiskLabel(-99)==null}
    test("aviso ativo elegivel"){alertEligible("yellow","2026-11-26T10:00:00","2026-11-26T13:00:00",now)}
    test("aviso futuro ate 24 horas"){alertEligible("orange","2026-11-27T12:00:00","2026-11-27T13:00:00",now)&&!alertEligible("orange","2026-11-27T12:01:00","2026-11-27T13:00:00",now)}
    test("aviso expirado e verde excluidos"){!alertEligible("red","2026-11-26T10:00:00","2026-11-26T12:00:00",now)&&!alertEligible("green","2026-11-26T10:00:00","2026-11-26T13:00:00",now)}
    test("aviso com datas invalidas excluido"){!alertEligible("red","bad","2026-11-26T13:00:00",now)&&!alertEligible("red","2026-11-26T14:00:00","2026-11-26T13:00:00",now)}
    test("chuva limiar inclusivo"){rainEligible(70.0,0.1,70)&&!rainEligible(69.9,0.1,70)}
    test("chuva requer precipitacao positiva"){!rainEligible(90.0,0.0,70)&&!rainEligible(90.0,null,70)&&!rainEligible(null,1.0,70)&&!rainEligible(101.0,1.0,70)}
    test("distancia sismo invalida rejeitada"){quakeDistance(p,-99.0,0.0)==null&&quakeDistance(p,null,0.0)==null&&quakeDistance(p,p.latitude,p.longitude)==0.0}
    test("caudal sete dias e quantis"){val q=params(floodUrl(p));q["forecast_days"]=="7"&&q["daily"]=="river_discharge,river_discharge_p25,river_discharge_p75"}
    test("datas UTC explicitas e sem sufixo"){utcInstant("2026-11-26T12:00:00")==now&&utcInstant("2026-11-26T12:00:00Z")==now&&utcInstant("invalid")==null}
    test("pesquisa com codificação e filtro PT"){val q=params(searchUrl("São João & Mar",true));q["name"]=="São João & Mar"&&q["countryCode"]=="PT"&&q["count"]=="20"}
    test("pesquisa mundial e termo curto"){!params(searchUrl("Paris",false)).containsKey("countryCode")&&runCatching{searchUrl("A",true)}.isFailure}
    val lisbon=ZoneId.of("Europe/Lisbon")
    val date=LocalDate.of(2026,11,26)
    test("USNO fuso inverno/verão"){params(astronomicalUrl(p,date,lisbon))["tz"]=="0.0"&&params(astronomicalUrl(p,LocalDate.of(2026,7,1),lisbon))["tz"]=="1.0"}
    test("USNO coordenadas e data"){val q=params(astronomicalUrl(p,date,lisbon));q["coords"]=="38.58977,-8.92003"&&q["date"]==date.toString()}
    test("crepusculos ordem fisica"){val a=twilight(p,date,lisbon,6.0)!!;val b=twilight(p,date,lisbon,12.0)!!;val c=twilight(p,date,lisbon,18.0)!!;c.first<b.first&&b.first<a.first&&a.second<b.second&&b.second<c.second}
    test("crepusculo Palmela janela inverno"){val a=twilight(p,date,lisbon,6.0)!!;a.first.hour in 6..7&&a.second.hour in 17..18&&a.first.toLocalDate()==date}
    test("crepusculo polar sem cruzamento"){twilight(p.copy(latitude=89.0),LocalDate.of(2026,6,21),ZoneOffset.UTC,18.0)==null}
    test("Kp valores válidos e inválidos"){kpValue(0.0)==0.0&&kpValue(9.0)==9.0&&kpValue(9.1)==null&&kpValue(Double.NaN)==null}
    test("escala geomagnética"){kpScale(5.0)=="G1"&&kpScale(8.0)=="G4"&&kpScale(9.0)=="G5"&&kpScale(4.67)=="Abaixo de G1"}
    test("efeitos clima sem falsificar ausência"){weatherEffect(null,true)=="none"&&weatherEffect(0,null)=="none"&&weatherEffect(95,true)=="storm"&&weatherEffect(73,false)=="snow"&&weatherEffect(48,true)=="fog"}
    test("efeitos dia noite e chuva"){weatherEffect(0,true)=="sun"&&weatherEffect(0,false)=="stars"&&weatherEffect(61,false)=="rain"&&weatherEffect(3,true)=="cloud"}
    test("climatologia pedido ERA5 fixo"){val q=params(climateUrl(p));q["start_date"]=="1991-01-01"&&q["end_date"]=="2020-12-31"&&q["models"]=="era5"}
    val start=LocalDate.of(1991,1,1);val rows=(0L until 10958L).map{ClimateDay(start.plusDays(it),15.0,1.0)}
    test("normais completas 12 meses"){val n=climateNormals(rows);n.size==12&&n.all{it.years==30&&it.temp==15.0}&&n.first().rain==31.0&&n[1].rain in 28.0..28.3}
    test("normal rejeita mês incompleto"){climateNormals(rows.drop(1)).size==11&&climateNormals(rows.drop(1)).none{it.month==1}}
    test("normal rejeita dados nulos e duplicados"){climateNormals(rows.toMutableList().apply{this[0]=this[0].copy(temp=null)}).size==11&&climateNormals(rows+rows.first()).size==11}
    println("$n testes aprovados")
}
