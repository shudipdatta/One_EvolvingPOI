#! /bin/sh
java -Xmx8G -cp .:lib/ECLA.jar:lib/DTNConsoleConnection.jar core.DTNSim $*
