package Server.RMI;

import Server.Interface.*;
import Server.Common.*;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

public class RMIMiddleware extends ResourceManager
{
	private static final String s_serverName = "Middleware";
	private static final String s_rmiPrefix = "group_21_";

	protected IResourceManager m_flightRM = null;
	protected IResourceManager m_carRM = null;
	protected IResourceManager m_roomRM = null;

	public RMIMiddleware(String name)
	{
		super(name);
	}

	public void setFlightRM(IResourceManager rm)
	{
		m_flightRM = rm;
	}

	public void setCarRM(IResourceManager rm)
	{
		m_carRM = rm;
	}

	public void setRoomRM(IResourceManager rm)
	{
		m_roomRM = rm;
	}

	// -------------------------------------------------------------------------
	// FLIGHT METHODS (routed to Flights RM)
	// -------------------------------------------------------------------------

	@Override
	public boolean addFlight(int flightNum, int flightSeats, int flightPrice) throws RemoteException
	{
		Trace.info("RMIMiddleware::addFlight(" + flightNum + ", " + flightSeats + ", $" + flightPrice + ") -> Flights RM");
		if (m_flightRM == null) {
			throw new RemoteException("Flights RM not connected");
		}
		return m_flightRM.addFlight(flightNum, flightSeats, flightPrice);
	}

	@Override
	public boolean deleteFlight(int flightNum) throws RemoteException
	{
		Trace.info("RMIMiddleware::deleteFlight(" + flightNum + ") -> Flights RM");
		if (m_flightRM == null) {
			throw new RemoteException("Flights RM not connected");
		}
		return m_flightRM.deleteFlight(flightNum);
	}

	@Override
	public int queryFlight(int flightNum) throws RemoteException
	{
		Trace.info("RMIMiddleware::queryFlight(" + flightNum + ") -> Flights RM");
		if (m_flightRM == null) {
			throw new RemoteException("Flights RM not connected");
		}
		return m_flightRM.queryFlight(flightNum);
	}

	@Override
	public int queryFlightPrice(int flightNum) throws RemoteException
	{
		Trace.info("RMIMiddleware::queryFlightPrice(" + flightNum + ") -> Flights RM");
		if (m_flightRM == null) {
			throw new RemoteException("Flights RM not connected");
		}
		return m_flightRM.queryFlightPrice(flightNum);
	}

	// -------------------------------------------------------------------------
	// CAR METHODS (routed to Cars RM)
	// -------------------------------------------------------------------------

	@Override
	public boolean addCars(String location, int count, int price) throws RemoteException
	{
		Trace.info("RMIMiddleware::addCars(" + location + ", " + count + ", $" + price + ") -> Cars RM");
		if (m_carRM == null) {
			throw new RemoteException("Cars RM not connected");
		}
		return m_carRM.addCars(location, count, price);
	}

	@Override
	public boolean deleteCars(String location) throws RemoteException
	{
		Trace.info("RMIMiddleware::deleteCars(" + location + ") -> Cars RM");
		if (m_carRM == null) {
			throw new RemoteException("Cars RM not connected");
		}
		return m_carRM.deleteCars(location);
	}

	@Override
	public int queryCars(String location) throws RemoteException
	{
		Trace.info("RMIMiddleware::queryCars(" + location + ") -> Cars RM");
		if (m_carRM == null) {
			throw new RemoteException("Cars RM not connected");
		}
		return m_carRM.queryCars(location);
	}

	@Override
	public int queryCarsPrice(String location) throws RemoteException
	{
		Trace.info("RMIMiddleware::queryCarsPrice(" + location + ") -> Cars RM");
		if (m_carRM == null) {
			throw new RemoteException("Cars RM not connected");
		}
		return m_carRM.queryCarsPrice(location);
	}

	// -------------------------------------------------------------------------
	// ROOM METHODS (routed to Rooms RM)
	// -------------------------------------------------------------------------

	@Override
	public boolean addRooms(String location, int count, int price) throws RemoteException
	{
		Trace.info("RMIMiddleware::addRooms(" + location + ", " + count + ", $" + price + ") -> Rooms RM");
		if (m_roomRM == null) {
			throw new RemoteException("Rooms RM not connected");
		}
		return m_roomRM.addRooms(location, count, price);
	}

	@Override
	public boolean deleteRooms(String location) throws RemoteException
	{
		Trace.info("RMIMiddleware::deleteRooms(" + location + ") -> Rooms RM");
		if (m_roomRM == null) {
			throw new RemoteException("Rooms RM not connected");
		}
		return m_roomRM.deleteRooms(location);
	}

	@Override
	public int queryRooms(String location) throws RemoteException
	{
		Trace.info("RMIMiddleware::queryRooms(" + location + ") -> Rooms RM");
		if (m_roomRM == null) {
			throw new RemoteException("Rooms RM not connected");
		}
		return m_roomRM.queryRooms(location);
	}

	@Override
	public int queryRoomsPrice(String location) throws RemoteException
	{
		Trace.info("RMIMiddleware::queryRoomsPrice(" + location + ") -> Rooms RM");
		if (m_roomRM == null) {
			throw new RemoteException("Rooms RM not connected");
		}
		return m_roomRM.queryRoomsPrice(location);
	}

	// -------------------------------------------------------------------------
	// CUSTOMER MANAGEMENT (Centralized Option C - Middleware is single source of truth)
	// -------------------------------------------------------------------------

	@Override
	public int newCustomer() throws RemoteException
	{
		// Resolved locally on Middleware without network RPC
		return super.newCustomer();
	}

	@Override
	public boolean newCustomer(int customerID) throws RemoteException
	{
		// Resolved locally on Middleware without network RPC
		return super.newCustomer(customerID);
	}

	@Override
	public String queryCustomerInfo(int customerID) throws RemoteException
	{
		// Computed and formatted locally from Middleware Customer record
		return super.queryCustomerInfo(customerID);
	}

	@Override
	public boolean deleteCustomer(int customerID) throws RemoteException
	{
		Trace.info("RMIMiddleware::deleteCustomer(" + customerID + ") called");
		Customer customer = (Customer) readData(Customer.getKey(customerID));
		if (customer == null)
		{
			Trace.warn("RMIMiddleware::deleteCustomer(" + customerID + ") failed--customer doesn't exist");
			return false;
		}

		// Cascading cancellation: dispatch compensating cancellation RPCs to backend ResourceManagers
		if (m_flightRM != null)
		{
			try {
				m_flightRM.deleteCustomer(customerID);
			} catch (Exception e) {
				Trace.warn("RMIMiddleware::deleteCustomer - failed deleteCustomer on Flights RM: " + e.getMessage());
			}
		}

		if (m_carRM != null)
		{
			try {
				m_carRM.deleteCustomer(customerID);
			} catch (Exception e) {
				Trace.warn("RMIMiddleware::deleteCustomer - failed deleteCustomer on Cars RM: " + e.getMessage());
			}
		}

		if (m_roomRM != null)
		{
			try {
				m_roomRM.deleteCustomer(customerID);
			} catch (Exception e) {
				Trace.warn("RMIMiddleware::deleteCustomer - failed deleteCustomer on Rooms RM: " + e.getMessage());
			}
		}

		// Remove the customer from Middleware local storage
		removeData(customer.getKey());
		Trace.info("RMIMiddleware::deleteCustomer(" + customerID + ") succeeded locally and across RMs");
		return true;
	}

	// -------------------------------------------------------------------------
	// RESERVATIONS (Middleware coordinates with backend RMs and updates local bill)
	// -------------------------------------------------------------------------

	@Override
	public boolean reserveFlight(int customerID, int flightNum) throws RemoteException
	{
		Trace.info("RMIMiddleware::reserveFlight(" + customerID + ", " + flightNum + ") called");
		Customer customer = (Customer) readData(Customer.getKey(customerID));
		if (customer == null)
		{
			Trace.warn("RMIMiddleware::reserveFlight(" + customerID + ", " + flightNum + ") failed--customer doesn't exist");
			return false;
		}

		if (m_flightRM == null) {
			throw new RemoteException("Flights RM not connected");
		}

		// Ensure customer exists on backend Flights RM
		try {
			m_flightRM.newCustomer(customerID);
		} catch (Exception ignored) {}

		// Reserve on backend Flights RM
		boolean reserved = m_flightRM.reserveFlight(customerID, flightNum);
		if (!reserved)
		{
			Trace.warn("RMIMiddleware::reserveFlight(" + customerID + ", " + flightNum + ") failed on Flights RM");
			return false;
		}

		// Retrieve current price and record reservation in local customer record
		int price = m_flightRM.queryFlightPrice(flightNum);
		customer.reserve(Flight.getKey(flightNum), String.valueOf(flightNum), price);
		writeData(customer.getKey(), customer);

		Trace.info("RMIMiddleware::reserveFlight(" + customerID + ", " + flightNum + ") succeeded");
		return true;
	}

	@Override
	public boolean reserveCar(int customerID, String location) throws RemoteException
	{
		Trace.info("RMIMiddleware::reserveCar(" + customerID + ", " + location + ") called");
		Customer customer = (Customer) readData(Customer.getKey(customerID));
		if (customer == null)
		{
			Trace.warn("RMIMiddleware::reserveCar(" + customerID + ", " + location + ") failed--customer doesn't exist");
			return false;
		}

		if (m_carRM == null) {
			throw new RemoteException("Cars RM not connected");
		}

		// Ensure customer exists on backend Cars RM
		try {
			m_carRM.newCustomer(customerID);
		} catch (Exception ignored) {}

		// Reserve on backend Cars RM
		boolean reserved = m_carRM.reserveCar(customerID, location);
		if (!reserved)
		{
			Trace.warn("RMIMiddleware::reserveCar(" + customerID + ", " + location + ") failed on Cars RM");
			return false;
		}

		// Retrieve current price and record reservation in local customer record
		int price = m_carRM.queryCarsPrice(location);
		customer.reserve(Car.getKey(location), location, price);
		writeData(customer.getKey(), customer);

		Trace.info("RMIMiddleware::reserveCar(" + customerID + ", " + location + ") succeeded");
		return true;
	}

	@Override
	public boolean reserveRoom(int customerID, String location) throws RemoteException
	{
		Trace.info("RMIMiddleware::reserveRoom(" + customerID + ", " + location + ") called");
		Customer customer = (Customer) readData(Customer.getKey(customerID));
		if (customer == null)
		{
			Trace.warn("RMIMiddleware::reserveRoom(" + customerID + ", " + location + ") failed--customer doesn't exist");
			return false;
		}

		if (m_roomRM == null) {
			throw new RemoteException("Rooms RM not connected");
		}

		// Ensure customer exists on backend Rooms RM
		try {
			m_roomRM.newCustomer(customerID);
		} catch (Exception ignored) {}

		// Reserve on backend Rooms RM
		boolean reserved = m_roomRM.reserveRoom(customerID, location);
		if (!reserved)
		{
			Trace.warn("RMIMiddleware::reserveRoom(" + customerID + ", " + location + ") failed on Rooms RM");
			return false;
		}

		// Retrieve current price and record reservation in local customer record
		int price = m_roomRM.queryRoomsPrice(location);
		customer.reserve(Room.getKey(location), location, price);
		writeData(customer.getKey(), customer);

		Trace.info("RMIMiddleware::reserveRoom(" + customerID + ", " + location + ") succeeded");
		return true;
	}

	// -------------------------------------------------------------------------
	// BUNDLE TRANSACTION ATOMICITY PROTOCOL
	// -------------------------------------------------------------------------

	private interface RollbackAction
	{
		void undo() throws RemoteException;
	}

	@Override
	public boolean bundle(int customerID, Vector<String> flightNumbers, String location, boolean car, boolean room) throws RemoteException
	{
		Trace.info("RMIMiddleware::bundle(" + customerID + ", flights=" + flightNumbers + ", loc=" + location + ", car=" + car + ", room=" + room + ") called");

		// Phase 1: Validate Customer Presence locally
		Customer customer = (Customer) readData(Customer.getKey(customerID));
		if (customer == null)
		{
			Trace.warn("RMIMiddleware::bundle failed--customer " + customerID + " does not exist");
			return false;
		}

		if (m_flightRM == null || m_carRM == null || m_roomRM == null)
		{
			throw new RemoteException("One or more backend ResourceManagers not connected");
		}

		// Phase 2: Availability Pre-check across all requested resources
		Map<Integer, Integer> flightFrequencies = new HashMap<Integer, Integer>();
		for (String flightStr : flightNumbers)
		{
			int fn = Integer.parseInt(flightStr);
			Integer count = flightFrequencies.get(fn);
			flightFrequencies.put(fn, (count == null ? 1 : count + 1));
		}

		for (Map.Entry<Integer, Integer> entry : flightFrequencies.entrySet())
		{
			int flightNum = entry.getKey();
			int needed = entry.getValue();
			int available = m_flightRM.queryFlight(flightNum);
			if (available < needed)
			{
				Trace.warn("RMIMiddleware::bundle aborting: flight " + flightNum + " has " + available + " seats, needed " + needed);
				return false;
			}
		}

		if (car)
		{
			int availableCars = m_carRM.queryCars(location);
			if (availableCars < 1)
			{
				Trace.warn("RMIMiddleware::bundle aborting: no cars available at " + location);
				return false;
			}
		}

		if (room)
		{
			int availableRooms = m_roomRM.queryRooms(location);
			if (availableRooms < 1)
			{
				Trace.warn("RMIMiddleware::bundle aborting: no rooms available at " + location);
				return false;
			}
		}

		// Phase 3: Sequential Reservation with LIFO Compensating Rollback Stack
		Stack<RollbackAction> rollbackStack = new Stack<RollbackAction>();
		boolean reservationSuccess = true;

		// 3.1 Reserve Flights
		for (String flightStr : flightNumbers)
		{
			final int fn = Integer.parseInt(flightStr);
			boolean ok = reserveFlight(customerID, fn);
			if (ok)
			{
				rollbackStack.push(new RollbackAction() {
					@Override
					public void undo() throws RemoteException {
						unreserveFlightInternal(customerID, fn);
					}
				});
			}
			else
			{
				reservationSuccess = false;
				break;
			}
		}

		// 3.2 Reserve Car
		if (reservationSuccess && car)
		{
			boolean ok = reserveCar(customerID, location);
			if (ok)
			{
				rollbackStack.push(new RollbackAction() {
					@Override
					public void undo() throws RemoteException {
						unreserveCarInternal(customerID, location);
					}
				});
			}
			else
			{
				reservationSuccess = false;
			}
		}

		// 3.3 Reserve Room
		if (reservationSuccess && room)
		{
			boolean ok = reserveRoom(customerID, location);
			if (ok)
			{
				rollbackStack.push(new RollbackAction() {
					@Override
					public void undo() throws RemoteException {
						unreserveRoomInternal(customerID, location);
					}
				});
			}
			else
			{
				reservationSuccess = false;
			}
		}

		// Phase 4: Atomic Commit or Compensating Rollback
		if (!reservationSuccess)
		{
			Trace.warn("RMIMiddleware::bundle encountered reservation failure; triggering LIFO rollback");
			while (!rollbackStack.isEmpty())
			{
				try {
					rollbackStack.pop().undo();
				} catch (Exception e) {
					Trace.error("RMIMiddleware::bundle rollback step error: " + e.getMessage());
				}
			}
			return false;
		}

		Trace.info("RMIMiddleware::bundle committed successfully for customer " + customerID);
		return true;
	}

	private void unreserveFlightInternal(int customerID, int flightNum) throws RemoteException
	{
		Trace.info("RMIMiddleware::unreserveFlightInternal(customer=" + customerID + ", flight=" + flightNum + ")");
		// Restore available seats on backend RM
		m_flightRM.addFlight(flightNum, 1, 0);

		// Rollback reservation in Middleware customer record
		Customer customer = (Customer) readData(Customer.getKey(customerID));
		if (customer != null)
		{
			customer.unreserve(Flight.getKey(flightNum));
			writeData(customer.getKey(), customer);
		}
	}

	private void unreserveCarInternal(int customerID, String location) throws RemoteException
	{
		Trace.info("RMIMiddleware::unreserveCarInternal(customer=" + customerID + ", location=" + location + ")");
		// Restore available cars on backend RM
		m_carRM.addCars(location, 1, 0);

		// Rollback reservation in Middleware customer record
		Customer customer = (Customer) readData(Customer.getKey(customerID));
		if (customer != null)
		{
			customer.unreserve(Car.getKey(location));
			writeData(customer.getKey(), customer);
		}
	}

	private void unreserveRoomInternal(int customerID, String location) throws RemoteException
	{
		Trace.info("RMIMiddleware::unreserveRoomInternal(customer=" + customerID + ", location=" + location + ")");
		// Restore available rooms on backend RM
		m_roomRM.addRooms(location, 1, 0);

		// Rollback reservation in Middleware customer record
		Customer customer = (Customer) readData(Customer.getKey(customerID));
		if (customer != null)
		{
			customer.unreserve(Room.getKey(location));
			writeData(customer.getKey(), customer);
		}
	}

	// -------------------------------------------------------------------------
	// BOOTSTRAP & DYNAMIC BINDING
	// -------------------------------------------------------------------------

	public static void main(String args[])
	{
		String flightHost = "localhost";
		String carHost = "localhost";
		String roomHost = "localhost";

		int flightPort = 1099;
		int carPort = 1099;
		int roomPort = 1099;
		int middlewarePort = 1099;

		if (args.length >= 1) flightHost = args[0];
		if (args.length >= 2) carHost = args[1];
		if (args.length >= 3) roomHost = args[2];

		if (args.length >= 4) flightPort = Integer.parseInt(args[3]);
		if (args.length >= 5) carPort = Integer.parseInt(args[4]);
		if (args.length >= 6) roomPort = Integer.parseInt(args[5]);
		if (args.length >= 7) middlewarePort = Integer.parseInt(args[6]);

		try {
			RMIMiddleware middleware = new RMIMiddleware(s_serverName);

			// Connect to Flights RM
			middleware.setFlightRM(connectRM(flightHost, flightPort, "Flights"));
			// Connect to Cars RM
			middleware.setCarRM(connectRM(carHost, carPort, "Cars"));
			// Connect to Rooms RM
			middleware.setRoomRM(connectRM(roomHost, roomPort, "Rooms"));

			// Export Middleware RMI stub
			IResourceManager stub = (IResourceManager) UnicastRemoteObject.exportObject(middleware, 0);

			// Locate or create local RMI registry
			Registry l_registry;
			try {
				l_registry = LocateRegistry.createRegistry(middlewarePort);
			} catch (RemoteException e) {
				l_registry = LocateRegistry.getRegistry(middlewarePort);
			}
			final Registry registry = l_registry;
			final String bindName = s_rmiPrefix + s_serverName;
			registry.rebind(bindName, stub);

			// Attach JVM shutdown hook for graceful unbind
			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					try {
						registry.unbind(bindName);
						System.out.println("'" + bindName + "' unbound from RMI registry");
					} catch (Exception e) {
						System.err.println("Middleware unbind exception: " + e.getMessage());
					}
				}
			});

			System.out.println("'" + s_serverName + "' resource manager server ready and bound to '" + bindName + "' on port " + middlewarePort);
		}
		catch (Exception e) {
			System.err.println("RMIMiddleware initialization failed: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
	}

	private static IResourceManager connectRM(String host, int port, String name)
	{
		String fullName = s_rmiPrefix + name;
		boolean first = true;
		int attempts = 0;
		int maxAttempts = 30; // 15 seconds max wait
		while (attempts < maxAttempts)
		{
			attempts++;
			try {
				Registry registry = LocateRegistry.getRegistry(host, port);
				IResourceManager rm = (IResourceManager) registry.lookup(fullName);
				System.out.println("Connected to '" + name + "' server [" + host + ":" + port + "/" + fullName + "]");
				return rm;
			}
			catch (NotBoundException | RemoteException e) {
				if (first) {
					System.out.println("Waiting for '" + name + "' server [" + host + ":" + port + "/" + fullName + "]...");
					first = false;
				}
				try {
					Thread.sleep(500);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}
		throw new RuntimeException("Timed out waiting to connect to '" + name + "' on " + host + ":" + port);
	}
}
