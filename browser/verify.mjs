import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';
const fixture=JSON.parse(readFileSync('.runtime/workspace-fixture.json','utf8'));
const config=JSON.parse(readFileSync('.runtime/browser-host.json','utf8'));
const base=`http://127.0.0.1:${config.port}/${config.token}`;
const response=await fetch(fixture.browser);assert.equal(response.status,200);
const pages=JSON.parse(readFileSync('.runtime/browser-pages.json','utf8'));
const targets=await (await fetch(`http://127.0.0.1:${config.cdpPort}/json/list`)).json();
const target=targets.find(t=>t.id===pages[fixture.group].target);assert.ok(target);
const ws=new WebSocket(target.webSocketDebuggerUrl);
await new Promise((yes,no)=>{ws.addEventListener('open',yes,{once:true});ws.addEventListener('error',no,{once:true});});
let seq=0;
async function call(method,params){return new Promise((yes,no)=>{const id=++seq;const timer=setTimeout(()=>{ws.removeEventListener('message',read);no(Error('CDP timed out'));},7000);function read(e){const m=JSON.parse(e.data);if(m.id===id){clearTimeout(timer);ws.removeEventListener('message',read);m.error?no(Error(m.error.message)):yes(m.result);}}ws.addEventListener('message',read);ws.send(JSON.stringify({id,method,params}));});}
const after=process.argv.includes('--after-game');
if(!after){
  await call('Runtime.evaluate',{expression:`document.body.innerHTML='<h1>Studio team browser</h1><p>This browser stays running when Minecraft closes.</p><input id="entry" placeholder="Type here"><p id="clock"></p>';document.body.style='background:#102631;color:#78e4c8;font:28px sans-serif;padding:60px';window.officeCount=0;setInterval(()=>document.querySelector('#clock').textContent='Persistent heartbeat '+(++window.officeCount),1000);document.querySelector('#entry').focus();`,returnByValue:true});
  const r=await fetch(base+'/input/'+fixture.group,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({method:'Input.insertText',params:{text:'Input reached the real browser'}})});assert.equal(r.status,200);
  const value=await call('Runtime.evaluate',{expression:"document.querySelector('#entry').value",returnByValue:true});assert.equal(value.result.value,'Input reached the real browser');
  const frame=await fetch(base+'/frame/'+fixture.group);assert.equal(frame.headers.get('content-type'),'image/jpeg');assert.ok((await frame.arrayBuffer()).byteLength>1000);
  assert.equal((await fetch(base+'/input/'+fixture.group,{method:'POST',headers:{Origin:'https://untrusted.example'},body:'{}'})).status,403);
  assert.equal((await fetch(`http://127.0.0.1:${config.port}/wrong/health`)).status,403);
  console.log('PASS persistent browser: live JPEG frames, real input, origin and capability checks');
}else{
  const value=await call('Runtime.evaluate',{expression:"({count:window.officeCount,input:document.querySelector('#entry').value})",returnByValue:true});
  assert.ok(value.result.value.count>5);assert.equal(value.result.value.input,'Input reached the real browser');
  console.log('PASS page JavaScript and input survived Minecraft exit');
}
ws.close();
