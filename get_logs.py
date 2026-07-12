import urllib.request
import json
import sys

def check():
    url = "https://api.github.com/repos/qutlawsoasis-debug/translator/actions/runs/29188858320/jobs"
    req = urllib.request.Request(url)
    try:
        with urllib.request.urlopen(req) as response:
            data = json.loads(response.read().decode())
            for job in data.get('jobs', []):
                if job.get('conclusion') == 'failure':
                    print(f"Job failed: {job.get('name')}")
                    for step in job.get('steps', []):
                        if step.get('conclusion') == 'failure':
                            print(f"Step failed: {step.get('name')}")
    except Exception as e:
        print("Error:", e)

check()
