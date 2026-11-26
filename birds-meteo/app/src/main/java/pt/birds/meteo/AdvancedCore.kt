package pt.birds.meteo

import java.time.*
import java.net.URLEncoder
import kotlin.math.*

fun searchUrl(query:String,portugalOnly:Boolean):String {
    val q=query.trim();require(q.length in 2..100)
    return "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(q,"UTF-8")}&count=20&language=pt&format=json"+if(portugalOnly)"&countryCode=PT" else ""
}
fun astronomicalUrl(p:Place,date:LocalDate,zone:ZoneId):String {
    val offset=date.atTime(12,0).atZone(zone).offset.totalSeconds/3600.0
    return "https://aa.usno.navy.mil/api/rstt/oneday?date=$date&coords=${p.latitude}%2C${p.longitude}&tz=$offset"
}
fun kpValue(value:Double?):Double?=value?.takeIf{it.isFinite()&&it in 0.0..9.0}
fun kpScale(value:Double?):String?=kpValue(value)?.let{when{it>=9->"G5";it>=8->"G4";it>=7->"G3";it>=6->"G2";it>=5->"G1";else->"Abaixo de G1"}}
fun weatherEffect(code:Int?,isDay:Boolean?):String=when(code){null->"none";95,96,99->"storm";71,73,75,77,85,86->"snow";51,53,55,56,57,61,63,65,66,67,80,81,82->"rain";45,48->"fog";1,2,3->"cloud";0->if(isDay==true)"sun" else if(isDay==false)"stars" else "none";else->"none"}
// NOAA fractional-year approximation. UTC solar noon and hour angle, then local conversion.
// No terrain/horizon model; null when a crossing cannot be calculated on that day.
fun twilight(p:Place,date:LocalDate,zone:ZoneId,depression:Double):Pair<ZonedDateTime,ZonedDateTime>? {
    require(depression in 0.0..18.0)
    val gamma=2*PI/(if(date.isLeapYear)366 else 365)*(date.dayOfYear-1)
    val eq=229.18*(0.000075+0.001868*cos(gamma)-0.032077*sin(gamma)-0.014615*cos(2*gamma)-0.040849*sin(2*gamma))
    val dec=0.006918-0.399912*cos(gamma)+0.070257*sin(gamma)-0.006758*cos(2*gamma)+0.000907*sin(2*gamma)-0.002697*cos(3*gamma)+0.00148*sin(3*gamma)
    val lat=Math.toRadians(p.latitude)
    val c=(cos(Math.toRadians(90+depression))-sin(lat)*sin(dec))/(cos(lat)*cos(dec))
    if(!c.isFinite()||c !in -1.0..1.0)return null
    val noon=720-4*p.longitude-eq;val delta=4*Math.toDegrees(acos(c))
    val utc=date.atStartOfDay(ZoneOffset.UTC)
    // Resolve date relative to requested local date, including time zones east/west of the date line.
    val correction=java.time.temporal.ChronoUnit.DAYS.between(utc.plusSeconds((noon*60).roundToLong()).withZoneSameInstant(zone).toLocalDate(),date)
    fun at(minutes:Double)=utc.plusDays(correction).plusSeconds((minutes*60).roundToLong()).withZoneSameInstant(zone)
    return at(noon-delta) to at(noon+delta)
}
// Calendar-month normals from a complete fixed 1991–2020 ERA5 daily series.
fun climateUrl(p:Place):String="https://archive-api.open-meteo.com/v1/archive?latitude=${p.latitude}&longitude=${p.longitude}&start_date=1991-01-01&end_date=2020-12-31&daily=temperature_2m_mean,precipitation_sum&models=era5&timezone=auto"
data class ClimateDay(val date:LocalDate,val temp:Double?,val rain:Double?)
data class ClimateNormal(val month:Int,val years:Int,val temp:Double,val rain:Double)
fun climateNormals(rows:List<ClimateDay>):List<ClimateNormal>{
    val groups=rows.filter{it.date.year in 1991..2020}.groupBy{YearMonth.from(it.date)}
    val valid=groups.mapNotNull{(m,ds)->
        if(ds.size!=m.lengthOfMonth()||ds.map{it.date}.toSet().size!=m.lengthOfMonth()||ds.any{it.temp?.isFinite()!=true||it.rain?.isFinite()!=true||(it.rain ?: -1.0)<0})null
        else Triple(m,ds.map{it.temp!!}.average(),ds.sumOf{it.rain!!})
    }
    return (1..12).mapNotNull{month->val v=valid.filter{it.first.monthValue==month};if(v.size!=30)null else ClimateNormal(month,v.size,v.map{it.second}.average(),v.map{it.third}.average())}
}
