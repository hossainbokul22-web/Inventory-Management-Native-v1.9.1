package com.rmstock.nativeapp;
import java.util.*;
public class VoiceParseTest {
  static int fails=0;
  static void eq(String n,Object a,Object b){ if(!String.valueOf(a).equals(String.valueOf(b))){fails++;System.out.println("FAIL "+n+": got "+a+" expected "+b);} else System.out.println("ok   "+n+" = "+a); }
  public static void main(String[] x){
    List<String[]> c=new ArrayList<>();
    c.add(new String[]{"Abdul Karim","করিম"});
    c.add(new String[]{"Md Rahim Uddin","রহিম, rahim sir"});
    c.add(new String[]{"Shafiq Ahmed","শফিক"});
    Map<String,String> lab=new LinkedHashMap<>(); lab.put("stock","Stock Report"); lab.put("prod","Production Report");
    VoiceParse.Result r=VoiceParse.parse("Stock report WhatsApp e Karim sir ke pathao","2026-10-22",c,lab);
    eq("report",r.report,"stock"); eq("via",r.via,"wa"); eq("who",r.contacts,"[0]"); eq("date",r.date,"2026-10-22");
    r=VoiceParse.parse("গতকালের প্রোডাকশন রিপোর্ট রহিম স্যার আর করিমকে মেইল করো","2026-10-22",c,lab);
    eq("bn report",r.report,"prod"); eq("bn via",r.via,"mail"); eq("bn who",r.contacts,"[0, 1]"); eq("bn date",r.date,"2026-10-21");
    r=VoiceParse.parse("মাসিক ডেলিভারি রিপোর্ট শফিক আহমেদকে","2026-10-22",c,lab);
    eq("monthly",r.report+" "+r.monthly,"m_delivery true"); eq("who3",r.contacts,"[2]");
    r=VoiceParse.parse("monthly report","2026-10-22",c,lab);
    eq("monthly no sub",r.report.isEmpty()&&r.monthly,"true");
    r=VoiceParse.parse("daily consumption report to Rahim","2026-10-22",c,lab);
    eq("cons",r.report,"cons"); eq("rahim",r.contacts,"[1]");
    r=VoiceParse.parse("send it to the boss","2026-10-22",c,lab);
    eq("no match",r.contacts.size()+r.report+r.via,"0");
    r=VoiceParse.parse("১২ তারিখ 2026-10-05 স্টক","2026-10-22",c,lab);
    eq("iso date",r.date,"2026-10-05");
    eq("wa bd",VoiceParse.waNumber("01712-345678"),"8801712345678");
    eq("wa intl",VoiceParse.waNumber("+966 50 123 4567"),"966501234567");
    eq("wa 00",VoiceParse.waNumber("0088017"),"88017");
    if(fails>0){System.out.println(fails+" FAILED");System.exit(1);} System.out.println("ALL PASS");
  }
}
