@echo off
title miner-mem
:: feel free to adjust -Xmx#g to fit your system
start /B /LOW java -XX:+UseParallelOldGC -Xmx68g -jar SnowBlossomMiner_deploy.jar configs/miner-mem.conf
pause
