// A persistent Chromium process with a small local viewer for Minecraft's MCEF surfaces.
// No npm dependencies. The actual pages remain alive when Minecraft disconnects.
import {spawn} from 'node:child_process';
import {createServer} from 'node:http';
import {readFileSync,writeFileSync,existsSync,mkdirSync} from 'node:fs';
import {randomBytes} from 'node:crypto';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const runtime=path.join(root,'.runtime');mkdirSync(runtime,{recursive:true});
const port=Number(process.env.OFFICE_BROWSER_PORT||9334), cdpPort=Number(process.env.OFFICE_CDP_PORT||9333);
const stateFile=path.join(runtime,'browser-pages.json');
const settingsFile=path.join(runtime,'browser-host.json');
const prior=existsSync(settingsFile)?JSON.parse(readFileSync(settingsFile,'utf8')):{};
const token=prior.token||randomBytes(32).toString('hex');
const chrome=process.env.OFFICE_CHROME||['/opt/google/chrome/chrome','/usr/bin/chromium','/usr/bin/google-chrome'].find(existsSync);
if(!chrome)throw Error('Install Chromium or set OFFICE_CHROME to its executable');
try { const r=await fetch(`http://127.0.0.1:${cdpPort}/json/version`,{signal:AbortSignal.timeout(700)});if(r.ok)throw Error('CDP_PORT_IN_USE'); } catch(e) { if(e.message==='CDP_PORT_IN_USE')throw Error('Office CDP port is already in use; refusing to attach to another browser'); }
const chromeProcess=spawn(chrome,['--headless=new','--remote-debugging-address=127.0.0.1',`--remote-debugging-port=${cdpPort}`,`--user-data-dir=${runtime}/browser-profile`,'--no-first-run','--no-default-browser-check','--window-size=1280,720','about:blank'],{stdio:'ignore'});
const endpoint=`http://127.0.0.1:${cdpPort}`;
let pages=existsSync(stateFile)?JSON.parse(readFileSync(stateFile,'utf8')):{};
const clients=new Map(), pending=new Map();
const save=()=>writeFileSync(stateFile,JSON.stringify(pages,null,2),{mode:0o600});
async function fetchJson(url,options={}){const r=await fetch(url,{...options,signal:AbortSignal.timeout(7000)});if(!r.ok)throw Error(`Chromium HTTP ${r.status}`);return r.json();}
class CDP {
  constructor(url){this.ws=new WebSocket(url);this.seq=0;this.jobs=new Map();this.ready=new Promise((yes,no)=>{this.ws.addEventListener('open',yes,{once:true});this.ws.addEventListener('error',()=>no(Error('Browser connection failed')),{once:true});});
    this.ws.addEventListener('message',e=>{const m=JSON.parse(e.data),job=this.jobs.get(m.id);if(job){this.jobs.delete(m.id);clearTimeout(job.timer);m.error?job.no(Error(m.error.message)):job.yes(m.result);}});
    this.ws.addEventListener('close',()=>{for(const job of this.jobs.values()){clearTimeout(job.timer);job.no(Error('Browser disconnected'));}this.jobs.clear();});}
  async call(method,params={}){await this.ready;return new Promise((yes,no)=>{const id=++this.seq;const timer=setTimeout(()=>{this.jobs.delete(id);no(Error('Browser command timed out'));},8000);this.jobs.set(id,{yes,no,timer});try{this.ws.send(JSON.stringify({id,method,params}));}catch(e){clearTimeout(timer);this.jobs.delete(id);no(e);}});}
}
async function page(id){
  if(!/^group_[a-f0-9]{12}$/.test(id))throw Error('Invalid group');
  if(clients.has(id))return clients.get(id);
  if(pending.has(id))return pending.get(id);
  const promise=(async()=>{
    let list=await fetchJson(endpoint+'/json/list'),target=list.find(t=>t.id===pages[id]?.target);
    if(!target){target=await fetchJson(endpoint+'/json/new?'+encodeURIComponent(pages[id]?.url||'about:blank'),{method:'PUT'});pages[id]={target:target.id,url:pages[id]?.url||'about:blank'};save();}
    const c=new CDP(target.webSocketDebuggerUrl);await c.call('Emulation.setDeviceMetricsOverride',{width:1280,height:720,deviceScaleFactor:1,mobile:false});
    c.ws.addEventListener('close',()=>clients.delete(id));clients.set(id,c);return c;
  })();pending.set(id,promise);try{return await promise;}finally{pending.delete(id);}
}
const viewer=readFileSync(path.join(root,'browser/viewer.html'));
const screenshots=new Map();
async function screenshot(id){if(screenshots.has(id))return screenshots.get(id);const p=(async()=>{const c=await page(id);const r=await c.call('Page.captureScreenshot',{format:'jpeg',quality:78,fromSurface:true});return Buffer.from(r.data,'base64');})();screenshots.set(id,p);try{return await p;}finally{screenshots.delete(id);}}
const server=createServer(async(req,res)=>{
  try{
    const host=`127.0.0.1:${port}`;
    if(req.headers.host!==host || (req.headers.origin && req.headers.origin!==`http://${host}`)){res.writeHead(403);return res.end('Forbidden');}
    const url=new URL(req.url,`http://${host}`),parts=url.pathname.split('/').filter(Boolean);
    if(parts[0]!==token){res.writeHead(403);return res.end('Forbidden');}
    const [_,action,id]=parts;
    res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');res.setHeader('Referrer-Policy','no-referrer');
    if(action==='health'){res.setHeader('Content-Type','application/json');return res.end(JSON.stringify({ok:true}));}
    if(action==='view'){await page(id);res.setHeader('Content-Type','text/html; charset=utf-8');return res.end(viewer);}
    if(action==='frame'){res.setHeader('Content-Type','image/jpeg');return res.end(await screenshot(id));}
    if(action==='info'){const target=(await fetchJson(endpoint+'/json/list')).find(t=>t.id===pages[id]?.target);if(target){pages[id].url=target.url;save();}res.setHeader('Content-Type','application/json');return res.end(JSON.stringify(target?{url:target.url,title:target.title}:{}));}
    if(action!=='input' || req.method!=='POST'){res.writeHead(404);return res.end();}
    let body='';for await(const chunk of req){body+=chunk;if(body.length>64000){res.writeHead(413);return res.end();}}
    const message=JSON.parse(body),c=await page(id);
    const allowed=['Input.dispatchMouseEvent','Input.dispatchKeyEvent','Input.insertText','Page.navigate','Page.reload'];
    if(!allowed.includes(message.method))throw Error('Unsupported input');
    if(message.method==='Page.navigate'){const u=new URL(message.params.url);if(!['http:','https:','about:'].includes(u.protocol))throw Error('Use an HTTP(S) URL');}
    await c.call(message.method,message.params);res.setHeader('Content-Type','application/json');res.end('{"ok":true}');
  }catch(e){res.writeHead(400,{'Content-Type':'application/json'});res.end(JSON.stringify({error:e.message}));}
});
server.on('error',e=>{console.error(e.message);chromeProcess.kill();process.exit(1);});
for(let i=0;i<80;i++){try{await fetchJson(endpoint+'/json/version');break;}catch{if(chromeProcess.exitCode!==null)throw Error('Chromium failed to start');await new Promise(r=>setTimeout(r,100));}}
server.listen(port,'127.0.0.1',()=>{writeFileSync(settingsFile,JSON.stringify({pid:process.pid,port,cdpPort,token}),{mode:0o600});console.log('Persistent office browser ready');});

for(const signal of ['SIGTERM','SIGINT'])process.on(signal,()=>{server.close();for(const c of clients.values())c.ws.close();chromeProcess.kill();setTimeout(()=>process.exit(0),800).unref();});
