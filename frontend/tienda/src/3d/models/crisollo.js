// Geometría procedural original del prototipo Huerto Estelar.
// Estado: PROVISIONAL. No corresponde aún a un SKU comercial.
import { Sculpt, TAU, rand, plus } from '../core/sculpt.js'

export default function createCrisollo() {
  const s = new Sculpt()
  s.tube([[0,-1.32,0],[0,-1.06,0],[0,-.49,0],[0,.02,0]],[.30,.39,.24,.13],["#8d57a2","#aa6bbc","#7b4e9c"],7);
  for(let k=0;k<9;k++){const a=k*TAU/9,r=.68+(k%2)*.20;let base=[0,-.55,0],mid=[r*.62*Math.cos(a),.14,r*.62*Math.sin(a)],top=[r*Math.cos(a),.45+(k%3)*.13,r*Math.sin(a)];s.tube([base,mid,top],[.15,.10,.07],["#8f57a5","#b175c8","#7657a7"],6)}
  const centers=[[0,.66,0],[.57,.61,.17],[-.57,.52,.17],[.14,.54,-.55],[-.2,.45,.67],[.75,.24,-.40],[-.65,.29,-.40]];
  centers.forEach((c,k)=>{let big=k===0?.58:.38;for(let i=0;i<19;i++){let ang=TAU*i/19,ring=i<7?.25:.75,rr=big*Math.sqrt((i+.5)/19);let off=[Math.cos(ang*(i<7?1:3))*rr,.1*Math.sin(i*2),Math.sin(ang*(i<7?1:3))*rr];let pos=plus(c,off);s.gem(pos,.10+rand(i,k)*.055,.23+rand(i+3,k)*.24,["#70c9b7","#5dd4c7","#a9e78b","#86d3bc","#48b5ba"],5)}});
  for(let i=0;i<5;i++){let a=i*TAU/5;s.leaf([0,-1.05,0],[.70*Math.cos(a),-.80,.70*Math.sin(a)],.18,["#4f9e8e","#a0dba2","#6bb8a0"],{arch:.13})}
  return s.finalize()
}
