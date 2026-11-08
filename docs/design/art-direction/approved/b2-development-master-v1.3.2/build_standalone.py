from pathlib import Path
import re, base64, html
O=Path(__file__).parent
# Bundle only CSS actually linked from index, in this order (preserves the approved sections' scoped cascade).
source=(O/'index.html').read_text(encoding='utf-8')
cssfiles=re.findall(r'<link rel="stylesheet" href="([^"]+)"\s*/>',source)
css=[]
for rel in cssfiles:
 s=(O/rel).read_text(encoding='utf-8')
 def inline_url(m):
  asset=m.group(1)
  actual=(O/rel).parent/asset
  if not actual.exists(): actual=O/'assets'/Path(asset).name
  if not actual.exists():raise FileNotFoundError((rel,asset))
  mime={'.jpg':'image/jpeg','.png':'image/png','.svg':'image/svg+xml','.webp':'image/webp'}[actual.suffix]
  return 'url("data:'+mime+';base64,'+base64.b64encode(actual.read_bytes()).decode('ascii')+'")'
 s=re.sub(r'url\([\'\"]?((?:\.\./)?assets/[\w.-]+\.(?:jpg|png|svg|webp))[\'\"]?\)',inline_url,s)
 css.append('/* -------- '+rel+' -------- */\n'+s)
source=re.sub(r'<link rel="stylesheet" href="[^"]+"\s*/>','',source)
source=source.replace('</head>','<style>\n'+'\n'.join(css)+'\n</style></head>')
def inline_img(m):
 asset=m.group(1); actual=O/asset
 if not actual.exists():raise FileNotFoundError(asset)
 mime={'.jpg':'image/jpeg','.png':'image/png','.svg':'image/svg+xml','.webp':'image/webp'}[actual.suffix]
 return 'src="data:'+mime+';base64,'+base64.b64encode(actual.read_bytes()).decode('ascii')+'"'
source=re.sub(r'src="(assets/[\w.-]+\.(?:jpg|png|svg|webp))"',inline_img,source)
source=source.replace('<script src="script.js"></script>','<script>\n'+(O/'script.js').read_text(encoding='utf-8')+'\n</script>')
(O/'B2_DEVELOPMENT_MASTER_V1_3_2_STANDALONE.html').write_text(source,encoding='utf-8')
print('bundled',len(cssfiles), 'CSS files:', ', '.join(cssfiles),'output MB:',round(len(source)/1024**2,2))
