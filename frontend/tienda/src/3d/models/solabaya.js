// Geometría procedural original del prototipo Huerto Estelar.
// Estado: PROVISIONAL. No corresponde aún a un SKU comercial.
import { Sculpt, rand } from '../core/sculpt.js'

export default function createSolabaya() {
  const s = new Sculpt()
  s.tube([[-.67,1.2,0],[-.10,1.45,0],[.51,1.33,0]],[.15,.125,.09],["#8c536f","#bc6976","#d39b5a"],7);
  s.leaf([-.15,1.43,0],[-1.25,1.63,.1],.37,["#2a8a7a","#60bd96","#91dba0"],{arch:.2,turn:.08});
  s.leaf([.3,1.36,.04],[1.22,1.5,-.04],.38,["#348d8e","#8bd4a0","#6db99b"],{arch:.2});
  for(let i=0;i<13;i++){
   let row=Math.floor(i/4),col=i%4,xx=(col-1.5)*.38+(row%2)*.1,yy=.70-row*.48+rand(i,2)*.08,zz=(rand(i,4)-.5)*.35;
   const center=[xx,yy,zz];s.tube([[xx*.32,1.3,0],[xx*.72,yy+.29,zz*.45],[xx,yy+.12,zz]],[.047,.032,.012],["#a65371","#d98770"],5);
   const r=.28+(i%3)*.025;
   s.ellipsoid(center,[r,r*.99,r],(lat,j,rows)=>lat<rows/2?["#e37e69","#e79359","#ffbb62"][j%3]:["#efaa44","#f4a756","#e87f5b"][j%3],{rings:7,parts:9});
   s.gem([xx,yy+r*.79,zz],.11,.095,["#b75b79","#de7a7a","#a64e7b"],5);
  }
  return s.finalize()
}
