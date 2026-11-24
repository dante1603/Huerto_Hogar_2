// Geometría procedural original del prototipo Huerto Estelar.
// Estado: PROVISIONAL. No corresponde aún a un SKU comercial.
import { Sculpt, TAU } from '../core/sculpt.js'

export default function createZorbulbo() {
  const s = new Sculpt()
  s.ellipsoid([0,-.06,0],[1.08,.93,.97],["#3b9d92","#5fb7a8","#73bbaa","#399c8f","#41999b","#76b98f","#68a4b5","#6bbfa6"],{rings:10,parts:14,twist:.6,shape:(t,a)=>1+.054*Math.sin(7*a+2.5*t)});
  for(let k=0;k<7;k++){let a=k*TAU/7+.3;const from=[.52*Math.cos(a),-.65,.52*Math.sin(a)],to=[.40*Math.cos(a+.55),.88,.40*Math.sin(a+.55)];s.leaf(from,to,.36,["#337c76","#71bca0","#80d6b4"],{arch:.31,turn:.12})}
  for(let i=0;i<12;i++){const a=i*TAU/12;let pts=[[.29*Math.cos(a),-.7,.29*Math.sin(a)],[.52*Math.cos(a),-1.02,.54*Math.sin(a)],[.95*Math.cos(a+.22),-1.25,.86*Math.sin(a+.22)],[1.24*Math.cos(a+.4),-1.18,1.12*Math.sin(a+.4)]];s.tube(pts,[.09,.075,.05,.008],["#994a8d","#c064ab","#824a91","#d475bd"],5)}
  for(let i=0;i<5;i++){let a=i*TAU/5;s.tube([[0,.67,0],[.25*Math.cos(a),1.14,.25*Math.sin(a)],[.38*Math.cos(a),1.34,.38*Math.sin(a)]],[.085,.06,.03],["#8f528d","#af62ac"],5);s.leaf([.38*Math.cos(a),1.27,.38*Math.sin(a)],[.65*Math.cos(a),1.52,.65*Math.sin(a)],.15,["#70b68b","#c6e58d","#8ed8a0"],{arch:.07})}
  return s.finalize()
}
