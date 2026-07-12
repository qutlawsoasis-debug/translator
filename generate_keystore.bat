@echo off
setlocal

echo =========================================================
echo GENERATING RELEASE.JKS KEYSTORE
echo =========================================================
echo.

if exist release.jks (
    echo [i] release.jks already exists! Deleting old one...
    del release.jks
)

echo [i] Running keytool...
keytool -genkey -v -keystore release.jks -alias translator -keyalg RSA -keysize 2048 -validity 10000 -storepass translator123 -keypass translator123 -dname "CN=Translator, OU=Dev, O=Oasis, L=City, S=State, C=RU"

if not exist release.jks (
    echo.
    echo [ERROR] Failed to create release.jks! Make sure Java is in your PATH.
    pause
    exit /b 1
)

echo.
echo [i] Converting release.jks to Base64...
powershell -Command "$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes('release.jks')); Set-Clipboard -Value $b64"
echo [OK] Base64 string successfully copied to clipboard!

echo.
echo [i] Opening GitHub Secrets page in browser...
start https://github.com/qutlawsoasis-debug/translator/settings/secrets/actions

echo.
echo =========================================================
echo In your browser, click "New repository secret" and add:
echo.
echo SECRET 1:
echo Name: KEYSTORE_BASE64
echo Value: (Press Ctrl+V to paste from clipboard)
echo.
pause

echo.
echo SECRET 2:
echo Name: KEY_ALIAS
echo Value: translator
echo.
pause

echo.
echo SECRET 3:
echo Name: KEY_PASSWORD
echo Value: translator123
echo.
pause

echo.
echo SECRET 4:
echo Name: STORE_PASSWORD
echo Value: translator123
echo.
echo =========================================================
echo Done! Next time you build, it will use this new keystore.
pause
