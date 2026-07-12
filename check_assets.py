import urllib.request
import json
url = 'https://api.github.com/repos/qutlawsoasis-debug/translator/releases/latest'
req = urllib.request.Request(url)
with urllib.request.urlopen(req) as response:
    data = json.loads(response.read().decode('utf-8'))
    print(f"Release Name: {data['name']}")
    print(f"Assets Count: {len(data['assets'])}")
    for asset in data['assets']:
        print(f"Asset Name: {asset['name']}")
        print(f"Asset URL: {asset['browser_download_url']}")
