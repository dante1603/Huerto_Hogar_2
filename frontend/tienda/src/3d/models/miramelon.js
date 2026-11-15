// Geometría procedural original del prototipo Huerto Estelar.
// Estado: PROVISIONAL. No corresponde aún a un SKU comercial.
import { Sculpt, plus, mix } from '../core/sculpt.js'

export default function createMiramelon() {
  const s = new Sculpt()
  s.ellipsoid([-.32,0,-.19],[.96,1.05,.83],(i,j,rings,parts)=>{let band=(j+Math.floor(.6*i))%parts;return Math.floor(band/2)%2===0?["#599f69","#7bb168","#528c6c"][j%3]:["#efb55c","#d6a35c","#e8ba67"][j%3]}, {rings:12,parts:16,twist:.18,shape:(t,a)=>1+.03*Math.cos(a*8)});
  const center=[.48,-.61,.78],rad=.80;
  let frontCenter=center,backCenter=plus(center,[0,0,-.16]),lastF=null,lastB=null;
  for(let j=0;j<=14;j++){
   let a=-.28+j*Math.PI/14*1.18,p=[center[0]+rad*Math.cos(a),center[1]+rad*Math.sin(a),center[2]],q=[p[0],p[1],p[2]-.16];
   if(lastF){s.tri(frontCenter,lastF,p,j%2?"#ffc56a":"#f5ac4e");s.tri(backCenter,q,lastB,"#5d9e62");s.quad(lastF,lastB,q,p,j%2?"#5c966c":"#7fa86d")}
   if(j>0&&j<14){let q2=mix(center,p,.55);s.gem([q2[0],q2[1],center[2]+.025],.075,.12,["#ffedaa","#fcebc7","#e8d5bd"],5)}
   lastF=p;lastB=q;
  }
  s.tube([[0,.93,-.13],[.13,1.19,-.13],[.36,1.32,-.12]],[.09,.065,.042],["#668f50","#8d9860"],6);
  s.leaf([.19,1.13,-.12],[.97,1.53,-.10],.22,["#52966c","#a6cd81","#69b584"],{arch:.14});
  s.leaf([.11,1.16,-.12],[-.58,1.55,-.12],.22,["#4c8d63","#a7c17b","#84bd8f"],{arch:.15});
  s.gem([.12,1.22,-.12],.17,.25,["#ffe5a1","#ffc77d","#f7b864"],6);
  return s.finalize()
}
