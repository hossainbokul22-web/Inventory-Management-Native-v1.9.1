package com.rmstock.nativeapp;
import java.io.*;import java.nio.file.*;import java.util.*;
public class PmModelCheck {
  static void dump(String name,PmModel.Report R){
    System.out.println("## "+name);
    System.out.println("title="+R.title);System.out.println("sub="+R.sub);System.out.println("company="+R.company);
    StringBuilder c=new StringBuilder();for(PmModel.Col x:R.cols)c.append(x.h).append(":").append(x.w).append(":").append(x.left?1:0).append(":").append(x.fixed).append("|");
    System.out.println("cols="+c);System.out.println("neg="+R.neg);System.out.println("empty="+R.empty);
    for(String[] r:R.pre)System.out.println("pre="+String.join("|",r));
    for(int i=0;i<R.rows.size();i++)System.out.println("row["+R.rowKinds.get(i)+"]="+String.join("|",R.rows.get(i)));
    if(R.total!=null)System.out.println("total="+String.join("|",R.total));
    if(R.mpTitle!=null){System.out.println("mpTitle="+R.mpTitle);System.out.println("mpHead="+String.join("|",R.mpHead));for(String[] r:R.mpRows)System.out.println("mp="+String.join("|",r));}
    for(String[] s:R.signs)System.out.println("sign="+s[0]+":"+s[1]);
  }
  public static void main(String[] a)throws Exception{
    PmModel m=new PmModel();
    for(String line:Files.readAllLines(Paths.get(a[0]))){
      if(line.isEmpty())continue;String[] p=line.split("\t",-1);
      switch(p[0]){
        case "COMPANY":m.company=p[1];break;case "PREPARED":m.preparedBy=p[1];break;
        case "ITEM":{PmModel.Item i=new PmModel.Item();i.code=Integer.parseInt(p[1]);i.name=p[2];i.uom=p[3];i.open=Double.parseDouble(p[4]);m.items.add(i);break;}
        case "PROD":{PmModel.Prod i=new PmModel.Prod();i.code=Integer.parseInt(p[1]);i.name=p[2];m.prods.add(i);break;}
        case "REC":{PmModel.Rec r=new PmModel.Rec();r.p=Integer.parseInt(p[1]);r.i=Integer.parseInt(p[2]);r.q=Double.parseDouble(p[3]);m.recs.add(r);break;}
        case "ENT":{PmModel.Ent e=new PmModel.Ent();e.kind=p[1];e.code=Integer.parseInt(p[2]);e.date=p[3];e.qty=Double.parseDouble(p[4]);e.shift=p[5];m.ent.get(p[1]).add(e);break;}
        case "FG":{PmModel.Fg f=new PmModel.Fg();f.pcs=Double.parseDouble(p[2]);f.g=Double.parseDouble(p[3]);m.fg.put(Integer.parseInt(p[1]),f);break;}
        case "OPEN":m.openOv.put(p[1],Double.parseDouble(p[2]));break;
        case "RUN":m.runtime.put(p[1],p[2]);break;
        case "SHOW":{List<Integer> l=new ArrayList<>();for(String t:p[2].split(","))l.add(Integer.parseInt(t));m.prodShow.put(p[1],l);break;}
        case "MPX":{PmModel.MP mp=new PmModel.MP();mp.h1=p[2];mp.h2=p[3];for(String r:p[4].split(";",-1)){String[] c=r.split("~",-1);mp.rows.add(new String[]{c[0],c[1],c[2]});}m.mpx.put(p[1],mp);break;}
        case "MPOLD":{Map<Integer,String[]> mm=new HashMap<>();for(String r:p[2].split(";",-1)){String[] c=r.split("~",-1);mm.put(Integer.parseInt(c[0]),new String[]{c[1],c[2]});}m.mpOld.put(p[1],mm);break;}
        case "LABEL":m.labels.put(p[1],p[2].replace("\\n","\n"));break;
      }
    }
    String[] pd={"2026-10-01","2026-10-02","2026-10-05","2026-10-06","2026-10-10","2026-10-20","2026-09-22","2026-11-15"};
    for(String d:pd){dump("prod "+d,m.productionReport(d));dump("stock "+d,m.stockReport(d));dump("cons "+d,m.consumptionReport(d));}
    for(String ym:new String[]{"2026-10","2026-02","2026-09"})for(String rep:PmModel.REPKEYS)dump("month "+ym+" "+rep,m.monthReport(ym,rep));
  }
}
