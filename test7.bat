cls
SET JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-11.0.25.9-hotspot
call mvn -X package
if %errorlevel% neq 0 exit /b %errorlevel%
set testLabels=websocket
set testAdditional=d:\work\lucee-extensions\extension-websocket\tests
set testServices=mysql

ant -buildfile="d:\work\script-runner" -DluceeVersionQuery="7/stable/jar" -Dwebroot="d:\work\lucee6\test" -Dexecute="/bootstrap-tests.cfm" -DextensionDir="d:\work\lucee-extensions\extension-websocket\target" 