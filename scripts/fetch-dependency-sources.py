#!/usr/bin/env python3
"""Fetch source archives and POM license metadata for the resolved runtime inventory."""
import pathlib, urllib.request, urllib.error, concurrent.futures, re, json
root=pathlib.Path(__file__).resolve().parents[1]
folder=root/'third-party'; source=folder/'sources'; source.mkdir(exist_ok=True)
rows=[dict(zip(('group','artifact','version'), line.split('\t'))) for line in (folder/'DEPENDENCIES.tsv').read_text().splitlines()[1:]]
def fetch(row):
 group,name,version=row['group'],row['artifact'],row['version']
 repo='https://dl.google.com/dl/android/maven2' if group.startswith('androidx.') else 'https://jitpack.io' if group.startswith('com.github.') else 'https://repo.maven.apache.org/maven2'
 prefix=repo+'/'+group.replace('.','/')+'/'+name+'/'+version+'/'+name+'-'+version
 result={**row,'pom':prefix+'.pom','source_url':prefix+'-sources.jar'}
 try:
  raw=urllib.request.urlopen(prefix+'.pom',timeout=45).read().decode()
  result['licenses']=[dict(re.findall(r'<(name|url)>(.*?)</\1>', section, re.S)) for section in re.findall(r'<license>(.*?)</license>',raw,re.S)]
 except Exception as e:result['license_error']=str(e)
 target=source/(name+'-'+version+'-sources.jar')
 try:
  if not target.exists():urllib.request.urlretrieve(prefix+'-sources.jar',target)
  result['source_file']=str(target.relative_to(folder))
 except Exception as e:result['source_error']=str(e)
 return result
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
 records=list(pool.map(fetch,rows))
(folder/'DEPENDENCIES.json').write_text(json.dumps(records,indent=2)+'\n')
print('Source archives:',sum('source_file'in r for r in records),'/',len(records))
for r in records:
 if 'source_error'in r:print(r['group']+':'+r['artifact'],r['source_error'])
