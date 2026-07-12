import urllib.request
import json

url = "https://api.github.com/repos/qutlawsoasis-debug/translator/actions/runs?per_page=5"
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
try:
    with urllib.request.urlopen(req) as response:
        data = json.loads(response.read().decode())
    
    for run in data['workflow_runs']:
        if run['conclusion'] == 'failure':
            print(f"Failed run: {run['name']} (ID: {run['id']})")
            jobs_url = f"https://api.github.com/repos/qutlawsoasis-debug/translator/actions/runs/{run['id']}/jobs"
            req = urllib.request.Request(jobs_url, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req) as response:
                jobs_data = json.loads(response.read().decode())
            
            for job in jobs_data['jobs']:
                if job['conclusion'] == 'failure':
                    print(f"  FAILED JOB: {job['name']} - URL: {job['html_url']}")
            break
except Exception as e:
    print(f"Error: {e}")
