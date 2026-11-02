# COMP 512: Programming Assignment 1 (Distributed Travel Reservation System)

This repository contains the complete implementation of the distributed component-based Travel Reservation Information System across both **Java RMI** (Part 1.1) and **TCP Sockets** (Part 1.2) architectures.

---

## 🛠 Compilation & Build

Before running any servers or clients, compile the server interfaces, common classes, and clients using `make`:

```bash
# Compile Server components and build RMIInterface.jar
cd Server/
make clean
make

# Compile Client components
cd ../Client/
make clean
make
```

---

## 🚀 Part 1.1: Java RMI Distribution

### 1. Manual Launch (Individual Processes)

#### Step 1: Start RMI Registry
```bash
cd Server/
./run_rmi.sh
```

#### Step 2: Start Backend ResourceManagers
In separate terminals on their respective hosts:
```bash
cd Server/
./run_server.sh Flights
./run_server.sh Cars
./run_server.sh Rooms
```

#### Step 3: Start RMI Middleware
```bash
cd Server/
./run_middleware.sh [<flight_hostname>] [<car_hostname>] [<room_hostname>]
# Defaults to localhost for all three hosts if omitted
```

#### Step 4: Start RMI Client
```bash
cd Client/
./run_client.sh [<middleware_hostname> [<middleware_rmi_name>]]
# Default: ./run_client.sh localhost Middleware
```

### 2. Convenience Script (Local Multi-RM Launch)
```bash
cd Server/
./run_servers.sh
```

---

## ⚡ Part 1.2: TCP Sockets & Asynchronous Concurrency

### 1. Manual Launch (Individual Processes)

#### Step 1: Start Backend ResourceManagers
In separate terminals on their respective hosts:
```bash
cd Server/
./run_tcp_server.sh Flights 3022
./run_tcp_server.sh Cars 3023
./run_tcp_server.sh Rooms 3024
```

#### Step 2: Start TCP Middleware (Non-blocking Dispatcher)
```bash
cd Server/
./run_tcp_middleware.sh [<flightHost>] [<carHost>] [<roomHost>] [<flightPort>] [<carPort>] [<roomPort>] [<middlewarePort>]
# Default: ./run_tcp_middleware.sh localhost localhost localhost 3022 3023 3024 3021
```

#### Step 3: Start TCP Client
```bash
cd Client/
./run_tcp_client.sh [<middleware_hostname> [<middleware_port>]]
# Default: ./run_tcp_client.sh localhost 3021
```

### 2. Convenience Script (Local Multi-RM Launch)
```bash
cd Server/
./run_tcp_servers.sh
```

---

## 🧪 Client Usage & Automated Scripts

The client supports both interactive CLI commands and automated script piping:
```bash
# Interactive CLI
./run_client.sh localhost Middleware
./run_tcp_client.sh localhost 3021

# Script execution via standard input redirection
./run_tcp_client.sh localhost 3021 < test_script.txt
```

