#!/usr/bin/env python3
"""Extract the original title icon from the locally decoded ISO's XEX resource."""
from pathlib import Path
import hashlib, json, struct, zlib
ROOT=Path(__file__).resolve().parents[1]
PORT=ROOT/'ports/game_europe'
image=(PORT/'image.bin').read_bytes()
xex=(PORT/'default.xex').read_bytes()
n=struct.unpack_from('>I',xex,20)[0]
assert n<=4096 and 24+8*n<=len(xex)
resource=next(v for k,v in (struct.unpack_from('>II',xex,24+8*i) for i in range(n)) if k==0x2ff)
size=struct.unpack_from('>I',xex,resource)[0]
assert size>=20 and (size-4)%16==0 and resource+size<=len(xex)
records=[struct.unpack_from('>8sII',xex,resource+4+i*16) for i in range((size-4)//16)]
name,address,length=next(record for record in records if record[0]==b'454107EC')
offset=address-0x82000000
assert 0<=offset and offset+length<=len(image)
data=image[offset:offset+length]
magic,version,count,used,free,freeused=struct.unpack_from('>6I',data)
assert magic==0x58444246 and used<=count and freeused<=free
content=24+18*count+8*free
assert content<=len(data)
off,size=next((off,size) for section,id,off,size in (struct.unpack_from('>HQII',data,24+18*i) for i in range(used)) if section==2 and id==0x8000)
assert content+off+size<=len(data)
png=data[content+off:content+off+size]
assert png.startswith(bytes.fromhex('89504e470d0a1a0a'))
pos=8
while pos<len(png):
 length=struct.unpack_from('>I',png,pos)[0];kind=png[pos+4:pos+8];end=pos+8+length
 assert end+4<=len(png) and zlib.crc32(png[pos+4:end])&0xffffffff==struct.unpack_from('>I',png,end)[0]
 pos=end+4
 if kind==b'IEND':break
assert pos==len(png)
w,h=struct.unpack_from('>II',png,16)
out=ROOT/'app/src/main/res/drawable-nodpi/carbon_icon.png'
out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(png)
meta={'source':'local ISO default.xex, title XDBF image 0x8000','xex_sha256':hashlib.sha256(xex).hexdigest(),'resource':name.decode(),'guest_resource_address':f'0x{address:08X}','xdbf_image_id':'0x8000','png_sha256':hashlib.sha256(png).hexdigest(),'width':w,'height':h,'original_bytes':True}
(PORT/'icon-provenance.json').write_text(json.dumps(meta,indent=2)+'\n')
print(json.dumps(meta,indent=2))
