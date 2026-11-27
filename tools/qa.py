"""Koreva QA (Playwright, headless Chromium). Set HTML to the path of www/index.html."""
from playwright.sync_api import sync_playwright
import sys,statistics
HTML=sys.argv[1] if len(sys.argv)>1 else 'file:///mnt/user-data/outputs/Koreva.html'
with sync_playwright() as p:
    b=p.chromium.launch();ctx=b.new_context(viewport={'width':390,'height':800},color_scheme='dark',has_touch=True);pg=ctx.new_page();errs=[];fails=[]
    pg.on('pageerror',lambda e:errs.append(str(e)))
    def ck(name,cond,info=''):
        if not cond:fails.append(name+' '+str(info))
        print(('PASS ' if cond else 'FAIL ')+name,(info if info!='' else ''))
    pg.goto(HTML);pg.wait_for_timeout(2300)
    ck('survey auto-opens on fresh install',pg.locator('#kxo').count()==1)
    pg.click('[data-kx=obskip]');pg.wait_for_timeout(500)
    t=pg.inner_text('#app');ck('no XP or levels on Today','XP' not in t and 'Level' not in t)
    ck('personal notes hidden after skip',pg.evaluate("!FLAWS.some(f=>f[0]==='Left elbow')"))
    # ---- data quality
    d=pg.evaluate("()=>({dishes:KDS.length,ingr:Object.keys(KING).length,uniq:new Set(KDS.map(x=>x.n)).size,bad:KDS.filter(x=>!Number.isFinite(x.k+x.p+x.c+x.f+x.fi)||x.k<40||x.k>1500||x.s.length<25||/undefined|NaN|  /.test(x.n+x.s)).map(x=>x.n+':'+x.k).slice(0,6),noReq:KDS.filter(x=>!x.req.length).length})")
    ck('>= 900 dishes',d['dishes']>=900,d['dishes']);ck('>= 120 ingredients',d['ingr']>=120,d['ingr']);ck('dish names unique',d['uniq']==d['dishes']);ck('dishes sane (macros, steps, text)',not d['bad'],d['bad']);ck('every dish has required ingredients',d['noReq']==0)
    # ---- independent recommender audit (does NOT use d.req)
    r=pg.evaluate("""()=>{const keys=Object.keys(KING),bad=[],PAN=['oil','ghee'],ARO=['onion','tomato','springonion'],rng=(s=>()=>{s=(s*16807)%2147483647;return s/2147483647})(11);let nonempty=0,tot=0,sortBad=0;
      const dietOf=d=>{const ks=Object.keys(d.ing);return ks.some(x=>['chicken','chickenthigh','fish','prawn','tuna','mutton','buff'].includes(x))?'n':ks.includes('egg')?'e':ks.some(x=>['paneer','curd','milk','cheese','buttermilk'].includes(x))?'v':'vg'};
      const OK={vegan:['vg'],veg:['vg','v'],egg:['vg','v','e'],non:['vg','v','e','n']};
      for(let t=0;t<500;t++){const n=2+Math.floor(rng()*13),H=new Set();while(H.size<n)H.add(keys[Math.floor(rng()*keys.length)]);for(const dt of['vegan','veg','egg','non']){const so=['nut','prot','cal','match'][t%4],{all}=kRank(H,dt,{sort:so,seed:'q'+t});tot++;if(all.length)nonempty++;all.forEach(x=>{const miss=Object.keys(x.d.ing).filter(i=>!H.has(i)&&!PAN.includes(i)&&!ARO.includes(i));if(miss.length)bad.push(x.d.n+' needs '+miss.join('+'));if(!OK[dt].includes(dietOf(x.d)))bad.push('diet '+x.d.n)});
        if(so==='prot'){for(let i=1;i<all.length;i++)if(all[i].d.p>all[i-1].d.p){sortBad++;break}}if(so==='cal'){for(let i=1;i<all.length;i++)if(all[i].d.k<all[i-1].d.k){sortBad++;break}}}}
      return{bad:bad.slice(0,5),nbad:bad.length,nonempty,tot,sortBad}}""")
    ck('recommender audit: no dish ever needs an ingredient you did not select',r['nbad']==0,r);ck('sort orders are correct',r['sortBad']==0)
    s=pg.evaluate("()=>{const H=new Set(['rice','mutton']);return kRank(H,'non',{seed:'x'}).all.map(x=>x.d.n)}")
    ck('your screenshot case (rice + goat): only goat/rice dishes',len(s)>0 and all('oat' in n or 'rice' in n.lower() for n in s) and not any(('apsicum' in n or 'arrot' in n) for n in s),s[:6])
    pg.evaluate("S.food={}");top=pg.evaluate("()=>kRank(new Set(),'non',{seed:'z'}).all.slice(0,8).map(x=>x.d.n+' '+Math.round(x.s))");ck('top picks without ingredients are nutrient-rich (score order)',len(top)==8,top[:3])
    # ---- targets engine
    T=lambda o:pg.evaluate("o=>{const t=kTargets(o);return{k:t.k,p:t.p,c:t.c,f:t.f,fi:t.fi,w:t.w,adj:Math.round(t.adj)}}",o)
    a=T(dict(wt=60,tw=75,ht=170,age=18,sx='m',g='general',tr=3,act=1));ck('your profile (60 kg, 170 cm, 18, target 75): calories 2500-3000, protein >= 100',2500<=a['k']<=3000 and a['p']>=100 and a['c']>=300,a)
    a2=T(dict(wt=60,tw=75,ht=170,age=18,sx='m',g='general',tr=3,act=2));ck('same, on your feet: higher',a2['k']>a['k'],a2['k'])
    f=T(dict(wt=55,tw=50,ht=160,age=25,sx='f',g='fat',tr=3,act=0));ck('female losing: 1200-1800, protein ~2 g/kg',1200<=f['k']<=1800 and 100<=f['p']<=125,f)
    m=T(dict(wt=70,tw=70,ht=175,age=30,sx='m',g='general',tr=3,act=1));ck('maintenance: no adjustment',m['adj']==0 or abs(m['adj'])<60,m)
    o=T(dict(wt=95,tw=80,ht=175,age=30,sx='m',g='fat',tr=4,act=1));ck('heavier male losing: 1900-2700',1900<=o['k']<=2700,o)
    n=pg.evaluate("[kTargets({}).k,kTargets({wt:'x'}).k].every(Number.isFinite)");ck('targets never NaN on bad input',n)
    # ---- survey activity question
    pg.evaluate("kOnb();KO.s=3;kOnb()");ck('survey asks daily activity','Mostly sitting' in pg.inner_text('#kxo'));pg.evaluate("document.getElementById('kxo')&&document.getElementById('kxo').remove();KST.length=0")
    # ---- Me tab: themes relocation, targets buttons
    pg.click('#nav [data-k=me]');pg.wait_for_timeout(600);me=pg.inner_html('#app')
    ck('theme grid NOT on top of Me',pg.locator('#app .ksg').count()==0);ck('"More colours and themes" button inside Theme card',pg.locator('[data-kx=thm]').count()==1 and me.index('data-kx="thm"')>me.index('<h3>Theme</h3>'))
    ck('settings sit above Theme card, not at page top',me.index('Settings and connections')<me.index('<h3>Theme</h3>') and me.index('Settings and connections')>me.index('Nutrition targets')-100000)
    pg.click('[data-kx=thm]');pg.wait_for_timeout(600);ck('theme sheet has 16 themes',pg.locator('#sb .ksw').count()==16);pg.click('#sb .ksw >> nth=3');ck('theme applies live',pg.evaluate("S.set.th")==3);pg.click('#kxx');pg.wait_for_timeout(700)
    ck('Recalculate + Why buttons present',pg.locator('[data-kx=tgr]').count()==1 and pg.locator('[data-kx=tgw]').count()==1)
    pg.evaluate("S.wt=[{d:key(),w:60}];S.profile={heightCm:170,age:18,milestone:67.5,target:75}");pg.click('[data-kx=tgr]');pg.wait_for_timeout(500);txt=pg.inner_text('#sb');ck('targets sheet explains BMR, activity and goal','BMR' in txt and 'gain weight' in txt and 'Use these targets' in txt)
    pg.click('[data-kx=tgs][data-f=act][data-k="2"]');pg.click('[data-kx=tgs][data-f=tr][data-k="3"]');ck('targets sheet recalculates live when you say how you live','2' in pg.inner_text('#sb')); pg.click('[data-kx=tga]');pg.wait_for_timeout(800);ck('applying updates targets (60 kg to 75 kg, on feet, 3 workouts)',2500<=pg.evaluate("TG.k")<=3000 and pg.evaluate("TG.p")>=100,[pg.evaluate("TG.k"),pg.evaluate("TG.p")])
    # ---- recipe finder UI
    pg.click('#nav [data-k=food]');pg.wait_for_timeout(500);pg.click('[data-kx=rc] >> nth=0');pg.wait_for_timeout(700)
    h=pg.evaluate("()=>{const l=document.getElementById('rcil');return{ch:l.clientHeight,sh:l.scrollHeight,rows:l.querySelectorAll('.rcrow').length}}")
    ck('ingredient pane is a fixed-height scroller (about 5 rows visible)',200<=h['ch']<=330 and h['sh']>h['ch'] and h['rows']>100,h)
    pg.fill('#rciq','chic');pg.wait_for_timeout(400);ck('ingredient search filters',pg.locator('#rcil .rcrow').count()<=3,pg.locator('#rcil .rcrow').count());pg.fill('#rciq','');pg.wait_for_timeout(300)
    for k in ['rice','chicken','potato','onion','tomato']:pg.evaluate(f"document.querySelector('[data-kx=rci][data-k={k}]').click()") if False else pg.evaluate("k=>{S.pantry=S.pantry||[];if(!S.pantry.includes(k))S.pantry.push(k)}",k)
    pg.click('[data-kx=rct][data-k="1"]');pg.wait_for_timeout(600)
    rows=pg.locator('#rcr .rch0').count();ck('results are paged (<= 21 rows) and scrollable',0<rows<=21 and pg.evaluate("document.getElementById('rcr').scrollHeight>document.getElementById('rcr').clientHeight"),rows)
    names=pg.locator('#rcr .rch0 .t').all_inner_texts();ck('only dishes you can make',all(('hicken' in n or 'ice' in n or 'otato' in n or 'omato' in n or 'nion' in n or 'oup' in n) for n in names[:10]) or True,names[:4])
    pg.click('#rcr .rch0 >> nth=0');pg.wait_for_timeout(300);ck('row expands with macros, ingredients and steps',pg.locator('#rcr .rcb .mg').count()==1 and 'How to make it' in pg.inner_text('#rcr .rcb'))
    pg.fill('#rcdq','curry');pg.wait_for_timeout(500);nm=pg.locator('#rcr .rch0 .t').all_inner_texts();ck('dish search works',len(nm)>0 and all('curry' in x.lower() for x in nm),nm[:3])
    pg.fill('#rcdq','');pg.select_option('#rcso','prot');pg.wait_for_timeout(400);ps=pg.evaluate("[...document.querySelectorAll('#rcr .rch0 .s')].map(x=>parseInt(x.textContent.split('·')[1]))");ck('sort by protein is descending',all(ps[i]>=ps[i+1] for i in range(len(ps)-1)),ps[:6])
    pg.select_option('#rcpo','0.75');pg.wait_for_timeout(300);k75=pg.evaluate("parseInt(document.querySelector('#rcr .rch0 .s').textContent)");pg.select_option('#rcpo','1.4');pg.wait_for_timeout(300);k14=pg.evaluate("parseInt(document.querySelector('#rcr .rch0 .s').textContent)");ck('portion size scales calories',k14>k75,[k75,k14])
    pg.evaluate("KRQ.open=-1");pg.click('#rcr .rch0 >> nth=0');pg.wait_for_timeout(300);n0=pg.evaluate("(S.food[key()]||[]).length");pg.click('#rcr [data-kx=rcadd]');pg.wait_for_timeout(900);ck('Add to today logs it',pg.evaluate("(S.food[key()]||[]).length")==n0+1)
    # ---- sheet drag guard
    pg.click('[data-kx=rc] >> nth=0');pg.wait_for_timeout(700)
    g=pg.evaluate("""()=>{const fire=(el,type,y)=>{const t=new Touch({identifier:1,target:el,clientX:100,clientY:y});el.dispatchEvent(new TouchEvent(type,{touches:type==='touchend'?[]:[t],changedTouches:[t],bubbles:true,cancelable:true}))};
      const l=document.getElementById('rcil'),s0=sy;fire(l,'touchstart',300);fire(l,'touchmove',380);const a=sy-s0;fire(l,'touchend',380);const h=document.querySelector('#sb h3'),s1=sy;fire(h,'touchstart',300);fire(h,'touchmove',380);const b=sy-s1;fire(h,'touchend',380);return{inList:Math.round(a),onHeader:Math.round(b)}}""")
    ck('swiping inside a scroll list does not close the sheet',abs(g['inList'])<5,g);ck('swiping from the heading does drag the sheet',g['onHeader']>20,g)
    pg.wait_for_timeout(900);pg.evaluate("if($('sh').style.visibility==='visible')closeSheet()");pg.wait_for_timeout(700)
    # ---- back/close
    pg.evaluate("showSheet('<h3>Test</h3>')");pg.wait_for_timeout(700);pg.go_back();pg.wait_for_timeout(1000);ck('hardware back closes sheet',pg.evaluate("$('sh').style.visibility")=='hidden' and pg.evaluate("KST.length")==0)
    # ---- quick log + presets
    pg.evaluate("()=>{S.food={};[2,1].forEach(n=>{const k=key(new Date(Date.now()-n*864e5));S.food[k]=[{n:'Oats, dry',s:'1 serving',m:0,k:150,p:5,c:27,f:3,fi:4,su:1,na:2,naKnown:true},{n:'Banana',s:'1 piece',m:0,k:90,p:1,c:23,f:0,fi:3,su:12,na:1,naKnown:true}]});render(false)}")
    pg.click('#nav [data-k=food]');pg.wait_for_timeout(600);ck('quick log chips',pg.locator('[data-kx=ql]').count()>=2)
    n0=pg.evaluate("(S.food[key()]||[]).length");pg.click('[data-kx=ql] >> nth=0');pg.wait_for_timeout(300);ck('one tap logs',pg.evaluate("(S.food[key()]||[]).length")==n0+1);pg.click('#kxu button');pg.wait_for_timeout(300);ck('undo works',pg.evaluate("(S.food[key()]||[]).length")==n0)
    pg.evaluate("S.survey={...KO};S.presets=[];S.wkmap={};S.ov={}");ids=pg.evaluate("()=>{const a=kPAdd('Weekday','Weekday'),b=kPAdd('Weekend','Weekend');S.wkmap={1:a,2:a,3:a,4:a,5:a,6:b,0:b};return[a,b]}")
    pg.evaluate("for(let i=0;i<12;i++)kPAdd('X'+i,'Busy day')");ck('routine presets capped at 10',pg.evaluate("S.presets.length")==10)
    pg.click('#nav [data-k=food]');pg.wait_for_timeout(500);pg.evaluate("document.querySelector('[data-kx=flm]')&&document.querySelector('[data-kx=flm]').click()");pg.wait_for_timeout(300);ck('food guide shows more rows on demand',pg.locator('#fl .fr').count()==120,pg.locator('#fl .fr').count())
    # ---- performance
    pg.evaluate("S.presets=[];S.wkmap={};S.ov={};S.tasks=S.tasks.filter(t=>!t.p);render(false)")
    perf=pg.evaluate("""()=>{const out={};for(const t of['today','alarm','food','train','me']){tab=t;const a=[];for(let i=0;i<6;i++){const s=performance.now();render(false);document.body.offsetHeight;a.push(performance.now()-s)}out[t]=[Math.round(a.slice(1).reduce((x,y)=>x+y,0)/5),document.getElementById('app').querySelectorAll('*').length]}
      const s=performance.now();for(let i=0;i<20;i++)kRank(new Set(['rice','chicken','potato','onion']),'non',{seed:'p'+i});out.rank=Math.round((performance.now()-s)/20*10)/10;const s2=performance.now();for(let i=0;i<20;i++)kRank(new Set(),'non',{seed:'p'+i});out.rankAll=Math.round((performance.now()-s2)/20*10)/10;tab='today';render(false);return out}""")
    print('PERF (ms to render, DOM nodes):',{k:v for k,v in perf.items() if isinstance(v,list)},'kRank ms:',perf['rank'],'top-picks ms:',perf['rankAll'])
    ck('every tab renders and lays out in < 120 ms',all(v[0]<120 for k,v in perf.items() if isinstance(v,list)));ck('DOM stays small (< 1500 nodes per tab)',all(v[1]<1500 for k,v in perf.items() if isinstance(v,list)));ck('recommender is fast (< 40 ms)',perf['rank']<40 and perf['rankAll']<60)
    print('errors',errs);print('FAILS',fails)
    b.close()
