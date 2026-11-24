// Local analysis only; reuse the upstream XEX reader and libmspack WebAssembly.
import fs from 'node:fs';
import { readXexImage } from '../ports/carbon/android/app/src/main/assets/shaders/lib/xex.js';
import createLzx from '../ports/carbon/android/app/src/main/assets/shaders/wasm/lzx.mjs';
const xex=process.argv[2], output=process.argv[3];
if(!output)throw new Error('usage: node tools/decode_xex.mjs input.xex output.bin');
const module=await createLzx({print:()=>{},printErr:console.error});
const {image}=await readXexImage(new Uint8Array(fs.readFileSync(xex)),async(bytes,bits,size)=>{
  module.FS.writeFile('/input.lzx',bytes);
  if(module.callMain(['/input.lzx','/output.bin',String(bits),String(size)]))throw new Error('LZX failed');
  return module.FS.readFile('/output.bin');
});
fs.writeFileSync(output,image);
console.log(`Decoded ${image.length} bytes`);
