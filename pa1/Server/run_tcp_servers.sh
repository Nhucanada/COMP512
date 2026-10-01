#!/bin/bash
# Convenient launch script for 3 TCP backend ResourceManagers and TCP Middleware
# Usage: ./run_tcp_servers.sh [local|cluster]

MODE=${1:-local}

# Cluster CS Machines (tr-open-01, tr-open-02, etc...)
MACHINES=()

if [ "$MODE" = "cluster" ] && [ ${#MACHINES[@]} -ge 4 ]; then
	tmux new-session \; \
		split-window -h \; \
		split-window -v \; \
		split-window -v \; \
		select-layout main-vertical \; \
		select-pane -t 1 \; \
		send-keys "ssh -t ${MACHINES[0]} \"cd $(pwd) > /dev/null; echo -n 'Connected to '; hostname; ./run_tcp_server.sh Flights 3022\"" C-m \; \
		select-pane -t 2 \; \
		send-keys "ssh -t ${MACHINES[1]} \"cd $(pwd) > /dev/null; echo -n 'Connected to '; hostname; ./run_tcp_server.sh Cars 3023\"" C-m \; \
		select-pane -t 3 \; \
		send-keys "ssh -t ${MACHINES[2]} \"cd $(pwd) > /dev/null; echo -n 'Connected to '; hostname; ./run_tcp_server.sh Rooms 3024\"" C-m \; \
		select-pane -t 0 \; \
		send-keys "ssh -t ${MACHINES[3]} \"cd $(pwd) > /dev/null; echo -n 'Connected to '; hostname; sleep 1s; ./run_tcp_middleware.sh ${MACHINES[0]} ${MACHINES[1]} ${MACHINES[2]} 3022 3023 3024 3021\"" C-m \;
else
	tmux new-session \; \
		split-window -h \; \
		split-window -v \; \
		split-window -v \; \
		select-layout main-vertical \; \
		select-pane -t 1 \; \
		send-keys "./run_tcp_server.sh Flights 3022" C-m \; \
		select-pane -t 2 \; \
		send-keys "./run_tcp_server.sh Cars 3023" C-m \; \
		select-pane -t 3 \; \
		send-keys "./run_tcp_server.sh Rooms 3024" C-m \; \
		select-pane -t 0 \; \
		send-keys "sleep 1s; ./run_tcp_middleware.sh localhost localhost localhost 3022 3023 3024 3021" C-m \;
fi
