import urllib.request
import json
url = 'https://api.github.com/repos/qutlawsoasis-debug/translator/actions/runs?per_page=1'
req = urllib.request.Request(url)
with urllib.request.urlopen(req) as response:
    data = json.loads(response.read().decode('utf-8'))
    run = data['workflow_runs'][0]
    print(f"Status: {run['status']}")
    print(f"Conclusion: {run['conclusion']}")
    print(f"URL: {run['html_url']}")
