// Geometría procedural original del prototipo Huerto Estelar.
// Estado: PROVISIONAL. No corresponde aún a un SKU comercial.
import { Sculpt, TAU } from '../core/sculpt.js'

export default function createLunahoja() {
  const s = new Sculpt()
  s.ellipsoid([0,-.67,0],[.36,.49,.36],["#86c98b","#d8eea0","#c8eca9"],{rings:8,parts:10});
  for(let k=0;k<14;k++){
   const a=k*TAU/14,r=.16+(k%3)*.065;let tipR=.80+(k%4)*.18;
   const from=[r*Math.cos(a),-1.06,r*Math.sin(a)],tip=[tipR*Math.cos(a+.25),.85+(k%4)*.21,tipR*Math.sin(a+.25)];
   s.leaf(from,tip,.22+(k%3)*.065,["#7badb5","#dcf5b2","#b2e6cd"],{arch:.16+(k%2)*.11,turn:(k%2?-.22:.21),segments:8});
  }
  for(let j=0;j<4;j++){let a=j*TAU/4;s.tube([[.1*Math.cos(a),-1.05,.1*Math.sin(a)],[.52*Math.cos(a),-.94,.52*Math.sin(a)],[.77*Math.cos(a+.22),-.75,.77*Math.sin(a+.22)]],[.045,.034,.012],["#9683a9","#b4a7bf"],6)}
  return s.finalize()
}
