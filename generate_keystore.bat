@echo off
chcp 65001 >nul
setlocal

echo =========================================================
echo === Генерация постоянного ключа для Android (release.jks) ===
echo =========================================================
echo.

if exist release.jks (
    echo [i] Файл release.jks уже существует! Удаляю старый...
    del release.jks
)

echo [i] Запускаю keytool...
keytool -genkey -v -keystore release.jks -alias translator -keyalg RSA -keysize 2048 -validity 10000 -storepass translator123 -keypass translator123 -dname "CN=Translator, OU=Dev, O=Oasis, L=City, S=State, C=RU"

if not exist release.jks (
    echo.
    echo [ОШИБКА] Не удалось создать release.jks! Убедитесь, что Java (keytool) установлена и прописана в PATH.
    pause
    exit /b 1
)

echo.
echo [i] Конвертация release.jks в Base64...
powershell -Command "[Convert]::ToBase64String([IO.File]::ReadAllBytes('release.jks')) | Set-Clipboard"
echo [OK] Base64 строка успешно скопирована в буфер обмена!

echo.
echo [i] Открываю страницу GitHub Secrets в браузере...
start https://github.com/qutlawsoasis-debug/translator/settings/secrets/actions

echo.
echo =========================================================
echo В браузере нажмите "New repository secret" и добавьте 4 секрета:
echo.
echo СЕКРЕТ 1:
echo Название: KEYSTORE_BASE64
echo Значение: (нажмите Ctrl+V, чтобы вставить длинную строку из буфера)
echo.
pause

echo.
echo СЕКРЕТ 2:
echo Название: KEY_ALIAS
echo Значение: translator
echo.
pause

echo.
echo СЕКРЕТ 3:
echo Название: KEY_PASSWORD
echo Значение: translator123
echo.
pause

echo.
echo СЕКРЕТ 4:
echo Название: STORE_PASSWORD
echo Значение: translator123
echo.
echo =========================================================
echo Готово! Теперь при каждой сборке приложение будет подписываться 
echo вашим ключом. Ошибка установки поверх старой версии решена!
pause
