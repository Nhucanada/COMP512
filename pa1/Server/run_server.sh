#Usage: ./run_server.sh [<rmi_name>]

./run_rmi.sh > /dev/null 2>&1
CODEBASE="file://$(pwd | sed 's/ /%20/g')/"
java -Djava.rmi.server.codebase="$CODEBASE" Server.RMI.RMIResourceManager $1
