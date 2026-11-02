#!/bin/bash

# Edit this dir name to where your comp512p2.jar and build_tiapp.sh script are.
# Default to $HOME/comp512/p2 if present, otherwise auto-resolve to script directory.
if [[ -z "$BASEDIR" || ! -d "$BASEDIR" ]]; then
	if [[ -d "$HOME/comp512/p2" ]]; then
		BASEDIR=$HOME/comp512/p2
	else
		BASEDIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
	fi
fi

if [[ ! -d $BASEDIR ]]
then
	echo "Error $BASEDIR is not a valid dir."
	exit 1
fi

if [[ ! -f $BASEDIR/comp512p2.jar ]]
then
	echo "Error cannot locate $BASEDIR/comp512p2.jar . Make sure it is present."
	exit 1
fi

if [[ ! -d $BASEDIR/comp512st ]]
then
	echo "Error cannot locate $BASEDIR/comp512st directory . Make sure it is present."
	exit 1
fi

export CLASSPATH="$BASEDIR/comp512p2.jar:$BASEDIR"
cd "$BASEDIR"

javac -cp "$CLASSPATH" $(find -L comp512st -name '*.java')

