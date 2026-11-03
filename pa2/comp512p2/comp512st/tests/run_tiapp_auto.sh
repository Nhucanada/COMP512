#!/bin/bash

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

# Group number configuration (default: 21)
group=${GROUP:-21}

# Optional game map identifier (same across all players)
gameid=${GAMEID:-game-$group-99}

# Hostname configuration: default to local hostname or override with AUTOTESTHOST
export autotesthost=${AUTOTESTHOST:-$(hostname)}

# Process mappings: supports up to 9 processes on the autotesthost
export process1=${autotesthost}:401$group
export process2=${autotesthost}:402$group

# Default to 3 players unless NUMPLAYERS specifies otherwise
desired_players=${NUMPLAYERS:-3}
if [[ $desired_players -ge 3 ]]; then export process3=${autotesthost}:403$group; fi
if [[ $desired_players -ge 4 ]]; then export process4=${autotesthost}:404$group; fi
if [[ $desired_players -ge 5 ]]; then export process5=${autotesthost}:405$group; fi
if [[ $desired_players -ge 6 ]]; then export process6=${autotesthost}:406$group; fi
if [[ $desired_players -ge 7 ]]; then export process7=${autotesthost}:407$group; fi
if [[ $desired_players -ge 8 ]]; then export process8=${autotesthost}:408$group; fi
if [[ $desired_players -ge 9 ]]; then export process9=${autotesthost}:409$group; fi

# Configurable benchmark parameters
maxmoves=${MAXMOVES:-100}
interval=${INTERVAL:-100}
randseed=${RANDSEED:-123456789}

# Optional failure point simulation: enable via environment variable FAILMODE_N (e.g. FAILMODE_2=AFTERBECOMINGLEADER)
if [[ -n "$FAILMODE_1" ]]; then export failmode_1="$FAILMODE_1"; fi
if [[ -n "$FAILMODE_2" ]]; then export failmode_2="$FAILMODE_2"; fi
if [[ -n "$FAILMODE_3" ]]; then export failmode_3="$FAILMODE_3"; fi
if [[ -n "$FAILMODE_4" ]]; then export failmode_4="$FAILMODE_4"; fi
if [[ -n "$FAILMODE_5" ]]; then export failmode_5="$FAILMODE_5"; fi

# Check if this script is being executed on the correct server.
if [[ $autotesthost != $(hostname) && $autotesthost != "localhost" && $autotesthost != "127.0.0.1" ]]
then
	echo "Error !! This script is configured to run from $autotesthost, but you are trying to run this script on $(hostname)."
	exit 10
fi

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

# set your classpath to include the gcl tar files and the parent path to the comp512st directory.
export CLASSPATH="$BASEDIR/comp512p2.jar:$BASEDIR"

# Build the process group string.
export processgroup=$(env | grep '^process[1-9]=' | sort | sed -e 's/.*=//')
processgroup=$(echo $processgroup | sed -e 's/ /,/g')

# Total number of players
numplayers=$(echo $processgroup | awk -F',' '{ print NF}')
echo "There are $numplayers players in the setup"

# We do not need colors to track.
export TIMONOCHROME=true
# We do not want the island display to get constantly refreshed.
export UPDATEDISPLAY=false

playernum=1
for process in $(echo $processgroup | sed -e 's/,/ /g')
do
	failmode=$(env | grep '^failmode_'${playernum}'=' | sed -e 's/.*=//')
	echo java comp512st.tests.TreasureIslandAppAuto $process $processgroup $gameid $numplayers $playernum $maxmoves $interval $randseed$playernum $failmode '>' $gameid-$playernum-display.log
	if [[ -n "$failmode" ]]; then
		java -cp "$CLASSPATH" comp512st.tests.TreasureIslandAppAuto $process $processgroup $gameid $numplayers $playernum $maxmoves $interval $randseed$playernum $failmode > $gameid-$playernum-display.log 2>&1 &
	else
		java -cp "$CLASSPATH" comp512st.tests.TreasureIslandAppAuto $process $processgroup $gameid $numplayers $playernum $maxmoves $interval $randseed$playernum > $gameid-$playernum-display.log 2>&1 &
	fi
	playernum=$(expr $playernum + 1);
done
wait
