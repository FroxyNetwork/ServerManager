#!/bin/sh
# This script will stop an existing minecraft server
# Here is a list of given variables:
# $1 = server id

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 <id>"
  exit 1
fi

# Variables

# EDIT THESE TWO VARIABLES
# The directory where all servers will be. Do not end this line with a /
SERVERDIRECTORY="/root/test/servers/servers"

# DO NOT EDIT HERE
ID=$1

SERVER="$SERVERDIRECTORY/$ID"

if [ ! -d "$SERVER" ]; then
	echo "Directory $SERVER doesn't exist"
	# This directory doesn't exist so we assume it is stopped
	exit 0
fi

# Stops process
echo "Stopping screen process $ID"
screen -X -S $ID quit

# Wait one second
echo "Waiting ..."
sleep 1

# Delete directory
echo "Deleting directory $SERVER"
rm -rf $SERVER
if [ -d "$SERVER" ]; then
	echo "Directory $SERVER still exists !"
	exit 1
fi

exit 0