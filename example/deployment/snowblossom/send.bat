@echo off
title send
call client.bat
echo .
set /p amount="Enter amount to send: "
set /p address="Enter address to send to: "
echo .
client.bat send %amount% %address%
