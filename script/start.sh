#!/bin/sh
# This script will configures a new minecraft server and will starts it
# Here is a list of given variables:
# $1 = type of server
# $2 = server id
# $3 = client secret
# $4 = port of server

if [ "$#" -ne 4 ]; then
  echo "Usage: $0 <type> <id> <secret> <port>"
  exit 1
fi

# Variables

# EDIT THESE TWO VARIABLES
# The directory where all servers pre-configured are. Do not end this line with a /
INPUTDIRECTORY="/root/test/servers/all"
# The directory where all servers will be. Do not end this line with a /
OUTPUTDIRECTORY="/root/test/servers/servers"
# The minimum amount of ram you want to allocate
MINRAM="-Xms512M"
# The maximum amount of ram you want to allocate
MAXRAM="-Xmx2G"

# DO NOT EDIT HERE
JAVACMD=$(which java)
TYPE=$1
ID=$2
SECRET=$3
PORT=$4

OUTPUT="$OUTPUTDIRECTORY/$ID"
INPUT="$INPUTDIRECTORY/$TYPE"
AUTH="$OUTPUT/plugins/FroxyCore/auth"
SPIGOT="$OUTPUT/spigot.jar"

if [ ! -d "$INPUT" ]; then
	echo "Input $INPUT is not a correct directory"
	exit 1
fi

# copy directory
cp -r $INPUT $OUTPUT

if [ ! -d "$OUTPUT" ]; then
	echo "Output $OUTPUT doesn't exist"
	exit 1
fi

# server-port
echo "server-port is $PORT"
echo "server-port=$PORT" >> $OUTPUT/server.properties

# ID & SECRET
mkdir -p $OUTPUT/plugins/FroxyCore
echo $ID > $AUTH
echo $SECRET >> $AUTH

if [ ! -f "$SPIGOT" ]; then
	echo "Spigot file $SPIGOT doesn't exist"
	exit 1
fi

# Start the server
echo "Starting server"
cd $OUTPUT
# Please be careful when editing this line
screen -A -m -d -L -S $ID $JAVACMD $MINRAM $MAXRAM -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:-OmitStackTraceInFastThrow -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=8 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:MaxTenuringThreshold=1 -Dusing.aikars.flags=true -Daikars.new.flags=true -jar $SPIGOT nogui

exit 0