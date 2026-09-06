from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
p=Path('store');p.mkdir(exist_ok=True)
font='/System/Library/Fonts/Supplemental/Arial.ttf'
bold='/System/Library/Fonts/Supplemental/Arial Bold.ttf'
im=Image.new('RGB',(512,512),'#087F73');d=ImageDraw.Draw(im)
d.polygon([(86,170),(256,104),(426,170),(256,236)],fill='white')
d.polygon([(142,219),(256,266),(370,219),(370,332),(256,389),(142,332)],fill='white')
d.line([(422,190),(422,316)],fill='#D2F6DE',width=18)
im.save(p/'icon-512.png')
feature=Image.new('RGB',(1024,500),'#064E48');d=ImageDraw.Draw(feature)
d.rounded_rectangle((660,52,976,446),36,fill='#0B7467')
d.ellipse((704,97,931,324),fill='#D2F6DE')
d.polygon([(737,172),(818,137),(898,172),(818,207)],fill='#087F73')
d.polygon([(761,196),(818,220),(875,196),(875,250),(818,278),(761,250)],fill='#087F73')
d.text((58,92),'AI LMS TMS',font=ImageFont.truetype(bold,61),fill='white')
d.text((60,187),'Your learning.\nAll in one place.',font=ImageFont.truetype(font,38),fill='#D2F6DE',spacing=8)
d.text((60,333),'COURSEWARE  •  CALENDAR  •  REMINDERS',font=ImageFont.truetype(bold,19),fill='white')
d.text((60,412),'Tertiary Infotech Academy Pte. Ltd.',font=ImageFont.truetype(font,19),fill='#D2F6DE')
feature.save(p/'feature-1024x500.png')
