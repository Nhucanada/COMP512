package Client;

import Server.Interface.*;
import Server.TCP.TCPProxy;

import java.io.*;
import java.util.*;
import java.rmi.RemoteException;

/**
 * TCP Socket Client for COMP 512 Travel Reservation Information System.
 * Connects to the TCP Middleware using dynamic reflection proxies and executes CLI commands.
 */
public class TCPClient extends Client
{
	private static String s_serverHost = "localhost";
	private static int s_serverPort = 3021;

	public static void main(String args[])
	{
		if (args.length > 0)
		{
			s_serverHost = args[0];
		}
		if (args.length > 1)
		{
			try {
				s_serverPort = Integer.parseInt(args[1]);
			} catch (NumberFormatException e) {
				System.err.println("Invalid port number: " + args[1]);
				System.exit(1);
			}
		}
		if (args.length > 2)
		{
			System.err.println("Usage: java Client.TCPClient [server_hostname [server_port]]");
			System.exit(1);
		}

		try {
			TCPClient client = new TCPClient();
			client.connectServer();
			client.start();
		}
		catch (Exception e) {
			System.err.println("Client exception: Uncaught exception");
			e.printStackTrace();
			System.exit(1);
		}
	}

	public TCPClient()
	{
		super();
	}

	@Override
	public void connectServer()
	{
		connectServer(s_serverHost, s_serverPort);
	}

	public void connectServer(String host, int port)
	{
		boolean first = true;
		while (true)
		{
			try {
				IResourceManager proxy = TCPProxy.create(host, port, IResourceManager.class);
				// Probe the server to verify connectivity
				String name = proxy.getName();
				m_resourceManager = proxy;
				System.out.println("Connected to '" + name + "' TCP server [" + host + ":" + port + "]");
				break;
			}
			catch (Exception e) {
				if (first) {
					System.out.println("Waiting for TCP server [" + host + ":" + port + "]...");
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
	}
}
