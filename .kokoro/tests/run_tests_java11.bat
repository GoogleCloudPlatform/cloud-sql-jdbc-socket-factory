setx JAVA_HOME "C:\\OpenJDK\jdk-11.0.11"
setx PATH "%JAVA_HOME%\bin;%PATH%"
call RefreshEnv.cmd
"C:\Program Files\Git\bin\bash.exe" github/cloud-sql-jdbc-socket-factory/.kokoro/tests/run_tests.sh
