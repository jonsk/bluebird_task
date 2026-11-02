@echo off
set SERVER_PORT=8080
start /B "" "D:/Develop/jdk-21.0.4/bin/java.exe" -jar target\bluebird-task.jar > target\run.log 2>&1
