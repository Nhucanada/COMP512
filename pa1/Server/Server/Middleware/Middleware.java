package Server.Middleware;

import Server.Common.ResourceManager;
import Server.Interface.IResourceManager;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Vector;

public class Middleware implements IResourceManager {

    private static IResourceManager m_resourceManager;

    //rms
    private static ResourceManager flight_RM;
    private static ResourceManager car_RM;
    private static ResourceManager room_RM;

    // server info
    private static final String s_serverName = "Middleware";
    private static final String s_flightRMName = "Flight";
    private static final String s_carRMName = "Car";
    private static final String s_roomRMName = "Room";
    private static int s_rmiPort = 3021;
    private static String s_rmiPrefix = "group_21_";

    public Middleware() {
        super();
    }

    public void main(String args[]) {
        // list of RMS on startup, parse through and assign to the appropriate RM variable

        if (args.length < 3) {
            System.err.println("Usage: java Middleware <flightRM> <carRM> <roomRM>");
            System.exit(1);
        }

        String flighthost = args[0];
        String carhost = args[1];
        String roomhost = args[2];

        if (args.length >= 4) {
            //get port number, if not default to 3021
            try {
                s_rmiPort = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[3]);
                System.exit(1);
            }
        }

        try {
            // create connections to the RMS
            connectRM(flighthost, s_rmiPort, s_flightRMName);
            connectRM(carhost, s_rmiPort, s_carRMName);
            connectRM(roomhost, s_rmiPort, s_roomRMName);

            Middleware middleware = new Middleware();
            IResourceManager stub = (IResourceManager) java.rmi.server.UnicastRemoteObject.exportObject(middleware, 0);

            //bind middleware to RMI registry
            Registry registry;
            try {
                //check if registry exists
                registry = LocateRegistry.getRegistry(s_rmiPort);
                registry.list();
            } catch (RemoteException e) {
                //create if it doesn't load/exist
                registry = LocateRegistry.createRegistry(s_rmiPort);
            }

            registry.rebind(s_serverName, stub);
            System.out.println("'" + s_serverName + "' middleware server ready and bound to '" + s_serverName + "'")

        } catch (Exception e) {
            System.err.println("Middleware exception: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }


    }

    public void connectRM(String server, int port, String name) {
        //taken from Client
        try {
            boolean first = true;
            while (true) {
                try {
                    Registry registry = LocateRegistry.getRegistry(server, port);
                    m_resourceManager = (IResourceManager) registry.lookup(s_rmiPrefix + name);
                    System.out.println("Connected to '" + name + "' server [" + server + ":" + port + "/" + s_rmiPrefix + name + "]");
                    break;
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
    }




    @Override
    public boolean addFlight(int flightNum, int flightSeats, int flightPrice) throws RemoteException {
        return flight_RM.addFlight(flightNum, flightSeats, flightPrice);
    }

    @Override
    public boolean addCars(String location, int numCars, int price) throws RemoteException {
        return car_RM.addCars(location, numCars, price);
    }

    @Override
    public boolean addRooms(String location, int numRooms, int price) throws RemoteException {
        return room_RM.addRooms(location, numRooms, price);
    }


    @Override
    public int newCustomer() throws RemoteException {
        //TODO: figure out how to handle customers
        return 0;
    }

    @Override
    public boolean newCustomer(int cid) throws RemoteException {
        //TODO: figure out how to handle customers
        return false;
    }

    @Override
    public boolean deleteFlight(int flightNum) throws RemoteException {
        return flight_RM.deleteFlight(flightNum);
    }

    @Override
    public boolean deleteCars(String location) throws RemoteException {
        return car_RM.deleteCars(location);
    }

    @Override
    public boolean deleteRooms(String location) throws RemoteException {
        return room_RM.deleteRooms(location);
    }

    @Override
    public boolean deleteCustomer(int customerID) throws RemoteException {
        //TODO: figure out how to handle customers
        return false;
    }

    @Override
    public int queryFlight(int flightNumber) throws RemoteException {
        return flight_RM.queryFlight(flightNumber);
    }

    @Override
    public int queryCars(String location) throws RemoteException {
        return car_RM.queryCars(location);
    }

    @Override
    public int queryRooms(String location) throws RemoteException {
        return room_RM.queryRooms(location);
    }

    @Override
    public String queryCustomerInfo(int customerID) throws RemoteException {
        //TODO: figure out how to handle customers
        return null;
    }

    @Override
    public int queryFlightPrice(int flightNumber) throws RemoteException {
        return flight_RM.queryFlightPrice(flightNumber);
    }

    @Override
    public int queryCarsPrice(String location) throws RemoteException {
        return car_RM.queryCarsPrice(location);
    }

    @Override
    public int queryRoomsPrice(String location) throws RemoteException {
        return room_RM.queryRoomsPrice(location);
    }

    @Override
    public boolean reserveFlight(int customerID, int flightNumber) throws RemoteException {
        return flight_RM.reserveFlight(customerID, flightNumber);
    }

    @Override
    public boolean reserveCar(int customerID, String location) throws RemoteException {
        return car_RM.reserveCar(customerID, location);
    }

    @Override
    public boolean reserveRoom(int customerID, String location) throws RemoteException {
        return room_RM.reserveRoom(customerID, location);
    }

    @Override
    public boolean bundle(int customerID, Vector<String> flightNumbers, String location, boolean car, boolean room) throws RemoteException{
        boolean success = true;

        for (int i = 0; i < flightNumbers.size(); i++)
        {
            success = success && flight_RM.reserveFlight(customerID, Integer.parseInt(flightNumbers.get(i)));
        }

        if (car)
        {
            success = success && car_RM.reserveCar(customerID, location);
        }

        if (room)
        {
            success = success && room_RM.reserveRoom(customerID, location);
        }

        return success;
    }
}

