#!/bin/bash
# Usage: ./run_tcp_server.sh <name> [port]
# Example: ./run_tcp_server.sh Flights 3022

NAME=${1:-Server}
PORT=${2:-3022}

java -cp .:RMIInterface.jar Server.TCP.TCPResourceManager "$NAME" "$PORT"
