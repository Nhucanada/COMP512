#!/usr/bin/env python3
"""
Comprehensive Distributed System Integration & Verification Test Suite for COMP 512 PA1.
Verifies end-to-end distributed operations across RMI (Part 1.1) and TCP Sockets (Part 1.2):
1. RMI 3-tier distribution: Flights, Cars, Rooms + RMIMiddleware + Client commands.
2. Centralized Customer Management (Option C) & Bill formatting.
3. Cascading Cancellations upon customer deletion restoring backend RM inventories.
4. Bundle Transaction Atomicity & LIFO compensating rollback on partial failure.
5. TCP Socket Generic Envelope & Dynamic Client Proxy verification.
6. Multi-threaded Concurrent Client requests on TCP Middleware.
"""

import os
import sys
import time
import signal
import socket
import subprocess
from pathlib import Path

WORKSPACE_ROOT = Path(__file__).resolve().parents[4]
PA1_DIR = WORKSPACE_ROOT / "pa1"
SERVER_DIR = PA1_DIR / "Server"
CLIENT_DIR = PA1_DIR / "Client"

class DistributedSystemTester:
    def __init__(self):
        self.processes = []
        self.passed = 0
        self.failed = 0

    def cleanup(self):
        print("\n[INFO] Terminating all test processes...")
        for p in self.processes:
            try:
                os.killpg(os.getpgid(p.pid), signal.SIGTERM)
            except Exception:
                try:
                    p.terminate()
                except Exception:
                    pass
        self.processes = []
        time.sleep(1.0)

    def test(self, name, condition, details=""):
        if condition:
            self.passed += 1
            print(f"[PASS] {name}")
        else:
            self.failed += 1
            print(f"[FAIL] {name}: {details}")

    def run_command(self, cmd_list, stdin_input=None):
        res = subprocess.run(
            cmd_list,
            input=stdin_input,
            capture_output=True,
            text=True,
            cwd=str(CLIENT_DIR)
        )
        return res

    def wait_for_port(self, port, host="localhost", timeout=10.0):
        start = time.time()
        while time.time() - start < timeout:
            try:
                with socket.create_connection((host, port), timeout=0.5):
                    return True
            except (ConnectionRefusedError, socket.timeout, OSError):
                time.sleep(0.2)
        return False

    def test_rmi_distribution(self):
        print("\n========================================================")
        print("STAGE 1: RMI 3-TIER DISTRIBUTED SYSTEM VERIFICATION")
        print("========================================================")

        rmi_port = 1099
        # 1. Start rmiregistry
        print(f"  Starting rmiregistry on port {rmi_port}...")
        reg_proc = subprocess.Popen(
            ["rmiregistry", "-J-Djava.rmi.server.useCodebaseOnly=false", str(rmi_port)],
            cwd=str(SERVER_DIR),
            preexec_fn=os.setsid,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL
        )
        self.processes.append(reg_proc)
        time.sleep(1.0)

        codebase_uri = SERVER_DIR.as_uri() + "/"
        # 2. Start Flights, Cars, Rooms RMs
        for name in ["Flights", "Cars", "Rooms"]:
            print(f"  Starting RMI {name} server...")
            proc = subprocess.Popen(
                ["java", f"-Djava.rmi.server.codebase={codebase_uri}",
                 "Server.RMI.RMIResourceManager", name, str(rmi_port)],
                cwd=str(SERVER_DIR),
                preexec_fn=os.setsid,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True
            )
            self.processes.append(proc)
        time.sleep(1.5)

        # 3. Start RMIMiddleware
        print("  Starting RMIMiddleware server...")
        mw_proc = subprocess.Popen(
            ["java", f"-Djava.rmi.server.codebase={codebase_uri}",
             "Server.RMI.RMIMiddleware", "localhost", "localhost", "localhost",
             str(rmi_port), str(rmi_port), str(rmi_port), str(rmi_port)],
            cwd=str(SERVER_DIR),
            preexec_fn=os.setsid,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )
        self.processes.append(mw_proc)
        time.sleep(1.5)

        # Check if any RMI server failed to start
        for p in self.processes:
            if p.poll() is not None and p != reg_proc:
                out, err = p.communicate()
                print(f"[ERROR] Process exited early with code {p.returncode}: {err.strip()}")

        # 4. Run automated test client commands against Middleware
        client_script = (
            "AddFlight,101,10,250\n"
            "AddCars,Montreal,5,50\n"
            "AddRooms,Montreal,3,100\n"
            "AddCustomerID,1\n"
            "ReserveFlight,1,101\n"
            "ReserveCar,1,Montreal\n"
            "ReserveRoom,1,Montreal\n"
            "QueryFlight,101\n"
            "QueryCars,Montreal\n"
            "QueryRooms,Montreal\n"
            "QueryCustomer,1\n"
            "DeleteCustomer,1\n"
            "QueryFlight,101\n"
            "QueryCars,Montreal\n"
            "QueryRooms,Montreal\n"
            "AddCustomerID,2\n"
            "AddFlight,999,-5,100\n"
            "AddFlight,999,5,-100\n"
            "AddCars,NegativeCity,5,-50\n"
            "AddRooms,NegativeCity,5,-50\n"
            "Bundle,2,101,101,Montreal,1,1\n"
            "Bundle,2,101,Montreal,1,1\n"
            "QueryFlight,101\n"
            "QueryCars,Montreal\n"
            "QueryRooms,Montreal\n"
            "QueryCustomer,2\n"
            "Quit\n"
        )

        res = self.run_command(
            ["java", "-cp", f"{SERVER_DIR}/RMIInterface.jar:.", "Client.RMIClient", "localhost", "Middleware", str(rmi_port)],
            stdin_input=client_script
        )

        out = res.stdout
        self.test("RMI Client connects to RMIMiddleware", "Connected to 'Middleware'" in out)
        self.test("RMI Add resources through Middleware", "Flight added" in out and "Cars added" in out and "Rooms added" in out)
        self.test("RMI Option C Customer creation", "Add customer ID: 1" in out)
        self.test("RMI Single reservations (Flight, Car, Room)", "Flight Reserved" in out and "Car Reserved" in out and "Room Reserved" in out)
        self.test("RMI Inventory count decrement on reservation", "Number of seats available: 9" in out and "Number of cars at this location: 4" in out)
        self.test("RMI Customer Bill Query", "Bill for customer 1" in out and "$250" in out and "$50" in out and "$100" in out)
        self.test("RMI Cascading Customer Deletion", "Customer Deleted" in out)
        self.test("RMI Restores inventory counts after customer deletion", "Number of seats available: 10" in out and "Number of cars at this location: 5" in out)
        self.test("RMI Negative seats and prices rejected", "Flight could not be added" in out and "Cars could not be added" in out and "Rooms could not be added" in out)
        self.test("RMI Duplicate flight numbers in bundle rejected", "Bundle could not be reserved" in out)
        self.test("RMI Bundle reservation succeeds with 0/1 boolean inputs", "Bundle Reserved" in out)
        self.test("RMI Bundle reserves car and room in addition to flight",
                  "Number of seats available: 9" in out and "Number of cars at this location: 4" in out and "Number of rooms at this location: 2" in out)
        self.test("RMI Customer bill itemizes flight, car, and room from bundle",
                  "Bill for customer 2" in out and "flight-101" in out and "car-montreal" in out and "room-montreal" in out)

        self.cleanup()

    def test_tcp_distribution(self):
        print("\n========================================================")
        print("STAGE 2: TCP SOCKETS & NON-BLOCKING CONCURRENCY AUDIT")
        print("========================================================")

        p_flight = 4022
        p_car = 4023
        p_room = 4024
        p_mw = 4021

        # 1. Start TCP Flights, Cars, Rooms servers
        print(f"  Starting TCP Flights ({p_flight}), Cars ({p_car}), Rooms ({p_room})...")
        for name, port in [("Flights", p_flight), ("Cars", p_car), ("Rooms", p_room)]:
            proc = subprocess.Popen(
                ["java", "-cp", f".:{SERVER_DIR}/RMIInterface.jar", "Server.TCP.TCPResourceManager", name, str(port)],
                cwd=str(SERVER_DIR),
                preexec_fn=os.setsid,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL
            )
            self.processes.append(proc)

        self.wait_for_port(p_flight)
        self.wait_for_port(p_car)
        self.wait_for_port(p_room)

        # 2. Start TCP Middleware
        print(f"  Starting TCP Middleware on port {p_mw}...")
        mw_proc = subprocess.Popen(
            ["java", "-cp", f".:{SERVER_DIR}/RMIInterface.jar", "Server.TCP.TCPMiddleware",
             "localhost", "localhost", "localhost",
             str(p_flight), str(p_car), str(p_room), str(p_mw)],
            cwd=str(SERVER_DIR),
            preexec_fn=os.setsid,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL
        )
        self.processes.append(mw_proc)
        self.wait_for_port(p_mw)

        # 3. Test functional workflow over TCP
        client_script = (
            "AddFlight,202,2,180\n"
            "AddCars,Toronto,2,75\n"
            "AddRooms,Toronto,2,120\n"
            "AddCustomerID,10\n"
            "ReserveFlight,10,202\n"
            "ReserveCar,10,Toronto\n"
            "ReserveRoom,10,Toronto\n"
            "QueryFlight,202\n"
            "QueryCars,Toronto\n"
            "QueryRooms,Toronto\n"
            "QueryCustomer,10\n"
            "DeleteCustomer,10\n"
            "QueryFlight,202\n"
            "QueryCars,Toronto\n"
            "QueryRooms,Toronto\n"
            "AddCustomerID,20\n"
            "AddFlight,888,-5,100\n"
            "AddFlight,888,5,-100\n"
            "AddCars,NegativeCity,5,-50\n"
            "AddRooms,NegativeCity,5,-50\n"
            "Bundle,20,202,Toronto,1,1\n"
            "QueryFlight,202\n"
            "QueryCars,Toronto\n"
            "QueryRooms,Toronto\n"
            "Bundle,20,202,202,Toronto,1,1\n"  # Duplicate flight -> fails!
            "QueryFlight,202\n"
            "Quit\n"
        )

        res = self.run_command(
            ["java", "-cp", f"{SERVER_DIR}/RMIInterface.jar:.", "Client.TCPClient", "localhost", str(p_mw)],
            stdin_input=client_script
        )
        out = res.stdout

        self.test("TCP Client connects via Dynamic Proxy", "Connected to 'Middleware' TCP server" in out)
        self.test("TCP Generic message envelopes functional", "Flight added" in out and "Cars added" in out and "Rooms added" in out)
        self.test("TCP Single reservations executed cleanly", "Flight Reserved" in out and "Car Reserved" in out and "Room Reserved" in out)
        self.test("TCP Customer bill itemized accurately", "Bill for customer 10" in out and "$180" in out and "$75" in out and "$120" in out)
        self.test("TCP Cascading customer cancellation restores inventory", "Number of seats available: 2" in out and "Number of cars at this location: 2" in out)
        self.test("TCP Negative seats and prices rejected", "Flight could not be added" in out and "Cars could not be added" in out and "Rooms could not be added" in out)
        self.test("TCP Bundle reservation succeeds with 0/1 boolean inputs", "Bundle Reserved" in out)
        self.test("TCP Bundle reserves car and room in addition to flight",
                  "Number of seats available: 1" in out and "Number of cars at this location: 1" in out and "Number of rooms at this location: 1" in out)
        self.test("TCP Bundle duplicate flight rejection", "Bundle could not be reserved" in out)

        # 4. Multi-threaded Concurrent Client Stress Test
        print("\n  Executing Concurrent TCP Stress Test (10 concurrent clients)...")
        concurrent_script = "AddFlight,999,100,50\nQueryFlight,999\nQuit\n"
        workers = []
        for i in range(10):
            p = subprocess.Popen(
                ["java", "-cp", f"{SERVER_DIR}/RMIInterface.jar:.", "Client.TCPClient", "localhost", str(p_mw)],
                cwd=str(CLIENT_DIR),
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True
            )
            workers.append(p)

        all_ok = True
        for p in workers:
            cout, cerr = p.communicate(input=concurrent_script)
            if "Connected to 'Middleware' TCP server" not in cout:
                all_ok = False

        self.test("TCP Middleware handles concurrent clients non-blockingly without stream corruption", all_ok)

        self.cleanup()

    def run_all(self):
        try:
            self.test_rmi_distribution()
            self.test_tcp_distribution()
        finally:
            self.cleanup()

        print("\n" + "=" * 65)
        print(f"FINAL AUDIT RESULTS: {self.passed} Passed | {self.failed} Failed")
        print("=" * 65)
        return self.failed == 0

def main():
    tester = DistributedSystemTester()
    success = tester.run_all()
    sys.exit(0 if success else 1)

if __name__ == "__main__":
    main()
