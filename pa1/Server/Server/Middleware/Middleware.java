package Server.Middleware;

import Server.Common.Customer;
import Server.Common.RMHashMap;
import Server.Interface.IResourceManager;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Calendar;
import java.util.Vector;

public class Middleware implements IResourceManager {

    // Remote interface stubs for backend RMs
    private static IResourceManager flight_RM = null;
    private static IResourceManager car_RM = null;
    private static IResourceManager room_RM = null;

    // customer hashmap
    protected RMHashMap m_customers = new RMHashMap();

    // server config
    private static final String s_serverName = "Middleware";
    private static final String s_flightRMName = "Flight";
    private static final String s_carRMName = "Car";
    private static final String s_roomRMName = "Room";
    private static int s_rmiPort = 3021;
    private static String s_rmiPrefix = "group_21_";

    public Middleware() {
        super();
    }

    public static void main(String args[]) {
        if (args.length < 3) {
            System.err.println("Usage: java Middleware <flightRM> <carRM> <roomRM> [port]");
            System.exit(1);
        }

        String flighthost = args[0];
        String carhost = args[1];
        String roomhost = args[2];

        if (args.length >= 4) {
            try {
                s_rmiPort = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[3]);
                System.exit(1);
            }
        }

        try {
            // establish RM instances
            flight_RM = connectRM(flighthost, s_rmiPort, s_flightRMName);
            car_RM = connectRM(carhost, s_rmiPort, s_carRMName);
            room_RM = connectRM(roomhost, s_rmiPort, s_roomRMName);

            Middleware middleware = new Middleware();
            IResourceManager stub = (IResourceManager) UnicastRemoteObject.exportObject(middleware, 0);

            // bind to rmi registry
            Registry registry;
            try {
                registry = LocateRegistry.getRegistry(s_rmiPort);
                registry.list();
            } catch (RemoteException e) {
                registry = LocateRegistry.createRegistry(s_rmiPort);
            }

            String bindName = s_rmiPrefix + s_serverName;
            registry.rebind(bindName, stub);
            System.out.println("'" + bindName + "' middleware server ready and bound.");

        } catch (Exception e) {
            System.err.println("Middleware exception: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static IResourceManager connectRM(String server, int port, String name) {
        try {
            boolean first = true;
            while (true) {
                try {
                    Registry registry = LocateRegistry.getRegistry(server, port);
                    IResourceManager rm = (IResourceManager) registry.lookup(s_rmiPrefix + name);
                    System.out.println("Connected to '" + name + "' server [" + server + ":" + port + "/" + s_rmiPrefix + name + "]");
                    return rm;
                } catch (NotBoundException | RemoteException e) {
                    if (first) {
                        System.out.println("Waiting for '" + name + "' server [" + server + ":" + port + "/" + s_rmiPrefix + name + "]");
                        first = false;
                    }
                }
                Thread.sleep(500);
            }
        } catch (Exception e) {
            System.err.println((char) 27 + "[31;1mServer exception: " + (char) 27 + "[0mUncaught exception");
            e.printStackTrace();
            System.exit(1);
        }
        return null;
    }

    //--FLIGHTS--

    @Override
    public boolean addFlight(int id, int flightNum, int flightSeats, int flightPrice) throws RemoteException {
        return flight_RM.addFlight(id, flightNum, flightSeats, flightPrice);
    }

    @Override
    public boolean deleteFlight(int id, int flightNum) throws RemoteException {
        return flight_RM.deleteFlight(id, flightNum);
    }

    @Override
    public int queryFlight(int id, int flightNumber) throws RemoteException {
        return flight_RM.queryFlight(id, flightNumber);
    }

    @Override
    public int queryFlightPrice(int id, int flightNumber) throws RemoteException {
        return flight_RM.queryFlightPrice(id, flightNumber);
    }

    // --CARS--

    @Override
    public boolean addCars(int id, String location, int numCars, int price) throws RemoteException {
        return car_RM.addCars(id, location, numCars, price);
    }

    @Override
    public boolean deleteCars(int id, String location) throws RemoteException {
        return car_RM.deleteCars(id, location);
    }

    @Override
    public int queryCars(int id, String location) throws RemoteException {
        return car_RM.queryCars(id, location);
    }

    @Override
    public int queryCarsPrice(int id, String location) throws RemoteException {
        return car_RM.queryCarsPrice(id, location);
    }

    //--ROOMS--

    @Override
    public boolean addRooms(int id, String location, int numRooms, int price) throws RemoteException {
        return room_RM.addRooms(id, location, numRooms, price);
    }

    @Override
    public boolean deleteRooms(int id, String location) throws RemoteException {
        return room_RM.deleteRooms(id, location);
    }

    @Override
    public int queryRooms(int id, String location) throws RemoteException {
        return room_RM.queryRooms(id, location);
    }

    @Override
    public int queryRoomsPrice(int id, String location) throws RemoteException {
        return room_RM.queryRoomsPrice(id, location);
    }

    // --CUSTOMERS ---

    @Override
    public int newCustomer(int id) throws RemoteException {
        int cid = Integer.parseInt(String.valueOf(id) +
                String.valueOf(Calendar.getInstance().get(Calendar.MILLISECOND)) +
                String.valueOf((int) (Math.random() * 100));
        Customer customer = new Customer(cid);
        synchronized(m_customers) {
            m_customers.put(customer.getKey(), customer);
        }
        System.out.println("Middleware created new customer with ID: " + cid);
        return cid;
    }

    @Override
    public boolean newCustomer(int id, int cid) throws RemoteException {
        synchronized(m_customers) {
            Customer customer = (Customer) m_customers.get(Customer.getKey(cid));
            if (customer != null) {
                return false;
            }
            customer = new Customer(cid);
            m_customers.put(customer.getKey(), customer);
            System.out.println("Middleware created new customer with ID: " + cid);
            return true;
        }
    }

    @Override
    public boolean deleteCustomer(int id, int customerID) throws RemoteException {
        Customer customer;
        synchronized(m_customers) {
            customer = (Customer) m_customers.get(Customer.getKey(customerID));
            if (customer == null) {
                return false;
            }
            // Remove customer local hashmap
            m_customers.remove(customer.getKey());
        }

        // unreserve all items reserved by this customer across all the rms
        RMHashMap reservations = customer.getReservations();
        for (Object reservedKey : reservations.keySet()) {
            String key = (String) reservedKey;
            RMItem item = (RMItem) reservations.get(key);

            // direct items on appropriate RM backend based on item key type
            if (key.startsWith("flight-")) {
                flight_RM.removeReservation(id, customerID, item.getKey(), item.getCount());
            } else if (key.startsWith("car-")) {
                car_RM.removeReservation(id, customerID, item.getKey(), item.getCount());
            } else if (key.startsWith("room-")) {
                room_RM.removeReservation(id, customerID, item.getKey(), item.getCount());
            }
        }
        return true;
    }

    @Override
    public String queryCustomerInfo(int id, int customerID) throws RemoteException {
        Customer customer;
        synchronized(m_customers) {
            customer = (Customer) m_customers.get(Customer.getKey(customerID));
        }
        if (customer == null) {
            return "";
        }
        return customer.printBill();
    }

    // --- RESERVATIONS ---

    @Override
    public boolean reserveFlight(int id, int customerID, int flightNumber) throws RemoteException {
        Customer customer;
        synchronized(m_customers) {
            customer = (Customer) m_customers.get(Customer.getKey(customerID));
        }
        if (customer == null) {
            return false;
        }

        // delegate seat reservation to flight RM
        boolean success = flight_RM.reserveFlight(id, customerID, flightNumber);
        if (success) {
            int price = flight_RM.queryFlightPrice(id, flightNumber);
            synchronized(m_customers) {
                customer.reserve(Flight.getKey(flightNumber), String.valueOf(flightNumber), price);
            }
        }
        return success;
    }

    @Override
    public boolean reserveCar(int id, int customerID, String location) throws RemoteException {
        Customer customer;
        synchronized(m_customers) {
            customer = (Customer) m_customers.get(Customer.getKey(customerID));
        }
        if (customer == null) {
            return false;
        }

        boolean success = car_RM.reserveCar(id, customerID, location);
        if (success) {
            int price = car_RM.queryCarsPrice(id, location);
            synchronized(m_customers) {
                customer.reserve(Car.getKey(location), location, price);
            }
        }
        return success;
    }

    @Override
    public boolean reserveRoom(int id, int customerID, String location) throws RemoteException {
        Customer customer;
        synchronized(m_customers) {
            customer = (Customer) m_customers.get(Customer.getKey(customerID));
        }
        if (customer == null) {
            return false;
        }

        boolean success = room_RM.reserveRoom(id, customerID, location);
        if (success) {
            int price = room_RM.queryRoomsPrice(id, location);
            synchronized(m_customers) {
                customer.reserve(Room.getKey(location), location, price);
            }
        }
        return success;
    }

    // --- BUNDLE ---

    @Override
    public boolean bundle(int id, int customerID, Vector<String> flightNumbers, String location, boolean car, boolean room) throws RemoteException {
        Customer customer;
        synchronized(m_customers) {
            customer = (Customer) m_customers.get(Customer.getKey(customerID));
        }
        if (customer == null) {
            return false;
        }

        // reserve flights
        for (String flightStr : flightNumbers) {
            int flightNum = Integer.parseInt(flightStr);
            if (!reserveFlight(id, customerID, flightNum)) {
                return false;
            }
        }

        //  Reserve car if requested
        if (car) {
            if (!reserveCar(id, customerID, location)) {
                return false;
            }
        }

        // Reserve room if requested
        if (room) {
            if (!reserveRoom(id, customerID, location)) {
                return false;
            }
        }

        return true;
    }
}