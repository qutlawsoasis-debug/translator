import urllib.request
import json
import time
import zipfile
import io
import sys

url = "https://api.github.com/repos/qutlawsoasis-debug/translator/actions/runs?per_page=5"
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
with urllib.request.urlopen(req) as response:
    data = json.loads(response.read().decode())

for run in data['workflow_runs']:
    print(f"Run: {run['name']} (ID: {run['id']})")
    if run['name'] == 'Generate Keystore':
        jobs_url = f"https://api.github.com/repos/qutlawsoasis-debug/translator/actions/runs/{run['id']}/jobs"
        req = urllib.request.Request(jobs_url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req) as response:
            jobs_data = json.loads(response.read().decode())
        
        for job in jobs_data['jobs']:
            print(f"  Job: {job['name']}, Status: {job['status']}")
            if job['status'] == 'completed':
                log_url = f"https://api.github.com/repos/qutlawsoasis-debug/translator/actions/jobs/{job['id']}/logs"
                try:
                    req = urllib.request.Request(log_url, headers={'User-Agent': 'Mozilla/5.0'})
                    with urllib.request.urlopen(req) as response:
                        log_data = response.read().decode('utf-8', errors='ignore')
                        lines = log_data.split('\n')
                        capture = False
                        for line in lines:
                            if "YOUR BASE64 KEYSTORE STRING:" in line:
                                capture = True
                                print("\n=== BASE64 FOUND ===")
                            elif capture and "========" in line:
                                capture = False
                                print("==================\n")
                                sys.exit(0)
                            elif capture:
                                print(line.split('Z ')[-1])
                except Exception as e:
                    print("  Error:", e)
        break
