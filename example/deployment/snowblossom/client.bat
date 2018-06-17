@echo off
title client
java -jar SnowBlossomClient_deploy.jar configs/client.conf %1 %2 %3
echo .
pause
