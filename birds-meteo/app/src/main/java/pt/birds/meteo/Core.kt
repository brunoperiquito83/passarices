package pt.birds.meteo

import java.time.*
import java.util.Locale
import java.net.URLEncoder

// JVM-only domain logic, also exercised by tests/CoreTest.kt.
data class Place(val id:String,val name:String,val reference:String,val latitude:Double,val longitude:Double,val district:String = "",val gps:Boolean = false) {
    init { require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0) }
}
data class Field(val key:String,val label:String,val unit:String="",val group:String="Atmosfera")
object WeatherSpec {
    val current = listOf("temperature_2m","relative_humidity_2m","apparent_temperature","is_day","precipitation","rain","showers","snowfall","weather_code","cloud_cover","pressure_msl","surface_pressure","wind_speed_10m","wind_direction_10m","wind_gusts_10m")
    val fields = listOf(
        Field("temperature_2m","Temperatura","°C"),Field("apparent_temperature","Sensação térmica","°C"),Field("relative_humidity_2m","Humidade","%"),Field("dew_point_2m","Ponto de orvalho","°C"),Field("pressure_msl","Pressão ao nível do mar","hPa"),Field("surface_pressure","Pressão à superfície","hPa"),Field("visibility","Visibilidade","m"),Field("cloud_cover","Nebulosidade","%"),Field("cloud_cover_low","Nuvens baixas","%"),Field("cloud_cover_mid","Nuvens médias","%"),Field("cloud_cover_high","Nuvens altas","%"),Field("freezing_level_height","Nível de congelação","m"),
        Field("precipitation_probability","Probabilidade de precipitação","%","Precipitação"),Field("precipitation","Precipitação","mm","Precipitação"),Field("rain","Chuva","mm","Precipitação"),Field("showers","Aguaceiros","mm","Precipitação"),Field("snowfall","Queda de neve","cm","Precipitação"),Field("snow_depth","Espessura de neve","m","Precipitação"),
        Field("wind_speed_10m","Vento a 10 m","km/h","Vento"),Field("wind_direction_10m","Direção do vento","°","Vento"),Field("wind_gusts_10m","Rajadas","km/h","Vento"),Field("wind_speed_80m","Vento a 80 m","km/h","Vento"),Field("wind_speed_120m","Vento a 120 m","km/h","Vento"),Field("wind_speed_180m","Vento a 180 m","km/h","Vento"),
        Field("uv_index","Índice UV","","Radiação e Sol"),Field("uv_index_clear_sky","UV com céu limpo","","Radiação e Sol"),Field("shortwave_radiation","Radiação solar","W/m²","Radiação e Sol"),Field("direct_radiation","Radiação direta","W/m²","Radiação e Sol"),Field("diffuse_radiation","Radiação difusa","W/m²","Radiação e Sol"),Field("sunshine_duration","Duração de insolação","s","Radiação e Sol"),
        Field("cape","Energia convectiva CAPE","J/kg","Instabilidade"),Field("soil_temperature_0cm","Temperatura do solo à superfície","°C","Solo"),Field("soil_temperature_6cm","Temperatura do solo a 6 cm","°C","Solo"),Field("soil_temperature_18cm","Temperatura do solo a 18 cm","°C","Solo"),Field("soil_temperature_54cm","Temperatura do solo a 54 cm","°C","Solo"),Field("soil_moisture_0_to_1cm","Humidade do solo 0–1 cm","m³/m³","Solo"),Field("soil_moisture_1_to_3cm","Humidade do solo 1–3 cm","m³/m³","Solo"),Field("soil_moisture_3_to_9cm","Humidade do solo 3–9 cm","m³/m³","Solo"),Field("soil_moisture_9_to_27cm","Humidade do solo 9–27 cm","m³/m³","Solo"),Field("soil_moisture_27_to_81cm","Humidade do solo 27–81 cm","m³/m³","Solo"),Field("et0_fao_evapotranspiration","Evapotranspiração de referência","mm","Solo")
    )
    val daily = listOf("weather_code","temperature_2m_max","temperature_2m_min","apparent_temperature_max","apparent_temperature_min","sunrise","sunset","daylight_duration","sunshine_duration","uv_index_max","uv_index_clear_sky_max","precipitation_sum","rain_sum","showers_sum","snowfall_sum","precipitation_hours","precipitation_probability_max","wind_speed_10m_max","wind_gusts_10m_max","wind_direction_10m_dominant","shortwave_radiation_sum","et0_fao_evapotranspiration")
    val quarter = listOf("temperature_2m","precipitation","rain","snowfall","weather_code","wind_speed_10m","wind_gusts_10m")
    val air = listOf(Field("european_aqi","Índice europeu"),Field("us_aqi","Índice americano"),Field("pm10","PM10","µg/m³"),Field("pm2_5","PM2.5","µg/m³"),Field("carbon_monoxide","CO","µg/m³"),Field("nitrogen_dioxide","NO₂","µg/m³"),Field("sulphur_dioxide","SO₂","µg/m³"),Field("ozone","Ozono","µg/m³"),Field("dust","Poeiras","µg/m³"),Field("alder_pollen","Amieiro","grãos/m³"),Field("birch_pollen","Bétula","grãos/m³"),Field("grass_pollen","Gramíneas","grãos/m³"),Field("mugwort_pollen","Artemísia","grãos/m³"),Field("olive_pollen","Oliveira","grãos/m³"),Field("ragweed_pollen","Ambrósia","grãos/m³"))
    val districts = mapOf("palmela" to "STB","carnaxide" to "LSB","oriente" to "LSB","centro" to "LSB","restelo" to "LSB","castro_verde" to "BJA","almeida" to "GDA","porto" to "PTO","setubal" to "STB")
    private fun query(base:String,p:Place,params:Map<String,String>):String = base+"?"+(linkedMapOf("latitude" to p.latitude.toString(),"longitude" to p.longitude.toString())+params).entries.joinToString("&") { (k,v)-> "$k=${URLEncoder.encode(v,"UTF-8")}" }
    fun forecast(p:Place, extended:Boolean=true):String = query("https://api.open-meteo.com/v1/forecast",p,linkedMapOf("current" to current.joinToString(","),"hourly" to (if(extended) fields.map{it.key}+"weather_code" else listOf("temperature_2m","apparent_temperature","relative_humidity_2m","precipitation_probability","precipitation","weather_code","wind_speed_10m","wind_direction_10m","wind_gusts_10m","pressure_msl","cloud_cover")).joinToString(","),"daily" to (if(extended) daily else listOf("weather_code","temperature_2m_max","temperature_2m_min","sunrise","sunset","precipitation_sum","precipitation_probability_max","wind_gusts_10m_max")).joinToString(","),"forecast_days" to "16","timezone" to "auto","wind_speed_unit" to "kmh") + if(extended) mapOf("minutely_15" to quarter.joinToString(","),"forecast_minutely_15" to "24") else emptyMap())
    fun air(p:Place):String = query("https://air-quality-api.open-meteo.com/v1/air-quality",p,mapOf("hourly" to air.joinToString(","){it.key},"timezone" to "auto","forecast_days" to "4"))
    fun model(p:Place, model:String):String = query("https://api.open-meteo.com/v1/forecast",p,mapOf("models" to model,"hourly" to "temperature_2m,precipitation,wind_speed_10m","forecast_days" to "3","timezone" to "auto"))
    fun historical(p:Place, date:LocalDate):String = query("https://archive-api.open-meteo.com/v1/archive",p,mapOf("start_date" to date.toString(),"end_date" to date.toString(),"daily" to "temperature_2m_max,temperature_2m_min,precipitation_sum,wind_speed_10m_max","timezone" to "auto"))
    fun marine(p:Place):String = query("https://marine-api.open-meteo.com/v1/marine",p,mapOf("hourly" to "wave_height,wave_direction,wave_period,swell_wave_height,swell_wave_direction,swell_wave_period,sea_surface_temperature,ocean_current_velocity,ocean_current_direction,sea_level_height_msl","timezone" to "auto","forecast_days" to "3","cell_selection" to "sea"))
}
fun describeWeather(code:Int?):String = when(code){0->"Céu limpo";1->"Céu pouco nublado";2->"Parcialmente nublado";3->"Encoberto";45,48->"Nevoeiro";51,53,55->"Chuvisco";56,57->"Chuvisco gelado";61->"Chuva fraca";63->"Chuva moderada";65->"Chuva forte";66,67->"Chuva gelada";71,73,75,77->"Neve";80,81,82->"Aguaceiros";85,86->"Aguaceiros de neve";95->"Trovoada";96,99->"Trovoada com granizo";else->"Sem informação"}
fun direction(deg:Double?):String = if(deg==null || !deg.isFinite()) "—" else listOf("N","NNE","NE","ENE","E","ESE","SE","SSE","S","SSO","SO","OSO","O","ONO","NO","NNO")[((((deg%360)+360)%360+11.25)/22.5).toInt()%16]
fun beaufort(kmh:Double?):Int? = kmh?.takeIf{it.isFinite()&&it>=0}?.let{v-> listOf(1,6,12,20,29,39,50,62,75,89,103,118).count{v>=it}}
fun formatValue(value:Double?,unit:String=""):String = if(value==null||!value.isFinite()) "—" else String.format(Locale.forLanguageTag("pt-PT"),if(value%1==0.0) "%.0f" else "%.1f",value)+if(unit.isEmpty()) "" else " $unit"
fun timeIndex(times:List<String>,now:LocalDateTime):Int = times.indexOfLast{runCatching{!LocalDateTime.parse(it).isAfter(now)}.getOrDefault(false)}
fun expired(saved:Long,now:Long,minutes:Long):Boolean = saved<=0||saved>now||now-saved>=minutes*60_000
fun moonPhase(date:LocalDate):Pair<String,Int> { val days=java.time.temporal.ChronoUnit.DAYS.between(LocalDate.of(2000,1,6),date).toDouble(); val phase=((days/29.530588853)%1+1)%1; val names=listOf("Lua nova","Crescente","Quarto crescente","Gibosa crescente","Lua cheia","Gibosa minguante","Quarto minguante","Minguante"); return names[((phase*8+0.5).toInt())%8] to ((1-kotlin.math.cos(phase*2*Math.PI))*50).toInt() }

// Domain logic for IPMA observations and radar. No Android dependencies.
data class RadarTile(val z:Int,val x:Int,val y:Int,val markerX:Double,val markerY:Double)
fun radarTile(lat:Double,lon:Double,zoom:Int):RadarTile {
    require(lat.isFinite()&&lon.isFinite()&&lat in -90.0..90.0&&lon in -180.0..180.0&&zoom in 3..7)
    val n=1 shl zoom;val r=Math.toRadians(lat.coerceIn(-85.05112878,85.05112878))
    val xx=((lon+180)/360*n).coerceIn(0.0,n-1e-9)
    val yy=((1-kotlin.math.ln(kotlin.math.tan(r)+1/kotlin.math.cos(r))/Math.PI)/2*n).coerceIn(0.0,n-1e-9)
    return RadarTile(zoom,xx.toInt(),yy.toInt(),xx%1,yy%1)
}
fun radarUrl(host:String,path:String,t:RadarTile):String? {
    if(host.trimEnd('/')!="https://tilecache.rainviewer.com" || !Regex("/v2/radar/[A-Za-z0-9_-]+").matches(path))return null
    return "${host.trimEnd('/')}$path/512/${t.z}/${t.x}/${t.y}/2/1_1.png"
}
fun distanceKm(a:Double,b:Double,c:Double,d:Double):Double {
    val lat=Math.toRadians(c-a);val lon=Math.toRadians(d-b)
    val h=kotlin.math.sin(lat/2).let{it*it}+kotlin.math.cos(Math.toRadians(a))*kotlin.math.cos(Math.toRadians(c))*kotlin.math.sin(lon/2).let{it*it}
    return 6371.0088*2*kotlin.math.asin(kotlin.math.sqrt(h.coerceIn(0.0,1.0)))
}
fun ipmaValue(v:Double?):Double?=v?.takeIf{it.isFinite()&&it != -99.0}
fun ipmaDirection(v:Int?):String=when(v){1,9->"N";2->"NE";3->"E";4->"SE";5->"S";6->"SO";7->"O";8->"NO";else->"—"}
fun observationAgeMinutes(time:String,now:Instant):Long? = runCatching{val instant=LocalDateTime.parse(time).toInstant(ZoneOffset.UTC);Duration.between(instant,now).toMinutes().takeIf{!instant.isAfter(now)}}.getOrNull()

val municipalities = mapOf("palmela" to "1508", "carnaxide" to "1110", "oriente" to "1106", "centro" to "1106", "restelo" to "1106", "castro_verde" to "0206", "almeida" to "0902", "porto" to "1312", "setubal" to "1512")
fun fireRiskLabel(level:Int?):String? = mapOf(1 to "Reduzido",2 to "Moderado",3 to "Elevado",4 to "Muito elevado",5 to "Máximo")[level]
fun utcInstant(s:String):Instant? = runCatching { Instant.parse(s) }.getOrNull() ?: runCatching { LocalDateTime.parse(s).toInstant(ZoneOffset.UTC) }.getOrNull()
fun alertEligible(level:String,start:String,end:String,now:Instant):Boolean {
    val a=utcInstant(start) ?: return false; val b=utcInstant(end) ?: return false
    return level in setOf("yellow","orange","red") && b.isAfter(a) && b.isAfter(now) && !a.isAfter(now.plusSeconds(86400))
}
fun quakeDistance(p:Place,lat:Double?,lon:Double?):Double? = if(lat!=null&&lon!=null&&lat in -90.0..90.0&&lon in -180.0..180.0)distanceKm(p.latitude,p.longitude,lat,lon) else null
fun floodUrl(p:Place)="https://flood-api.open-meteo.com/v1/flood?latitude=${p.latitude}&longitude=${p.longitude}&daily=river_discharge,river_discharge_p25,river_discharge_p75&forecast_days=7"
fun rainEligible(prob:Double?,mm:Double?,threshold:Int):Boolean = prob!=null&&prob.isFinite()&&prob in 0.0..100.0&&prob>=threshold&&mm!=null&&mm.isFinite()&&mm>0.0
