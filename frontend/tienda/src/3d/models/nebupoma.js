// Geometría procedural original del prototipo Huerto Estelar.
// Estado: PROVISIONAL. No corresponde aún a un SKU comercial.
import { Sculpt, TAU, rand } from '../core/sculpt.js'

export default function createNebupoma() {
  const s = new Sculpt()
  const palette=["#7754ad","#7a5eae","#675cac","#a456b1","#b14b9e","#684c9b","#b458ab","#8056ac","#b8589c","#9f4a9c","#62458a","#9255bd","#c85aa7","#a446a9","#71549a","#bd5693"];
  s.ellipsoid([0,-.10,0],[1.02,1.12,1.02],(i,j,rings)=>palette[(j+Math.floor(i*.9))%palette.length],{rings:15,parts:16,twist:.48,shape:(t,a)=>1+.045*Math.cos(8*a+Math.sin(t)*2)});
  const seam=[];for(let i=0;i<=29;i++){let t=i/29;let a=-.95+t*TAU*.95;seam.push([1.02*Math.sin(a),-.1+.47*Math.sin(t*Math.PI*2+.3),1.05*Math.cos(a)]);}
  s.tube(seam,.025,["#edb2cf","#ffd0c8","#ff91c5"],5);
  for(let i=0;i<45;i++){const t=.35+rand(i,3)*2.32,a=rand(i,9)*TAU,r=1.04;s.gem([r*Math.sin(t)*Math.cos(a),-.1+1.14*Math.cos(t),r*Math.sin(t)*Math.sin(a)],.019,.035,["#ffb3dc","#d9aedc","#ffe0ba"],4)}
  s.tube([[0,.98,0],[.05,1.28,0],[.26,1.47,.07]],[.12,.08,.052],["#8f547f","#b47a76"],6);
  s.leaf([.1,1.25,.05],[-.79,1.65,.05],.30,["#449d89","#8bcf9e","#60c0a7"],{arch:.11});
  s.leaf([.23,1.38,0],[.95,1.62,-.07],.22,["#418c78","#a6dc9e","#6fc4a8"],{arch:.15});
  return s.finalize()
}
