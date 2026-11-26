"""Validação inicial de respostas reais. Python 3, sem dependências externas."""
import argparse, json, math, urllib.parse, urllib.request
from pathlib import Path
from datetime import datetime, timezone
from concurrent.futures import ThreadPoolExecutor

LOCATIONS = json.loads(Path(__file__).with_name('locations.json').read_text(encoding='utf-8'))
CURRENT = 'temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m,precipitation,cloud_cover,pressure_msl'
HOURLY = 'temperature_2m,precipitation_probability,precipitation,wind_speed_10m,wind_gusts_10m,visibility,dew_point_2m,cloud_cover,cape,uv_index'
DAILY = 'weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max,sunrise,sunset,uv_index_max,wind_gusts_10m_max'
AIR = 'european_aqi,pm2_5,pm10,ozone,nitrogen_dioxide,sulphur_dioxide,carbon_monoxide,dust,olive_pollen,grass_pollen'
def check_locations():
    assert len(LOCATIONS) == 9 and len({x['id'] for x in LOCATIONS}) == 9
    assert all(36 < x['latitude'] < 43 and -10 < x['longitude'] < -6 for x in LOCATIONS)
    assert len({(x['latitude'],x['longitude']) for x in LOCATIONS}) == 9

def url(base, **params): return base + '?' + urllib.parse.urlencode(params)
def get(address):
    with urllib.request.urlopen(urllib.request.Request(address,headers={'User-Agent':'BirdsMeteo-validation/0.3'}),timeout=20) as response:
        return json.load(response)
def numeric(x): return isinstance(x,(int,float)) and not isinstance(x,bool) and math.isfinite(x)
def series(block, keys):
    times=block.get('time',[])
    if not isinstance(times,list) or not times: raise ValueError('Série temporal vazia')
    result={}
    for key in keys.split(','):
        vals=block.get(key)
        result[key]={'total':len(vals),'disponiveis':sum(v is not None for v in vals)} if isinstance(vals,list) and len(vals)==len(times) else {'erro':'Campo ausente ou tamanho incorreto'}
    return result

def validate_location(loc):
    coord=dict(latitude=loc['latitude'],longitude=loc['longitude'],timezone='Europe/Lisbon')
    result={'id':loc['id']}
    for name,address in [('forecast',url('https://api.open-meteo.com/v1/forecast',**coord,current=CURRENT,hourly=HOURLY,daily=DAILY,forecast_days=16)),('air_quality',url('https://air-quality-api.open-meteo.com/v1/air-quality',**coord,hourly=AIR,forecast_days=5))]:
        try:
            data=get(address)
            if name=='forecast':
                result[name]={'current':{k:numeric(data.get('current',{}).get(k)) for k in CURRENT.split(',')},'hourly':series(data.get('hourly',{}),HOURLY),'daily':series(data.get('daily',{}),DAILY)}
            else: result[name]={'hourly':series(data.get('hourly',{}),AIR)}
        except Exception as e: result[name]={'erro':str(e)}
    return result

def shared():
    result={}
    for name,address in [('ipma_warnings','https://api.ipma.pt/open-data/forecast/warnings/warnings_www.json'),('ipma_stations','https://api.ipma.pt/open-data/observation/meteorology/stations/stations.json'),('rainviewer','https://api.rainviewer.com/public/weather-maps.json')]:
        try:
            data=get(address)
            if name=='rainviewer': result[name]={'past':len(data['radar']['past']),'nowcast':len(data['radar'].get('nowcast',[])),'satellite':len(data.get('satellite',{}).get('infrared',[]))}
            else:
                if not isinstance(data,list): raise ValueError('Esperava lista JSON')
                result[name]={'registos':len(data)}
        except Exception as e: result[name]={'erro':str(e)}
    return result

def main():
    parser=argparse.ArgumentParser(); parser.add_argument('--offline',action='store_true'); parser.add_argument('--output',default='validation_report.json'); args=parser.parse_args()
    check_locations()
    assert series({'time':['a','b'],'test':[0,None]},'test')['test']['disponiveis']==1
    assert not numeric(None) and numeric(0) and not numeric(True)
    report={'utc':datetime.now(timezone.utc).isoformat(),'local_config':'PASS','offline':args.offline}
    if not args.offline:
        with ThreadPoolExecutor(max_workers=3) as pool: report['locations']=list(pool.map(validate_location,LOCATIONS))
        report['shared']=shared()
    Path(args.output).write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=='__main__': main()
