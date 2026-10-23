const {chromium}=require('playwright');const fs=require('fs');
(async()=>{const b=await chromium.launch();const p=await b.newPage();const err=[];p.on('pageerror',e=>err.push(e.message));
await p.goto('file://'+process.argv[2]);
const data=JSON.parse(fs.readFileSync(process.argv[3],'utf8'));
const out=await p.evaluate((data)=>{
  S=Object.assign(fresh(),data);S.nextId=99999;S=migrate(S);
  const lines=[];
  const dump=(name,R)=>{lines.push('## '+name);lines.push('title='+R.title);lines.push('sub='+R.sub);lines.push('company='+R.company);
    lines.push('cols='+R.cols.map(c=>c.h+':'+c.w+':'+(c.left?1:0)+':'+(c.fixed||0)).join('|')+'|');
    lines.push('neg='+(R.neg==null?-1:R.neg));lines.push('empty='+(R.empty||''));
    (R.pre||[]).forEach(r=>lines.push('pre='+r.join('|')));
    R.rows.forEach(r=>{if(Array.isArray(r))lines.push('row[row]='+r.join('|'));else lines.push('row['+r.k+']='+r.r.join('|'))});
    if(R.total)lines.push('total='+R.total.join('|'));
    if(R.manpower){lines.push('mpTitle='+R.manpower.title);lines.push('mpHead='+R.manpower.head.join('|'));R.manpower.rows.forEach(r=>lines.push('mp='+r.join('|')))}
    R.signs.forEach(s=>lines.push('sign='+s[0]+':'+(s[1]||'')));};
  const pd=["2026-10-01","2026-10-02","2026-10-05","2026-10-06","2026-10-10","2026-10-20","2026-09-22","2026-11-15"];
  for(const d of pd){
    U.tab='production';U.prodDate=d;dump('prod '+d,buildReport());
    U.tab='daily';U.daily='stock';U.stockDate=d;dump('stock '+d,buildReport());
    U.tab='daily';U.daily='cons';U.stockDate=d;dump('cons '+d,buildReport());
  }
  for(const ym of ["2026-10","2026-02","2026-09"])for(const rep of REPKEYS){U.tab='monthly';U.month=ym;U.rep=rep;dump('month '+ym+' '+rep,buildReport());}
  return lines.join('\n');
},data);
fs.writeFileSync(process.argv[4],out+'\n');console.log('web errors',err);await b.close()})()
