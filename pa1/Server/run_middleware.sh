./run_rmi.sh > /dev/null 2>&1

CODEBASE="file://$(pwd | sed 's/ /%20/g')/"
java -Djava.rmi.server.codebase="$CODEBASE" Server.RMI.RMIMiddleware ${1:-localhost} ${2:-localhost} ${3:-localhost}
