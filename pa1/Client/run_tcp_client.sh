#!/bin/bash
# Usage: ./run_tcp_client.sh [<server_hostname> [<server_port>]]
# Default: ./run_tcp_client.sh localhost 3021

HOST=${1:-localhost}
PORT=${2:-3021}

java -cp ../Server/RMIInterface.jar:. Client.TCPClient "$HOST" "$PORT"
