@echo off
title send
call client-testnet.bat
echo .
set /p amount="Enter amount to send: "
set /p address="Enter address to send to: "
echo .
client-testnet.bat send %amount% %address%
