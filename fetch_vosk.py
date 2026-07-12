import urllib.request
import json
import re

url = "https://alphacephei.com/vosk/models/model-list.json"
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
try:
    with urllib.request.urlopen(req) as response:
        data = json.loads(response.read().decode())
    
    targets = ['en', 'de', 'fr', 'es', 'cn', 'ja', 'it', 'pt', 'ko', 'ru']
    for t in targets:
        # Find small models
        models = [m for m in data if m['lang'] == t and m['type'] == 'small']
        if models:
            best = models[0]
            print(f"{t.upper()}: {best['url']} ({best['size']} bytes)")
        else:
            print(f"{t.upper()}: No small model found")
except Exception as e:
    print(f"Error: {e}")
