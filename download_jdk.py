import urllib.request
import zipfile
import os
import sys

url = "https://download.java.net/java/GA/jdk17.0.2/dfd4a8d0985749f896bed50d7138ee7f/8/GPL/openjdk-17.0.2_windows-x64_bin.zip"
zip_path = "jdk.zip"

print("Downloading JDK...")
urllib.request.urlretrieve(url, zip_path)

print("Extracting JDK...")
with zipfile.ZipFile(zip_path, 'r') as zip_ref:
    zip_ref.extractall(".")

print("Done!")
