package Server.TCP;

import Server.Common.*;
import Server.Interface.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Non-blocking Multi-threaded TCP Middleware Server for COMP 512.
 * Sits between TCP Clients and backend TCP ResourceManagers (Flights, Cars, Rooms).
 * 
 * Architecture:
 * 1. Non-blocking concurrency: Listener loop immediately dispatches client connections
 *    to an executor thread pool; client requests are processed concurrently.
 * 2. Dedicated socket connections: Backend RM calls utilize dynamic TCP proxies with
 *    dedicated per-call sockets, eliminating stream corruption and head-of-line blocking.
 * 3. Centralized Customer Management (Option C): Customer accounts, billing, and active
 *    reservations are maintained natively in Middleware local storage.
 * 4. Cascading Cancellations: Customer deletion cascades compensating cancellations across all RMs.
 * 5. Bundle Atomicity Protocol: Availability pre-checks, sequential reservation, and
 *    LIFO compensating rollback on partial failures.
 */
public class TCPMiddleware extends ResourceManager
{
	private int m_middlewarePort;
	private ServerSocket m_serverSocket;
	private ExecutorService m_clientThreadPool;
	private volatile boolean m_running = true;

	protected IResourceManager m_flightRM = null;
	protected IResourceManager m_carRM = null;
	protected IResourceManager m_roomRM = null;

	public TCPMiddleware(String name, int port)
	{
		super(name);
		this.m_middlewarePort = port;
		this.m_clientThreadPool = Executors.newCachedThreadPool();
	}

	public void setFlightRM(IResourceManager rm)
	{
		this.m_flightRM = rm;
	}

	public void setCarRM(IResourceManager rm)
	{
		this.m_carRM = rm;
	}

	public void setRoomRM(IResourceManager rm)
	{
		this.m_roomRM = rm;
	}

	// -------------------------------------------------------------------------
	// FLIGHT METHODS (delegated to Flights RM)
	// -------------------------------------------------------------------------

	@Override
	public boolean addFlight(int flightNum, int flightSeats, int flightPrice) throws RemoteException
	{
		Trace.info("TCPMiddleware::addFlight(" + flightNum + ", " + flightSeats + ", $" + flightPrice + ") -> Flights RM");
		if (m_flightRM == null) {
			throw new RemoteException("Flights RM not connected");
		}
		return m_flightRM.addFlight(flightNum, flightSeats, flightPrice);
	}

	@Override
	public boolean deleteFlight(int flightNum) throws RemoteException
	{
		Trace.info("TCPMiddleware::deleteFlight(" + flightNum + ") -> Flights RM");
		if (m_flightRM == null) {
			throw new RemoteException("Flights RM not connected");
		}
		return m_flightRM.deleteFlight(flightNum);
	}

	@Override
	public int queryFlight(int flightNum) throws RemoteException
	{
		Trace.info("TCPMiddleware::queryFlight(" + flightNum + ") -> Flights RM");
		if (m_flightRM == null) {
			throw new RemoteException("Flights RM not connected");
		}
		return m_flightRM.queryFlight(flightNum);
	}

	@Override
	public int queryFlightPrice(int flightNum) throws RemoteException
	{
		Trace.info("TCPMiddleware::queryFlightPrice(" + flightNum + ") -> Flights RM");
		if (m_flightRM == null) {
			throw new RemoteException("Flights RM not connected");
		}
		return m_flightRM.queryFlightPrice(flightNum);
	}

	// -------------------------------------------------------------------------
	// CAR METHODS (delegated to Cars RM)
	// -------------------------------------------------------------------------

	@Override
	public boolean addCars(String location, int count, int price) throws RemoteException
	{
		Trace.info("TCPMiddleware::addCars(" + location + ", " + count + ", $" + price + ") -> Cars RM");
		if (m_carRM == null) {
			throw new RemoteException("Cars RM not connected");
		}
		return m_carRM.addCars(location, count, price);
	}

	@Override
	public boolean deleteCars(String location) throws RemoteException
	{
		Trace.info("TCPMiddleware::deleteCars(" + location + ") -> Cars RM");
		if (m_carRM == null) {
			throw new RemoteException("Cars RM not connected");
		}
		return m_carRM.deleteCars(location);
	}

	@Override
	public int queryCars(String location) throws RemoteException
	{
		Trace.info("TCPMiddleware::queryCars(" + location + ") -> Cars RM");
		if (m_carRM == null) {
			throw new RemoteException("Cars RM not connected");
		}
		return m_carRM.queryCars(location);
	}

	@Override
	public int queryCarsPrice(String location) throws RemoteException
	{
		Trace.info("TCPMiddleware::queryCarsPrice(" + location + ") -> Cars RM");
		if (m_carRM == null) {
			throw new RemoteException("Cars RM not connected");
		}
		return m_carRM.queryCarsPrice(location);
	}

	// -------------------------------------------------------------------------
	// ROOM METHODS (delegated to Rooms RM)
	// -------------------------------------------------------------------------

	@Override
	public boolean addRooms(String location, int count, int price) throws RemoteException
	{
		Trace.info("TCPMiddleware::addRooms(" + location + ", " + count + ", $" + price + ") -> Rooms RM");
		if (m_roomRM == null) {
			throw new RemoteException("Rooms RM not connected");
		}
		return m_roomRM.addRooms(location, count, price);
	}

	@Override
	public boolean deleteRooms(String location) throws RemoteException
	{
		Trace.info("TCPMiddleware::deleteRooms(" + location + ") -> Rooms RM");
		if (m_roomRM == null) {
			throw new RemoteException("Rooms RM not connected");
		}
		return m_roomRM.deleteRooms(location);
	}

	@Override
	public int queryRooms(String location) throws RemoteException
	{
		Trace.info("TCPMiddleware::queryRooms(" + location + ") -> Rooms RM");
		if (m_roomRM == null) {
			throw new RemoteException("Rooms RM not connected");
		}
		return m_roomRM.queryRooms(location);
	}

	@Override
	public int queryRoomsPrice(String location) throws RemoteException
	{
		Trace.info("TCPMiddleware::queryRoomsPrice(" + location + ") -> Rooms RM");
		if (m_roomRM == null) {
			throw new RemoteException("Rooms RM not connected");
		}
		return m_roomRM.queryRoomsPrice(location);
	}

	// -------------------------------------------------------------------------
	// CUSTOMER MANAGEMENT (Centralized Option C)
	// -------------------------------------------------------------------------

	@Override
	public int newCustomer() throws RemoteException
	{
		return super.newCustomer();
	}

	@Override
	public boolean newCustomer(int customerID) throws RemoteException
	{
		return super.newCustomer(customerID);
	}

	@Override
	public String queryCustomerInfo(int customerID) throws RemoteException
	{
		return super.queryCustomerInfo(customerID);
	}

	@Override
	public boolean deleteCustomer(int customerID) throws RemoteException
	{
		Trace.info("TCPMiddleware::deleteCustomer(" + customerID + ") called");
		Customer customer = (Customer) readData(Customer.getKey(customerID));
		if (customer == null)
		{
			Trace.warn("TCPMiddleware::deleteCustomer(" + customerID + ") failed--customer doesn't exist");
			return false;
		}

		// Cascading cancellation: dispatch compensating cancellation RPCs to backend ResourceManagers
		if (m_flightRM != null)
		{
			try {
				m_flightRM.deleteCustomer(customerID);
			} catch (Exception e) {
				Trace.warn("TCPMiddleware::deleteCustomer - failed deleteCustomer on Flights RM: " + e.getMessage());
			}
		}

		if (m_carRM != null)
		{
			try {
				m_carRM.deleteCustomer(customerID);
			} catch (Exception e) {
				Trace.warn("TCPMiddleware::deleteCustomer - failed deleteCustomer on Cars RM: " + e.getMessage());
			}
		}

		if (m_roomRM != null)
		{
			try {
				m_roomRM.deleteCustomer(customerID);
			} catch (Exception e) {
				Trace.warn("TCPMiddleware::deleteCustomer - failed deleteCustomer on Rooms RM: " + e.getMessage());
			}
		}

		// Remove the customer from Middleware local storage
		removeData(customer.getKey());
		Trace.info("TCPMiddleware::deleteCustomer(" + customerID + ") succeeded");
		return true;
	}

	// -------------------------------------------------------------------------
	// RESERVATIONS
	// -------------------------------------------------------------------------

	@Override
	public boolean reserveFlight(int customerID, int flightNum) throws RemoteException
	{
		Trace.info("TCPMiddleware::reserveFlight(" + customerID + ", " + flightNum + ") called");
		Customer customer = (Customer) readData(Customer.getKey(customerID));
		if (customer == null)
		{
			Trace.warn("TCPMiddleware::reserveFlight(" + customerID + ", " + flightNum + ") failed--customer doesn't exist");
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
			Trace.warn("TCPMiddleware::reserveFlight(" + customerID + ", " + flightNum + ") failed on Flights RM");
			return false;
		}

		// Retrieve current price and record reservation in local customer record
		int price = m_flightRM.queryFlightPrice(flightNum);
		customer.reserve(Flight.getKey(flightNum), String.valueOf(flightNum), price);
		writeData(customer.getKey(), customer);

		Trace.info("TCPMiddleware::reserveFlight(" + customerID + ", " + flightNum + ") succeeded");
		return true;
	}

	@Override
	public boolean reserveCar(int customerID, String location) throws RemoteException
	{
		Trace.info("TCPMiddleware::reserveCar(" + customerID + ", " + location + ") called");
		Customer customer = (Customer) readData(Customer.getKey(customerID));
		if (customer == null)
		{
			Trace.warn("TCPMiddleware::reserveCar(" + customerID + ", " + location + ") failed--customer doesn't exist");
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
			Trace.warn("TCPMiddleware::reserveCar(" + customerID + ", " + location + ") failed on Cars RM");
			return false;
		}

		// Retrieve current price and record reservation in local customer record
		int price = m_carRM.queryCarsPrice(location);
		customer.reserve(Car.getKey(location), location, price);
		writeData(customer.getKey(), customer);

		Trace.info("TCPMiddleware::reserveCar(" + customerID + ", " + location + ") succeeded");
		return true;
	}

	@Override
	public boolean reserveRoom(int customerID, String location) throws RemoteException
	{
		Trace.info("TCPMiddleware::reserveRoom(" + customerID + ", " + location + ") called");
		Customer customer = (Customer) readData(Customer.getKey(customerID));
		if (customer == null)
		{
			Trace.warn("TCPMiddleware::reserveRoom(" + customerID + ", " + location + ") failed--customer doesn't exist");
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
			Trace.warn("TCPMiddleware::reserveRoom(" + customerID + ", " + location + ") failed on Rooms RM");
			return false;
		}

		// Retrieve current price and record reservation in local customer record
		int price = m_roomRM.queryRoomsPrice(location);
		customer.reserve(Room.getKey(location), location, price);
		writeData(customer.getKey(), customer);

		Trace.info("TCPMiddleware::reserveRoom(" + customerID + ", " + location + ") succeeded");
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
		Trace.info("TCPMiddleware::bundle(" + customerID + ", flights=" + flightNumbers + ", loc=" + location + ", car=" + car + ", room=" + room + ") called");

		// Phase 1: Validate Customer Presence locally
		Customer customer = (Customer) readData(Customer.getKey(customerID));
		if (customer == null)
		{
			Trace.warn("TCPMiddleware::bundle failed--customer " + customerID + " does not exist");
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
				Trace.warn("TCPMiddleware::bundle aborting: flight " + flightNum + " has " + available + " seats, needed " + needed);
				return false;
			}
		}

		if (car)
		{
			int availableCars = m_carRM.queryCars(location);
			if (availableCars < 1)
			{
				Trace.warn("TCPMiddleware::bundle aborting: no cars available at " + location);
				return false;
			}
		}

		if (room)
		{
			int availableRooms = m_roomRM.queryRooms(location);
			if (availableRooms < 1)
			{
				Trace.warn("TCPMiddleware::bundle aborting: no rooms available at " + location);
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
			Trace.warn("TCPMiddleware::bundle encountered reservation failure; triggering LIFO rollback");
			while (!rollbackStack.isEmpty())
			{
				try {
					rollbackStack.pop().undo();
				} catch (Exception e) {
					Trace.error("TCPMiddleware::bundle rollback step error: " + e.getMessage());
				}
			}
			return false;
		}

		Trace.info("TCPMiddleware::bundle committed successfully for customer " + customerID);
		return true;
	}

	private void unreserveFlightInternal(int customerID, int flightNum) throws RemoteException
	{
		Trace.info("TCPMiddleware::unreserveFlightInternal(customer=" + customerID + ", flight=" + flightNum + ")");
		m_flightRM.addFlight(flightNum, 1, 0);

		Customer customer = (Customer) readData(Customer.getKey(customerID));
		if (customer != null)
		{
			customer.unreserve(Flight.getKey(flightNum));
			writeData(customer.getKey(), customer);
		}
	}

	private void unreserveCarInternal(int customerID, String location) throws RemoteException
	{
		Trace.info("TCPMiddleware::unreserveCarInternal(customer=" + customerID + ", location=" + location + ")");
		m_carRM.addCars(location, 1, 0);

		Customer customer = (Customer) readData(Customer.getKey(customerID));
		if (customer != null)
		{
			customer.unreserve(Car.getKey(location));
			writeData(customer.getKey(), customer);
		}
	}

	private void unreserveRoomInternal(int customerID, String location) throws RemoteException
	{
		Trace.info("TCPMiddleware::unreserveRoomInternal(customer=" + customerID + ", location=" + location + ")");
		m_roomRM.addRooms(location, 1, 0);

		Customer customer = (Customer) readData(Customer.getKey(customerID));
		if (customer != null)
		{
			customer.unreserve(Room.getKey(location));
			writeData(customer.getKey(), customer);
		}
	}

	// -------------------------------------------------------------------------
	// TCP SERVER LISTENER & REQUEST DISPATCHER (Non-blocking)
	// -------------------------------------------------------------------------

	public void start()
	{
		try {
			m_serverSocket = new ServerSocket(m_middlewarePort);
			Trace.info("TCPMiddleware listening on port " + m_middlewarePort);
			System.out.println("'Middleware' TCP server ready on port " + m_middlewarePort);

			// Register JVM shutdown hook
			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					TCPMiddleware.this.stop();
				}
			});

			// Listener loop does NOT block request processing
			while (m_running)
			{
				try {
					Socket clientSocket = m_serverSocket.accept();
					clientSocket.setTcpNoDelay(true);
					m_clientThreadPool.execute(new ClientHandler(clientSocket));
				}
				catch (SocketException se) {
					if (!m_running) break;
					Trace.warn("SocketException in TCPMiddleware accept: " + se.getMessage());
				}
			}
		}
		catch (IOException e) {
			if (m_running) {
				System.err.println("TCPMiddleware server error: " + e.getMessage());
				e.printStackTrace();
			}
		}
		finally {
			stop();
		}
	}

	public void stop()
	{
		m_running = false;
		try {
			if (m_serverSocket != null && !m_serverSocket.isClosed()) {
				m_serverSocket.close();
			}
		} catch (IOException ignored) {}

		if (m_clientThreadPool != null && !m_clientThreadPool.isShutdown()) {
			m_clientThreadPool.shutdown();
			try {
				if (!m_clientThreadPool.awaitTermination(3, TimeUnit.SECONDS)) {
					m_clientThreadPool.shutdownNow();
				}
			} catch (InterruptedException e) {
				m_clientThreadPool.shutdownNow();
			}
		}
		System.out.println("'Middleware' TCP server stopped cleanly");
	}

	private class ClientHandler implements Runnable
	{
		private Socket m_socket;

		public ClientHandler(Socket socket)
		{
			this.m_socket = socket;
		}

		@Override
		public void run()
		{
			try (
				ObjectInputStream ois = new ObjectInputStream(m_socket.getInputStream());
				ObjectOutputStream oos = new ObjectOutputStream(m_socket.getOutputStream())
			) {
				oos.flush();
				Object inputObj = ois.readObject();

				if (inputObj instanceof TCPMessage)
				{
					TCPMessage message = (TCPMessage) inputObj;
					TCPResponse response = dispatch(message);
					oos.writeObject(response);
					oos.flush();
				}
				else
				{
					Trace.warn("TCPMiddleware received unexpected message: " + inputObj);
				}
			}
			catch (EOFException ignored) {}
			catch (Exception e) {
				Trace.warn("Error handling client TCP request on Middleware: " + e.getMessage());
			}
			finally {
				try {
					if (m_socket != null && !m_socket.isClosed()) {
						m_socket.close();
					}
				} catch (IOException ignored) {}
			}
		}

		private TCPResponse dispatch(TCPMessage msg)
		{
			String methodName = msg.getMethodName();
			Class<?>[] paramTypes = msg.getParamTypes();
			Object[] args = msg.getArgs();

			try {
				java.lang.reflect.Method method = TCPMiddleware.this.getClass().getMethod(methodName, paramTypes);
				Object result = method.invoke(TCPMiddleware.this, args);
				return new TCPResponse(msg.getId(), result);
			}
			catch (java.lang.reflect.InvocationTargetException ite) {
				Throwable cause = ite.getCause();
				Exception ex = (cause instanceof Exception) ? (Exception) cause : new Exception(cause);
				return new TCPResponse(msg.getId(), null, ex);
			}
			catch (Exception e) {
				return new TCPResponse(msg.getId(), null, e);
			}
		}
	}

	// -------------------------------------------------------------------------
	// MAIN ENTRY POINT
	// -------------------------------------------------------------------------

	public static void main(String args[])
	{
		String flightHost = "localhost";
		String carHost = "localhost";
		String roomHost = "localhost";

		int flightPort = 3022;
		int carPort = 3023;
		int roomPort = 3024;
		int middlewarePort = 3021;

		if (args.length >= 1) flightHost = args[0];
		if (args.length >= 2) carHost = args[1];
		if (args.length >= 3) roomHost = args[2];

		if (args.length >= 4) flightPort = Integer.parseInt(args[3]);
		if (args.length >= 5) carPort = Integer.parseInt(args[4]);
		if (args.length >= 6) roomPort = Integer.parseInt(args[5]);
		if (args.length >= 7) middlewarePort = Integer.parseInt(args[6]);

		try {
			TCPMiddleware middleware = new TCPMiddleware("Middleware", middlewarePort);

			// Connect dynamic TCP proxies to backend ResourceManagers
			middleware.setFlightRM(connectRM(flightHost, flightPort, "Flights"));
			middleware.setCarRM(connectRM(carHost, carPort, "Cars"));
			middleware.setRoomRM(connectRM(roomHost, roomPort, "Rooms"));

			// Start multi-threaded non-blocking TCP server
			middleware.start();
		}
		catch (Exception e) {
			System.err.println("TCPMiddleware startup failed: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
	}

	private static IResourceManager connectRM(String host, int port, String name)
	{
		boolean first = true;
		int attempts = 0;
		int maxAttempts = 30; // 15 seconds max wait
		while (attempts < maxAttempts)
		{
			attempts++;
			try {
				IResourceManager proxy = TCPProxy.create(host, port, IResourceManager.class);
				// Probe connectivity
				proxy.getName();
				System.out.println("Connected to '" + name + "' TCP server [" + host + ":" + port + "]");
				return proxy;
			}
			catch (Exception e) {
				if (first) {
					System.out.println("Waiting for '" + name + "' TCP server [" + host + ":" + port + "]...");
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
		throw new RuntimeException("Timed out waiting to connect to '" + name + "' TCP server on " + host + ":" + port);
	}
}
