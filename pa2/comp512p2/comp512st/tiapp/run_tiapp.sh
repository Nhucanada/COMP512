#!/bin/bash

if [[ $# -ne 1 ]]
then
	echo "Please pass the player number as the argument"
	echo "  $0 <playernum>"
	echo "  $0 3"
	exit 2
fi

# The player number is passed as the argument.
playernum=$1

# Set this to where your code and jar file root dir is.
# Default to existing paths if present, otherwise auto-resolve to parent directory.
if [[ -z "$BASEDIR" || ! -d "$BASEDIR" ]]; then
	if [[ -d "$HOME/comp512/COMP512/pa2/comp512p2" ]]; then
		BASEDIR="$HOME/comp512/COMP512/pa2/comp512p2"
	elif [[ -d "$HOME/comp512/p2" ]]; then
		BASEDIR="$HOME/comp512/p2"
	else
		BASEDIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
	fi
fi

# Group number configuration (Group 21)
group=21

# Optional game map identifier (same across all players)
gameid=game-$group-99

# Server and port configurations
export process1=tr-open-02.cs.mcgill.ca:401$group
export process2=tr-open-04.cs.mcgill.ca:402$group
#export process3=server3:403$group
#export process4=server4:404$group
#export process5=server5:405$group
#export process6=server6:406$group
#export process7=server7:407$group
#export process8=server8:408$group
#export process9=server9:409$group

if [[ ! -d "$BASEDIR" ]]
then
	echo "Error $BASEDIR is not a valid dir."
	exit 1
fi

if [[ ! -f "$BASEDIR/comp512p2.jar" ]]
then
	echo "Error cannot locate $BASEDIR/comp512p2.jar . Make sure it is present."
	exit 1
fi

if [[ ! -d "$BASEDIR/comp512st" ]]
then
	echo "Error cannot locate $BASEDIR/comp512st directory . Make sure it is present."
	exit 1
fi

# set the classpath to include the gcl tar files and the parent path to the comp512st directory.
export CLASSPATH="$BASEDIR/comp512p2.jar:$BASEDIR"

# Build the process group string.
export processgroup=$(env | grep '^process[1-9]=' | sort | sed -e 's/.*=//')
processgroup=$(echo $processgroup | sed -e 's/ /,/g')

# Total number of players
numplayers=$(echo $processgroup | awk -F',' '{ print NF}')

if [[ $playernum -gt $numplayers ]]
then
	echo "Error, you have only $numplayers processes configured for your group. Cannot allocate a process from $processgroup to player number $playernum"
	exit 3
fi

# Find out the process mapped to THIS player.
myprocess=$(echo $processgroup | awk -F',' -v playernum=$playernum '{ print $playernum }')

if [[ -z $myprocess ]]
then
	echo "Error, unable to allocate a process to this player from the group $processgroup"
	exit 4
fi

# Check if this script is being exectuted on the correct server.
myhost=${myprocess%:*}
if [[ $myhost != $(hostname) ]]
then
	echo "Error !! your player's process [$myprocess] is set to run from $myhost, but you are trying to run this script on $(hostname)."
	exit 10
fi

set -x
# Start the game instance for this player.
java comp512st.tiapp.TreasureIslandApp $myprocess $processgroup $gameid $numplayers $playernum
