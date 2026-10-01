package Server.TCP;

import Server.Common.*;
import Server.Interface.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Multi-threaded TCP Backend Server for ResourceManagers (Flights, Cars, Rooms).
 * Listens on a dedicated TCP port, dispatches concurrent requests across an
 * executor thread pool, and executes operations on the underlying ResourceManager.
 */
public class TCPResourceManager
{
	private String m_name;
	private int m_port;
	private ResourceManager m_rm;
	private ServerSocket m_serverSocket;
	private ExecutorService m_threadPool;
	private volatile boolean m_running = true;

	public TCPResourceManager(String name, int port)
	{
		this.m_name = name;
		this.m_port = port;
		this.m_rm = new ResourceManager(name);
		this.m_threadPool = Executors.newCachedThreadPool();
	}

	public void start()
	{
		try {
			m_serverSocket = new ServerSocket(m_port);
			Trace.info("TCPResourceManager['" + m_name + "'] listening on port " + m_port);
			System.out.println("'" + m_name + "' TCP resource manager server ready on port " + m_port);

			// Register shutdown hook for graceful termination
			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					TCPResourceManager.this.stop();
				}
			});

			while (m_running)
			{
				try {
					Socket clientSocket = m_serverSocket.accept();
					clientSocket.setTcpNoDelay(true);
					m_threadPool.execute(new RequestHandler(clientSocket));
				}
				catch (SocketException se) {
					if (!m_running) {
						break;
					}
					Trace.warn("SocketException in TCPResourceManager accept: " + se.getMessage());
				}
			}
		}
		catch (IOException e) {
			if (m_running) {
				System.err.println("TCPResourceManager server error: " + e.getMessage());
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

		if (m_threadPool != null && !m_threadPool.isShutdown()) {
			m_threadPool.shutdown();
			try {
				if (!m_threadPool.awaitTermination(3, TimeUnit.SECONDS)) {
					m_threadPool.shutdownNow();
				}
			} catch (InterruptedException e) {
				m_threadPool.shutdownNow();
			}
		}
		System.out.println("'" + m_name + "' TCP server stopped cleanly");
	}

	/**
	 * Worker task handling an individual client/middleware TCP connection.
	 */
	private class RequestHandler implements Runnable
	{
		private Socket m_socket;

		public RequestHandler(Socket socket)
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
					Trace.warn("TCPResourceManager received unexpected message: " + inputObj);
				}
			}
			catch (EOFException ignored) {
				// Client closed connection cleanly
			}
			catch (Exception e) {
				Trace.warn("Error handling TCP request on " + m_name + ": " + e.getMessage());
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
				java.lang.reflect.Method method = m_rm.getClass().getMethod(methodName, paramTypes);
				Object result = method.invoke(m_rm, args);
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

	public static void main(String[] args)
	{
		String serverName = "Server";
		int port = 3022;

		if (args.length > 0) {
			serverName = args[0];
		}
		if (args.length > 1) {
			try {
				port = Integer.parseInt(args[1]);
			} catch (NumberFormatException e) {
				System.err.println("Invalid port number: " + args[1]);
				System.exit(1);
			}
		}

		TCPResourceManager server = new TCPResourceManager(serverName, port);
		server.start();
	}
}
