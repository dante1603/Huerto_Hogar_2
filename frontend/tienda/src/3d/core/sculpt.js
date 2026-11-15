export const rand=(a,b,c=0)=>{let n=Math.sin(a*127.1+b*311.7+c*78.233)*43758.5453;return n-Math.floor(n)};
const V=(x,y,z)=>[x,y,z];
export const mix=(a,b,t)=>a.map((v,i)=>v+(b[i]-v)*t);
export const plus=(a,b)=>a.map((v,i)=>v+b[i]);
const sub=(a,b)=>a.map((v,i)=>v-b[i]);
const mul=(a,s)=>a.map(v=>v*s);
const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
const dot=(a,b)=>a[0]*b[0]+a[1]*b[1]+a[2]*b[2];
const norm=a=>{const l=Math.hypot(...a)||1;return mul(a,1/l)};
const hex=h=>{h=h.replace("#","");return [parseInt(h.substring(0,2),16)/255,parseInt(h.substring(2,4),16)/255,parseInt(h.substring(4,6),16)/255]};
export class Sculpt{
 constructor(){this.p=[];this.n=[];this.c=[]}
 tri(a,b,c,color){const n=norm(cross(sub(b,a),sub(c,a)));const col=typeof color==="string"?hex(color):color;for(const point of [a,b,c]){this.p.push(...point);this.n.push(...n);this.c.push(...col)}}
 quad(a,b,c,d,col){this.tri(a,b,c,col);this.tri(a,c,d,col)}
 ellipsoid(center,radii,palette,opts={}){
  const rings=opts.rings||9,parts=opts.parts||12,fn=opts.shape||(()=>1);
  const rot=opts.spin||0;
  const point=(i,j)=>{let t=Math.PI*i/rings,ang=2*Math.PI*j/parts+rot+Math.cos(t)*(.07+(opts.twist||0));let pulse=fn(t,ang,i,j);return [center[0]+radii[0]*Math.sin(t)*Math.cos(ang)*pulse,center[1]+radii[1]*Math.cos(t),center[2]+radii[2]*Math.sin(t)*Math.sin(ang)*pulse]};
  for(let i=0;i<rings;i++)for(let j=0;j<parts;j++){
   const a=point(i,j),b=point(i+1,j),c=point(i+1,j+1),d=point(i,j+1);
   let color=typeof palette==="function"?palette(i,j,rings,parts):palette[(j+Math.floor(i/3))%palette.length];
   this.quad(a,b,c,d,color);
  }
 }
 tube(points,radii,palette,parts=6){
  const pcount=points.length;if(pcount<2)return;
  let rings=[];
  for(let i=0;i<pcount;i++){
   const tangent=norm(sub(points[Math.min(pcount-1,i+1)],points[Math.max(0,i-1)]));
   let ax=Math.abs(dot(tangent,[0,1,0]))>.84?[1,0,0]:[0,1,0];let u=norm(cross(tangent,ax)),v=norm(cross(tangent,u));const ring=[];
   for(let j=0;j<parts;j++){let angle=j*2*Math.PI/parts;ring.push(plus(points[i],mul(plus(mul(u,Math.cos(angle)),mul(v,Math.sin(angle))),Array.isArray(radii)?radii[i]:radii)));}
   rings.push(ring);
  }
  for(let i=0;i<pcount-1;i++)for(let j=0;j<parts;j++){this.quad(rings[i][j],rings[i+1][j],rings[i+1][(j+1)%parts],rings[i][(j+1)%parts],palette[(i+j)%palette.length]);}
 }
 stem(a,b,r1,r2,palette,seg=6){
  const mid=mix(a,b,.54);this.tube([a,mid,b],[r1,(r1+r2)*.5,r2],palette,seg)
 }
 leaf(a,b,width,palette,opt={}){
  const n=opt.segments||7,arch=opt.arch||0.28,turn=opt.turn||0;
  const dir=norm(sub(b,a));let across=norm(cross(dir,[0,0,1]));if(Math.hypot(...across)<.1)across=[1,0,0];
  const bulge=norm(cross(across,dir));
  let prev=null;
  for(let i=0;i<=n;i++){
   let t=i/n,blade=width*Math.pow(Math.sin(t*Math.PI),.78)*(1-.13*t);
   let center=plus(mix(a,b,t),mul(bulge,Math.sin(t*Math.PI)*arch));
   center=plus(center,mul(across,Math.sin(t*Math.PI)*turn*t));
   let l=plus(center,mul(across,-blade)),r=plus(center,mul(across,blade));
   let mid=plus(center,mul(bulge,blade*.19));
   if(prev){
    this.tri(prev.l,prev.m,l,palette[0]);this.tri(l,prev.m,mid,palette[1]);
    this.tri(prev.m,prev.r,mid,palette[2]);this.tri(mid,prev.r,r,palette[1]);
    this.tri(l,prev.m,prev.l,palette[0]);this.tri(mid,prev.m,l,palette[1]);
    this.tri(mid,prev.r,prev.m,palette[2]);this.tri(r,prev.r,mid,palette[1]);
   }
   prev={l,r,m:mid};
  }
 }
 gem(c,r,h,palette,sides=5){
  let lower=[],wide=[],upper=[];
  for(let j=0;j<sides;j++){let a=j*Math.PI*2/sides;const v=[Math.cos(a),0,Math.sin(a)];lower.push(plus(c,mul(v,r*.68)));wide.push(plus(plus(c,[0,h*.32,0]),mul(v,r)));upper.push(plus(plus(c,[0,h*.78,0]),mul(v,r*.53)));}
  const peak=plus(c,[0,h,0]);
  for(let j=0;j<sides;j++){let k=(j+1)%sides;this.quad(lower[j],wide[j],wide[k],lower[k],palette[j%palette.length]);this.quad(wide[j],upper[j],upper[k],wide[k],palette[(j+1)%palette.length]);this.tri(upper[j],peak,upper[k],palette[(j+2)%palette.length]);}
 }
 finalize(){const p=new Float32Array(this.p),n=new Float32Array(this.n),c=new Float32Array(this.c);let lo=[Infinity,Infinity,Infinity],hi=[-Infinity,-Infinity,-Infinity];for(let i=0;i<p.length;i+=3){for(let d=0;d<3;d++){lo[d]=Math.min(lo[d],p[i+d]);hi[d]=Math.max(hi[d],p[i+d])}}
  const center=lo.map((x,i)=>(x+hi[i])/2);const scale=2.9/Math.max(hi[0]-lo[0],hi[1]-lo[1],hi[2]-lo[2]);for(let i=0;i<p.length;i+=3)for(let d=0;d<3;d++)p[i+d]=(p[i+d]-center[d])*scale;return {p,n,c,count:p.length/3};}
}

export const TAU = Math.PI * 2;
