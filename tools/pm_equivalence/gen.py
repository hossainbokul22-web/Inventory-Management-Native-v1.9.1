import json,random,datetime
random.seed(11)
items=[{"code":100+i,"name":f"Item {i} ×{random.choice(['A','B','C'])}","uom":random.choice(['kg','pcs','roll']),"open":round(random.uniform(0,50),2)} for i in range(1,9)]
prods=[{"code":300+i,"name":f"Product {i}"} for i in range(1,7)]
recs=[]
for p in prods:
    for it in random.sample(items,4):
        recs.append({"p":p["code"],"i":it["code"],"q":round(random.choice([0.012,0.5,1.25,0.03,2,0.333]),3)})
ent={k:[] for k in ['receive','ret','output','delivery','hold','reject']}
nid=1
start=datetime.date(2026,9,20)
for d in range(0,32):
    day=(start+datetime.timedelta(days=d)).isoformat()
    for it in items:
        if random.random()<0.35: ent['receive'].append({"id":nid,"code":it['code'],"date":day,"qty":round(random.uniform(1,80),2),"shift":"","voucher":"V"+str(nid),"note":""});nid+=1
        if random.random()<0.1: ent['ret'].append({"id":nid,"code":it['code'],"date":day,"qty":round(random.uniform(0.5,5),2),"shift":"","voucher":"","note":""});nid+=1
    for p in prods:
        for k,pr in [('output',0.6),('delivery',0.4),('hold',0.1),('reject',0.15)]:
            if random.random()<pr:
                sh=random.choice(['A','B','C','A','B','']) if random.random()<0.9 else random.choice(['General',''])
                if k=='delivery': sh=''
                ent[k].append({"id":nid,"code":p['code'],"date":day,"qty":round(random.choice([1,2,3,5,10,12.5,40,100]),2),"shift":sh,"voucher":"","note":""});nid+=1
fg={}
for p in prods[:-1]:
    fg[str(p['code'])]={"pcs":random.choice([24,36,48,12]),"g":random.choice([50,75,100,120.5])}
openOv={"2026-10-01|301":120,"2026-10-10|302":-3.5,"2026-09-25|303":40,"2026-10-05|306":0}
runtime={"2026-10-05|301|A":"8h","2026-10-05|301|B":"7.5 h","2026-10-12|302|C":"6h"}
prodShow={"2026-10-02":[304,305],"2026-10-20":[301]}
mpx={"2026-10-05":{"h":["Cookies","Wafer"],"rows":[{"n":"AM","c1":"2","c2":"1.5"},{"n":"Casual","c1":"x","c2":"7"},{"n":"Extra row","c1":"","c2":"3"}]}}
manpower={"2026-10-06":{"0":{"c1":"3","c2":"4"},"2":{"c1":"1","c2":""}}}
labels={"r_prod":"Production Sheet","ph_run":"Run Time (h)","tab_entry":"Enter","mp_rows":"AM\nOfficer\n\nWorker","mp_c1":"Cake"}
data={"items":items,"products":prods,"recipes":recs,**ent,"fg":fg,"openOv":openOv,"runtime":runtime,"prodShow":prodShow,"mpx":mpx,"manpower":manpower,"labels":labels,"settings":{"company":"Test Co. Ltd","preparedBy":"Mr Tester"}}
json.dump(data,open('data.json','w'),ensure_ascii=False)
L=[]
L.append("COMPANY\tTest Co. Ltd");L.append("PREPARED\tMr Tester")
for i in items:L.append(f"ITEM\t{i['code']}\t{i['name']}\t{i['uom']}\t{i['open']}")
for p in prods:L.append(f"PROD\t{p['code']}\t{p['name']}")
for r in recs:L.append(f"REC\t{r['p']}\t{r['i']}\t{r['q']}")
for k,v in ent.items():
    for e in v:L.append(f"ENT\t{k}\t{e['code']}\t{e['date']}\t{e['qty']}\t{e['shift']}")
for k,v in fg.items():L.append(f"FG\t{k}\t{v['pcs']}\t{v['g']}")
for k,v in openOv.items():L.append(f"OPEN\t{k}\t{v}")
for k,v in runtime.items():L.append(f"RUN\t{k}\t{v}")
for k,v in prodShow.items():L.append(f"SHOW\t{k}\t"+",".join(map(str,v)))
for k,v in mpx.items():L.append(f"MPX\t{k}\t{v['h'][0]}\t{v['h'][1]}\t"+";".join(f"{r['n']}~{r['c1']}~{r['c2']}" for r in v['rows']))
for k,v in manpower.items():L.append(f"MPOLD\t{k}\t"+";".join(f"{i}~{c['c1']}~{c['c2']}" for i,c in v.items()))
for k,v in labels.items():L.append(f"LABEL\t{k}\t"+v.replace("\n","\\n"))
open('data.txt','w',encoding='utf-8').write("\n".join(L)+"\n")
print(len(L),'lines')
